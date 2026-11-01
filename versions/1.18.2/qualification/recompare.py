#!/usr/bin/env python3
"""Re-evaluates kept dumps of one run with the current comparison rules. Usage: recompare.py <run>"""
import json, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import harness as h, qualify as q
keep = os.path.join(h.Q, "runs", "run-%02d" % int(sys.argv[1]))
def load(side):
    out = {}
    d = os.path.join(keep, "out", side)
    for f in os.listdir(d):
        out[int(f[:-4])] = open(os.path.join(d, f)).read().split("\n")
    return out
n, ticks, first = q.compare(load("control"), load("recovery"))
r = json.load(open(os.path.join(keep, "result.json")))
r["mismatch"] = first
json.dump(r, open(os.path.join(keep, "result.json"), "w"), ensure_ascii=False, indent=1)
print(sys.argv[1], ticks, {k: v[0] for k, v in first.items()})
