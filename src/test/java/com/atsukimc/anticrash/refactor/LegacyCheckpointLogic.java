package com.atsukimc.anticrash.refactor;

import com.atsukimc.anticrash.config.AntiCrashConfig;
import com.atsukimc.anticrash.runtime.*;
import com.atsukimc.anticrash.snapshot.CheckpointManifest;

import java.io.IOException;
import java.util.*;

/** Frozen 1.0.1 reference; test-only, never shipped. Do not simplify this oracle. */
final class LegacyCheckpointLogic {
    static void validateStrictManifest(CheckpointManifest manifest, RuntimeCapsule capsule)
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
        int full =
                capsule.chunks.stream()
                        .mapToInt(com.atsukimc.anticrash.runtime.ChunkStateSnapshot::fullCount)
                        .sum();
        int block =
                capsule.chunks.stream()
                        .mapToInt(
                                com.atsukimc.anticrash.runtime.ChunkStateSnapshot
                                        ::blockTickingCount)
                        .sum();
        int forced =
                capsule.chunks.stream()
                        .mapToInt(com.atsukimc.anticrash.runtime.ChunkStateSnapshot::forcedCount)
                        .sum();
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

    static Set<String> strictAllowedUnsupportedCategories(AntiCrashConfig config) {
        Set<String> allowed = new java.util.LinkedHashSet<>();
        if (config.recovery.strict.allowUnsupportedRuntimeState) {
            allowed.add(EquivalenceCategory.WORLD_RNG);
            if (!config.recovery.strict.requireEntityOrder)
                allowed.add(EquivalenceCategory.ENTITIES);
            if (!config.recovery.strict.requireBlockEntityTickerOrder) {
                allowed.add(EquivalenceCategory.BLOCK_ENTITY_TICKERS);
            }
            if (!config.recovery.strict.requireTicketLedger) {
                allowed.add(EquivalenceCategory.TICKET_LEDGER);
            }
        }
        if (config.recovery.strict.allowUnsupportedCarpetState) {
            allowed.add(EquivalenceCategory.CARPET);
            allowed.add(EquivalenceCategory.FAKEPLAYERS);
        }
        return allowed;
    }

    static String checkpointDiagnostic(
            RuntimeCapsule capsule,
            CheckpointManifest manifest,
            long runtimeCapsuleBytes,
            long heapUsedBytes,
            long heapMaxBytes) {
        StringBuilder result = new StringBuilder(1024);
        result.append("fidelity=")
                .append(manifest.fidelity)
                .append("; dimensions=")
                .append(dimensionDiagnostic(capsule))
                .append("; chunks loaded/full/block/entity/forced=")
                .append(manifest.loadedChunkCount)
                .append('/')
                .append(manifest.fullChunkCount)
                .append('/')
                .append(manifest.blockTickingChunkCount)
                .append('/')
                .append(manifest.entityTickingChunkCount)
                .append('/')
                .append(manifest.forcedChunkCount)
                .append("; CHUNK_DOMAIN hash=")
                .append(
                        fingerprint(
                                capsule,
                                com.atsukimc.anticrash.runtime.EquivalenceCategory.CHUNK_DOMAIN))
                .append("; BLOCK_ENTITY_TICKERS count=")
                .append(manifest.blockEntityTickerCount)
                .append(" hash=")
                .append(
                        fingerprint(
                                capsule,
                                com.atsukimc.anticrash.runtime.EquivalenceCategory
                                        .BLOCK_ENTITY_TICKERS))
                .append("; ENTITIES count=")
                .append(manifest.entityOrderCount)
                .append(" hash=")
                .append(
                        fingerprint(
                                capsule,
                                com.atsukimc.anticrash.runtime.EquivalenceCategory.ENTITIES))
                .append("; BLOCK_EVENTS count=")
                .append(manifest.blockEventCount)
                .append(" hash=")
                .append(
                        fingerprint(
                                capsule,
                                com.atsukimc.anticrash.runtime.EquivalenceCategory.BLOCK_EVENTS))
                .append("; SCHEDULED_TICKS count=")
                .append(size(capsule.scheduledTicks))
                .append(" hash=")
                .append(
                        fingerprint(
                                capsule,
                                com.atsukimc.anticrash.runtime.EquivalenceCategory.SCHEDULED_TICKS))
                .append("; MOVING_PISTONS count=")
                .append(size(capsule.movingPistonTickOrder))
                .append(" hash=")
                .append(
                        fingerprint(
                                capsule,
                                com.atsukimc.anticrash.runtime.EquivalenceCategory.MOVING_PISTONS))
                .append("; TORCH_BURNOUT count=")
                .append(manifest.torchBurnoutCount)
                .append(" hash=")
                .append(
                        fingerprint(
                                capsule,
                                com.atsukimc.anticrash.runtime.EquivalenceCategory
                                        .REDSTONE_RUNTIME))
                .append("; WORLD_RUNTIME hash=")
                .append(
                        fingerprint(
                                capsule,
                                com.atsukimc.anticrash.runtime.EquivalenceCategory.WORLD_RUNTIME))
                .append(" support=")
                .append(worldRuntimeSupport(capsule))
                .append("; WORLD_RNG hash=")
                .append(
                        fingerprint(
                                capsule,
                                com.atsukimc.anticrash.runtime.EquivalenceCategory.WORLD_RNG))
                .append("; TICKET_LEDGER count=")
                .append(manifest.ticketCount)
                .append(" hash=")
                .append(
                        fingerprint(
                                capsule,
                                com.atsukimc.anticrash.runtime.EquivalenceCategory.TICKET_LEDGER))
                .append("; CARPET status=")
                .append(manifest.carpetCaptureStatus)
                .append(" hash=")
                .append(
                        fingerprint(
                                capsule, com.atsukimc.anticrash.runtime.EquivalenceCategory.CARPET))
                .append("; FAKEPLAYERS count=")
                .append(manifest.fakePlayerCount)
                .append(" hash=")
                .append(
                        fingerprint(
                                capsule,
                                com.atsukimc.anticrash.runtime.EquivalenceCategory.FAKEPLAYERS))
                .append("; runtimeCapsuleBytes=")
                .append(runtimeCapsuleBytes)
                .append("; heapUsed/max=")
                .append(heapUsedBytes)
                .append('/')
                .append(heapMaxBytes)
                .append("; unsupported=")
                .append(capsule.unsupportedRuntimeState);
        return result.toString();
    }

