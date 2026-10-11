package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.snapshot.EntityChunkWriteDeduplicator;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.storage.EntityChunkDataAccess;
import net.minecraft.world.storage.StorageIoWorker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.CompletableFuture;

/** Skips rewriting an unchanged entity chunk; see {@link EntityChunkWriteDeduplicator}. */
@Mixin(EntityChunkDataAccess.class)
public abstract class EntityChunkDataAccessMixin {
    @Unique
    private final EntityChunkWriteDeduplicator anticrash$deduplicator = new EntityChunkWriteDeduplicator();

    /**
     * Entity runtime state is added only to data that goes to the entity's chunk on disk. A wrap,
     * so the scope also ends when an entity fails to save.
     */
    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "writeChunkData")
    private void anticrash$scopeEntitySave(net.minecraft.world.storage.ChunkDataList<?> dataList,
                                           Operation<Void> original) {
        com.atsukimc.anticrash.runtime.RuntimeExtensionNbt.entitySave(true);
        try {
            original.call(dataList);
        } finally {
            com.atsukimc.anticrash.runtime.RuntimeExtensionNbt.entitySave(false);
        }
    }

    @WrapOperation(method = "writeChunkData", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/storage/StorageIoWorker;setResult(Lnet/minecraft/util/math/ChunkPos;Lnet/minecraft/nbt/NbtCompound;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<Void> anticrash$skipUnchangedEntityChunk(StorageIoWorker worker, ChunkPos pos,
                                                                      CompoundTag nbt,
                                                                      Operation<CompletableFuture<Void>> original) {
        long chunk = pos.toLong();
        if (nbt == null) {
            anticrash$deduplicator.forget(chunk);
            return original.call(worker, pos, nbt);
        }
        byte[] digest = anticrash$deduplicator.digestIfChanged(chunk, nbt);
        if (digest == null) return CompletableFuture.completedFuture(null);
        anticrash$deduplicator.submitted(chunk, digest);
        CompletableFuture<Void> result = original.call(worker, pos, nbt);
        result.whenComplete((ignored, failure) -> {
            if (failure != null) anticrash$deduplicator.forget(chunk);
        });
        return result;
    }
}
