package com.atsukimc.anticrash.mixin;

import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only view of the entity state AntiCrash keeps in its NBT extension, for verification. */
@Mixin(Entity.class)
public interface EntityRuntimeAccessorMixin {

    //? if (>=1.21.11 && <=26.3) {
    @Accessor("lastKnownSpeed")
    net.minecraft.world.phys.Vec3 anticrash$movement();

    @Accessor("lastKnownSpeed")
    void anticrash$setMovement(net.minecraft.world.phys.Vec3 value);

    @Accessor("lastKnownPosition")
    net.minecraft.world.phys.Vec3 anticrash$lastPos();

    @Accessor("lastKnownPosition")
    void anticrash$setLastPos(net.minecraft.world.phys.Vec3 value);

    //?}

    //? if (>=1.20 && <=26.3) {
    @Accessor("onGroundNoBlocks")
    boolean anticrash$forceSupportUpdate();

    //?}

    //? if (>=1.18 && <=1.20.6) {
    /*@Accessor("isInsidePortal")
    boolean anticrash$isInNetherPortal();

    @Accessor("portalTime")
    int anticrash$getNetherPortalTime();

    *///?}

    @Accessor("boardingCooldown")
    int anticrash$getRidingCooldown();

    @Accessor("firstTick")
    boolean anticrash$isFirstUpdate();

    @Accessor("wasTouchingWater")
    boolean anticrash$isTouchingWater();

    /** The counter new entities take their network id from. */

    //? if (>=1.18 && <=26.1) {
    /*@Accessor("ENTITY_COUNTER")
    static java.util.concurrent.atomic.AtomicInteger anticrash$currentId() {
        throw new AssertionError();
    }
    *///?}

}
