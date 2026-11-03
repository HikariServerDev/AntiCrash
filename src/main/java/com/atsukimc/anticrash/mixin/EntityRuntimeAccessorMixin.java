package com.atsukimc.anticrash.mixin;

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.world.entity.Entity;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.world.entity.Entity;
//?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only view of the entity state AntiCrash keeps in its NBT extension, for verification. */
@Mixin(Entity.class)
public interface EntityRuntimeAccessorMixin {

    //? if 1.21.11 {
    /*@Accessor("lastKnownSpeed")
        net.minecraft.world.phys.Vec3 anticrash$movement();
*///?} else if (>=26.1 && <=26.3) {
    @Accessor("lastKnownSpeed")
    net.minecraft.world.phys.Vec3 anticrash$movement();

    //?}

    //? if 1.21.11 {
    /*@Accessor("lastKnownSpeed")
        void anticrash$setMovement(net.minecraft.world.phys.Vec3 value);
*///?} else if (>=26.1 && <=26.3) {
    @Accessor("lastKnownSpeed")
    void anticrash$setMovement(net.minecraft.world.phys.Vec3 value);

    //?}

    //? if 1.21.11 {
    /*@Accessor("lastKnownPosition")
        net.minecraft.world.phys.Vec3 anticrash$lastPos();
*///?} else if (>=26.1 && <=26.3) {
    @Accessor("lastKnownPosition")
    net.minecraft.world.phys.Vec3 anticrash$lastPos();

    //?}

    //? if 1.21.11 {
    /*@Accessor("lastKnownPosition")
        void anticrash$setLastPos(net.minecraft.world.phys.Vec3 value);
*///?} else if (>=26.1 && <=26.3) {
    @Accessor("lastKnownPosition")
    void anticrash$setLastPos(net.minecraft.world.phys.Vec3 value);

    //?}

    //? if (>=1.20 && <=1.21.11) {
    /*@Accessor("onGroundNoBlocks")
        boolean anticrash$forceSupportUpdate();
*///?} else if (>=26.1 && <=26.3) {
    @Accessor("onGroundNoBlocks")
    boolean anticrash$forceSupportUpdate();

    //?}

    //? if (>=1.18 && <=1.20.6) {
    /*@Accessor("isInsidePortal")
    boolean anticrash$isInNetherPortal();

    *///?} else if (>=26.1 && <=26.3) {
    @Accessor("boardingCooldown")
    int anticrash$getRidingCooldown();

    //?}

    //? if (>=1.18 && <=1.20.6) {
    /*@Accessor("portalTime")
    int anticrash$getNetherPortalTime();

    *///?} else if (>=26.1 && <=26.3) {
    @Accessor("firstTick")
    boolean anticrash$isFirstUpdate();

    //?}

    //? if (>=1.18 && <=1.21.11) {
    /*@Accessor("boardingCooldown")
    int anticrash$getRidingCooldown();

    *///?}

    //? if (>=1.18 && <=1.21.11) {
    /*@Accessor("firstTick")
    boolean anticrash$isFirstUpdate();

    *///?}

    //? if (>=1.18 && <=1.21.11) {
    /*@Accessor("wasTouchingWater")
    boolean anticrash$isTouchingWater();

    *///?} else if (>=26.1 && <=26.3) {
    @Accessor("wasTouchingWater")
    boolean anticrash$isTouchingWater();
    //?}

    /** The counter new entities take their network id from. */

    //? if (>=1.18 && <=1.21.11) {
    /*@Accessor("ENTITY_COUNTER")
    static java.util.concurrent.atomic.AtomicInteger anticrash$currentId()
                *///?} else if 26.1 {
    /*@Accessor("ENTITY_COUNTER")
                    static java.util.concurrent.atomic.AtomicInteger anticrash$currentId()
*///?}
    //? if (>=1.18 && <=26.1) {
    /*{
        throw new AssertionError();
    }
    *///?}

}
