package com.atsukimc.anticrash.mixin;

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
*///?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractFurnaceBlockEntity.class)
public interface AbstractFurnaceBlockEntityTimerAccessor {

    //? if (>=1.18 && <=1.21.3) {
    @Accessor("litTime")
    int anticrash$getBurnTime();

    //?}
    //? if (>=1.21.4 && <=26.3) {
    /*@Accessor("litTimeRemaining")
        int anticrash$getBurnTime();
*///?}

    //? if (>=1.18 && <=1.21.3) {
    @Accessor("cookingProgress")
    int anticrash$getCookTime();
    //?}
    //? if (>=1.21.4 && <=1.21.11) {
    /*@Accessor("cookingTimer")
        int anticrash$getCookTime();
*///?}
    //? if (>=26.1 && <=26.3) {
    /*@Accessor("cookingTimer")
        int anticrash$getCookTime();
*///?}

}
