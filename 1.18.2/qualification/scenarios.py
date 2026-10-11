#!/usr/bin/env python3
"""Fixtures and per-run scripts for the runtime qualification world."""

STRESS_BOX = "minecraft:overworld -64 62 -64 431 62 207"

BOXES = [
    "minecraft:overworld -2 64 -2 42 76 150",
    "minecraft:overworld 43 64 6 420 76 26",
    "minecraft:overworld -2 77 92 6 200 100 entities",
    "minecraft:the_nether -200 0 -200 200 255 200 entities",
]


def machine(x, y, z, east=True, heavy=False):
    """Two-piston slime engine. Group A: observer, normal piston, 2 slime. Group B: 2 slime,
    sticky piston, observer. Each observer fires when its group is moved and powers its piston.
    Returns (observers, rest): a placed observer always pulses once, so they go in first."""
    d = 1 if east else -1
    fwd, back = ("east", "west") if east else ("west", "east")
    c = []
    put = lambda dx, dy, block: c.append("setblock %d %d %d %s" % (x + d * dx, y + dy, z, block))
    put(-1, 0, "observer[facing=%s]" % back)
    put(2, 1, "observer[facing=%s]" % fwd)
    observers, c = c, []
    put = lambda dx, dy, block: c.append("setblock %d %d %d %s" % (x + d * dx, y + dy, z, block))
    put(0, 1, "slime_block"); put(-1, 1, "slime_block")
    put(1, 0, "slime_block"); put(2, 0, "slime_block")
    if heavy:
        for dx, dy, b in [(2, -1, "slime_block"), (2, -2, "slime_block"), (1, -1, "stone"), (3, -1, "stone"),
                          (1, -2, "stone"), (3, -2, "stone"),
                          (-1, 2, "slime_block"), (-1, 3, "slime_block"), (-2, 2, "stone"), (-2, 3, "stone"), (0, 2, "stone")]:
            put(dx, dy, b)
    put(0, 0, "piston[facing=%s]" % fwd)
    put(1, 1, "sticky_piston[facing=%s]" % back)
    return observers, c


def trigger(x, y, z, east=True):
    return "setblock %d %d %d stone" % (x - 2 if east else x + 2, y, z)


SETUP = [
    "forceload add 0 0 431 31",
    "fill -2 63 -2 42 63 150 stone",
    # F1 single piston + sculk sensor 6 blocks away
    "setblock 2 64 0 sticky_piston[facing=east]", "setblock 3 64 0 stone", "setblock 8 64 0 sculk_sensor",
    # F4 hopper rings, placed in opposite orders
    'setblock 2 64 32 hopper[facing=east]{Items:[{Slot:0b,id:"minecraft:stone",Count:5b}]}',
    "setblock 3 64 32 hopper[facing=south]", "setblock 3 64 33 hopper[facing=west]", "setblock 2 64 33 hopper[facing=north]",
    "setblock 2 64 37 hopper[facing=north]", "setblock 3 64 37 hopper[facing=west]", "setblock 3 64 36 hopper[facing=south]",
    'setblock 2 64 36 hopper[facing=east]{Items:[{Slot:0b,id:"minecraft:stone",Count:5b}]}',
    # F6 end gateway
    "setblock 2 64 48 end_gateway{ExitPortal:{X:20,Y:64,Z:48},ExactTeleport:1b,Age:300L}",
    # F7 nether portal
    "fill 2 63 64 5 68 64 obsidian", "fill 3 64 64 4 67 64 nether_portal[axis=x]",
    # F11 ice lane
    "fill 8 63 112 40 63 112 packed_ice",
    # F12 magma
    "setblock 2 63 120 magma_block",
    # F16 torch burnout clock
    "setblock 2 64 144 stone", "setblock 3 65 144 stone", "setblock 2 65 144 redstone_wire",
    "setblock 3 64 144 redstone_wall_torch[facing=east]",
    # F14 fake player
    "player qbot spawn at 2.5 64 128.5 facing 0 0 in minecraft:overworld in survival",
]
MACHINES = [machine(10, 70, 8, True), machine(400, 70, 16, False), machine(10, 70, 24, True, True)]
TRIGGERS = [trigger(10, 70, 8, True), trigger(400, 70, 16, False), trigger(10, 70, 24, True)]


def setup_script():
    # Forced chunks far from spawn need time to generate and start ticking before anything that
    # relies on scheduled ticks (an observer's first pulse) is placed in them.
    lines = ["5 " + c for c in SETUP]
    lines += ["25 item replace entity qbot weapon.mainhand with bow", "25 give qbot arrow 64"]
    for observers, rest in MACHINES:
        lines += ["120 " + c for c in observers] + ["140 " + c for c in rest]
    lines += ["160 " + c for c in TRIGGERS]
    lines += ["150 ac checkpoint base", "300 @stop"]
    return lines


