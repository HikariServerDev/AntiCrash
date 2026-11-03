//? if (>=1.21.5 && <=26.3) {
package com.atsukimc.anticrash.runtime;

import net.minecraft.nbt.*;

/**
 * 1.21.5 removed type-aware contains and permits heterogeneous lists. Never erase malformed
 * recovery data by accepting a wrong type as an empty/default value.
 */
public final class VersionNbt {
    private VersionNbt() {}

    /** Sparse true-only flags default to false only when absent, never when malformed. */

    //? if (>=1.21.5 && <=1.21.11) {
    /*public static boolean sparseFlag(CompoundTag nbt, String key)
*///?} else if (>=26.1 && <=26.3) {
    public static boolean sparseFlag(CompoundTag nbt, String key)
                //?}
            {
        return !nbt.contains(key)
                ? false
                : nbt.getBoolean(key)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Invalid boolean recovery flag: " + key));
    }

    //? if (>=1.21.5 && <=1.21.11) {
    /*public static Tag item(
            net.minecraft.world.item.ItemStack stack,
            net.minecraft.core.HolderLookup.Provider registries)
*///?} else if (>=26.1 && <=26.3) {
    public static Tag item(
            net.minecraft.world.item.ItemStack stack,
            net.minecraft.core.HolderLookup.Provider registries)
                //?}
            {

        //? if (>=1.21.5 && <=1.21.11) {
        /*return net.minecraft.world.item.ItemStack.OPTIONAL_CODEC
                .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), stack)
                .getOrThrow();
*///?} else if (>=26.1 && <=26.3) {
        return net.minecraft.world.item.ItemStack.OPTIONAL_CODEC
                .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), stack)
                .getOrThrow();
        //?}

    }

    //? if (>=1.21.5 && <=1.21.11) {
    /*public static Tag effect(net.minecraft.world.effect.MobEffectInstance effect)
*///?} else if (>=26.1 && <=26.3) {
    public static Tag effect(net.minecraft.world.effect.MobEffectInstance effect)
                //?}
            {
        // MobEffect.ENTRY_CODEC uses the static STATUS_EFFECT registry, as verified in bytecode.

        //? if (>=1.21.5 && <=1.21.11) {
        /*return net.minecraft.world.effect.MobEffectInstance.CODEC
                .encodeStart(NbtOps.INSTANCE, effect)
                .getOrThrow();
*///?} else if (>=26.1 && <=26.3) {
        return net.minecraft.world.effect.MobEffectInstance.CODEC
                .encodeStart(NbtOps.INSTANCE, effect)
                .getOrThrow();
        //?}

    }

    //? if (>=1.21.5 && <=1.21.11) {
    /*public static net.minecraft.world.effect.MobEffectInstance effect(CompoundTag nbt)
*///?} else if (>=26.1 && <=26.3) {
    public static net.minecraft.world.effect.MobEffectInstance effect(CompoundTag nbt)
                //?}
            {

        //? if (>=1.21.5 && <=1.21.11) {
        /*return net.minecraft.world.effect.MobEffectInstance.CODEC
                .parse(NbtOps.INSTANCE, nbt)
                .getOrThrow();
*///?} else if (>=26.1 && <=26.3) {
        return net.minecraft.world.effect.MobEffectInstance.CODEC
                .parse(NbtOps.INSTANCE, nbt)
                .getOrThrow();
        //?}

    }

    //? if (>=1.21.5 && <=1.21.11) {
    /*public static boolean contains(CompoundTag nbt, String key, int type)
*///?} else if (>=26.1 && <=26.3) {
    public static boolean contains(CompoundTag nbt, String key, int type)
                //?}
            {

        //? if (>=1.21.5 && <=1.21.11) {
        /*Tag value = nbt.get(key);
*///?} else if (>=26.1 && <=26.3) {
        Tag value = nbt.get(key);
        //?}

        //? if (>=1.21.5 && <=1.21.11) {
        /*return value != null
                && (type == 99 ? value instanceof NumericTag : value.getId() == type);
*///?} else if (>=26.1 && <=26.3) {
        return value != null && (type == 99 ? value instanceof NumericTag : value.getId() == type);
        //?}

    }

    //? if (>=1.21.5 && <=1.21.11) {
    /*public static ListTag list(CompoundTag nbt, String key, int type)
*///?} else if (>=26.1 && <=26.3) {
    public static ListTag list(CompoundTag nbt, String key, int type)
                //?}
            {

        //? if (>=1.21.5 && <=1.21.11) {
        /*Tag value = nbt.get(key);
*///?} else if (>=26.1 && <=26.3) {
        Tag value = nbt.get(key);
        //?}

        if (value == null)
            //? if (>=1.21.5 && <=1.21.11) {
            /*return new ListTag();
*///?} else if (>=26.1 && <=26.3) {
            return new ListTag();
        //?}

        //? if (>=1.21.5 && <=1.21.11) {
        /*if (!(value instanceof ListTag list))

*///?} else if (>=26.1 && <=26.3) {
        if (!(value instanceof ListTag list))

            //?}
            throw new IllegalArgumentException("Expected NBT list: " + key);

        //? if (>=1.21.5 && <=1.21.11) {
        /*for (Tag entry : list)
*///?} else if (>=26.1 && <=26.3) {
        for (Tag entry : list)
        //?}
        {

            //? if (>=1.21.5 && <=1.21.11) {
            /*if (entry.getId() != type)

*///?} else if (>=26.1 && <=26.3) {
            if (entry.getId() != type)

                //?}
                throw new IllegalArgumentException("Wrong NBT list element type: " + key);
        }
        return list;
    }
}
//?}
