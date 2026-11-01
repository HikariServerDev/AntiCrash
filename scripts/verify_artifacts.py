#!/usr/bin/env python3
"""Validate release JARs; optionally compare executable classes with a previous release."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile


def asm_classpath():
    cache = Path(os.environ.get("GRADLE_USER_HOME", str(Path.home() / ".gradle")))
    base = cache / "caches/modules-2/files-2.1/org.ow2.asm"
    versions = {p.parent.parent.name for p in (base / "asm").glob("*/*/*.jar")}
    versions &= {p.parent.parent.name for p in (base / "asm-tree").glob("*/*/*.jar")}
    if not versions:
        raise ValueError("Run Gradle first: ASM dependencies are required for bytecode verification")
    version = max(versions, key=lambda value: tuple(int(part) for part in value.split('.') if part.isdigit()))
    return os.pathsep.join(str(next((base / name / version).glob("*/*.jar"))) for name in ["asm", "asm-tree"])


def verify(root, minecraft, mod, collect=False, baseline=None, refactor_baseline=None):
    artifacts = []
    for supervisor in [False, True]:
        prefix = "AntiCrash-Supervisor" if supervisor else "AntiCrash"
        name = f"{prefix}-{mod}+mc{minecraft}.jar"
        source = root / minecraft / ("supervisor/build/libs" if supervisor else "build/libs") / name
        with zipfile.ZipFile(source) as jar:
            assert jar.testzip() is None, source
            names = jar.namelist()
            assert len(names) == len(set(names)), source
            license_name = "LICENSE_AntiCrash-Supervisor" if supervisor else "LICENSE_AntiCrash"
            assert jar.read(license_name) == (root / "LICENSE").read_bytes(), source
            assert not any("org/junit/" in entry or "OfflineGameAccessTransformer" in entry for entry in names)
            if supervisor:
                assert "Main-Class: com.atsukimc.anticrash.supervisor.SupervisorMain" in jar.read("META-INF/MANIFEST.MF").decode()
            else:
                metadata = json.loads(jar.read("fabric.mod.json"))
                assert metadata['id'] == 'anticrash' and metadata['version'] == mod
                assert metadata['environment'] == 'server' and metadata['license'] == 'LGPL-3.0-only'
                assert metadata['depends']['minecraft'] == '=' + minecraft
                assert jar.read(metadata['icon']) == (root / 'anticrash-icon.jpg').read_bytes()
                assert 'UserCacheMixin' in json.loads(jar.read('anticrash.mixins.json'))['mixins']
            if baseline:
                old = root / "build" / f"{prefix}-{baseline}+mc{minecraft}.jar"
                with zipfile.ZipFile(old) as previous:
                    assert set(names) == set(previous.namelist()), (source, "JAR entry membership changed")
                    for entry in names:
                        if entry.endswith('.class') or entry.endswith('/'):
                            continue
                        expected = previous.read(entry)
                        actual = jar.read(entry)
                        if entry.endswith('.json'):
                            if entry == 'fabric.mod.json':
                                expected = expected.replace(b'"1.0.0"', b'"1.0.1"')
                            assert json.loads(expected) == json.loads(actual), (source, entry)
                        elif entry.endswith('.properties'):
                            expected = expected.replace(b'command.status=AntiCrash 1.0.0', b'command.status=AntiCrash 1.0.1')
                            assert expected == actual, (source, entry)
                        else:
                            assert expected == actual, (source, entry)
                java = Path(os.environ['JAVA_HOME']) / 'bin/java' if os.environ.get('JAVA_HOME') else 'java'
                subprocess.run([str(java), '-Xmx512m', '-cp', asm_classpath(),
                                str(root / 'scripts/BytecodeParity.java'), str(old), str(source)], check=True)
            if refactor_baseline:
                old = refactor_baseline / name
                with zipfile.ZipFile(old) as previous:
                    before = {entry for entry in previous.namelist() if not entry.endswith('.class') and not entry.endswith('/')}
                    after = {entry for entry in names if not entry.endswith('.class') and not entry.endswith('/')}
                    assert before == after, (source, 'Resource membership changed')
                    for entry in before:
                        expected, actual = previous.read(entry), jar.read(entry)
                        if entry.endswith('.json'):
                            assert json.loads(expected) == json.loads(actual), (source, entry)
                        else:
                            assert expected == actual, (source, entry)
                java = Path(os.environ['JAVA_HOME']) / 'bin/java' if os.environ.get('JAVA_HOME') else 'java'
                subprocess.run([str(java), '-Xmx512m', '-cp', asm_classpath(),
                                str(root / 'scripts/RefactorParity.java'), str(old), str(source)], check=True)
        if collect:
            shutil.copy2(source, root / 'build' / name)
        artifacts.append(dict(name=name, sha256=hashlib.sha256(source.read_bytes()).hexdigest()))
    return dict(minecraft=minecraft, artifacts=artifacts)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--version', action='append', help='Minecraft target; repeat to select multiple')
    parser.add_argument('--collect', action='store_true', help='Copy verified release JARs into root build/')
    parser.add_argument('--baseline-version', choices=['1.0.0'], help='One-time 1.0.0 → 1.0.1 refactor equivalence gate')
    parser.add_argument('--refactor-baseline', type=Path, help='Directory of pre-cleanup 1.0.1 JARs for bounded method/resource auditing')
    args = parser.parse_args()
    if args.baseline_version and args.refactor_baseline:
        parser.error('Choose one baseline comparison mode')
    root = Path(__file__).resolve().parents[1]
    matrix = json.loads((root / 'versions.json').read_text())
    targets = [row['minecraft'] for row in matrix['versions'] if row['status'] == 'BUILD_VERIFIED']
    selected = args.version or targets
    if not set(selected) <= set(targets):
        raise ValueError('Only build-verified Minecraft targets have distributable JARs')
    for target in selected:
        print(json.dumps(verify(root, target, matrix['mod_version'], args.collect, args.baseline_version, args.refactor_baseline)), flush=True)


if __name__ == '__main__':
    main()
