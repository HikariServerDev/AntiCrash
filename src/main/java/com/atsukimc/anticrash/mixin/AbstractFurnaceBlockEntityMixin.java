package com.atsukimc.anticrash.mixin;

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.level.block.state.BlockState;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.core.BlockPos;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.core.BlockPos;
//?}

//? if (>=1.18 && <=1.21.1) {
import net.minecraft.world.level.Level;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.block.state.BlockState;
*///?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Furnace, smoker and blast furnace progress ({@code BurnTime}/{@code CookTime}) advances every
 * tick but Vanilla only marks the chunk dirty when an item finishes or the lit state flips. Both
 * values change during this tick whenever either is positive.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceBlockEntityMixin {

    //? if (>=1.18 && <=1.21.1) {
    @Inject(method = "serverTick", at = @At("HEAD"))
    private static void anticrash$markProgressDirty(
            Level world,
            BlockPos pos,
            BlockState state,
            AbstractFurnaceBlockEntity furnace,
            CallbackInfo callbackInfo)
                //?}
                //? if (>=1.21.2 && <=1.21.11) {
                /*@Inject(method = "serverTick", at = @At("HEAD"))
                    private static void anticrash$markProgressDirty(
                            net.minecraft.server.level.ServerLevel world,
                            BlockPos pos,
                            BlockState state,
                            AbstractFurnaceBlockEntity furnace,
                            CallbackInfo callbackInfo)
*///?}
                //? if (>=26.1 && <=26.3) {
                /*@Inject(method = "serverTick", at = @At("HEAD"))
                    private static void anticrash$markProgressDirty(
                            net.minecraft.server.level.ServerLevel world,
                            BlockPos pos,
                            BlockState state,
                            AbstractFurnaceBlockEntity furnace,
                            CallbackInfo callbackInfo)
*///?}
            {
        AbstractFurnaceBlockEntityTimerAccessor timer =
                (AbstractFurnaceBlockEntityTimerAccessor) furnace;
        if (timer.anticrash$getBurnTime() > 0 || timer.anticrash$getCookTime() > 0)

            //? if (>=1.18 && <=1.21.11) {
            world.blockEntityChanged(pos);
        //?}
        //? if (>=26.1 && <=26.3) {
        /*world.blockEntityChanged(pos);
*///?}

    }
}
