package com.atsukimc.anticrash.runtime;

import com.atsukimc.anticrash.mixin.EntityRuntimeAccessorMixin;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Vec3d;

/** 1.21.11 kinetic movement history is not velocity and is not saved by Vanilla. */
public record EntityMotionFrame(Vec3d movement, Vec3d lastPosition, boolean knockedBack) {
    public EntityMotionFrame {
        if (!finite(movement) || (lastPosition != null && !finite(lastPosition)))
            throw new IllegalArgumentException("Invalid entity motion history");
    }

    private static boolean finite(Vec3d vector) {
        return vector != null
                && Double.isFinite(vector.x)
                && Double.isFinite(vector.y)
                && Double.isFinite(vector.z);
    }

    public static EntityMotionFrame capture(Entity entity) {
        var accessor = (EntityRuntimeAccessorMixin) entity;
        return new EntityMotionFrame(
                accessor.anticrash$movement(), accessor.anticrash$lastPos(), entity.knockedBack);
    }

    public NbtCompound write() {
        var nbt = new NbtCompound();
        nbt.put("Movement", Vec3d.CODEC, movement);
        nbt.putBoolean("HasLast", lastPosition != null);
        if (lastPosition != null) nbt.put("Last", Vec3d.CODEC, lastPosition);
        nbt.putBoolean("KnockedBack", knockedBack);
        return nbt;
    }

    public static EntityMotionFrame read(NbtCompound nbt) {
        return new EntityMotionFrame(
                nbt.get("Movement", Vec3d.CODEC).orElseThrow(),
                nbt.getBoolean("HasLast").orElseThrow()
                        ? nbt.get("Last", Vec3d.CODEC).orElseThrow()
                        : null,
                nbt.getBoolean("KnockedBack").orElseThrow());
    }

    public void apply(Entity entity) {
        var accessor = (EntityRuntimeAccessorMixin) entity;
        accessor.anticrash$setMovement(movement);
        accessor.anticrash$setLastPos(lastPosition);
        entity.knockedBack = knockedBack;
    }
}
