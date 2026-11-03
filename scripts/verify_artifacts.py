#!/usr/bin/env python3
"""Validate release JARs; optionally compare executable classes with a previous release."""
import argparse
from collections import Counter
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile

from build_model import load_targets, mod_version


STONECUTTER_MANIFEST_KEYS = {'Stonecutter-Plugin-Version': '0.9.8', 'Stonecutter-Gradle-Version': '9.6.0'}

TOOL_MANIFEST_KEYS = {'Fabric-Gradle-Version', 'Fabric-Loom-Version', 'Fabric-Tiny-Remapper-Version'}


def manifest_attributes(content):
    """Unfold the main section without ignoring runtime-relevant manifest attributes."""
    attributes = {}
    previous = None
    for line in content.decode('utf-8').splitlines():
        if not line:
            continue
        if line.startswith(' '):
            if previous is None:
                raise ValueError('Manifest continuation without an attribute')
            attributes[previous] += line[1:]
        else:
            key, value = line.split(': ', 1)
            if key in attributes:
                raise ValueError('Duplicate manifest attribute: ' + key)
            attributes[key] = value
            previous = key
    return attributes


def compare_build_manifest(expected, actual, minecraft):
    before = manifest_attributes(expected)
    after = manifest_attributes(actual)
    node_attributes = dict(STONECUTTER_MANIFEST_KEYS, **{
        'Stonecutter-Node-Project': minecraft, 'Stonecutter-Node-Version': minecraft})
    for key, value in node_attributes.items():
        if key in after and key not in before:
            if after.pop(key) != value:
                raise AssertionError('Unexpected build manifest value: ' + key)
    for key in TOOL_MANIFEST_KEYS:
        before.pop(key, None)
        after.pop(key, None)
    if before != after:
        raise AssertionError('Runtime manifest attributes changed')


def compare_refmap(expected, actual):
    """Compare runtime targets while allowing Yarn/Mojang source-name changes."""
    before = json.loads(expected)
    after = json.loads(actual)
    if set(before) != set(after):
        raise AssertionError('Mixin refmap sections changed')

    def compare_tables(old_table, new_table, label):
        if set(old_table) != set(new_table):
            raise AssertionError(f'Mixin refmap owners changed: {label}')
        for owner in old_table:
            old_entries = old_table[owner]
            new_entries = new_table[owner]
            if not isinstance(old_entries, dict) or not isinstance(new_entries, dict):
                raise AssertionError(f'Invalid Mixin refmap owner: {label}/{owner}')
            if Counter(old_entries.values()) != Counter(new_entries.values()):
                raise AssertionError(f'Mixin runtime targets changed: {label}/{owner}')

    compare_tables(before.get('mappings', {}), after.get('mappings', {}), 'mappings')
    old_data = before.get('data', {})
    new_data = after.get('data', {})
    if set(old_data) != set(new_data):
        raise AssertionError('Mixin refmap namespaces changed')
    for namespace in old_data:
        compare_tables(old_data[namespace], new_data[namespace], namespace)


def asm_classpath():
    cache = Path(os.environ.get("GRADLE_USER_HOME", str(Path.home() / ".gradle")))
    base = cache / "caches/modules-2/files-2.1/org.ow2.asm"
    versions = {p.parent.parent.name for p in (base / "asm").glob("*/*/*.jar")}
    versions &= {p.parent.parent.name for p in (base / "asm-tree").glob("*/*/*.jar")}
    if not versions:
        raise ValueError("Run Gradle first: ASM dependencies are required for bytecode verification")
    version = max(versions, key=lambda value: tuple(int(part) for part in value.split('.') if part.isdigit()))
    return os.pathsep.join(str(next((base / name / version).glob("*/*.jar"))) for name in ["asm", "asm-tree"])


