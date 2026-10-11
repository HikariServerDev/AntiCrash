#!/usr/bin/env python3
"""A start that dies before the world is loaded must not change how the previous exit is classified."""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import harness as h

def boot(label, script=None, fail=False):
    h.kill_leftovers(); h.clear_out()
    h.script(script or ["40 @stop"])
    if fail: open(os.path.join(h.PROBE, "halt-before-world"), "w").close()
    code, seconds = h.start(label, 300)
    dirty = h.log_lines(label, "予期せず終了")
    carried = h.log_lines(label, "Worldを読み込む前に終了")
    restored = h.log_lines(label, "から.*復元しました")
    print("%-22s exit=%s %4.0fs dirty-exit-reported=%s carried=%s restored=%s" % (
        label, code, seconds, bool(dirty), [l[l.find("（"):][:12] for l in carried], bool(restored)))
    return bool(dirty), bool(carried), bool(restored)

results = []
results.append(("clean stop", boot("fs-clean")))
results.append(("failed start 1", boot("fs-fail1", fail=True)))
results.append(("failed start 2", boot("fs-fail2", fail=True)))
after_clean = boot("fs-after-clean")
results.append(("start after failures", after_clean))
crash = boot("fs-crash", ["40 @checkpoint", "70 @halt"])
results.append(("crash with world open", crash))
results.append(("failed start 3", boot("fs-fail3", fail=True)))
after_crash = boot("fs-after-crash")
results.append(("start after crash+failure", after_crash))
ok = (not after_clean[0] and after_clean[1] and not after_clean[2]) and (after_crash[0] and after_crash[2])
print("RESULT", "PASS" if ok else "FAIL")
