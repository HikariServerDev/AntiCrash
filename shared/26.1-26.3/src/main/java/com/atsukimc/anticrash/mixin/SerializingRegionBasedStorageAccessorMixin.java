package com.atsukimc.anticrash.mixin;

import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * POI storage internals the checkpoint cut needs. PointOfInterestStorage#tick would save the
 * same sections, but it also advances the POI distance tracker, which the frozen cut must not do.
 */
@Mixin(SectionStorage.class)
public interface SerializingRegionBasedStorageAccessorMixin {
    @Accessor("simpleRegionStorage")
    net.minecraft.world.level.chunk.storage.SimpleRegionStorage anticrash$getStorageAccess();


    @Accessor("dirtyChunks")
    LongLinkedOpenHashSet anticrash$getUnsavedElements();

    @Invoker("writeChunk")
    void anticrash$save(ChunkPos pos);
}
