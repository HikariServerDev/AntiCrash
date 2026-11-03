package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.snapshot.logical.ChunkMutationTracker;

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.block.entity.BlockEntity;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.ChunkPos;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.util.math.ChunkPos;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.block.entity.BlockEntity;
*///?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Records block-entity NBT mutations that do not pass through WorldChunk#setBlockState. The hook is
 * an epoch invalidation signal only; it never serializes or replaces the live block entity and
 * therefore does not change Vanilla save semantics.
 */
@Mixin(BlockEntity.class)
public abstract class BlockEntityMutationMixin {

    //? if (>=1.18 && <=1.21.11) {
    @Inject(method = "markDirty()V", at = @At("HEAD"))
    private void anticrash$markDirty(CallbackInfo callbackInfo)
                //?}
                //? if (>=26.1 && <=26.3) {
                /*@Inject(method = "setChanged()V", at = @At("HEAD"))
                    private void anticrash$markDirty(CallbackInfo callbackInfo)
*///?}
            {
        if (!com.atsukimc.anticrash.snapshot.logical.LogicalTracking.enabled()) return;
        BlockEntity blockEntity = (BlockEntity) (Object) this;

        //? if (>=1.18 && <=1.21.11) {
        if (!blockEntity.hasWorld()
                || blockEntity.getWorld() == null
                || blockEntity.getPos() == null)
            //?}
            //? if (>=26.1 && <=26.3) {
            /*if (!blockEntity.hasLevel()
                            || blockEntity.getLevel() == null
                            || blockEntity.getBlockPos() == null)
*///?}
            return;

        //? if (>=1.18 && <=1.21.11) {
        ChunkMutationTracker.record(
                blockEntity.getWorld(),
                new ChunkPos(blockEntity.getPos()),
                "blockEntity.markDirty");
        //?}
        //? if (>=26.1 && <=26.3) {
        /*ChunkMutationTracker.record(
                        blockEntity.getLevel(),
                        ChunkPos.containing(blockEntity.getBlockPos()),
                        "blockEntity.markDirty");
*///?}

    }
}
