package com.atsukimc.anticrash.runtime;

import com.atsukimc.anticrash.mixin.NativeTickManagerAccessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickManager;
import java.util.List;

/** 1.20.3: Vanilla /tick state is independent of Carpet. Never replay wall-clock sprint state. */
public final class NativeTickState {
    public float tickRate;
    public int stepTicks;
    public boolean frozen;
    public boolean shouldTick;
    public boolean sprinting;

    public static NativeTickState capture(MinecraftServer server) {
        ServerTickManager manager = server.getTickManager();
        NativeTickState state = new NativeTickState();
        state.tickRate = manager.getTickRate();
        state.stepTicks = manager.getStepTicks();
        state.frozen = manager.isFrozen();
        state.shouldTick = manager.shouldTick();
        state.sprinting = manager.isSprinting();
        return state;
    }

    public void validate() {
        if (!Float.isFinite(tickRate) || tickRate < 1 || tickRate > 10000 || stepTicks < 0) {
            throw new IllegalArgumentException("Invalid Vanilla tick-manager snapshot");
        }
    }

    public void restore(MinecraftServer server) {
        validate();
        ServerTickManager manager = server.getTickManager();
        manager.stopSprinting();
        manager.setTickRate(tickRate);
        manager.setFrozen(frozen);
        manager.setStepTicks(stepTicks);
        ((NativeTickManagerAccessor) manager).anticrash$setShouldTick(shouldTick);
        // sprinting=true is explicitly UNSUPPORTED, not silently marked equivalent.
    }

    public List<String> lines() {
        validate();
        return List.of("rate=" + tickRate + "|step=" + stepTicks + "|frozen=" + frozen
                + "|shouldTick=" + shouldTick + "|sprinting=" + sprinting);
    }
}
