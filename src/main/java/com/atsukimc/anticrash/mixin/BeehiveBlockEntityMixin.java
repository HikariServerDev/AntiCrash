package com.atsukimc.anticrash.mixin;

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.world.level.block.state.BlockState;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.core.BlockPos;
//?}

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.world.level.Level;
//?}

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.core.BlockPos;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
//?}

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.world.level.Level;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.world.level.block.state.BlockState;
//?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(BeehiveBlockEntity.class)
public abstract class BeehiveBlockEntityMixin {
    /**
     * "TicksInHive" is saved by Vanilla, but the count advances every tick without the chunk being
     * marked for saving. A checkpoint therefore kept an old count and the bees left late.
     */

    //? if (>=1.18 && <=1.21.11) {
    /*@Inject(method = "tickOccupants", at = @At("TAIL"))
    private static void anticrash$markChunkDirty(
            Level world,
            BlockPos pos,
            BlockState state,
            List<?> bees,
            BlockPos flowerPos,
            CallbackInfo callbackInfo)
                *///?} else if (>=26.1 && <=26.3) {
    @Inject(method = "tickOccupants", at = @At("TAIL"))
    private static void anticrash$markChunkDirty(
            Level world,
            BlockPos pos,
            BlockState state,
            List<?> bees,
            BlockPos flowerPos,
            CallbackInfo callbackInfo)
                //?}
            {
        if (!bees.isEmpty())
            //? if (>=1.18 && <=1.21.11) {
            /*world.blockEntityChanged(pos);
        *///?} else if (>=26.1 && <=26.3) {
            world.blockEntityChanged(pos);
        //?}

    }
}
