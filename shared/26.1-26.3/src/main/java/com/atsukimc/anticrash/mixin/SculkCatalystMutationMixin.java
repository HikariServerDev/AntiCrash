package com.atsukimc.anticrash.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SculkCatalystBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla writes spread cursors but their countdown does not call markDirty (1.19 bytecode). */
@Mixin(SculkCatalystBlockEntity.class)
public abstract class SculkCatalystMutationMixin {
    @Inject(method = "serverTick", at = @At("HEAD"))
    private static void anticrash$markCursorTick(
            Level world,
            BlockPos pos,
            BlockState state,
            SculkCatalystBlockEntity catalyst,
            CallbackInfo ci) {
        if (!world.isClientSide()) world.blockEntityChanged(pos);
    }
}
