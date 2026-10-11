package com.atsukimc.anticrash.runtime;

import com.atsukimc.anticrash.integration.carpet.FakePlayerSnapshot;
import com.atsukimc.anticrash.integration.carpet.TickSpeedSnapshot;
import com.atsukimc.anticrash.util.Hashing;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Canonical, streaming-friendly fingerprints for Runtime Capsule categories. */
public final class RuntimeFingerprint {
    private RuntimeFingerprint() {
    }

    public static String hashLines(Iterable<?> lines) {
        // Hashing.sha256Lines already streams one canonical item at a time. Do not first copy a
        // large chunk/entity/ticker list into a second String list during checkpoint capture.
        return Hashing.sha256Lines(lines);
    }

    public static List<String> chunkDomainLines(List<ChunkStateSnapshot> chunks) {
        List<String> lines = new ArrayList<>();
        if (chunks == null) return lines;
        for (ChunkStateSnapshot snapshot : chunks) {
            if (snapshot == null) continue;
            if (snapshot.domain != null && !snapshot.domain.isEmpty()) {
                // One row per chunk makes --verbose actionable: a mismatch names the exact
                // coordinate and readiness flags instead of hiding the whole dimension in one
                // large list-valued string.
                for (ChunkStateSnapshot.ChunkReadiness readiness : snapshot.domain) {
                    if (readiness != null) {
                        // Do not make holder level/source a false mismatch. Those values describe
                        // the reconstructed ticket graph, not the executable tick domain.
                        lines.add(snapshot.dimension + "|" + readiness.executionCanonical());
                    }
                }
            } else {
                // Legacy capsules have no semantic domain rows. Keep their set representation
                // deterministic for diagnostics, but STRICT will classify the missing rows
                // separately and never treat this fallback as exact.
                lines.add(snapshot.dimension + "|loaded=" + join(snapshot.loadedChunks)
                        + "|full=" + join(snapshot.fullChunks)
                        + "|block=" + join(snapshot.blockTickingChunks)
                        + "|entity=" + join(snapshot.entityTickingChunks)
                        + "|forced=" + join(snapshot.forcedChunks));
            }
        }
        // The domain is a set of executable chunk states, not an iteration-order contract. The
        // storage holder map may enumerate dimensions/positions differently after a restart, so
        // make the fingerprint independent of that incidental map order while retaining every
        // readiness flag in each line.
        lines.sort(String::compareTo);
        return lines;
    }

    public static List<String> blockEventLines(BlockEventSnapshot snapshot) {
        List<String> lines = new ArrayList<>();
        if (snapshot == null || snapshot.entries == null) return lines;
        for (BlockEventSnapshot.Entry entry : snapshot.entries) {
            if (entry == null) { lines.add("<null>"); continue; }
            lines.add(entry.queue + "|" + entry.dimension + "|" + entry.x + ',' + entry.y + ',' + entry.z
                    + "|" + entry.block + "|" + entry.type + "|" + entry.data);
        }
        return lines;
    }

    public static List<String> scheduledTickLines(List<ScheduledTickSnapshot> ticks) {
        List<String> lines = new ArrayList<>();
        if (ticks == null) return lines;
        for (ScheduledTickSnapshot tick : ticks) {
            if (tick == null) { lines.add("<null>"); continue; }
            lines.add(tick.dimension + "|" + tick.kind + "|" + tick.type + "|"
                    + tick.x + ',' + tick.y + ',' + tick.z + "|" + tick.triggerTick
                    + "|" + tick.priority + "|" + tick.subTickOrder);
        }
        return lines;
    }

    public static List<String> movingPistonLines(List<MovingPistonTickSnapshot> pistons) {
        List<String> lines = new ArrayList<>();
        if (pistons == null) return lines;
        for (MovingPistonTickSnapshot piston : pistons) {
            if (piston == null) { lines.add("<null>"); continue; }
            lines.add(piston.dimension + "|" + piston.x + ',' + piston.y + ',' + piston.z);
        }
        return lines;
    }

    public static List<String> tickerLines(List<BlockEntityTickerSnapshot> tickers) {
        List<String> lines = new ArrayList<>();
        if (tickers == null) return lines;
        for (BlockEntityTickerSnapshot ticker : tickers) {
            if (ticker == null) { lines.add("<null>"); continue; }
            lines.add(ticker.canonical());
        }
        return lines;
    }

    public static List<String> entityLines(List<EntityRuntimeSnapshot> entities) {
        return entityLines(entities, Set.of(), true);
    }

