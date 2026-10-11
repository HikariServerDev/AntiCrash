#!/usr/bin/env python3
"""Sequential offline builds and verified artifact catalog; never operates a server."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
BUILDABLE = {'IMPLEMENTED', 'BUILD_VERIFIED'}

def build_environment(target, root=ROOT, environ=None):
    """Use a project-local JDK only for Java-25 targets; never alter the user's global JVM."""
    env = dict(os.environ if environ is None else environ)
    if target.get('minimum_game_java', 17) < 25:
        return env
    candidates = [Path(env[key]) for key in ('AC_JAVA25_HOME', 'JAVA_HOME') if env.get(key)]
    candidates.extend(sorted((root / '.toolchains').glob('jdk-*'), reverse=True))
    for candidate in candidates:
        release = candidate / 'release'
        if not release.is_file() or not (candidate / 'bin/java').is_file():
            continue
        match = re.search(r'^JAVA_VERSION="(\d+)', release.read_text(), re.MULTILINE)
        if match and int(match[1]) >= 25:
            env['JAVA_HOME'] = str(candidate.resolve())
            return env
    raise ValueError('Java 25 required. Set AC_JAVA25_HOME to an installed JDK 25 or use .toolchains/jdk-*')

def read_targets(root=ROOT):
    matrix = json.loads((root / 'versions.json').read_text())
    targets = []
    seen = set()
    for advertised in matrix['versions']:
        version = advertised['minecraft']
        if not re.fullmatch(r'\d+\.\d+(?:\.\d+)?', version) or version in seen:
            raise ValueError('Invalid/duplicate version: ' + version)
        seen.add(version)
        directory = root / version
        if directory.is_symlink() or directory.resolve().parent != root.resolve():
            raise ValueError('Unsafe version directory: ' + version)
        target = json.loads((directory / 'target.json').read_text())
        if target['minecraft'] != version:
            raise ValueError('Target/version mismatch: ' + version)
        targets.append(target)
    return matrix['mod_version'], targets

def sha256(path):
    digest = hashlib.sha256()
    with path.open('rb') as source:
        for block in iter(lambda: source.read(65536), b''):
            digest.update(block)
    return digest.hexdigest()

def test_totals(directory):
    reports = list(directory.glob('TEST-*.xml'))
    if not reports:
        raise ValueError('No test results: ' + str(directory))
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    for report in reports:
        suite = ET.parse(report).getroot()
        for field in totals:
            totals[field] += int(suite.get(field, '0'))
    if any(totals[field] for field in ('failures', 'errors', 'skipped')):
        raise ValueError('Failed/incomplete tests: ' + str(directory))
    return totals

def verified_build_evidence(directory, mod, supervisor):
    """Collect and promotion both reject stale/failed results, including Supervisor changes."""
    counts = []
    for project, artifact, minimum in ((directory, mod, 248), (directory / 'supervisor', supervisor, 13)):
        reports = project / 'build/test-results/test'
        total = test_totals(reports)
        if total['tests'] < minimum:
            raise ValueError('Incomplete baseline test coverage: ' + str(project))
        test_time = min(path.stat().st_mtime for path in reports.glob('TEST-*.xml'))
        for source in (project / 'src/test').rglob('*'):
            if source.is_file() and source.stat().st_mtime > test_time:
                raise ValueError('Tests newer than executed evidence: ' + str(source))
        inputs = list((project / 'src/main').rglob('*')) + [project / 'build.gradle']
        if project == directory:
            inputs += [directory / 'gradle.properties']
        artifact_time = artifact.stat().st_mtime
        for source in inputs:
            if source.is_file() and source.stat().st_mtime > test_time:
                raise ValueError('Source newer than executed tests: ' + str(source))
            if source.is_file() and source.stat().st_mtime > artifact_time:
                raise ValueError('Source newer than artifact: ' + str(source))
        counts.append(total)
    return counts

def inspect_jar(path, version, mod_version, icon=None):
    if path.is_symlink() or not path.is_file():
        raise ValueError('Not an independent JAR file: ' + str(path))
    with zipfile.ZipFile(path) as jar:
        broken = jar.testzip()
        if broken:
            raise ValueError('Corrupt ZIP entry: ' + broken)
        metadata = json.loads(jar.read('fabric.mod.json'))
        if metadata.get('id') != 'anticrash' or metadata.get('version') != mod_version:
            raise ValueError('Mod identity mismatch')
        if metadata['depends']['minecraft'] != '=' + version:
            raise ValueError('Minecraft dependency must match exactly: ' + version)
        if metadata.get('icon') != 'assets/anticrash/icon.jpg':
            raise ValueError('Mod icon not configured')
        if icon and hashlib.sha256(jar.read(metadata['icon'])).hexdigest() != sha256(icon):
            raise ValueError('Icon differs from supplied image')
        if 'fabric.mod.json' in jar.namelist() and any(n.startswith('com/atsukimc/anticrash/probe/') for n in jar.namelist()):
            raise ValueError('Test probe must not be distributed as the mod')
    return {'name': path.name, 'sha256': sha256(path), 'bytes': path.stat().st_size}

