package com.atsukimc.anticrash.runtime;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.entity.effect.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.registry.RegistryWrapper;

import org.junit.jupiter.api.Test;

class VersionNbtTest {
    @Test
    void sparseFalseFlagsMayBeAbsentButCannotHaveAnInvalidType() {
        var nbt = new NbtCompound();
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
        NbtCompound nbt = new NbtCompound();
        nbt.putString("Entities", "bad");
        assertFalse(VersionNbt.contains(nbt, "Entities", 9));
        assertThrows(IllegalArgumentException.class, () -> VersionNbt.list(nbt, "Entities", 10));
        assertTrue(VersionNbt.list(nbt, "missing", 10).isEmpty());
    }

    @Test
    void heterogeneousListsCannotDiscardInvalidPassengers() {
        NbtList list = new NbtList();
        list.add(new NbtCompound());
        list.add(NbtString.of("bad"));
        NbtCompound nbt = new NbtCompound();
        nbt.put("Passengers", list);
        assertThrows(IllegalArgumentException.class, () -> VersionNbt.list(nbt, "Passengers", 10));
    }

    @Test
    void numericContainsStillRecognizesAllNumericTypes() {
        NbtCompound nbt = new NbtCompound();
        nbt.putDouble("n", 2);
        assertTrue(VersionNbt.contains(nbt, "n", 99));
        assertFalse(VersionNbt.contains(nbt, "n", 8));
    }

    @Test
    void statusEffectCodecPreservesTimingAndFlags() {
        var effect = new StatusEffectInstance(StatusEffects.SPEED, 37, 2, true, false, false);
        var restored = VersionNbt.effect((NbtCompound) VersionNbt.effect(effect));
        assertEquals(effect.getEffectType(), restored.getEffectType());
        assertEquals(37, restored.getDuration());
        assertEquals(2, restored.getAmplifier());
        assertTrue(restored.isAmbient());
        assertFalse(restored.shouldShowParticles());
        assertFalse(restored.shouldShowIcon());
        assertThrows(RuntimeException.class, () -> VersionNbt.effect(new NbtCompound()));
    }

    @Test
    void emptyInventoryItemRemainsAnEmptyCompound() {
        assertTrue(
                ((NbtCompound)
                                VersionNbt.item(
                                        ItemStack.EMPTY,
                                        RegistryWrapper.WrapperLookup.of(
                                                java.util.stream.Stream.empty())))
                        .isEmpty());
    }
}
