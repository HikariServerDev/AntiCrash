package com.atsukimc.anticrash.mixin;

import net.minecraft.block.spawner.MobSpawnerLogic;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The spawner {@code Delay} changes while a player is in range without a chunk save mark. */
@Mixin(MobSpawnerLogic.class)
public abstract class MobSpawnerLogicMixin {
    @Shadow private int spawnDelay;
    @Unique private int anticrash$delayBefore;

    @Inject(method = "serverTick", at = @At("HEAD"))
    private void anticrash$rememberDelay(
            ServerWorld world, BlockPos pos, CallbackInfo callbackInfo) {
        anticrash$delayBefore = spawnDelay;
    }

    @Inject(method = "serverTick", at = @At("RETURN"))
    private void anticrash$markDelayDirty(
            ServerWorld world, BlockPos pos, CallbackInfo callbackInfo) {
        if (spawnDelay != anticrash$delayBefore) world.markDirty(pos);
    }
}
