package com.atsukimc.anticrash.legacy;

import com.atsukimc.anticrash.mixin.LegacyScheduledTickAccessor;
import com.atsukimc.anticrash.mixin.LegacyServerTickSchedulerAccessor;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerTickScheduler;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.List;

/**
 * Whole-server observational capture. The production checkpoint engine is not wired to this yet.
 */
public final class LegacySchedulerCapture {
    private LegacySchedulerCapture() {}

    public static LegacyTickLedger.Snapshot capture(MinecraftServer server) {
        if (!server.isOnThread())
            throw new IllegalStateException("Legacy tick capture requires the server thread");
        var entries = new ArrayList<LegacyTickLedger.Entry>();
        for (ServerWorld world : server.getWorlds()) {
            capture(
                    world.getRegistryKey().getValue().toString(),
                    LegacyTickLedger.Kind.BLOCK,
                    world.getBlockTickScheduler(),
                    entries);
            capture(
                    world.getRegistryKey().getValue().toString(),
                    LegacyTickLedger.Kind.FLUID,
                    world.getFluidTickScheduler(),
                    entries);
        }
        // No ScheduledTick is constructed, and no native query consumes a global id here.
        return LegacyTickLedger.atBoundary(
                LegacyScheduledTickAccessor.anticrash$nextSequence(), entries, 0, 0);
    }

    @SuppressWarnings("unchecked")
    private static <T> void capture(
            String dimension,
            LegacyTickLedger.Kind kind,
            ServerTickScheduler<T> scheduler,
            List<LegacyTickLedger.Entry> output) {
        var access = (LegacyServerTickSchedulerAccessor<T>) scheduler;
        if (!access.anticrash$current().isEmpty() || !access.anticrash$consumed().isEmpty())
            throw new IllegalStateException(
                    "Legacy scheduler is inside a tick; checkpoint must defer");
        var ordered = access.anticrash$ordered();
        var scheduled = access.anticrash$scheduled();
        if (ordered.size() != scheduled.size() || !scheduled.containsAll(ordered))
            throw new IllegalStateException("Native legacy scheduler collections disagree");
        if ((long) output.size() + ordered.size() > LegacyTickLedger.MAX_ENTRIES)
            throw new IllegalStateException("Legacy tick ledger exceeds supported memory limit");
        for (var tick : ordered) {
            var id = access.anticrash$registryNames().apply(tick.getObject());
            if (id == null) throw new IllegalStateException("Unregistered legacy scheduled object");
            output.add(
                    new LegacyTickLedger.Entry(
                            dimension,
                            kind,
                            id.toString(),
                            tick.pos.getX(),
                            tick.pos.getY(),
                            tick.pos.getZ(),
                            tick.time,
                            tick.priority.getIndex(),
                            ((LegacyScheduledTickAccessor) tick).anticrash$sequence()));
        }
    }
}
