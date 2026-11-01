package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.snapshot.logical.ChunkMutationTracker;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Covers World#markDirty, including piston progress that bypasses BlockEntity#markDirty. */
@Mixin(Level.class)
public abstract class WorldMutationMixin {
    @Inject(method = "blockEntityChanged", at = @At("HEAD"))
    private void anticrash$worldDirty(BlockPos pos, CallbackInfo callbackInfo) {
        if (!com.atsukimc.anticrash.snapshot.logical.LogicalTracking.enabled()) return;
        ChunkMutationTracker.record(
                (Level) (Object) this,
                net.minecraft.world.level.ChunkPos.containing(pos),
                "world.markDirty");
    }
}
