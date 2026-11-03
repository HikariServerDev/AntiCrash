package com.atsukimc.anticrash.mixin;

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
//?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

//? if (>=1.18 && <=1.21.11) {
/*@Mixin(PistonMovingBlockEntity.class)
public interface PistonBlockEntityAccessorMixin {

    *///?} else if (>=26.1 && <=26.3) {
@Mixin(PistonMovingBlockEntity.class)
public interface PistonBlockEntityAccessorMixin {
    //?}
    @Accessor("progress")
    float anticrash$getProgress();

    //? if (>=1.18 && <=1.21.11) {
    /*@Accessor("progressO")
    float anticrash$getLastProgress();
    *///?} else if (>=26.1 && <=26.3) {
    @Accessor("progressO")
    float anticrash$getLastProgress();
    //?}

}
