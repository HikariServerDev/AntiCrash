package com.atsukimc.anticrash.mixin;

import net.minecraft.block.Portal;
import net.minecraft.world.dimension.PortalManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PortalManager.class)
public interface PortalManagerAccessorMixin {
    @Accessor("portal")
    Portal anticrash$getPortal();

    @Accessor("ticksInPortal")
    void anticrash$setTicksInPortal(int ticks);
}
