//? if (>=1.21.5 && <=26.3) {
/*package com.atsukimc.anticrash.mixin;

//? if (>=1.21.5 && <=1.21.11) {
/^import net.minecraft.world.damagesource.DamageSource;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.damagesource.DamageSource;
^///?}

//? if (>=1.21.5 && <=1.21.10) {
/^import net.minecraft.world.entity.vehicle.MinecartTNT;
^///?}
//? if 1.21.11 {
/^import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
^///?}


import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

//? if (>=1.21.5 && <=1.21.11) {
/^@Mixin(MinecartTNT.class)
public interface TntMinecartRuntimeAccessorMixin {
    @Accessor("ignitionSource")
    DamageSource anticrash$getDamageSource();
^///?}
//? if (>=26.1 && <=26.3) {
/^@Mixin(MinecartTNT.class)
public interface TntMinecartRuntimeAccessorMixin {
    @Accessor("ignitionSource")
    DamageSource anticrash$getDamageSource();
^///?}

}
*///?}
