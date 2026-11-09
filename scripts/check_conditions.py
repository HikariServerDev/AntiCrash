#!/usr/bin/env python3
"""Compare changed Java sources against a Git baseline with the cached Stonecutter engine."""
import argparse
import hashlib
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile

from build_model import load_targets


def only_jar(paths, label):
    jars = sorted(paths)
    # A Wrapper URL change creates another cache directory for the same distribution.
    # Accept byte-identical copies only; different binaries remain an ambiguous classpath.
    if len(jars) > 1 and all(jar.is_file() for jar in jars):
        digests = set()
        for jar in jars:
            with jar.open('rb') as source:
                digests.add(hashlib.file_digest(source, 'sha256').digest())
        if len(digests) == 1:
            return jars[0]
    if len(jars) != 1:
        raise ValueError(f'Expected one cached {label} JAR, found {len(jars)}; pass its path explicitly')
    return jars[0]


def cached_libraries(root):
    cache = Path(os.environ.get('GRADLE_USER_HOME', Path.home() / '.gradle'))
    settings = (root / 'settings.gradle.kts').read_text()
    plugin = re.search(r'id\("dev.kikugie.stonecutter"\) version "([^"]+)"', settings).group(1)
    wrapper = (root / 'gradle/wrapper/gradle-wrapper.properties').read_text()
    gradle = re.search(r'gradle-([0-9.]+)-bin\.zip', wrapper).group(1)
    return (
        list((cache / f'caches/modules-2/files-2.1/dev.kikugie/stonecutter/{plugin}').glob(f'*/stonecutter-{plugin}.jar')),
        list((cache / f'wrapper/dists/gradle-{gradle}-bin').glob(f'*/gradle-{gradle}/lib/kotlin-stdlib-*.jar')),
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline', default='HEAD', help='Commit before the conditional refactor')
    parser.add_argument('--version', action='append', help='Limit the comparison to these targets')
    parser.add_argument('--stonecutter-jar', type=Path)
    parser.add_argument('--kotlin-jar', type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    targets = load_targets(root)
    versions = args.version or list(targets)
    if any(version not in targets for version in versions):
        parser.error('Every requested version must exist in the target matrix')
    def git(*arguments):
        return subprocess.check_output(['git', *arguments], cwd=root)
    baseline = git('rev-parse', '--verify', '--end-of-options', args.baseline + '^{commit}').decode().strip()
    untracked = git('ls-files', '--others', '--exclude-standard', '-z', '--', 'src').decode().split('\0')
    if any(name.endswith('.java') for name in untracked):
        raise ValueError('New Java sources have no baseline; review them separately before comparison')
    changed = git('diff', '--name-only', '-z', baseline, '--', 'src').decode().split('\0')
    sources = [name for name in changed if name.endswith('.java')]
    if not sources:
        print('No changed Java sources to compare.')
        return
    stonecutter, kotlin = cached_libraries(root)
    jars = [args.stonecutter_jar or only_jar(stonecutter, 'Stonecutter'),
            args.kotlin_jar or only_jar(kotlin, 'Kotlin')]
    if not all(path.is_file() for path in jars):
        raise ValueError('Processor classpath contains a missing JAR')
    java = str(Path(os.environ['JAVA_HOME']) / 'bin/java') if os.environ.get('JAVA_HOME') else shutil.which('java')
    if not java:
        raise ValueError('Java is required to run the cached processor')
    with tempfile.TemporaryDirectory(prefix='anticrash-condition-parity-') as temporary:
        before = Path(temporary)
        for name in sources:
            saved = before / name
            saved.parent.mkdir(parents=True, exist_ok=True)
            saved.write_bytes(git('show', f'{baseline}:{name}'))
        command = [java, '-Xmx512m', '-XX:ActiveProcessorCount=2', '--class-path',
                   os.pathsep.join(str(path.resolve()) for path in jars),
                   str(root / 'scripts/ConditionParity.java'), str(before), str(root), *versions]
        subprocess.run(command, cwd=root, check=True)


if __name__ == '__main__':
    main()
