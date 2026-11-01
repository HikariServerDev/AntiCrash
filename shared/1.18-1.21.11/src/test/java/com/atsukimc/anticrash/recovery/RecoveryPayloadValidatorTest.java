package com.atsukimc.anticrash.recovery;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.atsukimc.anticrash.integration.carpet.FakePlayerSnapshot;
import com.atsukimc.anticrash.player.PlayerSnapshotData;
import com.atsukimc.anticrash.runtime.ChunkStateSnapshot;
import com.atsukimc.anticrash.runtime.EquivalenceCategory;
import com.atsukimc.anticrash.runtime.MovingPistonTickSnapshot;
import com.atsukimc.anticrash.runtime.RuntimeCapsule;
import com.atsukimc.anticrash.runtime.RuntimeFingerprint;
import com.atsukimc.anticrash.runtime.ScheduledTickSnapshot;
import com.atsukimc.anticrash.runtime.TransientEntityTickSnapshot;
import com.atsukimc.anticrash.snapshot.CheckpointManifest;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

class RecoveryPayloadValidatorTest {
    @Test
    void acceptsMatchingManifestAndRejectsCounterMismatch() {
        RuntimeCapsule capsule = capsule();
        CheckpointManifest manifest = manifest(capsule);

        assertDoesNotThrow(
                () -> RecoveryPayloadValidator.validate(manifest, capsule, List.of(), List.of()));

        manifest.loadedChunkCount++;
        assertThrows(
                IOException.class,
                () -> RecoveryPayloadValidator.validate(manifest, capsule, List.of(), List.of()));
    }

    @Test
    void rejectsDuplicateChunkPositionsBeforeRecoveryMutation() {
        RuntimeCapsule capsule = capsule();
        capsule.chunks.get(0).loadedChunks.add(capsule.chunks.get(0).loadedChunks.get(0));

        assertThrows(
                IOException.class,
                () ->
                        RecoveryPayloadValidator.validate(
                                manifest(capsule), capsule, List.of(), List.of()));
    }

    @Test
    void rejectsDuplicateTransientStateChunksBeforeRecoveryMutation() {
        RuntimeCapsule capsule = capsule();
        capsule.chunks.get(0).transientStateChunks.add(2L);
        capsule.chunks.get(0).transientStateChunks.add(2L);

        assertThrows(
                IOException.class,
                () ->
                        RecoveryPayloadValidator.validate(
                                manifest(capsule), capsule, List.of(), List.of()));
    }

    @Test
    void acceptsLegacyVersionOneRuntimeCapsule() {
        RuntimeCapsule capsule = capsule();
        capsule.formatVersion = 1;
        capsule.chunks.get(0).formatVersion = 1;

        assertDoesNotThrow(() ->
                RecoveryPayloadValidator.validate(
                        manifest(capsule), capsule, List.of(), List.of()));
    }

    @Test
    void acceptsLegacyVersionTwoRuntimeCapsuleWithoutExactTickOrder() {
        RuntimeCapsule capsule = capsule();
        capsule.formatVersion = 2;

        assertDoesNotThrow(() ->
                RecoveryPayloadValidator.validate(
                        manifest(capsule), capsule, List.of(), List.of()));
    }

    @Test
    void acceptsVersionThreeExactScheduledTickAndRejectsManifestMismatch() {
        RuntimeCapsule capsule = capsule();
        capsule.formatVersion = 3;
        capsule.chunks.get(0).entityTickingChunks.add(1L);
        capsule.worlds.get(0).entityTickingChunkCount = 1;
        capsule.chunks.get(0).transientStateChunks.add(1L);
        capsule.scheduledTicks.add(scheduledTick());
        capsule.movingPistonTickOrder.add(movingPiston());
        CheckpointManifest manifest = manifest(capsule);

        assertDoesNotThrow(
                () -> RecoveryPayloadValidator.validate(manifest, capsule, List.of(), List.of()));

        manifest.orderedScheduledTickCount++;
        assertThrows(
                IOException.class,
                () -> RecoveryPayloadValidator.validate(manifest, capsule, List.of(), List.of()));
    }

