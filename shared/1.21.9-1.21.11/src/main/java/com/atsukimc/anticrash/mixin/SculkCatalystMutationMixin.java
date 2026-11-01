package com.atsukimc.anticrash.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.SculkCatalystBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla writes spread cursors but their countdown does not call markDirty (1.19 bytecode). */
@Mixin(SculkCatalystBlockEntity.class)
public abstract class SculkCatalystMutationMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private static void anticrash$markCursorTick(
            World world,
            BlockPos pos,
            BlockState state,
            SculkCatalystBlockEntity catalyst,
            CallbackInfo ci) {
        if (!world.isClient()) world.markDirty(pos);
    }
}
