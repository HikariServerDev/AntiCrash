#!/usr/bin/env python3
"""Control vs recovery differential runs. Usage: qualify.py <first index> <count>"""
import json, os, re, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import harness as h, scenarios as sc

OFFSETS = [22, 9, 27, 13, 31, 16, 37, 11, 43, 19, 29, 47, 24, 35, 15, 41]


def lane(line):
    """Which fixture a dump line belongs to."""
    kind = line.split(" ", 1)[0]
    m = re.search(r"(-?\d+),(-?\d+),(-?\d+)", line)
    m2 = re.search(r" (-?\d+\.\d+),(-?\d+\.\d+),(-?\d+\.\d+)", line)
    z = None
    if m2: z = float(m2.group(3))
    elif m: z = int(m.group(3))
    # A chunk whose last ticket is gone stays in the holder map at level -1 until the unload queue
    # gets to it; ChunkManager works through that queue only while the tick has wall-clock time left.
    if kind == "[domain-loaded]" or (kind == "[chunk]" and "type=INACCESSIBLE" in line and "level=-1" in line):
        return "unload-pending"
    if kind == "[age]":
        return "age:" + ("item/orb" if ("minecraft:item " in line or "experience_orb" in line) else "other-entity")
    if kind == "[ticket]" and " unknown " in line:
        return "ticket:unknown"
    if "the_nether" in line: return "nether"
    if kind in ("[block]", "[unloaded]") and re.search(r" -?\d+,62,-?\d+ ", line + " "): return "stress-blocks"
    if kind in ("[world]", "[inhabited]", "[domain]", "[chunk]", "[ticket]", "[player]", "[torch]", "[entity-list]", "[block-events]", "time"):
        return kind.strip("[]")
    if z is None: return kind
    for name, lo, hi in [("piston+sculk", -2, 4), ("flying-east", 6, 10), ("flying-west", 14, 18), ("flying-heavy", 22, 26),
                         ("hopper", 30, 39), ("brewing", 39.5, 42), ("gateway", 46, 50), ("portal", 62, 66), ("tnt", 94, 99),
                         ("falling", 102, 106), ("item+xp", 110, 116), ("golem", 118, 122), ("bot", 126, 130), ("container", 134, 139), ("bot-ride", 139.5, 141.5), ("torch-clock", 142, 146), ("pearl+ride", 147, 150)]:
        if lo <= z <= hi: return name
    return "other"


def split_domain(lines):
    """The count of holders still in the map is reported apart from the chunks that can do anything."""
    out = []
    for line in lines:
        m = re.match(r"(\[domain\] \S+) (loaded=\d+) (.*)", line)
        if m:
            out.append(m.group(1) + " " + m.group(3))
            out.append("[domain-loaded] " + m.group(1)[9:] + " " + m.group(2))
        else:
            out.append(line)
    return out


def compare(control, recovery):
    n = min(recovery)
    ticks = [t for t in sorted(recovery) if t in control]
    first = {}
    for t in ticks:
        c, r = split_domain(control[t]), split_domain(recovery[t])
        if c == r: continue
        cs, rs = set(c), set(r)
        for line in sorted(cs ^ rs):
            name = lane(line)
            if name not in first:
                first[name] = (t - n, ("control: " if line in cs else "recovery: ") + line[:300])
        # order-only differences
        if cs == rs:
            first.setdefault("ORDER", (t - n, "same lines in a different order"))
    return n, len(ticks), first


def main():
    start, count = int(sys.argv[1]), int(sys.argv[2])
    tail = int(sys.argv[3]) if len(sys.argv) > 3 else 70
    container = len(sys.argv) > 4 and sys.argv[4] == "container"
    extras = len(sys.argv) > 4 and sys.argv[4] == "extras"
    stress = len(sys.argv) > 4 and sys.argv[4] == "stress"
    results = []
    for index in range(start, start + count):
        at = OFFSETS[index % len(OFFSETS)] + 40
        h.kill_leftovers()
        h.clear_out()
        with open(os.path.join(h.PROBE, "boxes.txt"), "w") as f:
            f.write("\n".join(sc.BOXES + ([sc.STRESS_BOX] if stress else [])) + "\n")
        extra = ["1 gamerule doMobSpawning false", "1 gamerule randomTickSpeed 0", "1 gamerule doWeatherCycle false",
                 "1 gamerule doFireTick false"] if index == 0 else []
        h.script(extra + sc.run_script(index, at, at + tail, container, extras, stress))
        c_code, c_sec = h.start("control", 400)
        r_code, r_sec = h.start("recovery", 300)
        control, recovery = h.dumps("control"), h.dumps("recovery")
        if not recovery or not control:
            print("RUN", index, "no dumps", c_code, r_code); results.append({"run": index, "error": "no dumps"}); continue
        n, ticks, first = compare(control, recovery)
        log = h.log_lines("recovery", r"復旧準備完了|DEGRADED|照合しました|CONTAINER|tick順序を復元|失敗|解除しました|保持します|RECOVERY_FAILED")
        result = {"run": index, "checkpoint_offset": at, "checkpoint_tick": n, "ticks_compared": ticks,
                  "control_exit": c_code, "recovery_exit": r_code, "mismatch": first, "log": [l[33:1800] for l in log]}
        results.append(result)
        print(json.dumps(result, ensure_ascii=False, indent=1))
        sys.stdout.flush()
        keep = os.path.join(h.Q, "runs", "run-%02d" % index)
        os.makedirs(keep, exist_ok=True)
        with open(os.path.join(keep, "result.json"), "w") as f:
            json.dump(result, f, ensure_ascii=False, indent=1)
        if first:
            import shutil
            shutil.rmtree(os.path.join(keep, "out"), ignore_errors=True)
            shutil.copytree(os.path.join(h.PROBE, "out"), os.path.join(keep, "out"))
    return results


if __name__ == "__main__":
    main()
