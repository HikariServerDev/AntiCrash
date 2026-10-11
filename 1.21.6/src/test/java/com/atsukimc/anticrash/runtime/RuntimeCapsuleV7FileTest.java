package com.atsukimc.anticrash.runtime;

import com.atsukimc.anticrash.util.AtomicFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeCapsuleV7FileTest {
    @TempDir
    Path temp;

    /**
     * Regression test: InhabitedTime grows every tick without marking the chunk for saving, so the
     * chunk data of a checkpoint is stale and the value has to travel in the capsule.
     */
    @Test
    void inhabitedTimeAndTicketLifetimeSurviveTheFile() throws Exception {
        RuntimeCapsule capsule = new RuntimeCapsule();
        RuntimeCapsule.InhabitedTimes inhabited = new RuntimeCapsule.InhabitedTimes();
        inhabited.dimension = "minecraft:overworld";
        inhabited.chunks.add(net.minecraft.util.math.ChunkPos.toLong(-3, 7));
        inhabited.times.add(123456789012L);
        capsule.inhabitedTimes.add(inhabited);
        TicketLedgerSnapshot.Entry portal = new TicketLedgerSnapshot.Entry();
        portal.dimension = "minecraft:the_nether";
        portal.type = "portal";
        portal.level = 30;
        portal.expiryTicks = 300;
        portal.remainingTicks = 233L;
        capsule.ticketLedger.add(portal);
        capsule.runtimeExtensionCounts.put(RuntimeExtensionSnapshot.CHUNK, 1);

        Path file = temp.resolve("runtime-capsule.json");
        AtomicFiles.writeJson(file, capsule);
        RuntimeCapsule read = AtomicFiles.readJson(file, RuntimeCapsule.class);

        assertEquals(1, read.inhabitedTimes.size());
        assertEquals(123456789012L, read.inhabitedTimes.get(0).times.get(0));
        assertEquals(net.minecraft.util.math.ChunkPos.toLong(-3, 7), read.inhabitedTimes.get(0).chunks.get(0));
        assertEquals(233L, read.ticketLedger.get(0).remainingTicks);
        assertEquals(1, read.runtimeExtensionCounts.get(RuntimeExtensionSnapshot.CHUNK));
    }

    /** A capsule written before v7 has none of the new fields and must never be reported as MATCH. */
    @Test
    void anOlderCapsuleReportsTheNewCategoriesAsMissing() throws Exception {
        Path file = temp.resolve("runtime-capsule.json");
        Files.writeString(file, "{\"formatVersion\":6,\"expectedFingerprints\":{\"CHUNK_DOMAIN\":\"abc\"},"
                + "\"ticketLedger\":[{\"dimension\":\"minecraft:overworld\",\"type\":\"portal\",\"expiryTicks\":300}]}");
        RuntimeCapsule old = AtomicFiles.readJson(file, RuntimeCapsule.class);

        assertTrue(old.inhabitedTimes.isEmpty());
        assertTrue(old.runtimeExtensionCounts.isEmpty());
        assertNull(old.ticketLedger.get(0).remainingTicks);
        for (String category : RuntimeExtensionSnapshot.CATEGORIES) {
            assertEquals("MISSING_FROM_OLD_FORMAT", RuntimeExtensionSnapshot.verdict(
                    old.expectedFingerprints.get(category), "live", old.runtimeExtensionCounts.get(category), 3));
        }
        assertEquals("MATCH", RuntimeExtensionSnapshot.verdict("abc", "abc", null, null));
        assertEquals("MISMATCH 2/3", RuntimeExtensionSnapshot.verdict("abc", "abd", 3, 2));
        assertEquals("MATCH 3/3", RuntimeExtensionSnapshot.verdict("abc", "abc", 3, 3));
    }

    /**
     * Regression test: an item summoned after a restore got a different network id than in the
     * uninterrupted game, so its (age + id) % 4 move check ran on other ticks.
     */
    @Test
    void entityIdsContinueWithTheCheckpointsRemainder() {
        int[][] cases = {{0, 0}, {250, 123456}, {123456, 250}, {39, 40}, {41, 40}, {500, 499}, {7, Integer.MAX_VALUE - 3}};
        for (int[] c : cases) {
            int current = c[0], saved = c[1];
            int next = RuntimeExtensionSnapshot.nextEntityId(current, saved);
            assertTrue(next >= current && next < current + 40, current + " -> " + next);
            assertEquals(Math.floorMod(saved, 40), Math.floorMod(next, 40));
            assertEquals(Math.floorMod(saved, 4), Math.floorMod(next, 4));
        }
    }
}
