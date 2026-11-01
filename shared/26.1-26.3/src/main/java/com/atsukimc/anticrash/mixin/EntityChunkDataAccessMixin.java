package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.snapshot.EntityChunkWriteDeduplicator;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.CompletableFuture;

/** Skips rewriting an unchanged entity chunk; see {@link EntityChunkWriteDeduplicator}. */
@Mixin(EntityStorage.class)
public abstract class EntityChunkDataAccessMixin {
    @Unique
    private final EntityChunkWriteDeduplicator anticrash$deduplicator =
            new EntityChunkWriteDeduplicator();

    /**
     * Entity runtime state is added only to data that goes to the entity's chunk on disk. A wrap,
     * so the scope also ends when an entity fails to save.
     */
    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "storeEntities")
    private void anticrash$scopeEntitySave(
            net.minecraft.world.level.entity.ChunkEntities<?> dataList, Operation<Void> original) {
        com.atsukimc.anticrash.runtime.RuntimeExtensionNbt.entitySave(true);
        try {
            original.call(dataList);
        } finally {
            com.atsukimc.anticrash.runtime.RuntimeExtensionNbt.entitySave(false);
        }
    }

    @WrapOperation(
            method = "storeEntities",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/chunk/storage/SimpleRegionStorage;write(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/nbt/CompoundTag;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<Void> anticrash$skipUnchangedEntityChunk(
            SimpleRegionStorage worker,
            ChunkPos pos,
            CompoundTag nbt,
            Operation<CompletableFuture<Void>> original) {
        long chunk = pos.pack();
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

    // The empty-entity path now writes NULL_NBT_SUPPLIER. Forget its last digest so a later
    // identical entity payload cannot be incorrectly skipped after the chunk was deleted.
    @WrapOperation(
            method = "storeEntities",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/chunk/storage/SimpleRegionStorage;write(Lnet/minecraft/world/level/ChunkPos;Ljava/util/function/Supplier;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<Void> anticrash$forgetDeletedEntityChunk(
            SimpleRegionStorage storage,
            ChunkPos pos,
            java.util.function.Supplier<CompoundTag> value,
            Operation<CompletableFuture<Void>> original) {
        anticrash$deduplicator.forget(pos.pack());
        return original.call(storage, pos, value);
    }
}
