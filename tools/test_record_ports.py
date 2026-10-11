import sys
from pathlib import Path
import tempfile
import unittest
import os

sys.path.insert(0, str(Path(__file__).parent))
import record_ports


class BuildEvidenceTests(unittest.TestCase):
    def fixture(self, root):
        mod=root/'mod.jar'; supervisor=root/'supervisor.jar'
        for project,artifact,count in ((root,mod,248),(root/'supervisor',supervisor,13)):
            reports=project/'build/test-results/test'; reports.mkdir(parents=True)
            report=reports/'TEST-fixture.xml'; report.write_text(f'<testsuite tests="{count}"/>')
            os.utime(report,(10,10))
            artifact.write_bytes(b'artifact evidence fixture'); os.utime(artifact,(20,20))
        return mod,supervisor

    def test_collection_requires_current_main_and_supervisor_evidence(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder); mod,supervisor=self.fixture(root)
            counts=record_ports.verified_build_evidence(root,mod,supervisor)
            self.assertEqual([248,13],[row['tests'] for row in counts])
            source=root/'supervisor/src/main/java/Changed.java'; source.parent.mkdir(parents=True)
            source.write_text('class Changed {}'); os.utime(source,(30,30))
            with self.assertRaises(ValueError): record_ports.verified_build_evidence(root,mod,supervisor)

    def test_collection_rejects_tests_newer_than_the_result(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder); mod,supervisor=self.fixture(root)
            source=root/'src/test/java/ChangedTest.java'; source.parent.mkdir(parents=True)
            source.write_text('class ChangedTest {}'); os.utime(source,(30,30))
            with self.assertRaises(ValueError): record_ports.verified_build_evidence(root,mod,supervisor)

    def test_rebuilt_jar_without_retested_main_source_cannot_be_promoted(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder); mod,supervisor=self.fixture(root)
            source=root/'src/main/java/Changed.java'; source.parent.mkdir(parents=True)
            source.write_text('class Changed {}'); os.utime(source,(15,15))
            # XML at 10, source at 15, a freshly rebuilt JAR at 20: compilation is not a test.
            with self.assertRaises(ValueError): record_ports.verified_build_evidence(root,mod,supervisor)

    def test_collection_rejects_failed_supervisor_tests(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder); mod,supervisor=self.fixture(root)
            (root/'supervisor/build/test-results/test/TEST-fixture.xml').write_text('<testsuite tests="13" failures="1"/>')
            with self.assertRaises(ValueError): record_ports.verified_build_evidence(root,mod,supervisor)
    def test_missing_results_cannot_be_promoted(self):
        with tempfile.TemporaryDirectory() as folder:
            with self.assertRaises(ValueError): record_ports.test_totals(Path(folder))

    def test_failed_or_skipped_results_cannot_be_promoted(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'TEST-fixture.xml'
            for field in ('failures', 'errors', 'skipped'):
                path.write_text(f'<testsuite tests="248" {field}="1"/>')
                with self.assertRaises(ValueError): record_ports.test_totals(Path(folder))

    def test_passing_evidence_counts_every_suite(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / 'TEST-a.xml').write_text('<testsuite tests="248" failures="0" errors="0" skipped="0"/>')
            (root / 'TEST-b.xml').write_text('<testsuite tests="3" failures="0" errors="0" skipped="0"/>')
            self.assertEqual(251, record_ports.test_totals(root)['tests'])


if __name__ == '__main__': unittest.main()
