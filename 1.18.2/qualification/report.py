#!/usr/bin/env python3
"""Summary of kept runs. Usage: report.py <first run> [last run]"""
import json, os, re, sys
Q = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "build", "runtime-qualification", "runs")
first = int(sys.argv[1]); last = int(sys.argv[2]) if len(sys.argv) > 2 else 10 ** 6
EXPECTED = {"age:other-entity": "EXPECTED_UNSUPPORTED", "ticket:unknown": "WORLD_RNG_EFFECT", "container": "EXPECTED_UNSUPPORTED",
            "unload-pending": "TEST_NONDETERMINISM"}
lanes, runs, verdicts, degraded = {}, 0, {}, 0
for run in sorted(os.listdir(Q)):
    path = os.path.join(Q, run, "result.json")
    if not os.path.exists(path): continue
    r = json.load(open(path))
    if "run" not in r or not first <= r["run"] <= last: continue
    runs += 1
    for lane, (tick, line) in r["mismatch"].items():
        lanes.setdefault(lane, []).append((r["run"], tick))
    for l in r["log"]:
        if "DEGRADED" in l: degraded += 1
        if "照合しました: CHUNK" in l:
            for part in l.split(": ", 1)[1].split(", "):
                k, v = part.split("=", 1)
                verdicts.setdefault(k, {}).setdefault(v.split(" ")[0], []).append(v)
    print(r["run"], "cp@%d" % r["checkpoint_tick"], "ticks=%d" % r["ticks_compared"], "exit=%s/%s" % (r["control_exit"], r["recovery_exit"]),
          "unexpected=%s" % sorted(k for k in r["mismatch"] if k not in EXPECTED))
print("runs", runs, "degraded lines", degraded)
for lane, hits in sorted(lanes.items()):
    print("lane %-18s %2d/%d runs first tick +%s  %s" % (lane, len(hits), runs, sorted(set(t for _, t in hits))[:6], EXPECTED.get(lane, "UNCLASSIFIED")))
for k, v in verdicts.items():
    print("verify %-26s %s" % (k, {s: len(x) for s, x in v.items()}))
