package com.atsukimc.anticrash.mixin;

import net.minecraft.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only view of the entity state AntiCrash keeps in its NBT extension, for verification. */
@Mixin(Entity.class)
public interface EntityRuntimeAccessorMixin {
    @Accessor("forceUpdateSupportingBlockPos")
    boolean anticrash$forceSupportUpdate();

    @Accessor("inNetherPortal")
    boolean anticrash$isInNetherPortal();

    @Accessor("netherPortalTime")
    int anticrash$getNetherPortalTime();

    @Accessor("ridingCooldown")
    int anticrash$getRidingCooldown();

    @Accessor("firstUpdate")
    boolean anticrash$isFirstUpdate();

    @Accessor("touchingWater")
    boolean anticrash$isTouchingWater();

    /** The counter new entities take their network id from. */
    @Accessor("CURRENT_ID")
    static java.util.concurrent.atomic.AtomicInteger anticrash$currentId() {
        throw new AssertionError();
    }
}
