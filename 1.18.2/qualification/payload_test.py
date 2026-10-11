#!/usr/bin/env python3
"""A client that joins while recovery is frozen must still complete its mod handshakes."""
import os, re, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import harness as h

CONFIG = os.path.join(h.ENV, "config", "anticrash.toml")
def configure(key, value):
    text = open(CONFIG, encoding="utf-8").read()
    text, n = re.subn(r"(?m)^%s = .*$" % key, "%s = %s" % (key, value), text)
    assert n == 1, key
    open(CONFIG, "w", encoding="utf-8").write(text)

result = os.path.join(h.PROBE, "payload-result.txt")
try:
    if os.path.exists(result): os.remove(result)
    h.kill_leftovers(); h.clear_out()
    # Two rules changed at run time (not saved as defaults), as an operator does with /carpet.
    h.script(["5 player qbot spawn at 2.5 64 128.5 facing 0 0 in minecraft:overworld in survival",
              "10 carpet creativeNoClip true", "10 carpet flippinCactus true", "40 @checkpoint", "70 @halt"])
    print("crash run exit", h.start("pt-crash", 300)[0])
    configure("require_manual_start", "true")
    open(os.path.join(h.PROBE, "payload-test"), "w").close()
    h.clear_out(); h.script(["9999 @halt"])
    print("recovery run exit", h.start("pt-recovery", 300)[0])
    text = open(result).read() if os.path.exists(result) else "no result"
    print(text.strip())
    frozen, started = (text.strip().split("\n") + ["", ""])[:2]
    log = os.path.join(h.ENV, ".anticrash", "logs", "anticrash.log")
    delivered = [l.strip()[-40:] for l in open(log, encoding="utf-8", errors="replace").read().split("\n")[-60:] if "Modの通信" in l]
    print("deferred delivery log:", delivered)
    print("rules:", [l[33:130] for l in h.log_lines("pt-recovery", "Carpetのrule|CARPET_RULES=[A-Z]+ [0-9/]+")][:2],
          re.findall(r"CARPET_RULES=[A-Z_]+ ?[0-9/]*", " ".join(h.log_lines("pt-recovery", "照合しました: CHUNK"))))
    # Carpet's handshake and the channel registration work while frozen; a payload with a Fabric
    # receiver is handled exactly once; the one only a mod's own hook sees is delivered after the start.
    ok = ("carpetClient=true" in frozen and "channelRegistered=true" in frozen
          and "carpetClient=true" in started and "channelRegistered=true" in started and "modPayloadHandled=1" in started
          and "creativeNoClip=true flippinCactus=true" in frozen and "creativeNoClip=true flippinCactus=true" in started
          and any("1 件を処理しました" in l for l in delivered) and not any("処理できません" in l or "破棄" in l for l in delivered))
    print("RESULT", "PASS" if ok else "FAIL")
finally:
    configure("require_manual_start", "false")
    flag = os.path.join(h.PROBE, "payload-test")
    if os.path.exists(flag): os.remove(flag)
