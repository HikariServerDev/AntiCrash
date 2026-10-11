package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.snapshot.logical.ChunkMutationTracker;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Records block-entity NBT mutations that do not pass through WorldChunk#setBlockState.
 * The hook is an epoch invalidation signal only; it never serializes or replaces the live
 * block entity and therefore does not change Vanilla save semantics.
 */
@Mixin(BlockEntity.class)
public abstract class BlockEntityMutationMixin {
    @Inject(method = "setChanged()V", at = @At("HEAD"))
    private void anticrash$markDirty(CallbackInfo callbackInfo) {
        if (!com.atsukimc.anticrash.snapshot.logical.LogicalTracking.enabled()) return;
        BlockEntity blockEntity = (BlockEntity) (Object) this;
        if (!blockEntity.hasLevel() || blockEntity.getLevel() == null || blockEntity.getBlockPos() == null) return;
        ChunkMutationTracker.record(blockEntity.getLevel(),
                ChunkPos.containing(blockEntity.getBlockPos()), "blockEntity.markDirty");
    }
}
