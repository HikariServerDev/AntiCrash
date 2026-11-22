//? if >=1.21.5 {
package com.atsukimc.anticrash.mixin;

//? if <=1.21.10 {
/*import net.minecraft.world.entity.vehicle.MinecartTNT;
*///?} else {
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
//?}

import net.minecraft.world.damagesource.DamageSource;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MinecartTNT.class)
public interface TntMinecartRuntimeAccessorMixin {
    @Accessor("ignitionSource")
    DamageSource anticrash$getDamageSource();
}
//?}
