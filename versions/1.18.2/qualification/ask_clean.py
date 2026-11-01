#!/usr/bin/env python3
"""policy = ASK after a normal stop: the question is asked before the world loads.
Y resumes from the stop-time checkpoint (machines keep running), N and no answer start normally."""
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
    d = h.dumps(label)
    return [sum(1 for l in d[t] if l.startswith("[piston-order]") and int(l.split(" ")[2].split(",")[2]) >= 6) for t in sorted(d)]

def facts(label):
    return {"asked": bool(h.log_lines(label, "AntiCrash ASK / PRE-WORLD.*正常終了")),
            "restored": bool(h.log_lines(label, "World読み込み前にcheckpoint .* の完全検証と復元が完了")),
            "dirty": bool(h.log_lines(label, "予期せず終了")),
            "n": bool(h.log_lines(label, "Nを受け付けました|回答がなかったためNとして扱い"))}

ok = True
try:
    configure(create_on_clean_shutdown="true", policy='"AUTO"', ask_timeout_seconds="60")
    code = boot("ask-run", ["100 @stop"])
    final = h.log_lines("ask-run", "停止前checkpointが完了")
    print("normal stop with machines running: exit", code, "| final checkpoint:", bool(final), "| moving in the last 40 ticks:", sum(1 for c in moving("ask-run")[-40:] if c), "/ 40")
    ok &= code == 0 and bool(final)

    configure(policy='"ASK"')
    code = boot("ask-y", ["120 @stop"], answer="y")
    f, after = facts("ask-y"), moving("ask-y")
    verify = h.log_lines("ask-y", "照合しました: CHUNK")
    bad = [p for p in (verify[0].split(": ", 1)[1].split(", ") if verify else ["no verification line"]) if "MATCH" not in p and "NOT_APPLICABLE" not in p]
    print("ASK, answered Y:", f, "| exit", code, "| moving in the last 60 ticks:", sum(1 for c in after[-60:] if c), "/ 60 | not matching:", bad)
    ok &= f["asked"] and f["restored"] and not f["dirty"] and not bad and sum(1 for c in after[-60:] if c) > 20

    code = boot("ask-n", ["40 @stop"], answer="n")
    f = facts("ask-n")
    print("ASK, answered N:", f, "| exit", code)
    ok &= f["asked"] and f["n"] and not f["restored"] and code == 0

    configure(ask_timeout_seconds="5")
    code = boot("ask-timeout", ["40 @stop"])
    f = facts("ask-timeout")
    print("ASK, no answer (5 s):", f, "| exit", code)
    ok &= f["asked"] and f["n"] and not f["restored"] and code == 0

    configure(policy='"AUTO"')
    code = boot("ask-auto", ["40 @stop"])
    f = facts("ask-auto")
    print("AUTO after a normal stop:", f, "| exit", code)
    ok &= not f["asked"] and not f["restored"] and code == 0
    print("RESULT", "PASS" if ok else "FAIL")
finally:
    configure(create_on_clean_shutdown="false", policy='"AUTO"', ask_timeout_seconds="60")
