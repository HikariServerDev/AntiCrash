package com.atsukimc.anticrash.runtime;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;

import org.junit.jupiter.api.Test;

class VersionNbtTest {
    @Test
    void sparseFalseFlagsMayBeAbsentButCannotHaveAnInvalidType() {
        var nbt = new CompoundTag();
        assertFalse(VersionNbt.sparseFlag(nbt, "Ticked"));
        nbt.putBoolean("Ticked", true);
        assertTrue(VersionNbt.sparseFlag(nbt, "Ticked"));
        assertFalse(VersionNbt.sparseFlag(nbt, "Water"));
        nbt.putBoolean("Water", false);
        assertFalse(VersionNbt.sparseFlag(nbt, "Water"));
        nbt.putString("Water", "invalid");
        assertThrows(IllegalArgumentException.class, () -> VersionNbt.sparseFlag(nbt, "Water"));
    }

    @Test
    void wrongTypeIsNotAnEmptyRecoveryList() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("Entities", "bad");
        assertFalse(VersionNbt.contains(nbt, "Entities", 9));
        assertThrows(IllegalArgumentException.class, () -> VersionNbt.list(nbt, "Entities", 10));
        assertTrue(VersionNbt.list(nbt, "missing", 10).isEmpty());
    }

    @Test
    void heterogeneousListsCannotDiscardInvalidPassengers() {
        ListTag list = new ListTag();
        list.add(new CompoundTag());
        list.add(StringTag.valueOf("bad"));
        CompoundTag nbt = new CompoundTag();
        nbt.put("Passengers", list);
        assertThrows(IllegalArgumentException.class, () -> VersionNbt.list(nbt, "Passengers", 10));
    }

    @Test
    void numericContainsStillRecognizesAllNumericTypes() {
        CompoundTag nbt = new CompoundTag();
        nbt.putDouble("n", 2);
        assertTrue(VersionNbt.contains(nbt, "n", 99));
        assertFalse(VersionNbt.contains(nbt, "n", 8));
    }

    @Test
    void statusEffectCodecPreservesTimingAndFlags() {
        var effect = new MobEffectInstance(MobEffects.SPEED, 37, 2, true, false, false);
        var restored = VersionNbt.effect((CompoundTag) VersionNbt.effect(effect));
        assertEquals(effect.getEffect(), restored.getEffect());
        assertEquals(37, restored.getDuration());
        assertEquals(2, restored.getAmplifier());
        assertTrue(restored.isAmbient());
        assertFalse(restored.isVisible());
        assertFalse(restored.showIcon());
        assertThrows(RuntimeException.class, () -> VersionNbt.effect(new CompoundTag()));
    }

    @Test
    void emptyInventoryItemRemainsAnEmptyCompound() {
        assertTrue(
                ((CompoundTag)
                                VersionNbt.item(
                                        ItemStack.EMPTY,
                                        HolderLookup.Provider.create(
                                                java.util.stream.Stream.empty())))
                        .isEmpty());
    }
}
