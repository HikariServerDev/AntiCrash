#!/usr/bin/env python3
"""Portal lane evidence per kept run: for each item dropped into the portal around the cut, the
tick (relative to the checkpoint) it changed dimension in control and in recovery, how many times
it changed dimension afterwards (ping-pong check), and which item was pending at the cut."""
import os, re, sys
KINDS = ("iron_ingot", "copper_ingot", "gold_nugget", "iron_nugget", "netherite_ingot", "brick")
Q = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "build", "runtime-qualification", "runs")
for run in sorted(os.listdir(Q)):
    rec = os.path.join(Q, run, "out", "recovery")
    if not os.path.isdir(rec): continue
    n = min(int(f[:-4]) for f in os.listdir(rec))
    rows = {}
    for side in ("control", "recovery"):
        out = os.path.join(Q, run, "out", side)
        for t in sorted(int(f[:-4]) for f in os.listdir(out)):
            if t < n: continue
            for l in open(os.path.join(out, "%d.txt" % t)):
                if not l.startswith("[entity]"): continue
                for k in KINDS:
                    # only the items of this run: they are above or inside the overworld portal, or freshly arrived
                    if '"minecraft:%s"' % k in l:
                        m = re.search(r"Age:(\d+)s", l)
                        if m and int(m.group(1)) <= (t - n) + 8:
                            rows.setdefault((k, side), []).append((t - n, l.split(" ")[1].split(":")[1]))
    line = []
    for k in KINDS:
        cell = []
        for side in ("control", "recovery"):
            seq = rows.get((k, side), [])
            path, last = [], None
            for t, d in seq:
                if d != last: path.append("%s@+%d" % (d[:5], t)); last = d
            cell.append(">".join(path) or "-")
        line.append("%s[%s]" % (k, "same " + cell[0] if cell[0] == cell[1] else "C " + cell[0] + " | R " + cell[1]))
    print(run, "  ".join(line))