    /**
     * Builds entity-order lines while optionally excluding real players. Real Player readiness is
     * a separate policy because a restarted server may not have accepted those connections yet;
     * FakePlayers remain included by UUID so their ActionPack state is still part of this order.
     */
    public static List<String> entityLines(List<EntityRuntimeSnapshot> entities,
                                           Set<String> fakePlayerUuids,
                                           boolean includeRealPlayers) {
        List<String> lines = new ArrayList<>();
        if (entities == null) return lines;
        Set<String> fakeIds = new HashSet<>();
        if (fakePlayerUuids != null) {
            for (String uuid : fakePlayerUuids) {
                if (uuid != null) fakeIds.add(uuid.toLowerCase(Locale.ROOT));
            }
        }
        int comparisonOrder = 0;
        for (EntityRuntimeSnapshot entity : entities) {
            if (entity == null) { lines.add("<null>"); continue; }
            if (!includeRealPlayers && isRealPlayer(entity, fakeIds)) continue;
            lines.add(includeRealPlayers ? entity.canonical() : entity.canonicalAt(comparisonOrder));
            comparisonOrder++;
        }
        return lines;
    }

    private static boolean isRealPlayer(EntityRuntimeSnapshot entity, Set<String> fakePlayerUuids) {
        return "minecraft:player".equalsIgnoreCase(entity.type)
                && (entity.uuid == null
                || !fakePlayerUuids.contains(entity.uuid.toLowerCase(Locale.ROOT)));
    }

    public static List<String> burnoutLines(List<RedstoneTorchBurnoutSnapshot> snapshots) {
        List<String> lines = new ArrayList<>();
        if (snapshots == null) return lines;
        for (RedstoneTorchBurnoutSnapshot snapshot : snapshots) {
            if (snapshot == null) { lines.add("<null>"); continue; }
            lines.addAll(snapshot.canonicalLines());
        }
        return lines;
    }

    public static List<String> ticketLines(List<TicketLedgerSnapshot.Entry> entries) {
        List<String> lines = new ArrayList<>();
        if (entries == null) return lines;
        for (TicketLedgerSnapshot.Entry entry : entries) {
            if (entry != null) lines.add(entry.canonical());
        }
        return lines;
    }

    public static List<String> worldLines(List<RuntimeCapsule.WorldRuntime> worlds) {
        List<String> lines = new ArrayList<>();
        if (worlds == null) return lines;
        for (RuntimeCapsule.WorldRuntime world : worlds) {
            if (world == null) { lines.add("<null>"); continue; }
            lines.add(world.dimension + "|time=" + world.gameTime + "|day=" + world.timeOfDay
                    + "|tickOrder=" + world.nextTickOrder + "|lcg=" + world.lcgBlockSeed);
        }
        lines.sort(String::compareTo);
        return lines;
    }

    /**
     * Records the capability boundary for World.random.  Minecraft 1.18.2 exposes this as a
     * java.util.Random without a safe semantic state accessor; the line is therefore a
     * deterministic evidence marker, not a claim that the random seed was restored.
     */
    public static List<String> worldRngLines() {
        return List.of("implementation=java.util.Random",
                "state=UNSUPPORTED",
                "reason=internal-state-not-exposed-by-minecraft-1.18.2");
    }

    public static List<String> carpetLines(TickSpeedSnapshot snapshot) {
        if (snapshot == null) return List.of();
        return List.of("format=" + snapshot.formatVersion,
                "tickrate=" + snapshot.tickrate,
                "mspt=" + snapshot.mspt,
                "process_entities=" + snapshot.processEntities,
                "paused=" + snapshot.paused,
                "deepFrozen=" + snapshot.deepFrozen,
                "time_bias=" + snapshot.timeBias,
                "time_warp_start_time=" + snapshot.timeWarpStartTime,
                "time_warp_scheduled_ticks=" + snapshot.timeWarpScheduledTicks,
                "player_active_timeout=" + snapshot.playerActiveTimeout,
                "is_superHot=" + snapshot.superHot,
                "time_warp_active=" + snapshot.timeWarpActive);
    }

    public static List<String> fakePlayerLines(List<FakePlayerSnapshot> players) {
        List<String> lines = new ArrayList<>();
        if (players == null) return lines;
        for (FakePlayerSnapshot player : players) {
            if (player == null) { lines.add("<null>"); continue; }
            lines.add(player.canonical());
        }
        // PlayerManager order is not the FakePlayer ActionPack's execution order. Sort identities
        // so a harmless login/list-order change is not reported as a machine-state mismatch.
        lines.sort(String::compareTo);
        return lines;
    }

    public static List<String> checkpointPlayerLines(List<CheckpointPlayerSnapshot> players) {
        List<String> lines = new ArrayList<>();
        if (players == null) return lines;
        for (CheckpointPlayerSnapshot player : players) {
            lines.add(player == null ? "<null>" : player.canonical());
        }
        lines.sort(String::compareTo);
        return lines;
    }

    /** Returns only connected Real Players, excluding Carpet FakePlayers. */
    public static List<String> liveRealPlayerLines(MinecraftServer server) {
        List<String> lines = new ArrayList<>();
        if (server == null) return lines;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (com.atsukimc.anticrash.integration.carpet.CarpetIntegration.isFakePlayer(player)) {
                continue;
            }
            lines.add(player.getUuidAsString() + "|" + player.getGameProfile().getName() + "|"
                    + player.getServerWorld().getRegistryKey().getValue());
        }
        lines.sort(String::compareTo);
        return lines;
    }

    private static String join(List<Long> values) {
        if (values == null) return "<missing>";
        return values.toString();
    }
}
