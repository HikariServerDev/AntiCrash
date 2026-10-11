package com.atsukimc.anticrash.mixin;

import net.minecraft.block.spawner.TrialSpawnerLogic;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TrialSpawnerLogic.class)
public abstract class TrialSpawnerRuntimeMixin {
    @Inject(method = "tickServer", at = @At("HEAD"))
    private void anticrash$dirty(ServerWorld world, BlockPos pos, CallbackInfo ci) {
        // Codec-owned spawn deadlines, player and mob sets may change without a block transition.
        world.markDirty(pos);
    }
}
