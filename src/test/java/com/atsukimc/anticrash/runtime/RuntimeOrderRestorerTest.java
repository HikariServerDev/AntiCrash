package com.atsukimc.anticrash.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

//? if (>=1.18 && <=1.21.4) {
/*class RuntimeOrderRestorerTest {
    /^* Vanilla's Ticket#isExpired. ^/
    private static boolean expired(long age, long tickCreated, long expiry) {
        return expiry != 0L && age - tickCreated > expiry;
    }

    *///?} else if (>=1.21.5 && <=26.3) {
class RuntimeOrderRestorerTest {
    //?}
    @Test
    void aRestoredTicketLapsesOnTheSameTickItWouldHave() {
        long[][] cases = {
            {300, 1}, {300, 10}, {300, 299}, {300, 300}, {5, 1}, {5, 5}, {1, 0}, {1, 1}
        };
        for (long[] c : cases) {
            long expiry = c[0], remaining = c[1];
            // Live server: created at age 1000 and captured when `remaining` ticks were left.

            //? if (>=1.18 && <=1.21.4) {
            /*long liveCreated = 1000, captureAge = liveCreated + (expiry - remaining);
            *///?}

            // New JVM: the ticket manager's age restarted at some unrelated value.

            //? if (>=1.18 && <=1.21.4) {
            /*long newAge = 37;
            *///?}

            //? if (>=1.18 && <=1.21.4) {
            /*long restoredCreated = RuntimeOrderRestorer.tickCreatedFor(newAge, expiry, remaining);
            *///?} else if (>=1.21.5 && <=1.21.11) {
            /*var type =
                                expiry == 300
                                        ? net.minecraft.server.level.TicketType.PORTAL
                                        : net.minecraft.server.level.TicketType.UNKNOWN;
*///?} else if (>=26.1 && <=26.3) {
            var type =
                    expiry == 300
                            ? net.minecraft.server.level.TicketType.PORTAL
                            : net.minecraft.server.level.TicketType.UNKNOWN;
            //?}

            //? if (>=1.21.5 && <=1.21.11) {
            /*var nbt = new net.minecraft.nbt.CompoundTag();
*///?} else if (>=26.1 && <=26.3) {
            var nbt = new net.minecraft.nbt.CompoundTag();
            //?}

            //? if (>=1.21.5 && <=1.21.11) {
            /*nbt.putString(
                                "type", net.minecraft.core.registries.BuiltInRegistries.TICKET_TYPE.getKey(type).toString());
*///?} else if (>=26.1 && <=26.3) {
            nbt.putString(
                    "type",
                    net.minecraft.core.registries.BuiltInRegistries.TICKET_TYPE
                            .getKey(type)
                            .toString());
            //?}

            //? if (>=1.21.5 && <=26.3) {
            nbt.putInt("level", 31);
            //?}

            //? if (>=1.21.5 && <=26.3) {
            nbt.putLong("ticks_left", remaining);
            //?}

            //? if (>=1.21.5 && <=1.21.11) {
            /*var restored =
                                net.minecraft.server.level.Ticket.CODEC
                                        .codec()
                                        .parse(net.minecraft.nbt.NbtOps.INSTANCE, nbt)
                                        .getOrThrow();
*///?} else if (>=26.1 && <=26.3) {
            var restored =
                    net.minecraft.server.level.Ticket.CODEC
                            .codec()
                            .parse(net.minecraft.nbt.NbtOps.INSTANCE, nbt)
                            .getOrThrow();
            //?}

            for (int ticks = 0; ticks <= expiry + 2; ticks++) {

                //? if (>=1.18 && <=1.21.4) {
                /*assertEquals(
                        expired(captureAge + ticks, liveCreated, expiry),
                        expired(newAge + ticks, restoredCreated, expiry),
                        "expiry " + expiry + " remaining " + remaining + " after " + ticks);
                *///?} else if (>=1.21.5 && <=1.21.11) {
                /*assertEquals(
                                        remaining - ticks < 0,
                                        restored.isTimedOut(),
                                        "expiry " + expiry + " remaining " + remaining + " after " + ticks);
*///?} else if (>=26.1 && <=26.3) {
                assertEquals(
                        remaining - ticks < 0,
                        restored.isTimedOut(),
                        "expiry " + expiry + " remaining " + remaining + " after " + ticks);
                //?}

                //? if (>=1.21.5 && <=1.21.11) {
                /*restored.decreaseTicksLeft();
*///?} else if (>=26.1 && <=26.3) {
                restored.decreaseTicksLeft();
                //?}

            }

            //? if (>=1.18 && <=1.21.4) {
            /*assertFalse(expired(newAge + remaining, restoredCreated, expiry));
            *///?}

            //? if (>=1.18 && <=1.21.4) {
            /*assertTrue(expired(newAge + remaining + 1, restoredCreated, expiry));
            *///?}

        }
    }

