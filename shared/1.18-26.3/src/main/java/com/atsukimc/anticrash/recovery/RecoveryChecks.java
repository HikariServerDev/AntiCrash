package com.atsukimc.anticrash.recovery;

import com.atsukimc.anticrash.config.AntiCrashConfig;
import com.atsukimc.anticrash.runtime.ChunkStateSnapshot;
import com.atsukimc.anticrash.runtime.EquivalenceCategory;
import com.atsukimc.anticrash.runtime.RuntimeCapsule;
import com.atsukimc.anticrash.snapshot.CheckpointManifest;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/** Pure pre-start policy; all versions keep the same fail-closed checks and messages. */
public final class RecoveryChecks {
    private RecoveryChecks() {}

    public static void validateStrictManifest(CheckpointManifest manifest, RuntimeCapsule capsule)
            throws IOException {
        if (manifest == null || !"STRICT".equalsIgnoreCase(manifest.fidelity)) {
            throw new IOException("STRICT recovery requires a STRICT checkpoint manifest");
        }
        if (capsule == null
                || capsule.expectedFingerprints == null
                || capsule.chunks == null
                || capsule.unsupportedRuntimeState == null
                || manifest.unsupportedRuntimeState == null) {
            throw new IOException("STRICT checkpoint manifest or Runtime Capsule state is missing");
        }
        String expectedDomainHash =
                capsule.expectedFingerprints.get(EquivalenceCategory.CHUNK_DOMAIN);
        if (expectedDomainHash == null || !expectedDomainHash.equals(manifest.chunkDomainHash)) {
            throw new IOException(
                    "STRICT checkpoint manifest chunk-domain fingerprint does not match");
        }
        int full = capsule.chunks.stream().mapToInt(ChunkStateSnapshot::fullCount).sum();
        int block = capsule.chunks.stream().mapToInt(ChunkStateSnapshot::blockTickingCount).sum();
        int forced = capsule.chunks.stream().mapToInt(ChunkStateSnapshot::forcedCount).sum();
        if (manifest.fullChunkCount != full
                || manifest.blockTickingChunkCount != block
                || manifest.entityTickingChunkCount != capsule.entityTickingChunkCount()
                || manifest.forcedChunkCount != forced
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
                        capsule.expectedFingerprints.get(EquivalenceCategory.PLAYERS))
                || !java.util.Objects.equals(
                        manifest.carpetCaptureStatus, capsule.carpetCaptureStatus)
                || !new HashSet<>(manifest.unsupportedRuntimeState)
                        .equals(new HashSet<>(capsule.unsupportedRuntimeState))) {
            throw new IOException("STRICT checkpoint manifest runtime counters do not match");
        }
    }

    public static Set<String> allowedUnsupportedCategories(AntiCrashConfig.Strict strict) {
        Set<String> allowed = new java.util.LinkedHashSet<>();
        if (strict.allowUnsupportedRuntimeState) {
            allowed.add(EquivalenceCategory.WORLD_RNG);
            if (!strict.requireEntityOrder) allowed.add(EquivalenceCategory.ENTITIES);
            if (!strict.requireBlockEntityTickerOrder) {
                allowed.add(EquivalenceCategory.BLOCK_ENTITY_TICKERS);
            }
            if (!strict.requireTicketLedger) {
                allowed.add(EquivalenceCategory.TICKET_LEDGER);
            }
        }
        if (strict.allowUnsupportedCarpetState) {
            allowed.add(EquivalenceCategory.CARPET);
            allowed.add(EquivalenceCategory.FAKEPLAYERS);
        }
        return allowed;
    }
}