    static String dimensionDiagnostic(RuntimeCapsule capsule) {
        if (capsule == null || capsule.worlds == null) return "unavailable";
        List<String> dimensions = new ArrayList<>();
        for (RuntimeCapsule.WorldRuntime world : capsule.worlds) {
            if (world == null) continue;
            dimensions.add(
                    world.dimension
                            + "["
                            + world.loadedChunkCount
                            + "/"
                            + world.fullChunkCount
                            + "/"
                            + world.blockTickingChunkCount
                            + "/"
                            + world.entityTickingChunkCount
                            + "/"
                            + world.forcedChunkCount
                            + "]");
        }
        dimensions.sort(String::compareTo);
        return dimensions.isEmpty() ? "[]" : '[' + String.join(";", dimensions) + ']';
    }

    static int size(List<?> values) {
        return values == null ? -1 : values.size();
    }

    static String fingerprint(RuntimeCapsule capsule, String category) {
        if (capsule == null || capsule.expectedFingerprints == null) return "missing";
        String value = capsule.expectedFingerprints.get(category);
        return value == null ? "missing" : value;
    }

    static String worldRuntimeSupport(RuntimeCapsule capsule) {
        if (capsule == null || capsule.expectedFingerprints == null) return "missing";
        boolean rngUnsupported =
                capsule.unsupportedRuntimeState != null
                        && capsule.unsupportedRuntimeState.contains(
                                com.atsukimc.anticrash.runtime.EquivalenceCategory.WORLD_RNG);
        return "WORLD_RUNTIME="
                + (capsule.expectedFingerprints.containsKey(
                                com.atsukimc.anticrash.runtime.EquivalenceCategory.WORLD_RUNTIME)
                        ? "CAPTURED"
                        : "MISSING")
                + ",WORLD_RNG="
                + (rngUnsupported ? "UNSUPPORTED" : "CAPTURED");
    }

    static boolean usePortableGeneration(AntiCrashConfig config) {
        String backend =
                config.snapshot.backend == null
                        ? "AUTO"
                        : config.snapshot.backend.trim().toUpperCase(java.util.Locale.ROOT);
        return backend.equals("AUTO")
                || backend.equals("PORTABLE_GENERATION")
                || backend.equals("GENERATION");
    }

    static boolean logicalDeltaRequested(AntiCrashConfig config) {
        String mode =
                config.checkpoint.engineMode == null
                        ? "LOGICAL_DELTA"
                        : config.checkpoint.engineMode.trim().toUpperCase(java.util.Locale.ROOT);
        String backend =
                config.snapshot.backend == null
                        ? "AUTO"
                        : config.snapshot.backend.trim().toUpperCase(java.util.Locale.ROOT);
        return "LOGICAL_DELTA".equals(mode)
                && ("AUTO".equals(backend)
                        || "PORTABLE_GENERATION".equals(backend)
                        || "GENERATION".equals(backend));
    }

    static boolean hasUnsafeLogicalOptions(AntiCrashConfig config) {
        // Kept as a compatibility setting, not a license to carry stale POI/data across epochs.
        return config.checkpoint.allowUnsupportedPersistentState
                || config.playerSnapshot.vanillaFiles.restorePlayerdata
                || config.playerSnapshot.vanillaFiles.restoreStats
                || config.playerSnapshot.vanillaFiles.restoreAdvancements;
    }
}