    private static TicketLedgerSnapshot.Entry ticket(
            String type, int x, int z, int level, long expiry) {
        TicketLedgerSnapshot.Entry entry = new TicketLedgerSnapshot.Entry();
        entry.dimension = "minecraft:overworld";
        entry.type = type;
        entry.x = x;
        entry.z = z;
        entry.level = level;
        entry.expiryTicks = expiry;
        return entry;
    }

    /**
     * Regression test: the world was resumed before the FakePlayer's player ticket existed again,
     * so the recovery tickets were kept and chunks ticked that the checkpoint's world had not.
     */
    @Test
    void recoveryTicketsGoOnlyWhenEveryPermanentTicketIsBack() {
        java.util.List<TicketLedgerSnapshot.Entry> checkpoint =
                java.util.List.of(
                        ticket("player", 0, 8, 31, 0),
                        ticket("forced", 0, 0, 31, 0),
                        ticket("start", 0, 0, 22, 0),
                        ticket("portal", 0, 4, 30, 300),
                        ticket("unknown", 3, 3, 33, 1),
                        ticket("light", 5, 5, 33, 0));
        java.util.List<TicketLedgerSnapshot.Entry> beforePlayer =
                java.util.List.of(ticket("forced", 0, 0, 31, 0), ticket("start", 0, 0, 22, 0));
        assertFalse(RuntimeOrderRestorer.permanentTicketsCovered(beforePlayer, checkpoint));

        java.util.List<TicketLedgerSnapshot.Entry> wrongLevel =
                java.util.List.of(
                        ticket("forced", 0, 0, 31, 0),
                        ticket("start", 0, 0, 22, 0),
                        ticket("player", 0, 8, 32, 0));
        assertFalse(RuntimeOrderRestorer.permanentTicketsCovered(wrongLevel, checkpoint));

        // Expiring and light tickets are restored (or not needed) separately and are not waited
        // for.
        java.util.List<TicketLedgerSnapshot.Entry> ready =
                java.util.List.of(
                        ticket("forced", 0, 0, 31, 0),
                        ticket("start", 0, 0, 22, 0),
                        ticket("player", 0, 8, 31, 0),
                        ticket("player", 9, 9, 31, 0));
        assertTrue(RuntimeOrderRestorer.permanentTicketsCovered(ready, checkpoint));
    }

    /**
     * A chunk lookup during the recovery freeze leaves a one-tick UNKNOWN ticket (and can load the
     * chunk). Only the checkpoint's own expiring tickets may be there when the world resumes.
     */
    @Test
    void onlyTheCheckpointsExpiringTicketsAreKept() {
        TicketLedgerSnapshot.Entry unknown = ticket("unknown", 3, 3, 33, 1);
        unknown.argument = "[3, 3]";
        unknown.remainingTicks = 1L;

        //? if (>=1.18 && <=1.21.3) {
        /*TicketLedgerSnapshot.Entry teleport = ticket("post_teleport", 0, 9, 32, 5);
        *///?} else if (>=1.21.4 && <=26.3) {
        TicketLedgerSnapshot.Entry teleport = ticket("ender_pearl", 0, 9, 32, 40);
        //?}

        //? if (>=1.18 && <=1.21.3) {
        /*teleport.argument = "4274109";
        *///?} else if (>=1.21.4 && <=26.3) {
        teleport.argument = "[0, 9]";
        //?}

        teleport.remainingTicks = 3L;
        TicketLedgerSnapshot.Entry player = ticket("player", 0, 8, 31, 0);
        player.argument = "[0, 8]";
        java.util.Set<String> keys =
                RuntimeOrderRestorer.expiringKeys(java.util.List.of(unknown, teleport, player));

        assertEquals(2, keys.size());
        assertTrue(keys.contains("minecraft:overworld|3,3|unknown|33|[3, 3]"));
        // The network entity id of a post-teleport ticket changes with every restart.

        //? if (>=1.18 && <=1.21.3) {
        /*assertTrue(keys.contains("minecraft:overworld|0,9|post_teleport|32|-"));
        *///?} else if (>=1.21.4 && <=26.3) {
        assertTrue(keys.contains("minecraft:overworld|0,9|ender_pearl|32|[0, 9]"));
        //?}

    }
}
