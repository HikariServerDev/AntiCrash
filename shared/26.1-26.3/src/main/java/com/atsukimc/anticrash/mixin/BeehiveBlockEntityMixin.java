package com.atsukimc.anticrash.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

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
    @Inject(method = "tickOccupants", at = @At("TAIL"))
    private static void anticrash$markChunkDirty(
            Level world,
            BlockPos pos,
            BlockState state,
            List<?> bees,
            BlockPos flowerPos,
            CallbackInfo callbackInfo) {
        if (!bees.isEmpty()) world.blockEntityChanged(pos);
    }
}
