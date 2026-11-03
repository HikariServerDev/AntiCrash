#!/usr/bin/env python3
"""Build canonical Stonecutter targets sequentially and retain per-target evidence."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

from build_model import load_targets, mod_version
from verify_artifacts import verify


def junit_results(directory):
    reports = sorted(directory.glob('TEST-*.xml'))
    if not reports:
        raise ValueError(f'No JUnit evidence: {directory}')
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    for path in reports:
        suite = ET.parse(path).getroot()
        for key in totals:
            totals[key] += int(suite.get(key, '0'))
    if totals['failures'] or totals['errors']:
        raise ValueError(f'Failed JUnit reports: {totals}')
    return totals


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    selection = parser.add_mutually_exclusive_group(required=True)
    selection.add_argument('--all', action='store_true')
    selection.add_argument('--version', action='append')
    parser.add_argument('--test-only', action='store_true')
    parser.add_argument('--baseline-dir', type=Path)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--qualification', action='store_true', help='Also compile the isolated probe where supported')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    targets = load_targets(root)
    selected = list(targets) if args.all else args.version
    if not set(selected) <= targets.keys():
        parser.error('Unknown Minecraft target')
    output = (args.output or root / 'build/matrix' / time.strftime('%Y%m%d-%H%M%S')).resolve()
    output.mkdir(parents=True, exist_ok=True)
    environment = os.environ.copy()
    environment['JAVA_TOOL_OPTIONS'] = (environment.get('JAVA_TOOL_OPTIONS', '') + ' -XX:ActiveProcessorCount=2').strip()
    if not environment.get('JAVA_HOME'):
        bundled = sorted((root / '.toolchains').glob('jdk-25*'))
        if bundled:
            environment['JAVA_HOME'] = str(bundled[-1])
    # Artifact inspection launches Java too, using the same bounded environment.
    os.environ.update({key: environment[key] for key in ['JAVA_HOME', 'JAVA_TOOL_OPTIONS'] if key in environment})
    summary = dict(mod_version=mod_version(root), targets=[], supervisor=None)
    for index, version in enumerate(selected):
        row = dict(minecraft=version, build='NOT_RUN', test='NOT_RUN', artifacts='NOT_RUN')
        started = time.monotonic()
        tasks = [f':{version}:cleanTest', f':{version}:test' if args.test_only else f':{version}:build']
        if index == 0:
            tasks = [':supervisor:cleanTest', ':supervisor:test', *tasks]
        if args.qualification and targets[version].qualification:
            tasks.append(f':{version}:probeJar')
        wrapper = str(root / ('gradlew.bat' if os.name == 'nt' else 'gradlew'))
        command = [wrapper, '--no-daemon', '--no-build-cache', '--max-workers=1', f'-PmcVersion={version}', *tasks]
        row['command'] = command
        print(f'[{index + 1}/{len(selected)}] {version}', flush=True)
        try:
            with (output / f'{version}.log').open('w') as log:
                result = subprocess.run(command, cwd=root, env=environment, stdout=log, stderr=subprocess.STDOUT)
            if result.returncode:
                raise RuntimeError(f'Gradle exit {result.returncode}; see {output / (version + ".log")}')
            row['junit'] = junit_results(root / f'versions/{version}/build/test-results/test')
            row['test'] = 'PASS'
            if index == 0:
                summary['supervisor'] = junit_results(root / 'supervisor/build/test-results/test')
            if not args.test_only:
                row['build'] = 'PASS'
                with (output / f'{version}-parity.log').open('w') as log:
                    # Keep Java output (method-level parity failures included) in the target evidence.
                    arguments = [sys.executable, 'scripts/verify_artifacts.py', '--version', version, '--collect']
                    if args.baseline_dir:
                        arguments += ['--baseline-version', mod_version(root), '--baseline-dir', str(args.baseline_dir.resolve()), '--allow-tool-metadata']
                    checked = subprocess.run(arguments, cwd=root, env=environment, stdout=log, stderr=subprocess.STDOUT)
                if checked.returncode:
                    raise RuntimeError(f'Artifact/parity inspection failed: {version}')
                row['artifacts'] = 'PASS'
                row['jars'] = verify(root, version, mod_version(root))['artifacts']
            row['result'] = 'PASS'
        except (OSError, ValueError, RuntimeError) as error:
            row['result'] = 'FAIL'
            row['error'] = str(error)
            print(str(error), file=sys.stderr, flush=True)
        row['seconds'] = round(time.monotonic() - started, 2)
        summary['targets'].append(row)
        (output / 'summary.json').write_text(json.dumps(summary, indent=2) + '\n')
        print(f"  {row['result']} ({row['seconds']}s)", flush=True)
    print(f'Evidence: {output}', flush=True)
    return 0 if all(row['result'] == 'PASS' for row in summary['targets']) else 1


if __name__ == '__main__':
    sys.exit(main())
