package com.atsukimc.anticrash.runtime;

import net.minecraft.nbt.*;

/** 1.21.5 removed type-aware contains and permits heterogeneous lists. Never erase malformed
 * recovery data by accepting a wrong type as an empty/default value. */
public final class VersionNbt {
    private VersionNbt() { }
    /** Sparse true-only flags default to false only when absent, never when malformed. */
    public static boolean sparseFlag(NbtCompound nbt, String key) {
        return !nbt.contains(key) ? false : nbt.getBoolean(key).orElseThrow(
                () -> new IllegalArgumentException("Invalid boolean recovery flag: " + key));
    }

    public static NbtElement item(net.minecraft.item.ItemStack stack,
                                 net.minecraft.registry.RegistryWrapper.WrapperLookup registries) {
        return net.minecraft.item.ItemStack.OPTIONAL_CODEC.encodeStart(registries.getOps(NbtOps.INSTANCE), stack)
                .getOrThrow();
    }

    public static NbtElement effect(net.minecraft.entity.effect.StatusEffectInstance effect) {
        // StatusEffect.ENTRY_CODEC uses the static STATUS_EFFECT registry, as verified in bytecode.
        return net.minecraft.entity.effect.StatusEffectInstance.CODEC.encodeStart(NbtOps.INSTANCE, effect).getOrThrow();
    }

    public static net.minecraft.entity.effect.StatusEffectInstance effect(NbtCompound nbt) {
        return net.minecraft.entity.effect.StatusEffectInstance.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow();
    }

    public static boolean contains(NbtCompound nbt, String key, int type) {
        NbtElement value = nbt.get(key);
        return value != null && (type == 99 ? value instanceof AbstractNbtNumber : value.getType() == type);
    }

    public static NbtList list(NbtCompound nbt, String key, int type) {
        NbtElement value = nbt.get(key);
        if (value == null) return new NbtList();
        if (!(value instanceof NbtList list)) throw new IllegalArgumentException("Expected NBT list: " + key);
        for (NbtElement entry : list) {
            if (entry.getType() != type) throw new IllegalArgumentException("Wrong NBT list element type: " + key);
        }
        return list;
    }
}
