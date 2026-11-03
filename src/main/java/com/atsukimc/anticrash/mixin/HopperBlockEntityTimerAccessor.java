package com.atsukimc.anticrash.mixin;

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.world.level.block.entity.HopperBlockEntity;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.world.level.block.entity.HopperBlockEntity;
//?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(HopperBlockEntity.class)
public interface HopperBlockEntityTimerAccessor {

    //? if (>=1.18 && <=1.21.11) {
    /*@Accessor("cooldownTime")
    int anticrash$getTransferCooldown();
    *///?} else if (>=26.1 && <=26.3) {
    @Accessor("cooldownTime")
    int anticrash$getTransferCooldown();
    //?}

}