    @Test
    void rejectsVersionFourCounterThatWouldOverlapRestoredTick() {
        RuntimeCapsule capsule = capsule();
        capsule.chunks.get(0).entityTickingChunks.add(1L);
        capsule.worlds.get(0).entityTickingChunkCount = 1;
        capsule.chunks.get(0).transientStateChunks.add(1L);
        capsule.scheduledTicks.add(scheduledTick());
        capsule.worlds.get(0).nextTickOrder = scheduledTick().subTickOrder;

        assertThrows(
                IOException.class,
                () ->
                        RecoveryPayloadValidator.validate(
                                manifest(capsule), capsule, List.of(), List.of()));
    }

    @Test
    void acceptsVersionFourCounterAfterEveryRestoredTick() {
        RuntimeCapsule capsule = capsule();
        capsule.chunks.get(0).entityTickingChunks.add(1L);
        capsule.worlds.get(0).entityTickingChunkCount = 1;
        capsule.chunks.get(0).transientStateChunks.add(1L);
        ScheduledTickSnapshot tick = scheduledTick();
        capsule.scheduledTicks.add(tick);
        capsule.worlds.get(0).nextTickOrder = tick.subTickOrder + 1L;

        assertDoesNotThrow(() ->
                RecoveryPayloadValidator.validate(
                        manifest(capsule), capsule, List.of(), List.of()));
    }

    @Test
    void acceptsVersionFourTransientEntityOrderAndRejectsManifestMismatch() {
        RuntimeCapsule capsule = capsule();
        capsule.chunks.get(0).entityTickingChunks.add(1L);
        capsule.worlds.get(0).entityTickingChunkCount = 1;
        capsule.chunks.get(0).transientStateChunks.add(1L);
        TransientEntityTickSnapshot entity = new TransientEntityTickSnapshot();
        entity.dimension = "minecraft:overworld";
        entity.uuid = "e3f98d24-0db3-4bca-95c7-18b7368706e5";
        entity.kind = TransientEntityTickSnapshot.TNT;
        entity.chunkX = 1;
        entity.chunkZ = 0;
        capsule.transientEntityTickOrder.add(entity);
        CheckpointManifest manifest = manifest(capsule);

        assertDoesNotThrow(
                () -> RecoveryPayloadValidator.validate(manifest, capsule, List.of(), List.of()));
        manifest.orderedTransientEntityCount++;
        assertThrows(
                IOException.class,
                () -> RecoveryPayloadValidator.validate(manifest, capsule, List.of(), List.of()));
    }

    @Test
    void rejectsExactScheduledTickOutsideEntityTickingRecoverySet() {
        RuntimeCapsule capsule = capsule();
        capsule.chunks.get(0).transientStateChunks.add(1L);
        capsule.scheduledTicks.add(scheduledTick());

        assertThrows(
                IOException.class,
                () ->
                        RecoveryPayloadValidator.validate(
                                manifest(capsule), capsule, List.of(), List.of()));
    }

    @Test
    void rejectsDuplicateExactScheduledTick() {
        RuntimeCapsule capsule = capsule();
        capsule.chunks.get(0).entityTickingChunks.add(1L);
        capsule.worlds.get(0).entityTickingChunkCount = 1;
        capsule.chunks.get(0).transientStateChunks.add(1L);
        capsule.scheduledTicks.add(scheduledTick());
        capsule.scheduledTicks.add(scheduledTick());

        assertThrows(
                IOException.class,
                () ->
                        RecoveryPayloadValidator.validate(
                                manifest(capsule), capsule, List.of(), List.of()));
    }

    @Test
    void rejectsDuplicateMovingPistonTickPosition() {
        RuntimeCapsule capsule = capsule();
        capsule.chunks.get(0).entityTickingChunks.add(1L);
        capsule.worlds.get(0).entityTickingChunkCount = 1;
        capsule.chunks.get(0).transientStateChunks.add(1L);
        capsule.movingPistonTickOrder.add(movingPiston());
        capsule.movingPistonTickOrder.add(movingPiston());

        assertThrows(
                IOException.class,
                () ->
                        RecoveryPayloadValidator.validate(
                                manifest(capsule), capsule, List.of(), List.of()));
    }

    @Test
    void rejectsNullSnapshotEntriesBeforeRecoveryMutation() {
        RuntimeCapsule capsule = capsule();
        CheckpointManifest manifest = manifest(capsule);
        manifest.playerSnapshotCount = 1;

        assertThrows(
                IOException.class,
                () ->
                        RecoveryPayloadValidator.validate(
                                manifest,
                                capsule,
                                java.util.Arrays.asList((PlayerSnapshotData) null),
                                List.of()));
    }

