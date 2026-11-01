package com.atsukimc.anticrash.recovery;

import com.atsukimc.anticrash.integration.carpet.FakePlayerSnapshot;
import com.atsukimc.anticrash.player.PlayerSnapshotData;
import com.atsukimc.anticrash.runtime.EquivalenceCategory;
import com.atsukimc.anticrash.runtime.RuntimeCapsule;
import com.atsukimc.anticrash.runtime.RuntimeCapsuleManager;
import com.atsukimc.anticrash.runtime.RuntimeFingerprint;
import com.atsukimc.anticrash.snapshot.CheckpointManifest;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Cross-checks authenticated payloads before any runtime state is mutated. */
public final class RecoveryPayloadValidator {
    private RecoveryPayloadValidator() {}

    public static void validate(
            CheckpointManifest manifest,
            RuntimeCapsule capsule,
            List<PlayerSnapshotData> players,
            List<FakePlayerSnapshot> fakePlayers)
            throws IOException {
        try {
            RuntimeCapsuleManager.validate(capsule);
        } catch (IllegalStateException exception) {
            throw new IOException(exception.getMessage(), exception);
        }
        if (manifest == null || players == null || fakePlayers == null) {
            throw new IOException("Checkpoint recovery payload is incomplete");
        }
        if (players.stream().anyMatch(java.util.Objects::isNull)
                || fakePlayers.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IOException("Checkpoint recovery payload contains an empty snapshot");
        }
        if (manifest.runtimeCapsuleVersion != capsule.formatVersion
                || manifest.loadedChunkCount != capsule.loadedChunkCount()
                || manifest.entityTickingChunkCount != capsule.entityTickingChunkCount()
                || manifest.blockEventCount != capsule.blockEventCount()
                || (capsule.formatVersion >= 3
                        && manifest.orderedScheduledTickCount
                                != capsule.orderedScheduledTickCount())
                || (capsule.formatVersion >= 3
                        && manifest.orderedMovingPistonCount != capsule.orderedMovingPistonCount())
                || (capsule.formatVersion >= 4
                        && manifest.orderedTransientEntityCount
                                != capsule.orderedTransientEntityCount())
                || (capsule.formatVersion >= 6
                        && !exactV6ManifestMatches(manifest, capsule, fakePlayers))
                || manifest.playerSnapshotCount != players.size()
                || manifest.fakePlayerCount != fakePlayers.size()) {
            throw new IOException("Checkpoint manifest counters do not match recovery payloads");
        }
        Set<String> manifestDimensions = new HashSet<>(manifest.dimensions);
        Set<String> capsuleDimensions = new HashSet<>();
        for (RuntimeCapsule.WorldRuntime world : capsule.worlds)
            capsuleDimensions.add(world.dimension);
        if (manifestDimensions.size() != manifest.dimensions.size()
                || !manifestDimensions.equals(capsuleDimensions)) {
            throw new IOException(
                    "Checkpoint manifest dimensions do not match the runtime capsule");
        }
    }

    /**
     * v6 adds the authenticated evidence needed by STRICT recovery. Keep this cross-check next to
     * the payload validator: the manifest and capsule are authenticated independently, so either
     * one being edited must fail before any world/runtime state is restored.
     */
    private static boolean exactV6ManifestMatches(
            CheckpointManifest manifest,
            RuntimeCapsule capsule,
            List<FakePlayerSnapshot> fakePlayers) {
        String fakePlayerFingerprint =
                capsule.expectedFingerprints == null
                        ? null
                        : capsule.expectedFingerprints.get(EquivalenceCategory.FAKEPLAYERS);
        if (!java.util.Objects.equals(manifest.fidelity, capsule.fidelity)
                || !java.util.Objects.equals(
                        manifest.carpetCaptureStatus, capsule.carpetCaptureStatus)
                || manifest.fullChunkCount
                        != capsule.chunks.stream().mapToInt(snapshot -> snapshot.fullCount()).sum()
                || manifest.blockTickingChunkCount
                        != capsule.chunks.stream()
                                .mapToInt(snapshot -> snapshot.blockTickingCount())
                                .sum()
                || manifest.forcedChunkCount
                        != capsule.chunks.stream()
                                .mapToInt(snapshot -> snapshot.forcedCount())
                                .sum()
                || manifest.blockEntityTickerCount != capsule.blockEntityTickerCount()
                || manifest.entityOrderCount != capsule.entityOrderCount()
                || manifest.torchBurnoutCount != capsule.redstoneTorchBurnoutCount()
                || manifest.ticketCount != capsule.ticketCount()
                || manifest.checkpointPlayerCount
                        != (capsule.checkpointPlayersCaptured
                                ? capsule.checkpointPlayers.size()
                                : 0)
                || !java.util.Objects.equals(
                        manifest.checkpointPlayerHash,
                        capsule.expectedFingerprints == null
                                ? null
                                : capsule.expectedFingerprints.get(EquivalenceCategory.PLAYERS))
                || !java.util.Objects.equals(
                        manifest.chunkDomainHash,
                        capsule.expectedFingerprints == null
                                ? null
                                : capsule.expectedFingerprints.get(
                                        EquivalenceCategory.CHUNK_DOMAIN))
                // fakeplayers.json is a separately persisted payload. Check its semantic hash
                // here as well, so a manifest/capsule pair cannot authenticate a different
                // FakePlayer file before the restore path mutates the world.
                || fakePlayerFingerprint == null
                // Version 0 fingerprints included null members that the file never contains and
                // cannot be reproduced; the file itself is already authenticated by its SHA-256.
                || (capsule.fakePlayerFingerprintVersion >= 1
                        && !fakePlayerFingerprint.equals(
                                RuntimeFingerprint.hashLines(
                                        RuntimeFingerprint.fakePlayerLines(fakePlayers))))) {
            return false;
        }
        if (manifest.unsupportedRuntimeState == null || capsule.unsupportedRuntimeState == null)
            return false;
        return new HashSet<>(manifest.unsupportedRuntimeState)
                .equals(new HashSet<>(capsule.unsupportedRuntimeState));
    }
}
