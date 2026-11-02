package com.atsukimc.anticrash.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla decrements {@code TransferCooldown} every tick without marking the chunk for saving, so a
 * chunk saved just after a transfer keeps an old cooldown on disk. A checkpoint of such a chunk
 * would restart hopper clocks with the wrong timing. A positive cooldown always changes during this
 * tick, so the chunk is marked exactly when the persisted value changes.
 */
@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin {
    @Inject(method = "serverTick", at = @At("HEAD"))
    private static void anticrash$markCooldownDirty(
            World world,
            BlockPos pos,
            BlockState state,
            HopperBlockEntity hopper,
            CallbackInfo callbackInfo) {
        if (((HopperBlockEntityTimerAccessor) hopper).anticrash$getTransferCooldown() > 0)
            world.markDirty(pos);
    }
}
