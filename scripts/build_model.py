"""Read the same target/dependency matrix used by Stonecutter; no generated Java lives here."""
from dataclasses import dataclass
from pathlib import Path
import re
import tomllib


@dataclass(frozen=True)
class Target:
    minecraft: str
    java: int
    compiler: int
    obfuscated: bool
    loader: str
    fabric_api: str
    offline_access: bool
    qualification: bool
    mixin_profile: str
    refmap: str
    entity_audit: str
    block_entity_audit: str


def resource_path(root, relative):
    path = root / relative
    if path.is_symlink() or not path.is_file() or not path.resolve().is_relative_to(root.resolve()):
        raise ValueError(f'Missing or unsafe build input: {relative}')
    return path


def _flag(value):
    if value not in ['true', 'false']:
        raise ValueError(f'Expected a true/false string, got {value!r}')
    return value == 'true'


def load_targets(root):
    data = tomllib.loads((root / 'stonecutter.properties.toml').read_text(encoding='utf-8'))
    defaults = {key: data.get(key, {}) for key in ['mod', 'deps', 'resources', 'audit']}
    targets = {}
    for version, overrides in data.items():
        if version in defaults:
            continue
        if not re.fullmatch(r'[0-9]+(?:\.[0-9]+){1,2}', version):
            raise ValueError(f'Invalid Minecraft target: {version}')
        merged = {key: dict(defaults[key], **overrides.get(key, {})) for key in defaults}
        mod, deps, resources, audit = (merged[key] for key in defaults)
        target = Target(version, int(mod['java']), int(mod['compile_java']), _flag(mod['obfuscated']),
                        deps['fabric_loader'], deps['fabric_api'],
                        _flag(mod['offline_access']), _flag(mod['qualification']),
                        resources['mixin_profile'], resources['refmap'],
                        audit['entities'], audit['block_entities'])
        if target.java not in [17, 21, 25] or target.compiler < target.java:
            raise ValueError(f'Unsupported compiler contract: {version}')
        if target.obfuscated and _flag(mod.get('legacy_mixin_ap', 'true')) and not target.refmap:
            raise ValueError(f'Obfuscated Minecraft with the legacy Mixin AP needs a refmap: {version}')
        if not re.fullmatch(r'[A-Za-z0-9._-]+', target.mixin_profile):
            raise ValueError(f'Unsafe Mixin profile: {target.mixin_profile}')
        resource_path(root, f'gradle/mixins/{target.mixin_profile}.json')
        for name in [target.entity_audit, target.block_entity_audit]:
            if Path(name).name != name:
                raise ValueError(f'Unsafe audit fixture: {name}')
            resource_path(root, 'qualification/fixtures/' + name)
        targets[version] = target
    if not targets:
        raise ValueError('The build matrix is empty')
    return targets


def mod_version(root):
    values = re.findall(r'^mod_version=(\S+)\s*$', (root / 'gradle.properties').read_text(encoding='utf-8'), re.MULTILINE)
    if len(values) != 1:
        raise ValueError('Exactly one mod_version is required')
    return values[0]
