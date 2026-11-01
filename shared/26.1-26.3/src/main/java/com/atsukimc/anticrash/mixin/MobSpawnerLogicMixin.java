package com.atsukimc.anticrash.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BaseSpawner;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The spawner {@code Delay} changes while a player is in range without a chunk save mark. */
@Mixin(BaseSpawner.class)
public abstract class MobSpawnerLogicMixin {
    @Shadow private int spawnDelay;
    @Unique private int anticrash$delayBefore;

    @Inject(method = "serverTick", at = @At("HEAD"))
    private void anticrash$rememberDelay(
            ServerLevel world, BlockPos pos, CallbackInfo callbackInfo) {
        anticrash$delayBefore = spawnDelay;
    }

    @Inject(method = "serverTick", at = @At("RETURN"))
    private void anticrash$markDelayDirty(
            ServerLevel world, BlockPos pos, CallbackInfo callbackInfo) {
        if (spawnDelay != anticrash$delayBefore) world.blockEntityChanged(pos);
    }
}
