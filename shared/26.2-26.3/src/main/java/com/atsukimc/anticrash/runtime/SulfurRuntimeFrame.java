package com.atsukimc.anticrash.runtime;

import net.minecraft.nbt.CompoundTag;

/** Native countdown is saved but not dirtied; eruption phase is absent from native NBT. */
public record SulfurRuntimeFrame(int countdown, long eruptionTick) {
    public CompoundTag write() {
        var nbt = new CompoundTag();
        nbt.putInt("Countdown", countdown);
        nbt.putLong("EruptionTick", eruptionTick);
        return nbt;
    }

    public static SulfurRuntimeFrame read(CompoundTag nbt) {
        return new SulfurRuntimeFrame(
                nbt.getInt("Countdown").orElseThrow(), nbt.getLong("EruptionTick").orElseThrow());
    }

    public String canonical() {
        return countdown + "|" + eruptionTick;
    }
}
