#!/usr/bin/env python3
"""Validate canonical source ownership, matrix inputs, and unchanged release-version labels."""
import json
from pathlib import Path
import re

from build_model import load_targets, mod_version


def validate_release(root, release):
    base = root / 'src/main/java/com/atsukimc/anticrash'
    source = (base / 'AntiCrashMod.java').read_text(encoding='utf-8')
    if re.findall(r'\bVERSION\s*=\s*"([^"]+)"', source) != [release]:
        raise ValueError('Runtime VERSION does not match gradle.properties')
    if f'# AntiCrash {release} - complete' not in (base / 'config/AntiCrashConfig.java').read_text(encoding='utf-8'):
        raise ValueError('Configuration version header is stale')
    for language in ['ja', 'en']:
        path = root / f'src/main/resources/anticrash/messages_{language}.properties'
        if f'command.status=AntiCrash {release}\\n' not in path.read_text(encoding='utf-8'):
            raise ValueError(f'Status version header is stale: {language}')


def validate_ownership(root, targets):
    if (root / 'common').exists() or (root / 'shared').exists():
        raise ValueError('Legacy source trees must not coexist with canonical src/')
    for version in targets:
        if (root / version).exists():
            raise ValueError(f'Legacy version directory: {version}')
    for path in (root / 'versions').glob('*/src/**/*'):
        if path.is_file():
            raise ValueError(f'Version-local source overrides are prohibited: {path}')
    for path in (root / 'src').rglob('*'):
        if path.is_symlink():
            raise ValueError(f'Linked canonical input: {path}')
        if path.is_file() and path.suffix == '.java':
            scope = path.relative_to(root / 'src').parts[:2]
            if scope not in [('main', 'java'), ('test', 'java'), ('qualification', 'java')]:
                raise ValueError(f'Unexpected Java source root: {path}')


def validate(root):
    targets = load_targets(root)
    validate_ownership(root, targets)
    validate_release(root, mod_version(root))
    published = root / 'versions.json'
    if published.exists():
        rows = json.loads(published.read_text(encoding='utf-8'))['versions']
        previous = {row['minecraft'] for row in rows if row.get('status') == 'BUILD_VERIFIED'}
        if not previous <= targets.keys():
            raise ValueError(f'Previously supported targets removed: {sorted(previous - targets.keys())}')
    for target in targets.values():
        config = json.loads((root / f'gradle/mixins/{target.mixin_profile}.json').read_text(encoding='utf-8'))
        if not config.get('required') or config.get('injectors', {}).get('defaultRequire', 1) < 1:
            raise ValueError(f'Mixin failure policy weakened: {target.minecraft}')
        names = config['mixins']
        if len(names) != len(set(names)):
            raise ValueError(f'Duplicate Mixin registrations: {target.minecraft}')
    return targets


def main():
    root = Path(__file__).resolve().parents[1]
    targets = validate(root)
    print(f'Canonical source ownership and build inputs verified for {len(targets)} targets.')


if __name__ == '__main__':
    main()
