#!/usr/bin/env python3
"""Read-only inode/content audit, with report output limited to a NEW isolated build environment."""
import argparse
import hashlib
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]

def sha(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(65536), b''): digest.update(block)
    return digest.hexdigest()

def main():
    parser = argparse.ArgumentParser(); parser.add_argument('environment', type=Path)
    env = parser.parse_args().environment.resolve()
    if env.parent != ROOT / 'build' or not env.name.startswith('checkpoint-incident-'):
        raise SystemExit('Only a qualification/checkpoint_incident.py environment is permitted')
    report = {'production_modified': False, 'checkpoint_count': 0, 'checksum_failures': [],
              'checkpoint_live_aliases': [], 'shared_immutable_files': 0, 'inode_samples': [],
              'moving_piston_counts': [], 'loaded_chunk_counts': [], 'runtime_versions': [],
              'uuid_warning': 'INCONCLUSIVE_FOR_ORIGINAL_INCIDENT', 'sequential_scope': 'creation_only'}
    seen = {}
    for cp in sorted((env / '.anticrash/checkpoints').glob('cp-*')):
        if not re.fullmatch(r'cp-\d{6,18}', cp.name) or not (cp / 'READY').is_file(): continue
        report['checkpoint_count'] += 1
        manifest = json.loads((cp / 'manifest.json').read_text())
        runtime = json.loads((cp / 'runtime-capsule.json').read_text())
        report['runtime_versions'].append(runtime.get('formatVersion'))
        report['moving_piston_counts'].append(len(runtime.get('movingPistonTickOrder', [])))
        # Dimensions store packed tick-domain arrays; count the explicit loaded domain.
        report['loaded_chunk_counts'].append(sum(len(c.get('loadedChunks', [])) for c in runtime.get('chunks', [])))
        lines = (cp / 'checksums.sha256').read_text().splitlines()
        if (cp / 'READY').read_text().strip() != sha(cp / 'checksums.sha256'):
            report['checksum_failures'].append(f'{cp.name}/READY')
        for line in lines:
            expected, relative = line.split('  ', 1)
            path = cp / relative
            if not path.is_file() or sha(path) != expected:
                report['checksum_failures'].append(f'{cp.name}/{relative}')
        for file in manifest['worldFiles']:
            relative = file['path']; path = cp / 'world' / relative; live = env / 'world' / relative
            stat = path.stat(); identity = (stat.st_dev, stat.st_ino)
            if identity in seen: report['shared_immutable_files'] += 1
            seen[identity] = f'{cp.name}/{relative}'
            if live.exists() and identity == (live.stat().st_dev, live.stat().st_ino):
                report['checkpoint_live_aliases'].append(f'{cp.name}/{relative}')
            if len(report['inode_samples']) < 40:
                report['inode_samples'].append({'checkpoint': cp.name, 'path': relative,
                    'device': stat.st_dev, 'inode': stat.st_ino, 'nlink': stat.st_nlink,
                    'size': stat.st_size, 'mtime_ns': stat.st_mtime_ns, 'ctime_ns': stat.st_ctime_ns})
    text = (env / 'console.log').read_text(errors='replace')
    report['isolated_uuid_warning_count'] = text.count('UUID of added entity already exists')
    report['status'] = 'PASS' if not report['checksum_failures'] and not report['checkpoint_live_aliases'] else 'FAIL'
    (env / 'inode-audit.json').write_text(json.dumps(report, indent=2))
    # Memory sampling includes garbage; the GC log is the authority for post-collection occupancy.
    points = [tuple(map(int, line.split(','))) for line in (env / 'probe/memory.csv').read_text().splitlines()]
    if points:
        width, height = 900, 260; max_tick = max(p[0] for p in points); max_heap = points[0][2]
        path = ' '.join(('M' if i == 0 else 'L') + f'{40 + 820*p[0]/max_tick:.1f},{220-180*p[1]/max_heap:.1f}'
                        for i, p in enumerate(points))
        (env / 'memory.svg').write_text(f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">'
            '<rect width="100%" height="100%" fill="white"/><text x="40" y="22">1 GiB heap: used bytes (includes uncollected garbage)</text>'
            '<path d="M40,40 L40,220 L860,220" fill="none" stroke="#555"/>'
            f'<path d="{path}" fill="none" stroke="#286aa6"/><text x="40" y="245">Server tick</text></svg>')
    print(json.dumps({key: value for key, value in report.items() if key != 'inode_samples'}, indent=2))

if __name__ == '__main__': main()
