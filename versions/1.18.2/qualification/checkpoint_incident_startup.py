#!/usr/bin/env python3
"""Clean/N and dirty/Y regression in an owned incident-test environment only."""
import argparse
import json
from pathlib import Path
import shutil
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]

def launch(env, label, answer, script, timeout=180):
    probe = env / 'probe'
    (probe / 'label.txt').write_text('control')
    (probe / 'script.txt').write_text(script)
    absolute = probe / 'script.abs.txt'
    if absolute.exists(): absolute.unlink()  # owned test script only
    log_path = env / f'startup-{label}.log'
    began = time.monotonic()
    with log_path.open('w') as log:
        child = subprocess.Popen(['java', '-Xms1G', '-Xmx1G', '-jar', 'fabric-server-launch.jar', 'nogui'],
                                 cwd=env, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT)
        child.stdin.write((answer + '\n').encode()); child.stdin.flush()
        try:
            child.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            child.stdin.write(b'stop\n'); child.stdin.flush()
            try: child.wait(timeout=20)
            except subprocess.TimeoutExpired: child.kill(); child.wait()
        finally:
            if child.poll() is None: child.kill(); child.wait()
    text = log_path.read_text(errors='replace')
    return {'exit_code': child.returncode, 'seconds': time.monotonic() - began,
            'oom': 'OutOfMemoryError' in text or 'Java heap space' in text,
            'validation_count': text.count('の整合性を検証しています'), 'world_ready': 'Done (' in text,
            'restore_observed': 'Worldファイルを' in text and 'からWorld読み込み前に復元しました' in text,
            'log': str(log_path),
            'probe_error': (probe / 'probe-error.txt').exists()}

def classify(result, env):
    """Separate lifecycle success from diagnostics; prompt text is not a restore."""
    for key in ('clean_N', 'dirty_N', 'dirty_Y'):
        entry = result[key]
        text = Path(entry['log']).read_text(errors='replace')
        entry['restore_observed'] = ('Worldファイルを' in text
                and 'からWorld読み込み前に復元しました' in text)
    text = Path(result['dirty_Y']['log']).read_text(errors='replace')
    result['diagnostics'] = {
        'commands_observed': 'AntiCrash 復旧等価性' in text,
        'report_missing': 'No recovery equivalence report is available' in text,
        'resume_observed': '安全なtick境界でゲーム内の時間を再開しました' in text,
    }
    lifecycle_ok = (result['clean_N']['exit_code'] == 0
        and not result['clean_N']['restore_observed']
        and result['dirty_N']['validation_count'] == 0 and result['dirty_N']['exit_code'] == 0
        and not result['dirty_N']['restore_observed']
        and result['isolated_crash_setup']['exit_code'] == 1 and result['dirty_Y']['exit_code'] == 0
        and result['dirty_Y']['validation_count'] == 1 and result['dirty_Y']['restore_observed']
        and (env / 'probe/verify-before-start.done').exists()
        and result['diagnostics']['resume_observed']
        and not result['dirty_Y']['oom'] and not result['dirty_Y']['probe_error'])
    result['startup_lifecycle_status'] = 'PASS' if lifecycle_ok else 'PARTIAL'
    result['diagnostics_status'] = ('PASS' if result['diagnostics']['commands_observed']
        and not result['diagnostics']['report_missing'] else 'PARTIAL')
    result['status'] = ('PASS' if lifecycle_ok and result['diagnostics_status'] == 'PASS'
                        else 'PARTIAL')
    return result

def main():
    parser = argparse.ArgumentParser(); parser.add_argument('environment', type=Path)
    parser.add_argument('--analyze-only', action='store_true', help='Reclassify existing logs; never launch')
    args = parser.parse_args()
    env = args.environment.resolve()
    if env.parent != ROOT / 'build' or not env.name.startswith('checkpoint-incident-'):
        raise SystemExit('Only incident qualification environments are permitted')
    if args.analyze_only:
        result = classify(json.loads((env / 'startup-result.json').read_text()), env)
        (env / 'startup-result.json').write_text(json.dumps(result, indent=2))
        print(json.dumps(result, indent=2), flush=True)
        return
    marker = env / 'probe/incident-count.txt'
    if marker.exists(): marker.replace(env / 'probe/incident-count.completed.txt')
    config = env / 'config/anticrash.toml'
    config.write_text(config.read_text().replace('policy = "AUTO"', 'policy = "ASK"'))
    config.chmod(0o600)
    # Use the exact final artifact. Production mods are never destinations.
    shutil.copy2(ROOT / 'build/libs/AntiCrash-1.0.0+mc1.18.2.jar', env / 'mods/AntiCrash-1.0.0.jar')
    shutil.copy2(ROOT / 'build/libs/AntiCrash-1.0.0+mc1.18.2-probe.jar', env / 'mods/AntiCrash-1.0.0-probe.jar')
    result = {'production_modified': False, 'environment': str(env)}
    result['clean_N'] = launch(env, 'clean-N', 'N', '2 @stop\n')
    result['isolated_crash_setup'] = launch(env, 'crash-setup', 'N', '2 @halt\n')
    result['dirty_N'] = launch(env, 'dirty-N', 'N', '2 @stop\n')
    result['second_isolated_crash_setup'] = launch(env, 'crash-setup-Y', 'N', '2 @halt\n')
    config.write_text(config.read_text().replace('require_manual_start = false', 'require_manual_start = true'))
    (env / 'probe/verify-before-start').write_text('verify and explicitly start when READY\n')
    result['dirty_Y'] = launch(env, 'dirty-Y', 'Y',
            '40 @stop\n')
    result = classify(result, env)
    (env / 'startup-result.json').write_text(json.dumps(result, indent=2))
    print(json.dumps(result, indent=2), flush=True)

if __name__ == '__main__': main()
