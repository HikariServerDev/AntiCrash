//? if (>=1.21 && <=26.3) {
package com.atsukimc.anticrash.mixin;

import net.minecraft.world.level.block.Portal;
import net.minecraft.world.entity.PortalProcessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PortalProcessor.class)
public interface PortalManagerAccessorMixin {

    @Accessor("portal")
    Portal anticrash$getPortal();

    @Accessor("portalTime")
    void anticrash$setTicksInPortal(int ticks);
}
//?}
