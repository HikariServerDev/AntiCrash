package com.atsukimc.anticrash.runtime;

import com.atsukimc.anticrash.mixin.EntityRuntimeAccessorMixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** 1.21.11 kinetic movement history is not velocity and is not saved by Vanilla. */
public record EntityMotionFrame(Vec3 movement, Vec3 lastPosition, boolean knockedBack) {
    public EntityMotionFrame {
        if (!finite(movement) || (lastPosition != null && !finite(lastPosition)))
            throw new IllegalArgumentException("Invalid entity motion history");
    }

    private static boolean finite(Vec3 vector) {
        return vector != null
                && Double.isFinite(vector.x)
                && Double.isFinite(vector.y)
                && Double.isFinite(vector.z);
    }

    public static EntityMotionFrame capture(Entity entity) {
        var accessor = (EntityRuntimeAccessorMixin) entity;
        return new EntityMotionFrame(
                accessor.anticrash$movement(), accessor.anticrash$lastPos(), entity.syncVelocity);
    }

    public CompoundTag write() {
        var nbt = new CompoundTag();
        nbt.store("Movement", Vec3.CODEC, movement);
        nbt.putBoolean("HasLast", lastPosition != null);
        if (lastPosition != null) nbt.store("Last", Vec3.CODEC, lastPosition);
        nbt.putBoolean("KnockedBack", knockedBack);
        return nbt;
    }

    public static EntityMotionFrame read(CompoundTag nbt) {
        return new EntityMotionFrame(
                nbt.read("Movement", Vec3.CODEC).orElseThrow(),
                nbt.getBoolean("HasLast").orElseThrow()
                        ? nbt.read("Last", Vec3.CODEC).orElseThrow()
                        : null,
                nbt.getBoolean("KnockedBack").orElseThrow());
    }

    public void apply(Entity entity) {
        var accessor = (EntityRuntimeAccessorMixin) entity;
        accessor.anticrash$setMovement(movement);
        accessor.anticrash$setLastPos(lastPosition);
        entity.syncVelocity = knockedBack;
    }
}
