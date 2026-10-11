#!/usr/bin/env python3
"""Mechanical audit metadata migration using same-game Tiny v2 mapping identities.

This does not certify a version port: new/removed game fields still require bytecode review.
Only audit resources and audit test literals are changed; recovery payload keys are untouched.
"""
import argparse
from collections import defaultdict
from pathlib import Path


def tiny(path):
    classes, members = {}, {}
    owner = None
    for line in path.read_text().splitlines():
        columns = line.split('\t')
        if columns[0] == 'c':
            owner = columns[1]
            classes[owner] = columns[3]
        elif len(columns) >= 6 and columns[0] == '' and columns[1] in ('f', 'm'):
            members[owner, columns[1], columns[2], columns[3]] = columns[5]
    return classes, members


def migrate(source, destination, target):
    old_classes, old_members = tiny(source)
    new_classes, new_members = tiny(destination)
    if set(new_classes) - set(old_classes) or any('/' not in key for key in set(old_classes) - set(new_classes)):
        raise ValueError('Mappings must describe the same Minecraft game, not a version upgrade')
    # Already unobfuscated Mojang/blaze3d classes may be omitted by the official layer.
    class_names = {name: new_classes.get(key, key) for key, name in old_classes.items()}
    unique = defaultdict(set)
    fields = {}
    for key, name in old_members.items():
        replacement = new_members.get(key, key[3])
        unique[name].add(replacement)
        if key[1] == 'f':
            fields[old_classes[key[0]], name] = replacement
    changed = 0
    resources = target / 'src/test/resources/runtime-audit'
    for resource in resources.glob('*.tsv'):
        output = []
        for line in resource.read_text().splitlines():
            if not line or line.startswith('#'):
                output.append(line)
                continue
            columns = line.split('\t')
            name = columns[0].replace('.', '/')
            if '/' not in name:
                name = 'net/minecraft/block/entity/' + name
            if name not in class_names:
                raise ValueError('Unknown source class: ' + name)
            if columns[1] != '-':
                columns[1] = fields[name, columns[1]]
            replacement = class_names[name]
            columns[0] = replacement.replace('/', '.') if '.' in columns[0] else replacement.split('/')[-1]
            output.append('\t'.join(columns))
        resource.write_text('\n'.join(output) + '\n')
        changed += 1
    # These are ASM class/field/method expectations, not world serialization keys.
    for path in (target / 'src/test/java/com/atsukimc/anticrash/audit').glob('*.java'):
        text = path.read_text()
        for name, replacement in sorted(class_names.items(), key=lambda pair: -len(pair[0])):
            text = text.replace(name, replacement).replace(name.replace('/', '.'), replacement.replace('/', '.'))
        # Member names in ASM expectations need owner context. Never globally rename a string:
        # an AntiCrash method or payload key can legitimately share a Vanilla field's name.
        path.write_text(text)
        changed += 1
    return changed


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    parser.add_argument('destination', type=Path)
    parser.add_argument('target', type=Path)
    args = parser.parse_args()
    if args.target.is_symlink() or not (args.target / 'target.json').is_file():
        raise ValueError('Expected an independent version source directory')
    print('Migrated audit files:', migrate(args.source, args.destination, args.target))
