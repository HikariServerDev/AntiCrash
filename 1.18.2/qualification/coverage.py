#!/usr/bin/env python3
"""What state each fixture was in at the checkpoint tick of every run (evidence of coverage)."""
import json, os, re, sys
Q = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "build", "runtime-qualification", "runs")
for run in sorted(os.listdir(Q)):
    out = os.path.join(Q, run, "out", "recovery")
    if not os.path.isdir(out): continue
    n = min(int(f[:-4]) for f in os.listdir(out))
    lines = open(os.path.join(Q, run, "out", "control", "%d.txt" % n)).read().split("\n")
    def grep(p): return [l for l in lines if re.search(p, l)]
    pist = grep(r"^\[piston-order\]")
    prog = sorted(set(re.search(r"progress=(\S+) lastProgress=(\S+)", l).groups() for l in pist))
    lanes = {"single": 0, "east": 0, "west": 0, "heavy": 0}
    for l in pist:
        z = int(l.split(" ")[2].split(",")[2])
        lanes["single" if z == 0 else "east" if z == 8 else "west" if z == 16 else "heavy"] += 1
    hop = [re.search(r"TransferCooldown:(-?\d+)", l).group(1) for l in grep(r"^\[be\].*hopper")]
    brew = [re.search(r"BrewTime:(\d+)s", l).group(1) for l in grep(r"^\[be\].*brewing_stand")]
    gate = [l.split("cooldown=")[1] for l in grep(r"^\[be\].*end_gateway")]
    sculk = [re.search(r"sculk_sensor_phase=(\w+)", l).group(1) for l in grep(r"^\[block\].*sculk_sensor")]
    ents = {}
    for l in grep(r"^\[entity\]"):
        key = l.split(" ")[1].split(":")[1][:6] + ":" + l.split(" ")[2].split(":")[1]
        ents[key] = ents.get(key, 0) + 1
    golem = [re.search(r"regen=(\d+)", l).group(1) for l in grep(r"^\[entity\].*iron_golem")]
    world = [re.search(r"(overworld|the_nether|the_end) .*idle=(\d+) rain=(\S+)", l).groups() for l in grep(r"^\[world\]")]
    tick = {}
    for l in grep(r"^\[ticket\]"):
        t = l.split(" ")[3]; tick[t] = tick.get(t, 0) + 1
    portal = [re.search(r"remaining=(\S+)", l).group(1) for l in grep(r"^\[ticket\].* portal ")]
    bot = grep(r"^\[player\]")
    torch = len(grep(r"^\[torch\]"))
    ticks = len(grep(r"^\[tick\]"))
    be = lines and grep(r"^\[block-events\]")[0][15:60]
    print(run, "N=%d" % n, "pistons", lanes, "progress", prog, "| hopper cd", hop, "| brew", brew, "| gateway cooldown", gate,
          "| sculk", sculk, "| entities", ents, "| golem regen", golem, "| world", world, "| tickets", tick, "portal remaining", sorted(set(portal))[:4],
          "| bot", [re.search(r"using=\w+ useLeft=\d+ lastAttack=\d+", b).group(0) for b in bot], "| torch", torch, "| sched ticks", ticks, "| events", be)
