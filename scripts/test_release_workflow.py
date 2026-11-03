"""Exercise the exact shell entry points called by the release workflow."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

SCRIPTS = Path(__file__).resolve().parent


@unittest.skipUnless(shutil.which('bash') and shutil.which('jq'), 'release runner requires Bash and jq')
class ReleaseWorkflowTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        (self.root / 'gradle.properties').write_text('mod_version=1.0.4\n')
        (self.root / 'stonecutter.properties.toml').write_text('["1.18.2"]\n["26.3"]\n')
        self.env = dict(os.environ, MOD='1.0.4', PUBLISH='false', GITHUB_REF_TYPE='branch',
                        GITHUB_REF_NAME='main', GITHUB_OUTPUT=str(self.root / 'output'),
                        GITHUB_SERVER_URL='https://github.com', GITHUB_REPOSITORY='owner/repo',
                        GITHUB_RUN_ID='123', GITHUB_SHA='abc123')
        for version in ['1.18.2', '26.3']:
            self.write(f'verification/versions/{version}/build/libs/AntiCrash-1.0.4+mc{version}.jar', version)
            self.write(f'verification/supervisor/build/libs/AntiCrash-Supervisor-1.0.4+mc{version}.jar', 'supervisor')
            self.report(f'versions/{version}', 7)
        self.report('supervisor', 3)

    def write(self, relative, content):
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)
        return path

    def report(self, component, tests, failures=0):
        return self.write(f'verification/{component}/build/test-results/test/TEST-fixture.xml',
                          f'<testsuite name="fixture" tests="{tests}" skipped="0" failures="{failures}" errors="0"></testsuite>\n')

    def run_script(self, name):
        return subprocess.run(['bash', str(SCRIPTS / name)], cwd=self.root, env=self.env,
                              text=True, capture_output=True, timeout=15)

    def test_complete_identity_hashes_and_evidence(self):
        result = self.run_script('assemble_release.sh')
        self.assertEqual(0, result.returncode, result.stderr)
        manifest = json.loads((self.root / 'release/artifacts.json').read_text())
        self.assertEqual(4, len(manifest['artifacts']))
        self.assertEqual(14, manifest['verification']['main_tests'])
        self.assertEqual(3, manifest['verification']['supervisor_tests'])
        self.assertEqual({'1.18.2', '26.3'}, {x['minecraft'] for x in manifest['artifacts']})
        self.assertEqual({'mod', 'supervisor'}, {x['kind'] for x in manifest['artifacts']})
        for entry in manifest['artifacts']:
            payload = (self.root / 'release' / entry['name']).read_bytes()
            self.assertEqual(hashlib.sha256(payload).hexdigest(), entry['sha256'])
            self.assertEqual(len(payload), entry['size'])

    def test_same_count_wrong_target_is_rejected(self):
        path = self.root / 'verification/versions/26.3/build/libs/AntiCrash-1.0.4+mc26.3.jar'
        path.rename(path.with_name('AntiCrash-1.0.4+mc99.9.jar'))
        self.assertNotEqual(0, self.run_script('assemble_release.sh').returncode)

    def test_missing_target_report_is_rejected(self):
        (self.root / 'verification/versions/26.3/build/test-results/test/TEST-fixture.xml').unlink()
        self.assertNotEqual(0, self.run_script('assemble_release.sh').returncode)

    def test_failed_test_evidence_is_rejected(self):
        self.report('versions/26.3', 7, failures=1)
        self.assertNotEqual(0, self.run_script('assemble_release.sh').returncode)

    def test_branch_cannot_publish(self):
        self.env['PUBLISH'] = 'true'
        result = self.run_script('release_version.sh')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('requires the matching release tag', result.stderr)

    def test_matching_tag_can_publish(self):
        self.env.update(PUBLISH='true', GITHUB_REF_TYPE='tag', GITHUB_REF_NAME='1.0.4')
        self.assertEqual(0, self.run_script('release_version.sh').returncode)
        self.assertEqual('mod=1.0.4\n', (self.root / 'output').read_text())

    def test_wrong_tag_is_rejected(self):
        self.env.update(PUBLISH='true', GITHUB_REF_TYPE='tag', GITHUB_REF_NAME='1.0.3')
        self.assertNotEqual(0, self.run_script('release_version.sh').returncode)

    def test_branch_dry_run_is_allowed(self):
        self.assertEqual(0, self.run_script('release_version.sh').returncode)
