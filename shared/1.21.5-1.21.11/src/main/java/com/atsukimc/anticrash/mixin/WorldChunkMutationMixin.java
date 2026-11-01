package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.snapshot.logical.ChunkMutationTracker;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Auditable semantic mutation hooks; this is intentionally not a universal mutation journal. */
@Mixin(WorldChunk.class)
public abstract class WorldChunkMutationMixin {
    @Inject(method = "setBlockState", at = @At("TAIL"))
    private void anticrash$blockStateMutation(
            BlockPos pos,
            BlockState state,
            int flags,
            CallbackInfoReturnable<BlockState> callbackInfo) {
        WorldChunk chunk = (WorldChunk) (Object) this;
        ChunkMutationTracker.record(chunk.getWorld(), chunk.getPos(), "setBlockState");
    }

    @Inject(method = "setBlockEntity", at = @At("TAIL"))
    private void anticrash$blockEntityMutation(BlockEntity blockEntity, CallbackInfo callbackInfo) {
        WorldChunk chunk = (WorldChunk) (Object) this;
        ChunkMutationTracker.record(chunk.getWorld(), chunk.getPos(), "setBlockEntity");
    }

    @Inject(method = "removeBlockEntity", at = @At("TAIL"))
    private void anticrash$removeBlockEntityMutation(BlockPos pos, CallbackInfo callbackInfo) {
        WorldChunk chunk = (WorldChunk) (Object) this;
        ChunkMutationTracker.record(chunk.getWorld(), chunk.getPos(), "removeBlockEntity");
    }
}
