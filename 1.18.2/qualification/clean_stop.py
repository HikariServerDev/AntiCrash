#!/usr/bin/env python3
"""The documented restart that keeps machines running: checkpoint, `/ac checkpoint restore latest`,
start again. A plain stop followed by a start is Vanilla's restart and is not checked here."""
import os, re, subprocess, sys, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import harness as h

CONFIG = os.path.join(h.ENV, "config", "anticrash.toml")

def configure(**values):
    text = open(CONFIG, encoding="utf-8").read()
    for key, value in values.items():
        text, n = re.subn(r"(?m)^%s = .*$" % key, "%s = %s" % (key, value), text)
        assert n == 1, key
    open(CONFIG, "w", encoding="utf-8").write(text)

def boot(label, script, answer=None, timeout=300):
    h.kill_leftovers(); h.clear_out()
    h.script(script)
    with open(os.path.join(h.PROBE, "label.txt"), "w") as f: f.write(label)
    path = os.path.join(h.ENV, "console-%s.log" % label)
    log = open(path, "w")
    proc = subprocess.Popen(["systemd-run", "--user", "--scope", "-q", "-p", "MemoryMax=4G", "-p", "MemorySwapMax=0",
                             "java", "-Xms1G", "-Xmx2G", "-jar", "fabric-server-launch.jar", "nogui"],
                            cwd=h.ENV, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT)
    began, answered = time.time(), answer is None
    while proc.poll() is None and time.time() - began < timeout:
        time.sleep(0.5)
        if not answered and "AntiCrash ASK" in open(path, errors="replace").read():
            proc.stdin.write((answer + "\n").encode()); proc.stdin.flush(); answered = True
    if proc.poll() is None: proc.kill()
    code = proc.wait(); log.close(); h.kill_leftovers()
    return code

def moving(label):
    """Per dumped tick, how many flying-machine pistons were moving."""
    d = h.dumps(label)
    return [sum(1 for l in d[t] if l.startswith("[piston-order]") and int(l.split(" ")[2].split(",")[2]) >= 6) for t in sorted(d)]

try:
    configure(policy='"AUTO"')
    # The environment was left by a crash; AUTO recovers its last checkpoint with the machines
    # running. Then the documented restart that keeps machines: checkpoint, restore it, restart.
    code = boot("cs-run", ["100 @checkpoint", "112 ac checkpoint restore latest", "400 @halt"])
    before = moving("cs-run")
    scheduled = h.log_lines("cs-run", "復元を予約|restore")
    print("checkpoint + restore scheduled: exit", code, "| moving in the last 40 ticks:", sum(1 for c in before[-40:] if c), "/ 40 |", [l[33:110] for l in scheduled][-2:])
    code = boot("cs-resume", ["120 @checkpoint", "150 @halt"])
    after = moving("cs-resume")
    verify = h.log_lines("cs-resume", "照合しました: CHUNK")
    dirty = h.log_lines("cs-resume", "予期せず終了")
    bad = [p for p in (verify[0].split(": ", 1)[1].split(", ") if verify else ["no verification line"]) if "MATCH" not in p and "NOT_APPLICABLE" not in p]
    print("restart with the checkpoint restored: exit", code, "| reported as crash:", bool(dirty), "| ticks", len(after),
          "| moving in the last 60 ticks:", sum(1 for c in after[-60:] if c), "/ 60 | not matching:", bad,
          "| degraded:", [l[33:140] for l in h.log_lines("cs-resume", "DEGRADED")])
    print("RESULT", "PASS" if code == 1 and not dirty and sum(1 for c in after[-60:] if c) > 20 and not bad else "FAIL")
finally:
    configure(create_on_clean_shutdown="false", policy='"AUTO"')
