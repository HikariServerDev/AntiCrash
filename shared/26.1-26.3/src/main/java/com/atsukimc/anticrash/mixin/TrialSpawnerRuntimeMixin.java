package com.atsukimc.anticrash.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TrialSpawner.class)
public abstract class TrialSpawnerRuntimeMixin {
    @Inject(method = "tickServer", at = @At("HEAD"))
    private void anticrash$dirty(ServerLevel world, BlockPos pos, boolean ominous, CallbackInfo ci) {
        // Codec-owned spawn deadlines, player and mob sets may change without a block transition.
        world.blockEntityChanged(pos);
    }
}
