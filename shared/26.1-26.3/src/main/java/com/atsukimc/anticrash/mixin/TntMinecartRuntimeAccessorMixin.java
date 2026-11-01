package com.atsukimc.anticrash.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MinecartTNT.class)
public interface TntMinecartRuntimeAccessorMixin {
    @Accessor("ignitionSource") DamageSource anticrash$getDamageSource();
}