    @Test
    void rejectsASeparateFakePlayerPayloadWhoseFingerprintDoesNotMatchTheCapsule() {
        RuntimeCapsule capsule = v6Capsule();
        FakePlayerSnapshot saved = new FakePlayerSnapshot();
        saved.uuid = "e3f98d24-0db3-4bca-95c7-18b7368706e5";
        saved.name = "Builder";
        List<FakePlayerSnapshot> fakePlayers = List.of(saved);
        capsule.expectedFingerprints.put(
                EquivalenceCategory.FAKEPLAYERS,
                RuntimeFingerprint.hashLines(RuntimeFingerprint.fakePlayerLines(fakePlayers)));
        capsule.fakePlayerFingerprintVersion =
                RuntimeCapsule.CURRENT_FAKE_PLAYER_FINGERPRINT_VERSION;
        CheckpointManifest manifest = v6Manifest(capsule, fakePlayers);

        assertDoesNotThrow(
                () -> RecoveryPayloadValidator.validate(manifest, capsule, List.of(), fakePlayers));

        saved.x = 1.0D;
        assertThrows(
                IOException.class,
                () -> RecoveryPayloadValidator.validate(manifest, capsule, List.of(), fakePlayers));
    }

    /**
     * A live action pack holds explicit null members (Carpet's currentBlock while nothing is mined)
     * that Gson drops from fakeplayers.json. The fingerprint taken from the live snapshot must
     * still match the one recomputed from the file, or no FakePlayer checkpoint restores.
     */
    @Test
    void fakePlayerFingerprintSurvivesTheJsonRoundTripOfNullActionPackMembers() throws Exception {
        RuntimeCapsule capsule = v6Capsule();
        FakePlayerSnapshot live = new FakePlayerSnapshot();
        live.uuid = "e3f98d24-0db3-4bca-95c7-18b7368706e5";
        live.name = "Miner";
        live.actionPackState = new com.google.gson.JsonObject();
        live.actionPackState.addProperty("class", "carpet.helpers.EntityPlayerActionPack");
        com.google.gson.JsonObject fields = new com.google.gson.JsonObject();
        fields.add("currentBlock", com.google.gson.JsonNull.INSTANCE);
        fields.addProperty("blockHitDelay", "0");
        live.actionPackState.add("fields", fields);
        com.atsukimc.anticrash.runtime.RuntimeCapsuleManager.populateFakePlayerFingerprint(
                capsule, List.of(live));

        String json = com.atsukimc.anticrash.util.AtomicFiles.GSON.toJson(List.of(live));
        List<FakePlayerSnapshot> fromFile =
                com.atsukimc.anticrash.util.AtomicFiles.GSON.fromJson(
                        json,
                        com.google.gson.reflect.TypeToken.getParameterized(
                                        List.class, FakePlayerSnapshot.class)
                                .getType());
        CheckpointManifest manifest = v6Manifest(capsule, fromFile);
        assertDoesNotThrow(
                () -> RecoveryPayloadValidator.validate(manifest, capsule, List.of(), fromFile));
    }

    /**
     * Checkpoints written before the fix carry an irreproducible fingerprint; they must restore.
     */
    @Test
    void legacyFakePlayerFingerprintDoesNotBlockRestore() {
        RuntimeCapsule capsule = v6Capsule();
        FakePlayerSnapshot saved = new FakePlayerSnapshot();
        saved.uuid = "e3f98d24-0db3-4bca-95c7-18b7368706e5";
        saved.name = "Builder";
        List<FakePlayerSnapshot> fakePlayers = List.of(saved);
        capsule.expectedFingerprints.put(EquivalenceCategory.FAKEPLAYERS, "0".repeat(64));
        capsule.fakePlayerFingerprintVersion = 0;
        CheckpointManifest manifest = v6Manifest(capsule, fakePlayers);
        assertDoesNotThrow(
                () -> RecoveryPayloadValidator.validate(manifest, capsule, List.of(), fakePlayers));
    }

