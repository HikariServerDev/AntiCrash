package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.snapshot.logical.ChunkMutationTracker;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Covers World#markDirty, including piston progress that bypasses BlockEntity#markDirty. */
@Mixin(World.class)
public abstract class WorldMutationMixin {
    @Inject(method = "markDirty", at = @At("HEAD"))
    private void anticrash$worldDirty(BlockPos pos, CallbackInfo callbackInfo) {
        if (!com.atsukimc.anticrash.snapshot.logical.LogicalTracking.enabled()) return;
        ChunkMutationTracker.record((World) (Object) this,
                new net.minecraft.util.math.ChunkPos(pos), "world.markDirty");
    }
}
