//? if (>=1.21.11 && <=26.3) {
/*package com.atsukimc.anticrash.runtime;

import com.atsukimc.anticrash.mixin.EntityRuntimeAccessorMixin;

//? if 1.21.11 {
/^import net.minecraft.world.entity.Entity;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.nbt.CompoundTag;
^///?}

//? if 1.21.11 {
/^import net.minecraft.nbt.CompoundTag;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.entity.Entity;
^///?}

//? if 1.21.11 {
/^import net.minecraft.world.phys.Vec3;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.phys.Vec3;
^///?}


/^* 1.21.11 kinetic movement history is not velocity and is not saved by Vanilla. ^/
public record EntityMotionFrame(
//? if 1.21.11 {
/^Vec3 movement
^///?}
//? if (>=26.1 && <=26.3) {
/^Vec3 movement
^///?}
,
//? if 1.21.11 {
/^Vec3 lastPosition
^///?}
//? if (>=26.1 && <=26.3) {
/^Vec3 lastPosition
^///?}
, boolean knockedBack) {
    public EntityMotionFrame {
        if (!finite(movement) || (lastPosition != null && !finite(lastPosition)))
            throw new IllegalArgumentException("Invalid entity motion history");
    }


//? if 1.21.11 {
/^private static boolean finite(Vec3 vector)
^///?}
//? if (>=26.1 && <=26.3) {
/^private static boolean finite(Vec3 vector)
^///?}
{
        return vector != null
                && Double.isFinite(vector.x)
                && Double.isFinite(vector.y)
                && Double.isFinite(vector.z);
    }

    public static EntityMotionFrame capture(Entity entity) {
        var accessor = (EntityRuntimeAccessorMixin) entity;

//? if 1.21.11 {
/^return new EntityMotionFrame(
                accessor.anticrash$movement(), accessor.anticrash$lastPos(), entity.hurtMarked);
^///?}
//? if (>=26.1 && <=26.2) {
/^return new EntityMotionFrame(
                accessor.anticrash$movement(), accessor.anticrash$lastPos(), entity.hurtMarked);
^///?}
//? if 26.3 {
/^return new EntityMotionFrame(
                accessor.anticrash$movement(), accessor.anticrash$lastPos(), entity.syncVelocity);
^///?}

    }


//? if 1.21.11 {
/^public CompoundTag write()
^///?}
//? if (>=26.1 && <=26.3) {
/^public CompoundTag write()
^///?}
{

//? if 1.21.11 {
/^var nbt = new CompoundTag();
^///?}
//? if (>=26.1 && <=26.3) {
/^var nbt = new CompoundTag();
^///?}


//? if 1.21.11 {
/^nbt.store("Movement", Vec3.CODEC, movement);
^///?}
//? if (>=26.1 && <=26.3) {
/^nbt.store("Movement", Vec3.CODEC, movement);
^///?}

        nbt.putBoolean("HasLast", lastPosition != null);
        if (lastPosition != null)
//? if 1.21.11 {
/^nbt.store("Last", Vec3.CODEC, lastPosition);
^///?}
//? if (>=26.1 && <=26.3) {
/^nbt.store("Last", Vec3.CODEC, lastPosition);
^///?}

        nbt.putBoolean("KnockedBack", knockedBack);
        return nbt;
    }


//? if 1.21.11 {
/^public static EntityMotionFrame read(CompoundTag nbt)
^///?}
//? if (>=26.1 && <=26.3) {
/^public static EntityMotionFrame read(CompoundTag nbt)
^///?}
{

//? if 1.21.11 {
/^return new EntityMotionFrame(
                nbt.read("Movement", Vec3.CODEC).orElseThrow(),
                nbt.getBoolean("HasLast").orElseThrow()
                        ? nbt.read("Last", Vec3.CODEC).orElseThrow()
                        : null,
                nbt.getBoolean("KnockedBack").orElseThrow());
^///?}
//? if (>=26.1 && <=26.3) {
/^return new EntityMotionFrame(
                nbt.read("Movement", Vec3.CODEC).orElseThrow(),
                nbt.getBoolean("HasLast").orElseThrow()
                        ? nbt.read("Last", Vec3.CODEC).orElseThrow()
                        : null,
                nbt.getBoolean("KnockedBack").orElseThrow());
^///?}

    }

    public void apply(Entity entity) {
        var accessor = (EntityRuntimeAccessorMixin) entity;
        accessor.anticrash$setMovement(movement);
        accessor.anticrash$setLastPos(lastPosition);

//? if 1.21.11 {
/^entity.hurtMarked = knockedBack;
^///?}
//? if (>=26.1 && <=26.2) {
/^entity.hurtMarked = knockedBack;
^///?}
//? if 26.3 {
/^entity.syncVelocity = knockedBack;
^///?}

    }
}
*///?}