    private static RuntimeCapsule capsule() {
        RuntimeCapsule capsule = new RuntimeCapsule();
        // This fixture exercises the pre-v6 compatibility validator. New STRICT fixtures are
        // built separately with the exact v6 domain and fingerprint fields.
        capsule.formatVersion = 5;
        ChunkStateSnapshot chunks = new ChunkStateSnapshot();
        chunks.formatVersion = 2;
        chunks.dimension = "minecraft:overworld";
        chunks.loadedChunks.add(1L);
        capsule.chunks.add(chunks);
        RuntimeCapsule.WorldRuntime world = new RuntimeCapsule.WorldRuntime();
        world.dimension = chunks.dimension;
        world.loadedChunkCount = 1;
        capsule.worlds.add(world);
        return capsule;
    }

    private static RuntimeCapsule v6Capsule() {
        RuntimeCapsule capsule = new RuntimeCapsule();
        capsule.formatVersion = 6;
        capsule.fidelity = "STRICT";
        capsule.carpetCaptureStatus = "NOT_APPLICABLE";
        ChunkStateSnapshot chunks = new ChunkStateSnapshot();
        chunks.dimension = "minecraft:overworld";
        capsule.chunks.add(chunks);
        RuntimeCapsule.WorldRuntime world = new RuntimeCapsule.WorldRuntime();
        world.dimension = chunks.dimension;
        capsule.worlds.add(world);
        capsule.expectedFingerprints.put(
                EquivalenceCategory.CHUNK_DOMAIN,
                RuntimeFingerprint.hashLines(RuntimeFingerprint.chunkDomainLines(capsule.chunks)));
        capsule.unsupportedRuntimeState.clear();
        return capsule;
    }

    private static CheckpointManifest v6Manifest(
            RuntimeCapsule capsule, List<FakePlayerSnapshot> fakePlayers) {
        CheckpointManifest manifest = manifest(capsule);
        manifest.fidelity = capsule.fidelity;
        manifest.chunkDomainHash =
                capsule.expectedFingerprints.get(EquivalenceCategory.CHUNK_DOMAIN);
        manifest.fullChunkCount = 0;
        manifest.blockTickingChunkCount = 0;
        manifest.forcedChunkCount = 0;
        manifest.carpetCaptureStatus = capsule.carpetCaptureStatus;
        manifest.blockEntityTickerCount = capsule.blockEntityTickerCount();
        manifest.entityOrderCount = capsule.entityOrderCount();
        manifest.torchBurnoutCount = capsule.redstoneTorchBurnoutCount();
        manifest.ticketCount = capsule.ticketCount();
        manifest.unsupportedRuntimeState.addAll(capsule.unsupportedRuntimeState);
        manifest.fakePlayerCount = fakePlayers.size();
        return manifest;
    }

    private static CheckpointManifest manifest(RuntimeCapsule capsule) {
        CheckpointManifest manifest = new CheckpointManifest();
        manifest.runtimeCapsuleVersion = capsule.formatVersion;
        manifest.loadedChunkCount = capsule.loadedChunkCount();
        manifest.entityTickingChunkCount = capsule.entityTickingChunkCount();
        manifest.blockEventCount = capsule.blockEventCount();
        manifest.orderedScheduledTickCount = capsule.orderedScheduledTickCount();
        manifest.orderedMovingPistonCount = capsule.orderedMovingPistonCount();
        manifest.orderedTransientEntityCount = capsule.orderedTransientEntityCount();
        manifest.dimensions.add("minecraft:overworld");
        return manifest;
    }

    private static ScheduledTickSnapshot scheduledTick() {
        ScheduledTickSnapshot tick = new ScheduledTickSnapshot();
        tick.dimension = "minecraft:overworld";
        tick.kind = ScheduledTickSnapshot.BLOCK;
        tick.type = "minecraft:observer";
        tick.x = 16;
        tick.y = 64;
        tick.z = 0;
        tick.triggerTick = 12_345L;
        tick.priority = 0;
        tick.subTickOrder = 987_654_321L;
        return tick;
    }

    private static MovingPistonTickSnapshot movingPiston() {
        MovingPistonTickSnapshot piston = new MovingPistonTickSnapshot();
        piston.dimension = "minecraft:overworld";
        piston.x = 17;
        piston.y = 64;
        piston.z = 0;
        return piston;
    }
}
