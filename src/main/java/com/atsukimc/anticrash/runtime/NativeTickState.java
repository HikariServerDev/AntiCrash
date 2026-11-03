//? if (>=1.20.3 && <=26.3) {
/*package com.atsukimc.anticrash.runtime;

import com.atsukimc.anticrash.mixin.NativeTickManagerAccessor;

import net.minecraft.server.MinecraftServer;
//? if (>=1.20.3 && <=1.21.11) {
/^import net.minecraft.server.ServerTickRateManager;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.server.ServerTickRateManager;
^///?}


import java.util.List;

/^* 1.20.3: Vanilla /tick state is independent of Carpet. Never replay wall-clock sprint state. ^/
public final class NativeTickState {
    public float tickRate;
    public int stepTicks;
    public boolean frozen;
    public boolean shouldTick;
    public boolean sprinting;

    public static NativeTickState capture(MinecraftServer server) {

//? if (>=1.20.3 && <=1.21.11) {
/^ServerTickRateManager manager = server.tickRateManager();
^///?}
//? if (>=26.1 && <=26.3) {
/^ServerTickRateManager manager = server.tickRateManager();
^///?}

        NativeTickState state = new NativeTickState();

//? if (>=1.20.3 && <=1.21.11) {
/^state.tickRate = manager.tickrate();
^///?}
//? if (>=26.1 && <=26.3) {
/^state.tickRate = manager.tickrate();
^///?}


//? if (>=1.20.3 && <=1.21.11) {
/^state.stepTicks = manager.frozenTicksToRun();
^///?}
//? if (>=26.1 && <=26.3) {
/^state.stepTicks = manager.frozenTicksToRun();
^///?}

        state.frozen = manager.isFrozen();

//? if (>=1.20.3 && <=1.21.11) {
/^state.shouldTick = manager.runsNormally();
^///?}
//? if (>=26.1 && <=26.3) {
/^state.shouldTick = manager.runsNormally();
^///?}

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

//? if (>=1.20.3 && <=1.21.11) {
/^ServerTickRateManager manager = server.tickRateManager();
^///?}
//? if (>=26.1 && <=26.3) {
/^ServerTickRateManager manager = server.tickRateManager();
^///?}

        manager.stopSprinting();
        manager.setTickRate(tickRate);
        manager.setFrozen(frozen);

//? if (>=1.20.3 && <=1.21.11) {
/^manager.setFrozenTicksToRun(stepTicks);
^///?}
//? if (>=26.1 && <=26.3) {
/^manager.setFrozenTicksToRun(stepTicks);
^///?}

        ((NativeTickManagerAccessor) manager).anticrash$setShouldTick(shouldTick);
        // sprinting=true is explicitly UNSUPPORTED, not silently marked equivalent.
    }

    public List<String> lines() {
        validate();
        return List.of(
                "rate="
                        + tickRate
                        + "|step="
                        + stepTicks
                        + "|frozen="
                        + frozen
                        + "|shouldTick="
                        + shouldTick
                        + "|sprinting="
                        + sprinting);
    }
}
*///?}
