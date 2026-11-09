package com.atsukimc.anticrash.runtime;

import java.util.UUID;
import net.minecraft.nbt.*;

/**
 * NBT reads across versions. 1.21.5 made the typed getters return Optional, removed type-aware
 * contains and permits heterogeneous lists. The adapters preserve their existing version-specific
 * behavior: legacy getters use Vanilla defaults, while Optional getters require a value. Tolerant
 * reads are explicit; list validation is strict on 1.21.5 and later.
 */
public final class VersionNbt {
    private VersionNbt() {}

    /** Sparse flags default to false when absent; newer versions also reject malformed values. */
    public static boolean sparseFlag(CompoundTag nbt, String key) {
        //? if (>=1.18 && <=1.21.4) {
        /*return nbt.getBoolean(key);
        *///?} else {
        return !nbt.contains(key)
                ? false
                : nbt.getBoolean(key)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Invalid boolean recovery flag: " + key));
        //?}
    }

    //? if (>=1.20.5 && <=26.3) {
    public static Tag item(
            net.minecraft.world.item.ItemStack stack,
            net.minecraft.core.HolderLookup.Provider registries) {
        //? if (>=1.20.5 && <=1.21.4) {
        /*return stack.saveOptional(registries);
        *///?} else {
        return net.minecraft.world.item.ItemStack.OPTIONAL_CODEC
                .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), stack)
                .getOrThrow();
        //?}
    }

    //?}

    public static Tag effect(net.minecraft.world.effect.MobEffectInstance effect) {
        //? if (>=1.18 && <=1.20.4) {
        /*return effect.save(new CompoundTag());
        *///?} else if (>=1.20.5 && <=1.21.4) {
        /*return effect.save();
        *///?} else {
        // MobEffect.ENTRY_CODEC uses the static STATUS_EFFECT registry, as verified in bytecode.
        return net.minecraft.world.effect.MobEffectInstance.CODEC
                .encodeStart(NbtOps.INSTANCE, effect)
                .getOrThrow();
        //?}
    }

    public static net.minecraft.world.effect.MobEffectInstance effect(CompoundTag nbt) {
        //? if (>=1.18 && <=1.21.4) {
        /*return net.minecraft.world.effect.MobEffectInstance.load(nbt);
        *///?} else {
        return net.minecraft.world.effect.MobEffectInstance.CODEC
                .parse(NbtOps.INSTANCE, nbt)
                .getOrThrow();
        //?}
    }

    public static boolean contains(CompoundTag nbt, String key, int type) {
        //? if (>=1.18 && <=1.21.4) {
        /*return nbt.contains(key, type);
        *///?} else {
        Tag value = nbt.get(key);

        return value != null && (type == 99 ? value instanceof NumericTag : value.getId() == type);
        //?}
    }

    public static ListTag list(CompoundTag nbt, String key, int type) {
        //? if (>=1.18 && <=1.21.4) {
        /*return nbt.getList(key, type);
        *///?} else {
        Tag value = nbt.get(key);

        if (value == null) return new ListTag();

        if (!(value instanceof ListTag list))
            throw new IllegalArgumentException("Expected NBT list: " + key);

        for (Tag entry : list) {

            if (entry.getId() != type)
                throw new IllegalArgumentException("Wrong NBT list element type: " + key);
        }
        return list;
        //?}
    }

    /** Vanilla tolerant list read. The element-type filter applies only before 1.21.5. */
    public static ListTag listOrEmpty(CompoundTag nbt, String key, int type) {
        //? if (>=1.18 && <=1.21.4) {
        /*return nbt.getList(key, type);
        *///?} else {
        return nbt.getListOrEmpty(key);
        //?}
    }

    public static int getInt(CompoundTag nbt, String key) {
        //? if (>=1.18 && <=1.21.4) {
        /*return nbt.getInt(key);
        *///?} else {
        return nbt.getInt(key).orElseThrow();
        //?}
    }

    public static long getLong(CompoundTag nbt, String key) {
        //? if (>=1.18 && <=1.21.4) {
        /*return nbt.getLong(key);
        *///?} else {
        return nbt.getLong(key).orElseThrow();
        //?}
    }

    public static float getFloat(CompoundTag nbt, String key) {
        //? if (>=1.18 && <=1.21.4) {
        /*return nbt.getFloat(key);
        *///?} else {
        return nbt.getFloat(key).orElseThrow();
        //?}
    }

    public static double getDouble(CompoundTag nbt, String key) {
        //? if (>=1.18 && <=1.21.4) {
        /*return nbt.getDouble(key);
        *///?} else {
        return nbt.getDouble(key).orElseThrow();
        //?}
    }

    public static boolean getBoolean(CompoundTag nbt, String key) {
        //? if (>=1.18 && <=1.21.4) {
        /*return nbt.getBoolean(key);
        *///?} else {
        return nbt.getBoolean(key).orElseThrow();
        //?}
    }

    public static String getString(CompoundTag nbt, String key) {
        //? if (>=1.18 && <=1.21.4) {
        /*return nbt.getString(key);
        *///?} else {
        return nbt.getString(key).orElseThrow();
        //?}
    }

    public static String getString(ListTag list, int index) {
        //? if (>=1.18 && <=1.21.4) {
        /*return list.getString(index);
        *///?} else {
        return list.getString(index).orElseThrow();
        //?}
    }

    public static CompoundTag getCompound(CompoundTag nbt, String key) {
        //? if (>=1.18 && <=1.21.4) {
        /*return nbt.getCompound(key);
        *///?} else {
        return nbt.getCompound(key).orElseThrow();
        //?}
    }

    public static CompoundTag getCompound(ListTag list, int index) {
        //? if (>=1.18 && <=1.21.4) {
        /*return list.getCompound(index);
        *///?} else {
        return list.getCompound(index).orElseThrow();
        //?}
    }

    /** The compound under key, or an empty one when it is missing or holds another type. */
    public static CompoundTag getCompoundOrEmpty(CompoundTag nbt, String key) {
        //? if (>=1.18 && <=1.21.4) {
        /*return nbt.getCompound(key);
        *///?} else {
        return nbt.getCompoundOrEmpty(key);
        //?}
    }

    public static UUID getUUID(CompoundTag nbt, String key) {
        //? if (>=1.18 && <=1.21.4) {
        /*return nbt.getUUID(key);
        *///?} else {
        return nbt.read(key, net.minecraft.core.UUIDUtil.CODEC).orElseThrow();
        //?}
    }
}