def atomic_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, name = tempfile.mkstemp(prefix=path.name + '.', dir=path.parent)
    try:
        with os.fdopen(fd, 'w') as output:
            json.dump(value, output, indent=2, ensure_ascii=False)
            output.write('\n')
        os.replace(name, path)
    finally:
        if os.path.exists(name):
            os.unlink(name)  # exclusively this tool's unique temporary file

def collect(root, version, mod_version):
    # The expected filenames are explicit: old/development/probe JARs cannot be selected by glob.
    mod = root / version / 'build/libs' / f'AntiCrash-{mod_version}+mc{version}.jar'
    supervisor = root / version / 'supervisor/build/libs' / f'AntiCrash-Supervisor-{mod_version}+mc{version}.jar'
    artifact = inspect_jar(mod, version, mod_version, root / 'assets/anticrash-icon.jpg')
    verified_build_evidence(root / version, mod, supervisor)
    with zipfile.ZipFile(supervisor) as jar:
        if jar.testzip() or 'com/atsukimc/anticrash/supervisor/SupervisorMain.class' not in jar.namelist():
            raise ValueError('Invalid Supervisor artifact')
    output = root / 'build'
    output.mkdir(exist_ok=True)
    for source in (mod, supervisor):
        target = output / source.name
        if target.is_symlink():
            raise ValueError('Refusing symbolic-link artifact destination')
        shutil.copy2(source, target)
        if sha256(source) != sha256(target):
            raise ValueError('Artifact copy differs')
    return {'minecraft': version, 'artifact': artifact,
            'supervisor': {'name': supervisor.name, 'sha256': sha256(supervisor)},
            'integration_verified': False}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['status', 'build', 'collect'])
    selection = parser.add_mutually_exclusive_group()
    selection.add_argument('--version')
    selection.add_argument('--available', action='store_true', help='Select implemented ports only, not all requested versions')
    args = parser.parse_args()
    mod_version, targets = read_targets()
    if args.version and args.version not in {t['minecraft'] for t in targets}:
        parser.error('Unknown version')
    selected = [t for t in targets if (not args.version or t['minecraft'] == args.version)
                and (not args.available or t['status'] in BUILDABLE)]
    if args.command == 'status':
        for target in selected:
            print(f"{target['minecraft']:8} {target['status']:20} runtime={target['integration_verified']}")
        return 0
    results = []
    for target in selected:
        version = target['minecraft']
        if target['status'] not in BUILDABLE:
            results.append({'minecraft': version, 'result': 'BLOCKED_NOT_PORTED', 'reason': target['blocker']})
            print(version + ': BLOCKED_NOT_PORTED', flush=True)
            continue
        if args.command == 'build':
            log = ROOT / 'build/logs' / (version + '.log')
            log.parent.mkdir(parents=True, exist_ok=True)
            print(version + ': test/build (one worker)', flush=True)
            with log.open('w') as output:
                try:
                    child = subprocess.run(['./gradlew', 'test', 'build', '--no-daemon', '--max-workers=1'],
                            cwd=ROOT / version, stdout=output, stderr=subprocess.STDOUT,
                            env=build_environment(target))
                except (OSError, ValueError) as error:
                    results.append({'minecraft': version, 'result': 'BUILD_ENVIRONMENT_REJECTED',
                                    'reason': str(error)})
                    continue
            if child.returncode:
                results.append({'minecraft': version, 'result': 'BUILD_FAILED', 'log': str(log)})
                continue
        try:
            results.append({'result': 'COLLECTED', **collect(ROOT, version, mod_version)})
        except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
            results.append({'minecraft': version, 'result': 'ARTIFACT_REJECTED', 'reason': str(error)})
    report = {'requested_scope': 'available' if args.available else args.version or 'all',
              'production_modified': False, 'results': results}
    atomic_json(ROOT / 'build' / (args.command + '-report.json'), report)
    print(json.dumps(report, indent=2, ensure_ascii=False))
    return 0 if results and all(r['result'] == 'COLLECTED' for r in results) else 1

if __name__ == '__main__':
    raise SystemExit(main())
