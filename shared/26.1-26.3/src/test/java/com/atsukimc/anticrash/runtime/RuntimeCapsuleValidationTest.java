package com.atsukimc.anticrash.runtime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.atsukimc.anticrash.config.AntiCrashConfig;

import org.junit.jupiter.api.Test;

import java.util.List;

class RuntimeCapsuleValidationTest {
    @Test
    void validV6CapsulePassesFormatValidationAndStrictPreflight() {
        RuntimeCapsule capsule = validCapsule();

        assertDoesNotThrow(() -> RuntimeCapsuleManager.validate(capsule));
        assertTrue(
                RuntimeCapsuleManager.strictCaptureBlockers(capsule, new AntiCrashConfig.Strict())
                        .contains("WORLD_RNG"));
    }

    @Test
    void aMissingExactChunkDomainCannotBeAcceptedAsV6() {
        RuntimeCapsule capsule = validCapsule();
        capsule.chunks.get(0).formatVersion = 2;

        assertThrows(IllegalStateException.class, () -> RuntimeCapsuleManager.validate(capsule));
        assertTrue(RuntimeCapsuleManager.strictMissingFields(capsule).contains("CHUNK_DOMAIN"));
    }

    @Test
    void unsupportedRuntimeAndCarpetSwitchesAreCategoryScoped() {
        RuntimeCapsule capsule = validCapsule();
        capsule.unsupportedRuntimeState.add("CARPET_TIME_WARP");
        capsule.carpetCaptureStatus = "INCOMPLETE";

        AntiCrashConfig.Strict strict = new AntiCrashConfig.Strict();
        List<String> defaultBlockers = RuntimeCapsuleManager.strictCaptureBlockers(capsule, strict);
        assertTrue(defaultBlockers.contains("WORLD_RNG"));
        assertTrue(defaultBlockers.contains("CARPET_TIME_WARP"));

        strict.allowUnsupportedRuntimeState = true;
        List<String> runtimeAllowed = RuntimeCapsuleManager.strictCaptureBlockers(capsule, strict);
        assertFalse(runtimeAllowed.contains("WORLD_RNG"));
        assertTrue(runtimeAllowed.contains("CARPET_TIME_WARP"));

        strict.allowUnsupportedCarpetState = true;
        assertTrue(RuntimeCapsuleManager.strictCaptureBlockers(capsule, strict).isEmpty());
    }

    @Test
    void oldCapsulesAreExplicitlyMissingStrictState() {
        RuntimeCapsule capsule = validCapsule();
        capsule.formatVersion = 5;

        assertTrue(
                RuntimeCapsuleManager.strictMissingFields(capsule).contains("RUNTIME_CAPSULE_V6"));
    }

    @Test
    void strictCheckpointPlayerIdentitiesAreValidatedIndependentlyOfPlayerRestorePayload() {
        RuntimeCapsule capsule = validCapsule();
        CheckpointPlayerSnapshot player = new CheckpointPlayerSnapshot();
        player.uuid = "e3f98d24-0db3-4bca-95c7-18b7368706e5";
        player.name = "Builder";
        player.dimension = "minecraft:overworld";
        capsule.checkpointPlayersCaptured = true;
        capsule.checkpointPlayers.add(player);
        capsule.expectedFingerprints.put(
                EquivalenceCategory.PLAYERS,
                RuntimeFingerprint.hashLines(
                        RuntimeFingerprint.checkpointPlayerLines(capsule.checkpointPlayers)));

        AntiCrashConfig.Strict strict = new AntiCrashConfig.Strict();
        strict.players.requireCheckpointPlayers = true;
        assertDoesNotThrow(() -> RuntimeCapsuleManager.validate(capsule));
        assertFalse(
                RuntimeCapsuleManager.strictMissingFields(capsule, strict)
                        .contains("CHECKPOINT_PLAYERS"));
    }

