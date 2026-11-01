package com.atsukimc.anticrash.checkpoint;

import com.atsukimc.anticrash.runtime.EquivalenceCategory;
import com.atsukimc.anticrash.runtime.RuntimeCapsule;
import com.atsukimc.anticrash.snapshot.CheckpointManifest;

import java.util.ArrayList;
import java.util.List;

/** Bounded diagnostic text, separate from snapshot IO and checkpoint lifecycle. */
public final class CheckpointDiagnostics {
    private CheckpointDiagnostics() {}

    public static String describe(
            RuntimeCapsule capsule,
            CheckpointManifest manifest,
            long runtimeCapsuleBytes,
            long heapUsedBytes,
            long heapMaxBytes) {
        StringBuilder out = new StringBuilder(1024);
        out.append("fidelity=")
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
                .append(manifest.forcedChunkCount);
        hash(out, capsule, "CHUNK_DOMAIN", EquivalenceCategory.CHUNK_DOMAIN);
        count(
                out,
                capsule,
                "BLOCK_ENTITY_TICKERS",
                manifest.blockEntityTickerCount,
                EquivalenceCategory.BLOCK_ENTITY_TICKERS);
        count(out, capsule, "ENTITIES", manifest.entityOrderCount, EquivalenceCategory.ENTITIES);
        count(
                out,
                capsule,
                "BLOCK_EVENTS",
                manifest.blockEventCount,
                EquivalenceCategory.BLOCK_EVENTS);
        count(
                out,
                capsule,
                "SCHEDULED_TICKS",
                size(capsule.scheduledTicks),
                EquivalenceCategory.SCHEDULED_TICKS);
        count(
                out,
                capsule,
                "MOVING_PISTONS",
                size(capsule.movingPistonTickOrder),
                EquivalenceCategory.MOVING_PISTONS);
        count(
                out,
                capsule,
                "TORCH_BURNOUT",
                manifest.torchBurnoutCount,
                EquivalenceCategory.REDSTONE_RUNTIME);
        hash(out, capsule, "WORLD_RUNTIME", EquivalenceCategory.WORLD_RUNTIME);
        out.append(" support=").append(worldRuntimeSupport(capsule));
        hash(out, capsule, "WORLD_RNG", EquivalenceCategory.WORLD_RNG);
        count(
                out,
                capsule,
                "TICKET_LEDGER",
                manifest.ticketCount,
                EquivalenceCategory.TICKET_LEDGER);
        out.append("; CARPET status=")
                .append(manifest.carpetCaptureStatus)
                .append(" hash=")
                .append(fingerprint(capsule, EquivalenceCategory.CARPET));
        count(
                out,
                capsule,
                "FAKEPLAYERS",
                manifest.fakePlayerCount,
                EquivalenceCategory.FAKEPLAYERS);
        return out.append("; runtimeCapsuleBytes=")
                .append(runtimeCapsuleBytes)
                .append("; heapUsed/max=")
                .append(heapUsedBytes)
                .append('/')
                .append(heapMaxBytes)
                .append("; unsupported=")
                .append(capsule.unsupportedRuntimeState)
                .toString();
    }

    private static void hash(
            StringBuilder out, RuntimeCapsule capsule, String label, String category) {
        out.append("; ").append(label).append(" hash=").append(fingerprint(capsule, category));
    }

    private static void count(
            StringBuilder out, RuntimeCapsule capsule, String label, int count, String category) {
        out.append("; ")
                .append(label)
                .append(" count=")
                .append(count)
                .append(" hash=")
                .append(fingerprint(capsule, category));
    }

    private static String dimensionDiagnostic(RuntimeCapsule capsule) {
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

    private static int size(List<?> values) {
        return values == null ? -1 : values.size();
    }

    private static String fingerprint(RuntimeCapsule capsule, String category) {
        if (capsule == null || capsule.expectedFingerprints == null) return "missing";
        String value = capsule.expectedFingerprints.get(category);
        return value == null ? "missing" : value;
    }

    private static String worldRuntimeSupport(RuntimeCapsule capsule) {
        if (capsule == null || capsule.expectedFingerprints == null) return "missing";
        boolean rngUnsupported =
                capsule.unsupportedRuntimeState != null
                        && capsule.unsupportedRuntimeState.contains(EquivalenceCategory.WORLD_RNG);
        return "WORLD_RUNTIME="
                + (capsule.expectedFingerprints.containsKey(EquivalenceCategory.WORLD_RUNTIME)
                        ? "CAPTURED"
                        : "MISSING")
                + ",WORLD_RNG="
                + (rngUnsupported ? "UNSUPPORTED" : "CAPTURED");
    }
}
