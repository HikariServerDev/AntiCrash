package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.checkpoint.CutSave;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.chunk.storage.EntityStorage;
*///?}

//? if (>=1.18 && <=1.21.1) {
import com.mojang.datafixers.util.Either;
//?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.server.world.ServerEntityManager;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.entity.EntityPersistentStorage;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.storage.ChunkDataAccess;
//?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.storage.EntityChunkDataAccess;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.entity.PersistentEntitySectionManager;
*///?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

//? if (>=1.18 && <=1.21.11) {
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
            ChunkDataAccess<?> access, boolean sync, Operation<Void> original)
                //?}
                //? if (>=26.1 && <=26.3) {
                /*@Mixin(PersistentEntitySectionManager.class)
                public abstract class ServerEntityManagerMixin {
                    /^*
                     * At a checkpoint cut the entity flush waits only for entity sections that are still loading,
                     * which it must add to the world before saving them. It does not wait for queued writes to
                     * reach the disk, nor fsync: the cut seals the queue and takes the queued writes itself (see
                     * {@link CutSave}).
                     ^/
                    @WrapOperation(
                            method = "saveAll",
                            at =
                                    @At(
                                            value = "INVOKE",
                                            target =
                                                    "Lnet/minecraft/world/level/entity/EntityPersistentStorage;flush(Z)V"))
                    private void anticrash$awaitLoadsOnlyAtCut(
                            EntityPersistentStorage<?> access, boolean sync, Operation<Void> original)
*///?}
            {

        //? if (>=1.18 && <=1.21.11) {
        if (!CutSave.active() || !(access instanceof EntityChunkDataAccess entities))
        //?}
        //? if (>=26.1 && <=26.3) {
        /*if (!CutSave.active() || !(access instanceof EntityStorage entities))
*///?}
        {
            original.call(access, sync);
            return;
        }
        EntityChunkDataAccessAccessorMixin accessor = (EntityChunkDataAccessAccessorMixin) entities;
        // Reads run at foreground priority in submission order, so this no-op finishes after every
        // read queued before it; queued writes run at background priority and are not waited for.

        //? if (>=1.18 && <=1.20.4) {
        ((StorageIoWorkerAccessorMixin) accessor.anticrash$getDataLoadWorker())
                .anticrash$run(() -> Either.left(null))
                .join();
        //?}
        //? if (>=1.20.5 && <=1.21.1) {
        /*((StorageIoWorkerAccessorMixin)
                                ((ChunkPosKeyedStorageAccessor) accessor.anticrash$getStorage())
                                        .anticrash$getWorker())
                        .anticrash$run(() -> Either.left(null))
                        .join();
*///?}
        //? if (>=1.21.2 && <=26.3) {
        /*((StorageIoWorkerAccessorMixin)
                                ((ChunkPosKeyedStorageAccessor) accessor.anticrash$getStorage())
                                        .anticrash$getWorker())
                        .anticrash$run(() -> null)
                        .join();
*///?}

        //? if (>=1.18 && <=1.21.1) {
        accessor.anticrash$getTaskExecutor().awaitAll();
        //?}
        //? if (>=1.21.2 && <=26.3) {
        /*accessor.anticrash$getTaskExecutor().runAll();
*///?}

    }
}