    @Test
    void unmarkedCheckpointPlayerIdentitiesAreRejected() {
        RuntimeCapsule capsule = validCapsule();
        CheckpointPlayerSnapshot player = new CheckpointPlayerSnapshot();
        player.uuid = "e3f98d24-0db3-4bca-95c7-18b7368706e5";
        player.name = "Builder";
        player.dimension = "minecraft:overworld";
        capsule.checkpointPlayers.add(player);

        assertThrows(IllegalStateException.class, () -> RuntimeCapsuleManager.validate(capsule));
    }

    private static RuntimeCapsule validCapsule() {
        RuntimeCapsule capsule = new RuntimeCapsule();
        capsule.clockStates = new java.util.ArrayList<>();
        capsule.fidelity = "STRICT";
        capsule.carpetCaptureStatus = "NOT_APPLICABLE";
        capsule.unsupportedRuntimeState.add("WORLD_RNG");

        ChunkStateSnapshot chunks = new ChunkStateSnapshot();
        chunks.dimension = "minecraft:overworld";
        capsule.chunks.add(chunks);

        RuntimeCapsule.WorldRuntime world = new RuntimeCapsule.WorldRuntime();
        world.dimension = chunks.dimension;
        capsule.worlds.add(world);

        capsule.expectedFingerprints.put(
                EquivalenceCategory.CHUNK_DOMAIN,
                RuntimeFingerprint.hashLines(RuntimeFingerprint.chunkDomainLines(capsule.chunks)));
        capsule.expectedFingerprints.put(
                EquivalenceCategory.BLOCK_EVENTS,
                RuntimeFingerprint.hashLines(
                        RuntimeFingerprint.blockEventLines(capsule.blockEvents)));
        capsule.expectedFingerprints.put(
                EquivalenceCategory.SCHEDULED_TICKS,
                RuntimeFingerprint.hashLines(
                        RuntimeFingerprint.scheduledTickLines(capsule.scheduledTicks)));
        capsule.expectedFingerprints.put(
                EquivalenceCategory.MOVING_PISTONS,
                RuntimeFingerprint.hashLines(
                        RuntimeFingerprint.movingPistonLines(capsule.movingPistonTickOrder)));
        capsule.expectedFingerprints.put(
                EquivalenceCategory.BLOCK_ENTITY_TICKERS,
                RuntimeFingerprint.hashLines(
                        RuntimeFingerprint.tickerLines(capsule.blockEntityTickerOrder)));
        capsule.expectedFingerprints.put(
                EquivalenceCategory.ENTITIES,
                RuntimeFingerprint.hashLines(RuntimeFingerprint.entityLines(capsule.entityOrder)));
        capsule.expectedFingerprints.put(
                EquivalenceCategory.REDSTONE_RUNTIME,
                RuntimeFingerprint.hashLines(
                        RuntimeFingerprint.burnoutLines(capsule.redstoneTorchBurnout)));
        capsule.expectedFingerprints.put(
                EquivalenceCategory.WORLD_RUNTIME,
                RuntimeFingerprint.hashLines(RuntimeFingerprint.worldLines(capsule.worlds)));
        capsule.expectedFingerprints.put(
                EquivalenceCategory.WORLD_RNG,
                RuntimeFingerprint.hashLines(RuntimeFingerprint.worldRngLines()));
        capsule.expectedFingerprints.put(
                EquivalenceCategory.CARPET,
                RuntimeFingerprint.hashLines(
                        RuntimeFingerprint.carpetLines(capsule.carpetTickSpeed)));
        capsule.expectedFingerprints.put(
                EquivalenceCategory.TICKET_LEDGER,
                RuntimeFingerprint.hashLines(RuntimeFingerprint.ticketLines(capsule.ticketLedger)));
        capsule.expectedFingerprints.put(
                EquivalenceCategory.FAKEPLAYERS,
                RuntimeFingerprint.hashLines(RuntimeFingerprint.fakePlayerLines(List.of())));
        return capsule;
    }
}
