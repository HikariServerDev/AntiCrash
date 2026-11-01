package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.checkpoint.CutSave;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.util.Either;

import net.minecraft.server.world.ServerEntityManager;
import net.minecraft.world.storage.ChunkDataAccess;
import net.minecraft.world.storage.EntityChunkDataAccess;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerEntityManager.class)
public abstract class ServerEntityManagerMixin {
    /**
     * At a checkpoint cut the entity flush waits only for entity sections that are still loading,
     * which it must add to the world before saving them. It does not wait for queued writes to
     * reach the disk, nor fsync: the cut seals the queue and takes the queued writes itself (see
     * {@link CutSave}).
     */
    @WrapOperation(
            method = "flush",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/world/storage/ChunkDataAccess;awaitAll(Z)V"))
    private void anticrash$awaitLoadsOnlyAtCut(
            ChunkDataAccess<?> access, boolean sync, Operation<Void> original) {
        if (!CutSave.active() || !(access instanceof EntityChunkDataAccess entities)) {
            original.call(access, sync);
            return;
        }
        EntityChunkDataAccessAccessorMixin accessor = (EntityChunkDataAccessAccessorMixin) entities;
        // Reads run at foreground priority in submission order, so this no-op finishes after every
        // read queued before it; queued writes run at background priority and are not waited for.
        ((StorageIoWorkerAccessorMixin) accessor.anticrash$getDataLoadWorker())
                .anticrash$run(() -> Either.left(null))
                .join();
        accessor.anticrash$getTaskExecutor().awaitAll();
    }
}