BREW = ('execute if data block 2 64 40 {BrewTime:0s} run setblock 2 64 40 brewing_stand{Items:['
        '{Slot:0b,id:"minecraft:potion",Count:1b,tag:{Potion:"minecraft:water"}},'
        '{Slot:3b,id:"minecraft:nether_wart",Count:1b},{Slot:4b,id:"minecraft:blaze_powder",Count:1b}]} replace')


MAINTENANCE = [
    # Only players create the destination portal; other entities need one to exist already.
    "5 execute in minecraft:the_nether run forceload add -16 0 15 15",
    "60 execute in minecraft:the_nether run fill -3 98 5 7 108 11 obsidian hollow",
    "62 execute in minecraft:the_nether run fill 0 99 8 3 104 8 obsidian",
    "64 execute in minecraft:the_nether run fill 1 100 8 2 103 8 nether_portal[axis=x]",
    "80 execute in minecraft:the_nether run forceload remove -16 0 15 15",
    # A NoAI mob never moves, so it never "steps" on magma. Lava damages from baseTick instead.
    "5 kill @e[type=iron_golem]", "5 kill @e[type=item]", "5 kill @e[type=experience_orb]",
    "6 setblock 1 64 120 stone", "6 setblock 3 64 120 stone", "6 setblock 2 64 119 stone", "6 setblock 2 64 121 stone",
    "6 setblock 2 63 120 stone", "8 setblock 2 64 120 lava",
    "8 setblock 2 64 40 brewing_stand",
    "100 @stop",
]

BREW_ITEMS = ('{Items:[{Slot:0b,id:"minecraft:potion",Count:1b,tag:{Potion:"minecraft:water"}},'
              '{Slot:3b,id:"minecraft:nether_wart",Count:1b}],Fuel:20b}')
GOLEM = ('execute unless entity @e[type=iron_golem] run summon iron_golem 2.5 64 120.5 '
         '{NoAI:1b,PersistenceRequired:1b,DeathLootTable:"minecraft:empty"}')
ITEM = 'summon item %s {Item:{id:"minecraft:%s",Count:1b},PickupDelay:32767%s}'