def verify(root, minecraft, mod, collect=False, baseline=None, refactor_baseline=None, baseline_dir=None,
           allow_tool_metadata=False):
    artifacts = []
    for supervisor in [False, True]:
        prefix = "AntiCrash-Supervisor" if supervisor else "AntiCrash"
        name = f"{prefix}-{mod}+mc{minecraft}.jar"
        source = root / ("supervisor/build/libs" if supervisor else f"versions/{minecraft}/build/libs") / name
        with zipfile.ZipFile(source) as jar:
            assert jar.testzip() is None, source
            names = jar.namelist()
            assert len(names) == len(set(names)), source
            license_name = "LICENSE_AntiCrash-Supervisor" if supervisor else "LICENSE_AntiCrash"
            assert jar.read(license_name) == (root / "LICENSE").read_bytes(), source
            assert not any("org/junit/" in entry or "OfflineGameAccessTransformer" in entry for entry in names)
            assert not any(entry.startswith('com/atsukimc/anticrash/probe/') for entry in names)
            if supervisor:
                assert "Main-Class: com.atsukimc.anticrash.supervisor.SupervisorMain" in jar.read("META-INF/MANIFEST.MF").decode()
            else:
                metadata = json.loads(jar.read("fabric.mod.json"))
                assert metadata['id'] == 'anticrash' and metadata['version'] == mod
                assert metadata['environment'] == 'server' and metadata['license'] == 'LGPL-3.0-only'
                assert metadata['depends']['minecraft'] == '=' + minecraft
                assert jar.read(metadata['icon']) == (root / 'src/main/resources' / metadata['icon']).read_bytes()
                assert 'UserCacheMixin' in json.loads(jar.read('anticrash.mixins.json'))['mixins']
            if baseline:
                old = (baseline_dir or root / 'dist' / baseline) / f"{prefix}-{baseline}+mc{minecraft}.jar"
                with zipfile.ZipFile(old) as previous:
                    assert set(names) == set(previous.namelist()), (source, "JAR entry membership changed")
                    for entry in names:
                        if entry.endswith('.class') or entry.endswith('/'):
                            continue
                        expected = previous.read(entry)
                        actual = jar.read(entry)
                        if entry.endswith('.json'):
                            if entry == 'fabric.mod.json':
                                expected = expected.replace(('"' + baseline + '"').encode(), ('"' + mod + '"').encode())
                            if entry == 'AntiCrash-refmap.json':
                                compare_refmap(expected, actual)
                            else:
                                assert json.loads(expected) == json.loads(actual), (source, entry)
                        elif entry.endswith('.properties'):
                            expected = expected.replace(('command.status=AntiCrash ' + baseline).encode(), ('command.status=AntiCrash ' + mod).encode())
                            assert expected == actual, (source, entry)
                        elif entry == 'META-INF/MANIFEST.MF' and allow_tool_metadata:
                            compare_build_manifest(expected, actual, minecraft)
                        else:
                            assert expected == actual, (source, entry)
                java = Path(os.environ['JAVA_HOME']) / 'bin/java' if os.environ.get('JAVA_HOME') else 'java'
                subprocess.run([str(java), '-Xmx512m', '-cp', asm_classpath(),
                                str(root / 'scripts/BytecodeParity.java'), str(old), str(source), baseline, mod], check=True)
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
            destination = root / 'dist/refactor' / mod
            destination.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, destination / name)
        artifacts.append(dict(name=name, sha256=hashlib.sha256(source.read_bytes()).hexdigest()))
    return dict(minecraft=minecraft, artifacts=artifacts)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--version', action='append', help='Minecraft target; repeat to select multiple')
    parser.add_argument('--collect', action='store_true', help='Copy verified candidate JARs into ignored dist/refactor/<mod-version>/')
    parser.add_argument('--baseline-version', help='Previous Mod version for executable/resource parity')
    parser.add_argument('--baseline-dir', type=Path, help='Previous release JAR directory; defaults to dist/<baseline-version>/')
    parser.add_argument('--allow-tool-metadata', action='store_true', help='Allow only Gradle/Loom/TinyRemapper version fields to differ')
    parser.add_argument('--refactor-baseline', type=Path, help='Directory of pre-cleanup 1.0.1 JARs for bounded method/resource auditing')
    args = parser.parse_args()
    if args.baseline_version and args.refactor_baseline:
        parser.error('Choose one baseline comparison mode')
    if args.baseline_dir and not args.baseline_version:
        parser.error('--baseline-dir requires --baseline-version')
    root = Path(__file__).resolve().parents[1]
    release = mod_version(root)
    targets = list(load_targets(root))
    selected = args.version or targets
    if not set(selected) <= set(targets):
        raise ValueError('Only build-verified Minecraft targets have distributable JARs')
    for target in selected:
        print(json.dumps(verify(root, target, release, args.collect, args.baseline_version,
                                args.refactor_baseline, args.baseline_dir, args.allow_tool_metadata)), flush=True)


if __name__ == '__main__':
    main()
