package com.atsukimc.anticrash.mixin;

import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TheEndGatewayBlockEntity.class)
public interface EndGatewayBlockEntityAccessorMixin {
    @Accessor("teleportCooldown")
    int anticrash$getTeleportCooldown();
}
