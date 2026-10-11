#!/usr/bin/env python3
"""Refresh the port matrix from version-local sources or verified offline build evidence.

This changes project metadata only. It never deploys, launches, or contacts a Minecraft server.
"""
import argparse
import json
import re
from pathlib import Path
from multiversion import ROOT, atomic_json, inspect_jar, read_targets, test_totals, verified_build_evidence


def record(version, state, matrix):
    advertised = next(row for row in matrix['versions'] if row['minecraft'] == version)
    directory = ROOT / version
    properties = dict(line.split('=', 1) for line in (directory / 'gradle.properties').read_text().splitlines()
                      if '=' in line and not line.startswith('#'))
    if properties['minecraft_version'] != version or not (directory / 'src/main/java').is_dir():
        raise ValueError('Missing/misidentified sources: ' + version)
    row = dict(advertised)
    row.update(status=state, integration_verified=False,
               java_target=max(17, row['minimum_game_java']),
               loader_version=properties['loader_version'], yarn_pinned=properties.get('yarn_mappings'),
               fabric_api_pinned=properties.get('fabric_version'))
    row['blocker'] = None if state in ('IMPLEMENTED', 'BUILD_VERIFIED') else 'Version adapter under investigation'
    capsule = directory / 'src/main/java/com/atsukimc/anticrash/runtime/RuntimeCapsule.java'
    row['runtime_capsule_version'] = int(re.search(r'CURRENT_FORMAT_VERSION\s*=\s*(\d+)', capsule.read_text())[1])
    row['minecraft_namespace'] = 'YARN' if properties.get('yarn_mappings') else 'MOJANG_UNOBFUSCATED'
    if version == '26.1': row['source_base'] = '1.21.11'
    elif version in ('26.2', '26.3'): row['source_base'] = '26.1'
    if state == 'BUILD_VERIFIED':
        jar = directory / f'build/libs/AntiCrash-{matrix["mod_version"]}+mc{version}.jar'
        inspect_jar(jar, version, matrix['mod_version'], ROOT / 'assets/anticrash-icon.jpg')
        supervisor_jar = directory / f'supervisor/build/libs/AntiCrash-Supervisor-{matrix["mod_version"]}+mc{version}.jar'
        main, supervisor = verified_build_evidence(directory, jar, supervisor_jar)
        row.update(main_tests=main['tests'], supervisor_tests=supervisor['tests'])
    atomic_json(directory / 'target.json', row)
    advertised.clear()
    advertised.update(row)
    evidence = (f"main {row['main_tests']} / supervisor {row['supervisor_tests']} tests、failure/error/skip 0。"
                if state == 'BUILD_VERIFIED' else '最終test/build・成果物検査は未完了です。')
    (directory / 'README.md').write_text(
        f"# AntiCrash — Minecraft {version}\n\n状態: **{state}**。\n\n"
        f"{evidence}\nMinecraft起動・Mixin適用・Carpet・TMC復旧の実機検証は未実施です。\n\n"
        f"Java target: {row['java_target']}、Loader: {row['loader_version']}、namespace: {row['minecraft_namespace']}。\n"
        f"Runtime Capsule: v{row['runtime_capsule_version']}。\n"
        "この版専用のsource/test/buildを持ちます。Minecraft版が異なるcheckpointの直接復元は禁止です。\n\n"
        "```sh\n./gradlew test build --no-daemon --max-workers=1\n```\n\n"
        f"配布名: `AntiCrash-1.0.0+mc{version}.jar` / `AntiCrash-Supervisor-1.0.0+mc{version}.jar`。\n"
        "成功したbuildだけを共通`../build/`へ集約します。\n\n"
        "[全版共通README](../README.md)、[版別内部実装](../docs/PORT-ADAPTERS.md)、"
        "[詳細状態](target.json)を参照してください。\n\nProduction server modified: NO\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('status', choices=['PORT_IN_PROGRESS', 'IMPLEMENTED', 'BUILD_VERIFIED'])
    parser.add_argument('versions', nargs='+')
    args = parser.parse_args()
    read_targets()  # validate exact version names and reject symlink/path traversal first
    matrix = json.loads((ROOT / 'versions.json').read_text())
    for version in args.versions:
        record(version, args.status, matrix)
        atomic_json(ROOT / 'versions.json', matrix)
        print(version + ': ' + args.status, flush=True)


if __name__ == '__main__':
    main()
