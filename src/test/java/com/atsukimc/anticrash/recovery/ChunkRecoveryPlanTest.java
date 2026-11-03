package com.atsukimc.anticrash.recovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.atsukimc.anticrash.runtime.ChunkStateSnapshot;
import com.atsukimc.anticrash.runtime.MovingPistonTickSnapshot;
import com.atsukimc.anticrash.runtime.RuntimeCapsule;
import com.atsukimc.anticrash.runtime.ScheduledTickSnapshot;

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.level.ChunkPos;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.ChunkPos;
*///?}

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

class ChunkRecoveryPlanTest {
    @Test
    void usesVanillaFullAndEntityTickingLevelsAndDeduplicatesPositions() {
        RuntimeCapsule capsule = new RuntimeCapsule();

        //? if (>=26.1 && <=26.3) {
        /*capsule.clockStates = new java.util.ArrayList<>();
*///?}

        ChunkStateSnapshot chunks = new ChunkStateSnapshot();
        chunks.dimension = "minecraft:overworld";

        //? if (>=1.18 && <=1.21.11) {
        long loadedOnly = new ChunkPos(1, 2).toLong();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*long loadedOnly = new ChunkPos(1, 2).pack();
*///?}

        //? if (>=1.18 && <=1.21.11) {
        long entity = new ChunkPos(3, 4).toLong();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*long entity = new ChunkPos(3, 4).pack();
*///?}

        //? if (>=1.18 && <=1.21.11) {
        long forced = new ChunkPos(5, 6).toLong();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*long forced = new ChunkPos(5, 6).pack();
*///?}

        chunks.loadedChunks.addAll(List.of(loadedOnly, entity, forced));
        chunks.entityTickingChunks.add(entity);
        chunks.forcedChunks.add(forced);
        capsule.chunks.add(chunks);

        List<ChunkRecoveryPlan.Entry> plan = ChunkRecoveryPlan.from(capsule);

        assertEquals(3, plan.size());
        assertEquals(ChunkRecoveryPlan.FULL_TICKET_LEVEL, plan.get(0).ticketLevel());
        assertFalse(plan.get(0).forced());
        assertEquals(ChunkRecoveryPlan.ENTITY_TICKING_TICKET_LEVEL, plan.get(1).ticketLevel());
        assertFalse(plan.get(1).forced());
        assertEquals(ChunkRecoveryPlan.ENTITY_TICKING_TICKET_LEVEL, plan.get(2).ticketLevel());
        assertTrue(plan.get(2).forced());
    }

    @Test
    void criticalModePreservesDormantExplicitAnchorAsFullOnly() {
        RuntimeCapsule capsule = new RuntimeCapsule();

        //? if (>=26.1 && <=26.3) {
        /*capsule.clockStates = new java.util.ArrayList<>();
*///?}

        ChunkStateSnapshot chunks = new ChunkStateSnapshot();
        chunks.dimension = "minecraft:overworld";

        //? if (>=1.18 && <=1.21.11) {
        long loadedOnly = new ChunkPos(1, 2).toLong();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*long loadedOnly = new ChunkPos(1, 2).pack();
*///?}

        //? if (>=1.18 && <=1.21.11) {
        long entity = new ChunkPos(3, 4).toLong();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*long entity = new ChunkPos(3, 4).pack();
*///?}

        //? if (>=1.18 && <=1.21.11) {
        long forced = new ChunkPos(5, 6).toLong();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*long forced = new ChunkPos(5, 6).pack();
*///?}

        //? if (>=1.18 && <=1.21.11) {
        long anchor = new ChunkPos(7, 8).toLong();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*long anchor = new ChunkPos(7, 8).pack();
*///?}

        chunks.loadedChunks.addAll(List.of(loadedOnly, entity, forced));
        chunks.entityTickingChunks.add(entity);
        chunks.forcedChunks.add(forced);
        capsule.chunks.add(chunks);

        ChunkRecoveryPlan.Selection selection =
                ChunkRecoveryPlan.select(
                        capsule,
                        ChunkRecoveryPlan.PreloadMode.CRITICAL,
                        Set.of(new ChunkRecoveryPlan.Anchor("minecraft:overworld", anchor)),
                        16);

        assertEquals(2, selection.entries().size());
        assertTrue(
                selection.entries().stream()
                        .anyMatch(entry -> entry.forced() && entry.packedPosition() == forced));
        assertTrue(
                selection.entries().stream()
                        .anyMatch(entry ->
                                !entry.forced()
                                        && entry.packedPosition() == anchor
                                        && entry.ticketLevel()
                                                == ChunkRecoveryPlan.FULL_TICKET_LEVEL));
        assertEquals(2, selection.deferredLoaded());
        assertEquals(1, selection.deferredEntityTicking());
        assertEquals(3, selection.totalLoaded());
        assertEquals(1, selection.totalEntityTicking());
        assertEquals(1, selection.totalForced());
    }

