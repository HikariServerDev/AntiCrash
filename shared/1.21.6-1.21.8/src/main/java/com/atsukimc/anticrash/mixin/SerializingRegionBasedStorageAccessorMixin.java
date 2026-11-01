package com.atsukimc.anticrash.mixin;

import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;

import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.storage.SerializingRegionBasedStorage;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * POI storage internals the checkpoint cut needs. PointOfInterestStorage#tick would save the same
 * sections, but it also advances the POI distance tracker, which the frozen cut must not do.
 */
@Mixin(SerializingRegionBasedStorage.class)
public interface SerializingRegionBasedStorageAccessorMixin {
    @Accessor("storageAccess")
    net.minecraft.world.storage.ChunkPosKeyedStorage anticrash$getStorageAccess();

    @Accessor("unsavedElements")
    LongLinkedOpenHashSet anticrash$getUnsavedElements();

    @Invoker("save")
    void anticrash$save(ChunkPos pos);
}
