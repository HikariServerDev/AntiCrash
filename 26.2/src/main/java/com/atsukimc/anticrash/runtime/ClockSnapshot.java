package com.atsukimc.anticrash.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.clock.ClockState;

/** 26.1 semantic clock cut; restoration is owned by the sealed Vanilla SavedData files.
 * Public setTotalTicks/setRate cannot restore fractional phase, so never approximate it. */
public record ClockSnapshot(String id, long totalTicks, float partialTick, float rate, boolean paused) {
    public ClockSnapshot {
        if (id == null || id.isBlank() || id.length() > 512 || !Float.isFinite(partialTick)
                || partialTick < 0 || partialTick >= 1 || !Float.isFinite(rate) || rate <= 0)
            throw new IllegalArgumentException("Invalid world clock state");
        net.minecraft.resources.Identifier.parse(id);
    }
    public static ClockSnapshot from(String id, ClockState state) {
        return new ClockSnapshot(id, state.totalTicks(), state.partialTick(), state.rate(), state.paused());
    }
    public static List<ClockSnapshot> capture(MinecraftServer server) {
        List<ClockSnapshot> result = new ArrayList<>();
        server.clockManager().packState().clocks().forEach((clock, state) -> result.add(from(
                clock.unwrapKey().orElseThrow(() -> new IllegalStateException("Unregistered clock"))
                        .identifier().toString(), state)));
        result.sort(Comparator.comparing(ClockSnapshot::id));
        return result;
    }
    public String canonical() {
        return "clock=" + id + "|ticks=" + totalTicks + "|partial=" + Float.toHexString(partialTick)
                + "|rate=" + Float.toHexString(rate) + "|paused=" + paused;
    }
}