def run_script(index, cp, length, container=False, extras=False, stress=False):
    """One control/recovery cycle. The checkpoint is requested at tick `cp` and is cut at the end
    of tick cp or cp+1, so no command may run on those two ticks."""
    s = []
    def at(t, cmd):
        if t in (cp, cp + 1): t = cp + 2
        s.append((t, cmd))
    # Carpet fake players do not survive a normal restart, so each control run brings it back.
    at(1, "player qbot spawn at 2.5 64 128.5 facing 0 0 in minecraft:overworld in survival")
    at(3, "item replace entity qbot weapon.mainhand with bow")
    # A brew takes 400 ticks; it is restarted only when the previous one is finished, so the
    # checkpoints of consecutive runs land at the start, the middle and the end of a brew.
    at(4, "execute if data block 2 64 40 {BrewTime:0s} run data merge block 2 64 40 " + BREW_ITEMS)
    # F1: the piston is toggled all the time (and the sculk sensor hears it).
    t, on = 5, True
    while t < length - 2:
        at(t, "setblock 2 65 0 " + ("redstone_block" if on else "air"))
        on = not on
        t += 5 + (index % 3)
    # The gateway picks which entity to teleport with the world RNG when several are inside, so
    # only one item is ever waiting: one goes through before the cut (40-tick cooldown starts)
    # and the next arrives during that cooldown and has to wait for it to end.
    # Items of earlier runs are removed first for the same reason.
    at(2, "kill @e[type=item,x=0,y=60,z=46,dx=24,dy=10,dz=5]")
    at(cp - 12 - index % 7, ITEM % ("2.5 64.5 48.5", "stone", ""))
    at(cp + 4, ITEM % ("2.5 64.5 48.5", "stone", ""))
    at(8, ITEM % ("3.5 64.5 64.5", "gold_ingot", ""))
    # An entity that touched the portal during the tick of the cut changes dimension at the start
    # of the next tick; that pending state exists for exactly one tick. Items thrown at the
    # portal from staggered distances reach it on consecutive ticks around the cut, so whichever
    # tick the cut lands on, one of them is in that state.
    for n, (distance, kind) in enumerate([(0.90, "iron_ingot"), (1.05, "copper_ingot"), (1.25, "gold_nugget"),
                                          (1.40, "iron_nugget"), (1.58, "netherite_ingot"), (1.75, "brick")]):
        at(cp - 6, ITEM % ("%.2f 67.4 %.3f" % (3.3 + 0.28 * n, 65.125 + distance), kind, ",Motion:[0.0d,0.0d,-0.2d]"))
    at(max(8, cp - 40), "summon tnt 2.5 150 96.5 {Fuse:60}")
    at(max(8, cp - 15), 'summon falling_block 2.5 100 104.5 {BlockState:{Name:"minecraft:sand"},Time:1}')
    at(10, ITEM % ("2.5 64.5 112.5", "diamond", ""))
    at(max(9, cp - 6), ITEM % ("9.5 64.2 112.5", "emerald", ",Motion:[0.35d,0.0d,0.0d]"))
    at(12, "summon experience_orb 6.5 64.5 114.5 {Value:3}")
    at(14, GOLEM)
    at(14, "weather rain 100000" if index % 2 == 0 else "weather clear 100000")
    at(16, "player qbot stop")
    at(18, "player qbot use continuous")
    at(max(18, cp - 7 - index % 5), "player qbot attack once")
    # Restart the torch clock shortly before the cut so its burnout history is not empty.
    at(max(3, cp - 14), "setblock 3 64 144 air")
    at(max(5, cp - 12), "setblock 3 64 144 redstone_wall_torch[facing=east]")
    if container:
        # Unsupported on purpose: a second bot holds a trapped chest open across the cut. The open
        # session cannot be restored, so the chest's signal drops after recovery; the checkpoint
        # must say so (CONTAINER_SESSION) instead of reporting an exact restore.
        at(2, "setblock 2 65 137 trapped_chest[facing=north]")
        at(2, "setblock 3 65 137 redstone_lamp")
        at(3, "player cbot spawn at 2.5 64 136.5 facing 0 0 in minecraft:overworld in survival")
        at(max(20, cp - 10), "player cbot use once")
    if extras:
        # Item cooldown: a creative bot (no fall damage) throws an ender pearl at its feet 8 ticks
        # before the cut; the 20-tick cooldown must end on the same tick after recovery.
        at(3, "player pbot spawn at 2.5 64 148.5 facing 0 90 in minecraft:overworld in creative")
        at(6, "item replace entity pbot weapon.mainhand with ender_pearl 16")
        at(max(20, cp - 8), "player pbot use once")
        # POST_TELEPORT ticket (5 ticks): /tp of a player adds one for the target chunk.
        at(cp - 2, "tp Pbot 2.5 64 148.5")
        # Remount cooldown: a husk thrown out of its boat (animals are disabled on this server; armor stands are not picked up) cannot board another for 60 ticks. The
        # second boat sits on top of it and picks it up on the tick the cooldown ends.
        at(2, "gamerule doEntityDrops false")
        at(2, "kill @e[type=boat]")
        at(2, "kill @e[type=husk]")
        at(20, 'summon boat 30.5 64 148.5 {Passengers:[{id:"minecraft:husk",NoAI:1b,PersistenceRequired:1b,Silent:1b,DeathLootTable:"minecraft:empty"}]}')
        at(cp - 20, "kill @e[type=boat]")
        at(cp - 14, "execute at @e[type=husk,limit=1] run summon boat ~ ~ ~")
        # A FakePlayer riding a minecart that shuttles on powered rails. Vanilla keeps a ridden
        # entity only in the rider's player data file, which a checkpoint does not contain, and
        # the save-all after the cut makes that file newer than the checkpoint.
        at(2, "kill @e[type=minecart]")
        at(2, "fill 19 63 140 31 63 140 redstone_block")
        at(2, "fill 20 64 140 30 64 140 powered_rail[shape=east_west,powered=true]")
        at(2, "setblock 19 64 140 stone")
        at(2, "setblock 31 64 140 stone")
        at(4, "summon minecart 20.5 64.1 140.5")
        at(5, "player rbot spawn at 20.5 64 140.5 facing 0 0 in minecraft:overworld in survival")
        at(9, "player rbot mount")
        at(cp + 20, "save-all")
    if stress:
        # Several hundred chunks changed two ticks before the cut: every one of them is saved by
        # the cut and is still in the IO queue when the cut seals it. A lost or stale queued save
        # shows as a missing block at +0.
        block = "glass" if index % 2 == 0 else "stone"
        for cx in range(-4, 27):
            for cz in range(-4, 13):
                at(cp - 2, "setblock %d 62 %d %s" % (cx * 16 + 8, cz * 16 + 8, block))
    # The first checkpoint after a start first verifies its parent, which delays the cut by a few
    # ticks. An early throw-away checkpoint gets that out of the way, so the real one is cut on
    # the tick it is requested (or the next) and no command can fall on the cut tick.
    s.append((6, "@checkpoint"))
    s.append((cp, "@checkpoint"))
    s.append((length, "@halt"))
    s.sort(key=lambda e: e[0])
    return ["%d %s" % e for e in s]


def rebuild_script():
    """Puts the flying machines back at their start; they reach the end of their lanes after a few thousand ticks."""
    # Carpet rules survive a recovery now, so a rule a test changed stays until it is put back.
    lines = ["2 carpet fillUpdates true", "2 carpet creativeNoClip false", "2 carpet flippinCactus false"]
    lines += ["5 fill 0 66 %d 431 76 %d air" % (z - 2, z + 2) for z in (8, 16, 24)]
    lines += ["6 kill @e[type=item]", "6 kill @e[type=experience_orb]"]
    for observers, rest in MACHINES:
        lines += ["120 " + c for c in observers] + ["140 " + c for c in rest]
    lines += ["160 " + c for c in TRIGGERS]
    # Ends as a crash after a checkpoint: the next start recovers it with the machines running.
    lines += ["260 @checkpoint", "300 @halt"]
    return lines
