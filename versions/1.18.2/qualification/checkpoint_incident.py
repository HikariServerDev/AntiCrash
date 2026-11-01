#!/usr/bin/env python3
"""Isolated incident qualification. Never uses systemd, production or an existing world.

Provision from immutable launcher dependencies in build/runtime-qualification/env;
the directory created by tempfile is the ONLY writable server environment.
"""
import argparse
import hashlib
import json
import os
import re
from pathlib import Path
import shutil
import subprocess
import tempfile
import time

ROOT = Path(__file__).resolve().parents[1]

def digest(path):
    sha = hashlib.sha256()
    with path.open('rb') as stream:
        for data in iter(lambda: stream.read(65536), b''):
            sha.update(data)
    return sha.hexdigest()

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--jar', type=Path, default=ROOT / 'build/libs/AntiCrash-1.0.0+mc1.18.2.jar')
    parser.add_argument('--count', type=int, default=20)
    parser.add_argument('--timeout', type=int, default=1200)
    args = parser.parse_args()
    source = ROOT / 'build/runtime-qualification/env'
    env = Path(tempfile.mkdtemp(prefix='checkpoint-incident-', dir=ROOT / 'build'))
    print(env, flush=True)
    for name in ('libraries', 'versions'):
        shutil.copytree(source / name, env / name)
    for name in ('fabric-server-launch.jar', 'fabric-server-launcher.properties', 'server.jar', 'eula.txt'):
        shutil.copy2(source / name, env / name)
    (env / 'mods').mkdir()
    for name in ('fabric-api-0.77.0+1.18.2.jar', 'fabric-carpet-1.18.2-1.4.69+v220331.jar'):
        shutil.copy2(source / 'mods' / name, env / 'mods' / name)
    shutil.copy2(args.jar, env / 'mods/AntiCrash-1.0.0.jar')
    shutil.copy2(ROOT / 'build/libs/AntiCrash-1.0.0+mc1.18.2-probe.jar', env / 'mods/AntiCrash-1.0.0-probe.jar')
    (env / 'probe').mkdir()
    (env / 'probe/incident-count.txt').write_text(str(args.count))
    # Port zero asks the OS for a free port; listen only on loopback.
    (env / 'server.properties').write_text('server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\n'
        'level-type=flat\ngenerate-structures=false\nview-distance=2\nsimulation-distance=2\n'
        'max-tick-time=120000\nsync-chunk-writes=true\nallow-flight=true\n')
    (env / 'config').mkdir()
    config = (source / 'config/anticrash.toml').read_text()
    config = config.replace('interval_seconds = 86400', 'interval_seconds = 30')
    config = config.replace('retention_count = 20', 'retention_count = 50')
    (env / 'config/anticrash.toml').write_text(config)
    (env / 'config/anticrash.toml').chmod(0o600)
    result = {'environment': str(env), 'jar_sha256': digest(args.jar), 'xmx': '1G',
              'production_modified': False, 'checkpoints': [], 'immutable': True}
    observed = {}
    began = time.monotonic()
    with (env / 'console.log').open('w') as log:
        process = subprocess.Popen(['java', '-Xms1G', '-Xmx1G', '-Xlog:gc:file=gc.log', '-jar',
                                    'fabric-server-launch.jar', 'nogui'], cwd=env,
                                   stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT)
        result['pid'] = process.pid
        try:
            while process.poll() is None and time.monotonic() - began < args.timeout:
                for cp in sorted((env / '.anticrash/checkpoints').glob('cp-*/READY')):
                    directory = cp.parent
                    if not re.fullmatch(r'cp-\d{6,18}', directory.name):
                        continue
                    if directory.name in observed:
                        continue
                    observed[directory.name] = {str(p.relative_to(directory)): digest(p)
                        for p in directory.rglob('*') if p.is_file()}
                    manifest = json.loads((directory / 'manifest.json').read_text())
                    result['checkpoints'].append({key: manifest.get(key) for key in
                        ('checkpointId', 'snapshotBackend', 'barrierDurationMs', 'snapshotDurationMs',
                         'heapUsedBeforeBytes', 'heapUsedAfterBytes', 'gcCountAfter', 'gcTimeAfterMs',
                         'changedFiles', 'reusedFiles', 'totalBytes', 'phaseDurationsMs')})
                    print('READY', directory.name, flush=True)
                time.sleep(1)
            if process.poll() is None:
                # Only the owned child is stopped. No inventory scan and no systemd interaction.
                process.stdin.write(b'stop\n'); process.stdin.flush()
                try: process.wait(timeout=30)
                except subprocess.TimeoutExpired: process.kill(); process.wait()
            result['exit_code'] = process.returncode
        finally:
            if process.poll() is None: process.kill(); process.wait()
    for name, files in observed.items():
        directory = env / '.anticrash/checkpoints' / name
        for relative, expected in files.items():
            path = directory / relative
            if not path.exists() or digest(path) != expected:
                result['immutable'] = False
                result.setdefault('mutations', []).append(f'{name}/{relative}')
    text = (env / 'console.log').read_text(errors='replace')
    result['duration_seconds'] = time.monotonic() - began
    result['oom'] = 'OutOfMemoryError' in text or 'Java heap space' in text
    result['parent_false_positive'] = 'Parent checkpoint file is missing or was modified' in text
    result['probe_error'] = (env / 'probe/probe-error.txt').exists()
    result['status'] = 'PASS' if (len(observed) >= args.count and result['immutable']
        and not result['oom'] and not result['parent_false_positive'] and not result['probe_error']) else 'PARTIAL'
    (env / 'result.json').write_text(json.dumps(result, ensure_ascii=False, indent=2))
    print(json.dumps({key: value for key, value in result.items() if key != 'checkpoints'},
                     ensure_ascii=False, indent=2), flush=True)

if __name__ == '__main__': main()
