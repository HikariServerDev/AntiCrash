package com.atsukimc.anticrash.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ProjectileEntity.class)
public interface ProjectileRuntimeAccessorMixin {
    @Accessor("lastDeflectedEntity")
    Entity anticrash$lastDeflectedEntity();
}
