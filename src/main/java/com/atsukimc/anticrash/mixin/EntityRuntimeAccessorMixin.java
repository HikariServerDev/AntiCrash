package com.atsukimc.anticrash.mixin;

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.entity.Entity;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.entity.Entity;
*///?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only view of the entity state AntiCrash keeps in its NBT extension, for verification. */
@Mixin(Entity.class)
public interface EntityRuntimeAccessorMixin {

    //? if 1.21.11 {
    /*@Accessor("movement")
        net.minecraft.util.math.Vec3d anticrash$movement();
*///?}
    //? if (>=26.1 && <=26.3) {
    /*@Accessor("lastKnownSpeed")
        net.minecraft.world.phys.Vec3 anticrash$movement();
*///?}

    //? if 1.21.11 {
    /*@Accessor("movement")
        void anticrash$setMovement(net.minecraft.util.math.Vec3d value);
*///?}
    //? if (>=26.1 && <=26.3) {
    /*@Accessor("lastKnownSpeed")
        void anticrash$setMovement(net.minecraft.world.phys.Vec3 value);
*///?}

    //? if 1.21.11 {
    /*@Accessor("lastPos")
        net.minecraft.util.math.Vec3d anticrash$lastPos();
*///?}
    //? if (>=26.1 && <=26.3) {
    /*@Accessor("lastKnownPosition")
        net.minecraft.world.phys.Vec3 anticrash$lastPos();
*///?}

    //? if 1.21.11 {
    /*@Accessor("lastPos")
        void anticrash$setLastPos(net.minecraft.util.math.Vec3d value);
*///?}
    //? if (>=26.1 && <=26.3) {
    /*@Accessor("lastKnownPosition")
        void anticrash$setLastPos(net.minecraft.world.phys.Vec3 value);
*///?}

    //? if (>=1.20 && <=1.21.11) {
    /*@Accessor("forceUpdateSupportingBlockPos")
        boolean anticrash$forceSupportUpdate();
*///?}
    //? if (>=26.1 && <=26.3) {
    /*@Accessor("onGroundNoBlocks")
        boolean anticrash$forceSupportUpdate();
*///?}

    //? if (>=1.18 && <=1.20.6) {
    @Accessor("inNetherPortal")
    boolean anticrash$isInNetherPortal();

    //?}
    //? if (>=26.1 && <=26.3) {
    /*@Accessor("boardingCooldown")
        int anticrash$getRidingCooldown();
*///?}

    //? if (>=1.18 && <=1.20.6) {
    @Accessor("netherPortalTime")
    int anticrash$getNetherPortalTime();

    //?}
    //? if (>=26.1 && <=26.3) {
    /*@Accessor("firstTick")
        boolean anticrash$isFirstUpdate();
*///?}

    //? if (>=1.18 && <=1.21.11) {
    @Accessor("ridingCooldown")
    int anticrash$getRidingCooldown();

    //?}

    //? if (>=1.18 && <=1.21.11) {
    @Accessor("firstUpdate")
    boolean anticrash$isFirstUpdate();

    //?}

    //? if (>=1.18 && <=1.21.11) {
    @Accessor("touchingWater")
    boolean anticrash$isTouchingWater();

    //?}
    //? if (>=26.1 && <=26.3) {
    /*@Accessor("wasTouchingWater")
        boolean anticrash$isTouchingWater();
*///?}

    /** The counter new entities take their network id from. */

    //? if (>=1.18 && <=1.21.11) {
    @Accessor("CURRENT_ID")
    static java.util.concurrent.atomic.AtomicInteger anticrash$currentId()
                //?}
                //? if 26.1 {
                /*@Accessor("ENTITY_COUNTER")
                    static java.util.concurrent.atomic.AtomicInteger anticrash$currentId()
*///?}
                //? if (>=1.18 && <=26.1) {
            {
        throw new AssertionError();
    }
    //?}

}
