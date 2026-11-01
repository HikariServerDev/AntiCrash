package com.atsukimc.anticrash.mixin;

import net.minecraft.block.entity.EndGatewayBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EndGatewayBlockEntity.class)
public interface EndGatewayBlockEntityAccessorMixin {
    @Accessor("teleportCooldown")
    int anticrash$getTeleportCooldown();
}