    @Test
    void criticalModeIncludesExactTransientStateChunksAndDeduplicatesAnchors() {
        RuntimeCapsule capsule = new RuntimeCapsule();

        //? if (>=26.1 && <=26.3) {
        /*capsule.clockStates = new java.util.ArrayList<>();
*///?}

        ChunkStateSnapshot chunks = new ChunkStateSnapshot();
        chunks.dimension = "minecraft:overworld";

        //? if (>=1.18 && <=1.21.11) {
        long transientChunk = new ChunkPos(-328, 123).toLong();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*long transientChunk = new ChunkPos(-328, 123).pack();
*///?}

        chunks.loadedChunks.add(transientChunk);
        chunks.entityTickingChunks.add(transientChunk);
        chunks.transientStateChunks.add(transientChunk);
        capsule.chunks.add(chunks);

        ChunkRecoveryPlan.Selection selection =
                ChunkRecoveryPlan.select(
                        capsule,
                        ChunkRecoveryPlan.PreloadMode.CRITICAL,
                        Set.of(new ChunkRecoveryPlan.Anchor("minecraft:overworld", transientChunk)),
                        16);

        assertEquals(1, selection.entries().size());
        assertEquals(transientChunk, selection.entries().get(0).packedPosition());
        assertEquals(
                ChunkRecoveryPlan.ENTITY_TICKING_TICKET_LEVEL,
                selection.entries().get(0).ticketLevel());
        assertEquals(0, selection.deferredEntityTicking());
    }

    @Test
    void criticalModePreparesScheduledTickChunksAtTheirObservedTickingLevel() {
        RuntimeCapsule capsule = new RuntimeCapsule();

        //? if (>=26.1 && <=26.3) {
        /*capsule.clockStates = new java.util.ArrayList<>();
*///?}

        ChunkStateSnapshot chunks = new ChunkStateSnapshot();
        chunks.dimension = "minecraft:overworld";

        //? if (>=1.18 && <=1.21.11) {
        long entityTicking = new ChunkPos(1, 1).toLong();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*long entityTicking = new ChunkPos(1, 1).pack();
*///?}

        //? if (>=1.18 && <=1.21.11) {
        long blockTicking = new ChunkPos(2, 2).toLong();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*long blockTicking = new ChunkPos(2, 2).pack();
*///?}

        //? if (>=1.18 && <=1.21.11) {
        long fullOnly = new ChunkPos(3, 3).toLong();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*long fullOnly = new ChunkPos(3, 3).pack();
*///?}

        chunks.loadedChunks.addAll(List.of(entityTicking, blockTicking, fullOnly));
        chunks.fullChunks.addAll(List.of(entityTicking, blockTicking, fullOnly));
        chunks.blockTickingChunks.addAll(List.of(entityTicking, blockTicking));
        chunks.entityTickingChunks.add(entityTicking);
        capsule.chunks.add(chunks);
        for (long packed : List.of(entityTicking, blockTicking, fullOnly)) {
            ScheduledTickSnapshot tick = new ScheduledTickSnapshot();
            tick.dimension = "minecraft:overworld";
            tick.kind = ScheduledTickSnapshot.BLOCK;
            tick.type = "minecraft:repeater";

            //? if (>=1.18 && <=1.21.11) {
            tick.x = new ChunkPos(packed).getMinBlockX();
            //?}
            //? if (>=26.1 && <=26.3) {
            /*tick.x = ChunkPos.unpack(packed).getMinBlockX();
*///?}

            //? if (>=1.18 && <=1.21.11) {
            tick.z = new ChunkPos(packed).getMinBlockZ();
            //?}
            //? if (>=26.1 && <=26.3) {
            /*tick.z = ChunkPos.unpack(packed).getMinBlockZ();
*///?}

            capsule.scheduledTicks.add(tick);
        }

        ChunkRecoveryPlan.Selection selection =
                ChunkRecoveryPlan.select(
                        capsule, ChunkRecoveryPlan.PreloadMode.CRITICAL, Set.of(), 16);

        java.util.Map<Long, Integer> levels = new java.util.HashMap<>();
        for (ChunkRecoveryPlan.Entry entry : selection.entries()) {
            levels.put(entry.packedPosition(), entry.ticketLevel());
        }
        assertEquals(ChunkRecoveryPlan.ENTITY_TICKING_TICKET_LEVEL, levels.get(entityTicking));
        assertEquals(ChunkRecoveryPlan.BLOCK_TICKING_TICKET_LEVEL, levels.get(blockTicking));
        assertEquals(ChunkRecoveryPlan.FULL_TICKET_LEVEL, levels.get(fullOnly));
    }

