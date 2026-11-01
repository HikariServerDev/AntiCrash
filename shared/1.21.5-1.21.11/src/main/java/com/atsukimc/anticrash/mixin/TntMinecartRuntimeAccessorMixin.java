package com.atsukimc.anticrash.mixin;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.vehicle.TntMinecartEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TntMinecartEntity.class)
public interface TntMinecartRuntimeAccessorMixin {
    @Accessor("damageSource")
    DamageSource anticrash$getDamageSource();
}
