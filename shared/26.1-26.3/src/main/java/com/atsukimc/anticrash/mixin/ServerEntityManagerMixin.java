package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.checkpoint.CutSave;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.entity.EntityPersistentStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PersistentEntitySectionManager.class)
public abstract class ServerEntityManagerMixin {
    /**
     * At a checkpoint cut the entity flush waits only for entity sections that are still loading,
     * which it must add to the world before saving them. It does not wait for queued writes to
     * reach the disk, nor fsync: the cut seals the queue and takes the queued writes itself (see
     * {@link CutSave}).
     */
    @WrapOperation(
            method = "saveAll",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/entity/EntityPersistentStorage;flush(Z)V"))
    private void anticrash$awaitLoadsOnlyAtCut(
            EntityPersistentStorage<?> access, boolean sync, Operation<Void> original) {
        if (!CutSave.active() || !(access instanceof EntityStorage entities)) {
            original.call(access, sync);
            return;
        }
        EntityChunkDataAccessAccessorMixin accessor = (EntityChunkDataAccessAccessorMixin) entities;
        // Reads run at foreground priority in submission order, so this no-op finishes after every
        // read queued before it; queued writes run at background priority and are not waited for.
        ((StorageIoWorkerAccessorMixin)
                        ((ChunkPosKeyedStorageAccessor) accessor.anticrash$getStorage())
                                .anticrash$getWorker())
                .anticrash$run(() -> null)
                .join();
        accessor.anticrash$getTaskExecutor().runAll();
    }
}