    @Test
    void allModeRestoresEveryLoadedChunkAtItsObservedLevel() {
        RuntimeCapsule capsule = new RuntimeCapsule();

        //? if (>=26.1 && <=26.3) {
        /*capsule.clockStates = new java.util.ArrayList<>();
*///?}

        ChunkStateSnapshot chunks = new ChunkStateSnapshot();
        chunks.dimension = "minecraft:overworld";

        //? if (>=1.18 && <=1.21.11) {
        long entityTicking = new ChunkPos(1, 1).toLong();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*long entityTicking = new ChunkPos(1, 1).pack();
*///?}

        //? if (>=1.18 && <=1.21.11) {
        long blockTicking = new ChunkPos(2, 2).toLong();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*long blockTicking = new ChunkPos(2, 2).pack();
*///?}

        //? if (>=1.18 && <=1.21.11) {
        long fullOnly = new ChunkPos(3, 3).toLong();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*long fullOnly = new ChunkPos(3, 3).pack();
*///?}

        chunks.loadedChunks.addAll(List.of(entityTicking, blockTicking, fullOnly));
        chunks.fullChunks.addAll(List.of(entityTicking, blockTicking, fullOnly));
        chunks.blockTickingChunks.addAll(List.of(entityTicking, blockTicking));
        chunks.entityTickingChunks.add(entityTicking);
        capsule.chunks.add(chunks);

        ChunkRecoveryPlan.Selection selection =
                ChunkRecoveryPlan.select(
                        capsule,
                        ChunkRecoveryPlan.PreloadMode.ALL,
                        Set.of(),
                        ChunkRecoveryPlan.DEFAULT_MAX_PRELOAD_CHUNKS);

        java.util.Map<Long, Integer> levels = new java.util.HashMap<>();
        for (ChunkRecoveryPlan.Entry entry : selection.entries()) {
            levels.put(entry.packedPosition(), entry.ticketLevel());
        }
        assertEquals(3, levels.size());
        assertEquals(ChunkRecoveryPlan.ENTITY_TICKING_TICKET_LEVEL, levels.get(entityTicking));
        assertEquals(ChunkRecoveryPlan.BLOCK_TICKING_TICKET_LEVEL, levels.get(blockTicking));
        assertEquals(ChunkRecoveryPlan.FULL_TICKET_LEVEL, levels.get(fullOnly));
        assertEquals(0, selection.deferredLoaded());
        assertEquals(0, selection.deferredEntityTicking());
    }

    @Test
    void versionOneCapsuleRemainsReadableWithoutTransientChunkMetadata() {
        RuntimeCapsule capsule = new RuntimeCapsule();

        //? if (>=26.1 && <=26.3) {
        /*capsule.clockStates = new java.util.ArrayList<>();
*///?}

        capsule.formatVersion = 1;
        ChunkStateSnapshot chunks = new ChunkStateSnapshot();
        chunks.formatVersion = 1;
        chunks.dimension = "minecraft:overworld";

        //? if (>=1.18 && <=1.21.11) {
        chunks.transientStateChunks.add(new ChunkPos(10, 10).toLong());
        //?}
        //? if (>=26.1 && <=26.3) {
        /*chunks.transientStateChunks.add(new ChunkPos(10, 10).pack());
*///?}

        capsule.chunks.add(chunks);

        ChunkRecoveryPlan.Selection selection =
                ChunkRecoveryPlan.select(
                        capsule, ChunkRecoveryPlan.PreloadMode.CRITICAL, Set.of(), 16);

        assertTrue(selection.entries().isEmpty());
    }

