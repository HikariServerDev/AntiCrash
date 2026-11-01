package com.atsukimc.anticrash.mixin;

import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only view of the entity state AntiCrash keeps in its NBT extension, for verification. */
@Mixin(Entity.class)
public interface EntityRuntimeAccessorMixin {
    @Accessor("lastKnownSpeed")
    net.minecraft.world.phys.Vec3 anticrash$movement();

    @Accessor("lastKnownSpeed")
    void anticrash$setMovement(net.minecraft.world.phys.Vec3 value);

    @Accessor("lastKnownPosition")
    net.minecraft.world.phys.Vec3 anticrash$lastPos();

    @Accessor("lastKnownPosition")
    void anticrash$setLastPos(net.minecraft.world.phys.Vec3 value);

    @Accessor("onGroundNoBlocks")
    boolean anticrash$forceSupportUpdate();

    @Accessor("boardingCooldown")
    int anticrash$getRidingCooldown();

    @Accessor("firstTick")
    boolean anticrash$isFirstUpdate();

    @Accessor("wasTouchingWater")
    boolean anticrash$isTouchingWater();

    /** The counter new entities take their network id from. */
    @Accessor("ENTITY_COUNTER")
    static java.util.concurrent.atomic.AtomicInteger anticrash$currentId() {
        throw new AssertionError();
    }
}
