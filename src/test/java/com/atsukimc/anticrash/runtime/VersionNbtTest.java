//? if (>=1.21.5 && <=26.3) {
/*package com.atsukimc.anticrash.runtime;

import static org.junit.jupiter.api.Assertions.*;

//? if (>=1.21.5 && <=1.21.11) {
/^import net.minecraft.world.effect.*;
^///?}

//? if (>=1.21.5 && <=1.21.11) {
/^import net.minecraft.world.item.ItemStack;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.core.HolderLookup;
^///?}

import net.minecraft.nbt.*;
//? if (>=1.21.5 && <=1.21.11) {
/^import net.minecraft.core.HolderLookup;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.effect.*;
^///?}

//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.effect.MobEffectInstance;
^///?}

//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.effect.MobEffects;
^///?}

//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.item.ItemStack;
^///?}


import org.junit.jupiter.api.Test;

class VersionNbtTest {
    @Test
    void sparseFalseFlagsMayBeAbsentButCannotHaveAnInvalidType() {

//? if (>=1.21.5 && <=1.21.11) {
/^var nbt = new CompoundTag();
^///?}
//? if (>=26.1 && <=26.3) {
/^var nbt = new CompoundTag();
^///?}

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

//? if (>=1.21.5 && <=1.21.11) {
/^CompoundTag nbt = new CompoundTag();
^///?}
//? if (>=26.1 && <=26.3) {
/^CompoundTag nbt = new CompoundTag();
^///?}

        nbt.putString("Entities", "bad");
        assertFalse(VersionNbt.contains(nbt, "Entities", 9));
        assertThrows(IllegalArgumentException.class, () -> VersionNbt.list(nbt, "Entities", 10));
        assertTrue(VersionNbt.list(nbt, "missing", 10).isEmpty());
    }

    @Test
    void heterogeneousListsCannotDiscardInvalidPassengers() {

//? if (>=1.21.5 && <=1.21.11) {
/^ListTag list = new ListTag();
^///?}
//? if (>=26.1 && <=26.3) {
/^ListTag list = new ListTag();
^///?}


//? if (>=1.21.5 && <=1.21.11) {
/^list.add(new CompoundTag());
^///?}
//? if (>=26.1 && <=26.3) {
/^list.add(new CompoundTag());
^///?}


//? if (>=1.21.5 && <=1.21.11) {
/^list.add(StringTag.valueOf("bad"));
^///?}
//? if (>=26.1 && <=26.3) {
/^list.add(StringTag.valueOf("bad"));
^///?}


//? if (>=1.21.5 && <=1.21.11) {
/^CompoundTag nbt = new CompoundTag();
^///?}
//? if (>=26.1 && <=26.3) {
/^CompoundTag nbt = new CompoundTag();
^///?}

        nbt.put("Passengers", list);
        assertThrows(IllegalArgumentException.class, () -> VersionNbt.list(nbt, "Passengers", 10));
    }

    @Test
    void numericContainsStillRecognizesAllNumericTypes() {

//? if (>=1.21.5 && <=1.21.11) {
/^CompoundTag nbt = new CompoundTag();
^///?}
//? if (>=26.1 && <=26.3) {
/^CompoundTag nbt = new CompoundTag();
^///?}

        nbt.putDouble("n", 2);
        assertTrue(VersionNbt.contains(nbt, "n", 99));
        assertFalse(VersionNbt.contains(nbt, "n", 8));
    }

    @Test
    void statusEffectCodecPreservesTimingAndFlags() {

//? if (>=1.21.5 && <=1.21.11) {
/^var effect = new MobEffectInstance(MobEffects.SPEED, 37, 2, true, false, false);
^///?}
//? if (>=26.1 && <=26.3) {
/^var effect = new MobEffectInstance(MobEffects.SPEED, 37, 2, true, false, false);
^///?}


//? if (>=1.21.5 && <=1.21.11) {
/^var restored = VersionNbt.effect((CompoundTag) VersionNbt.effect(effect));
^///?}
//? if (>=26.1 && <=26.3) {
/^var restored = VersionNbt.effect((CompoundTag) VersionNbt.effect(effect));
^///?}


//? if (>=1.21.5 && <=1.21.11) {
/^assertEquals(effect.getEffect(), restored.getEffect());
^///?}
//? if (>=26.1 && <=26.3) {
/^assertEquals(effect.getEffect(), restored.getEffect());
^///?}

        assertEquals(37, restored.getDuration());
        assertEquals(2, restored.getAmplifier());
        assertTrue(restored.isAmbient());

//? if (>=1.21.5 && <=1.21.11) {
/^assertFalse(restored.isVisible());
^///?}
//? if (>=26.1 && <=26.3) {
/^assertFalse(restored.isVisible());
^///?}


//? if (>=1.21.5 && <=1.21.11) {
/^assertFalse(restored.showIcon());
^///?}
//? if (>=26.1 && <=26.3) {
/^assertFalse(restored.showIcon());
^///?}


//? if (>=1.21.5 && <=1.21.11) {
/^assertThrows(RuntimeException.class, () -> VersionNbt.effect(new CompoundTag()));
^///?}
//? if (>=26.1 && <=26.3) {
/^assertThrows(RuntimeException.class, () -> VersionNbt.effect(new CompoundTag()));
^///?}

    }

    @Test
    void emptyInventoryItemRemainsAnEmptyCompound() {

//? if (>=1.21.5 && <=1.21.11) {
/^assertTrue(
                ((CompoundTag)
                                VersionNbt.item(
                                        ItemStack.EMPTY,
                                        HolderLookup.Provider.create(
                                                java.util.stream.Stream.empty())))
                        .isEmpty());
^///?}
//? if (>=26.1 && <=26.3) {
/^assertTrue(
                ((CompoundTag)
                                VersionNbt.item(
                                        ItemStack.EMPTY,
                                        HolderLookup.Provider.create(
                                                java.util.stream.Stream.empty())))
                        .isEmpty());
^///?}

    }
}
*///?}