    @Test
    void dormantTransientChunkIsNotActivatedByCriticalRecovery() {
        RuntimeCapsule capsule = new RuntimeCapsule();

        //? if (>=26.1 && <=26.3) {
        /*capsule.clockStates = new java.util.ArrayList<>();
*///?}

        ChunkStateSnapshot chunks = new ChunkStateSnapshot();
        chunks.dimension = "minecraft:overworld";

        //? if (>=1.18 && <=1.21.11) {
        long dormant = new ChunkPos(20, 20).toLong();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*long dormant = new ChunkPos(20, 20).pack();
*///?}

        chunks.loadedChunks.add(dormant);
        chunks.transientStateChunks.add(dormant);
        capsule.chunks.add(chunks);

        ChunkRecoveryPlan.Selection selection =
                ChunkRecoveryPlan.select(
                        capsule, ChunkRecoveryPlan.PreloadMode.CRITICAL, Set.of(), 16);

        assertTrue(selection.entries().isEmpty());
        assertEquals(1, selection.deferredLoaded());
    }

    @Test
    void selectedPlanCannotExceedConfiguredPreloadLimit() {
        RuntimeCapsule capsule = new RuntimeCapsule();

        //? if (>=26.1 && <=26.3) {
        /*capsule.clockStates = new java.util.ArrayList<>();
*///?}

        ChunkStateSnapshot chunks = new ChunkStateSnapshot();
        chunks.dimension = "minecraft:overworld";

        //? if (>=1.18 && <=1.21.11) {
        chunks.forcedChunks.add(new ChunkPos(1, 1).toLong());
        //?}
        //? if (>=26.1 && <=26.3) {
        /*chunks.forcedChunks.add(new ChunkPos(1, 1).pack());
*///?}

        capsule.chunks.add(chunks);

        //? if (>=1.18 && <=1.21.11) {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ChunkRecoveryPlan.select(
                                capsule,
                                ChunkRecoveryPlan.PreloadMode.CRITICAL,
                                Set.of(
                                        new ChunkRecoveryPlan.Anchor(
                                                "minecraft:overworld",
                                                new ChunkPos(2, 2).toLong())),
                                1));
        //?}
        //? if (>=26.1 && <=26.3) {
        /*assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                ChunkRecoveryPlan.select(
                                        capsule,
                                        ChunkRecoveryPlan.PreloadMode.CRITICAL,
                                        Set.of(
                                                new ChunkRecoveryPlan.Anchor(
                                                        "minecraft:overworld", new ChunkPos(2, 2).pack())),
                                        1));
*///?}

        assertEquals(
                ChunkRecoveryPlan.PreloadMode.CRITICAL,
                ChunkRecoveryPlan.PreloadMode.parse("critical"));
    }

    //? if (>=1.18 && <=1.21.4) {
    @Test
    void convertsVanillaTicketLevelsToServerChunkManagerRadii()
                //?}
                //? if (>=1.21.5 && <=26.3) {
                /*@Test
                    void passesVanillaTicketLevelsDirectlyIn1215()
*///?}
            {

        //? if (>=1.18 && <=1.21.4) {
        assertEquals(
                0, ChunkRecoveryPlan.ticketRadiusForLevel(ChunkRecoveryPlan.FULL_TICKET_LEVEL));
        //?}
        //? if (>=1.21.5 && <=26.3) {
        /*assertEquals(
                        33, ChunkRecoveryPlan.ticketArgumentForLevel(ChunkRecoveryPlan.FULL_TICKET_LEVEL));
*///?}

        //? if (>=1.18 && <=1.21.4) {
        assertEquals(
                2,
                ChunkRecoveryPlan.ticketRadiusForLevel(
                        ChunkRecoveryPlan.ENTITY_TICKING_TICKET_LEVEL));
        //?}
        //? if (>=1.21.5 && <=26.3) {
        /*assertEquals(
                        31,
                        ChunkRecoveryPlan.ticketArgumentForLevel(
                                ChunkRecoveryPlan.ENTITY_TICKING_TICKET_LEVEL));
*///?}

        //? if (>=1.18 && <=1.21.4) {
        assertThrows(
                IllegalArgumentException.class, () -> ChunkRecoveryPlan.ticketRadiusForLevel(-1));
        //?}
        //? if (>=1.21.5 && <=26.3) {
        /*assertThrows(
                        IllegalArgumentException.class, () -> ChunkRecoveryPlan.ticketArgumentForLevel(-1));
*///?}

        //? if (>=1.18 && <=1.21.4) {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ChunkRecoveryPlan.ticketRadiusForLevel(
                                ChunkRecoveryPlan.FULL_TICKET_LEVEL + 1));
        //?}
        //? if (>=1.21.5 && <=26.3) {
        /*assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                ChunkRecoveryPlan.ticketArgumentForLevel(
                                        ChunkRecoveryPlan.FULL_TICKET_LEVEL + 1));
*///?}

    }

    @Test
    void criticalModeKeepsScheduledTicksAndMovingPistonsInTheirExactChunks() {
        RuntimeCapsule capsule = new RuntimeCapsule();

        //? if (>=26.1 && <=26.3) {
        /*capsule.clockStates = new java.util.ArrayList<>();
*///?}

        ScheduledTickSnapshot scheduled = new ScheduledTickSnapshot();
        scheduled.dimension = "minecraft:overworld";
        scheduled.kind = ScheduledTickSnapshot.BLOCK;
        scheduled.type = "minecraft:piston";
        scheduled.x = 32;
        scheduled.y = 64;
        scheduled.z = 48;
        capsule.scheduledTicks.add(scheduled);

        MovingPistonTickSnapshot piston = new MovingPistonTickSnapshot();
        piston.dimension = "minecraft:overworld";
        piston.x = -17;
        piston.y = 64;
        piston.z = -1;
        capsule.movingPistonTickOrder.add(piston);

        ChunkRecoveryPlan.Selection selection =
                ChunkRecoveryPlan.select(
                        capsule, ChunkRecoveryPlan.PreloadMode.CRITICAL, Set.of(), 16);

        assertEquals(2, selection.entries().size());

        //? if (>=1.18 && <=1.21.11) {
        assertTrue(
                selection.entries().stream()
                        .anyMatch(entry ->
                                entry.packedPosition() == new ChunkPos(2, 3).toLong()
                                        && entry.ticketLevel()
                                                == ChunkRecoveryPlan.FULL_TICKET_LEVEL));
        //?}
        //? if (>=26.1 && <=26.3) {
        /*assertTrue(
                        selection.entries().stream()
                                .anyMatch(entry ->
                                        entry.packedPosition() == new ChunkPos(2, 3).pack()
                                                && entry.ticketLevel()
                                                        == ChunkRecoveryPlan.FULL_TICKET_LEVEL));
*///?}

        //? if (>=1.18 && <=1.21.11) {
        assertTrue(
                selection.entries().stream()
                        .anyMatch(entry ->
                                entry.packedPosition() == new ChunkPos(-2, -1).toLong()
                                        && entry.ticketLevel()
                                                == ChunkRecoveryPlan.BLOCK_TICKING_TICKET_LEVEL));
        //?}
        //? if (>=26.1 && <=26.3) {
        /*assertTrue(
                        selection.entries().stream()
                                .anyMatch(entry ->
                                        entry.packedPosition() == new ChunkPos(-2, -1).pack()
                                                && entry.ticketLevel()
                                                        == ChunkRecoveryPlan.BLOCK_TICKING_TICKET_LEVEL));
*///?}

    }

    @Test
    void criticalSelectionBoundsTheObserved2489ChunkCapsuleToThreeAnchors() {
        RuntimeCapsule capsule = new RuntimeCapsule();

        //? if (>=26.1 && <=26.3) {
        /*capsule.clockStates = new java.util.ArrayList<>();
*///?}

        ChunkStateSnapshot chunks = new ChunkStateSnapshot();
        chunks.dimension = "minecraft:overworld";
        for (int index = 0; index < 2_489; index++) {

            //? if (>=1.18 && <=1.21.11) {
            chunks.loadedChunks.add(new ChunkPos(index, 0).toLong());
            //?}
            //? if (>=26.1 && <=26.3) {
            /*chunks.loadedChunks.add(new ChunkPos(index, 0).pack());
*///?}

        }
        for (int index = 0; index < 821; index++) {

            //? if (>=1.18 && <=1.21.11) {
            chunks.entityTickingChunks.add(new ChunkPos(index, 100).toLong());
            //?}
            //? if (>=26.1 && <=26.3) {
            /*chunks.entityTickingChunks.add(new ChunkPos(index, 100).pack());
*///?}

        }

        //? if (>=1.18 && <=1.21.11) {
        long forced = new ChunkPos(0, 200).toLong();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*long forced = new ChunkPos(0, 200).pack();
*///?}

        chunks.forcedChunks.add(forced);
        capsule.chunks.add(chunks);

        //? if (>=1.18 && <=1.21.11) {
        ChunkRecoveryPlan.Selection selection =
                ChunkRecoveryPlan.select(
                        capsule,
                        ChunkRecoveryPlan.PreloadMode.CRITICAL,
                        Set.of(
                                new ChunkRecoveryPlan.Anchor(
                                        "minecraft:overworld", new ChunkPos(0, 201).toLong()),
                                new ChunkRecoveryPlan.Anchor(
                                        "minecraft:overworld", new ChunkPos(1, 201).toLong())),
                        256);
        //?}
        //? if (>=26.1 && <=26.3) {
        /*ChunkRecoveryPlan.Selection selection =
                        ChunkRecoveryPlan.select(
                                capsule,
                                ChunkRecoveryPlan.PreloadMode.CRITICAL,
                                Set.of(
                                        new ChunkRecoveryPlan.Anchor(
                                                "minecraft:overworld", new ChunkPos(0, 201).pack()),
                                        new ChunkRecoveryPlan.Anchor(
                                                "minecraft:overworld", new ChunkPos(1, 201).pack())),
                                256);
*///?}

        assertEquals(3, selection.entries().size());
        assertEquals(2_489, selection.deferredLoaded());
        assertEquals(821, selection.deferredEntityTicking());

        //? if (>=1.18 && <=1.21.4) {
        assertTrue(
                selection.entries().stream()
                        .allMatch(entry ->
                                ChunkRecoveryPlan.ticketRadiusForLevel(entry.ticketLevel()) <= 2));
        //?}
        //? if (>=1.21.5 && <=26.3) {
        /*assertTrue(
                        selection.entries().stream()
                                .allMatch(entry ->
                                        ChunkRecoveryPlan.ticketArgumentForLevel(entry.ticketLevel())
                                                >= 31));
*///?}

    }

    @Test
    void strictSelectionKeepsEveryObservedDomainRowAndReportsLimitWithoutTruncation() {
        RuntimeCapsule capsule = new RuntimeCapsule();

        //? if (>=26.1 && <=26.3) {
        /*capsule.clockStates = new java.util.ArrayList<>();
*///?}

        capsule.formatVersion = 6;
        ChunkStateSnapshot chunks = new ChunkStateSnapshot();
        chunks.dimension = "minecraft:overworld";

        //? if (>=1.18 && <=1.21.11) {
        long entity = new ChunkPos(-330, 122).toLong();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*long entity = new ChunkPos(-330, 122).pack();
*///?}

        //? if (>=1.18 && <=1.21.11) {
        long full = new ChunkPos(-330, 123).toLong();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*long full = new ChunkPos(-330, 123).pack();
*///?}

        //? if (>=1.18 && <=1.21.11) {
        long forced = new ChunkPos(-330, 124).toLong();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*long forced = new ChunkPos(-330, 124).pack();
*///?}

        chunks.loadedChunks.addAll(List.of(entity, full));
        chunks.fullChunks.addAll(List.of(entity, full));
        chunks.blockTickingChunks.add(entity);
        chunks.entityTickingChunks.add(entity);
        chunks.forcedChunks.add(forced);
        chunks.domain.add(domain(entity, true, true, true, true, false));
        chunks.domain.add(domain(full, true, true, false, false, false));
        chunks.domain.add(domain(forced, false, false, false, false, true));
        capsule.chunks.add(chunks);

        ChunkRecoveryPlan.Selection selection = ChunkRecoveryPlan.selectStrict(capsule, 2);

        assertEquals(3, selection.entries().size());
        assertTrue(selection.limitExceeded());
        assertEquals(
                ChunkRecoveryPlan.ENTITY_TICKING_TICKET_LEVEL,
                selection.entries().get(0).ticketLevel());
        assertEquals(ChunkRecoveryPlan.FULL_TICKET_LEVEL, selection.entries().get(1).ticketLevel());
        assertTrue(selection.entries().get(2).forced());
        assertEquals(3, selection.requiredCount());
    }

    private static ChunkStateSnapshot.ChunkReadiness domain(
            long packed,
            boolean loaded,
            boolean full,
            boolean block,
            boolean entity,
            boolean forced) {
        ChunkStateSnapshot.ChunkReadiness row = new ChunkStateSnapshot.ChunkReadiness();
        row.packedPosition = packed;
        row.loaded = loaded;
        row.full = full;
        row.blockTicking = block;
        row.entityTicking = entity;
        row.forced = forced;
        row.levelType = entity ? "ENTITY_TICKING" : block ? "TICKING" : "INACCESSIBLE";
        row.source = "test";
        return row;
    }
}
