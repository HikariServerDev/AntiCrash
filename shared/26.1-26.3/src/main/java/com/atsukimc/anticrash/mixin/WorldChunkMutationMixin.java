package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.snapshot.logical.ChunkMutationTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Auditable semantic mutation hooks; this is intentionally not a universal mutation journal. */
@Mixin(LevelChunk.class)
public abstract class WorldChunkMutationMixin {
    @Inject(method = "setBlockState", at = @At("TAIL"))
    private void anticrash$blockStateMutation(BlockPos pos, BlockState state, int flags,
                                               CallbackInfoReturnable<BlockState> callbackInfo) {
        LevelChunk chunk = (LevelChunk) (Object) this;
        ChunkMutationTracker.record(chunk.getLevel(), chunk.getPos(), "setBlockState");
    }

    @Inject(method = "setBlockEntity", at = @At("TAIL"))
    private void anticrash$blockEntityMutation(BlockEntity blockEntity, CallbackInfo callbackInfo) {
        LevelChunk chunk = (LevelChunk) (Object) this;
        ChunkMutationTracker.record(chunk.getLevel(), chunk.getPos(), "setBlockEntity");
    }

    @Inject(method = "removeBlockEntity", at = @At("TAIL"))
    private void anticrash$removeBlockEntityMutation(BlockPos pos, CallbackInfo callbackInfo) {
        LevelChunk chunk = (LevelChunk) (Object) this;
        ChunkMutationTracker.record(chunk.getLevel(), chunk.getPos(), "removeBlockEntity");
    }
}
