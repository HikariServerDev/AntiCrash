package com.atsukimc.anticrash.mixin;

//? if (>=1.18 && <=1.20.6) {
/*import it.unimi.dsi.fastutil.longs.LongSet;
*///?}

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.chunk.ChunkAccess;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

//? if (>=1.18 && <=1.20.6) {
/*@Mixin(ChunkMap.class)
public interface ThreadedAnvilChunkStorageAccessorMixin {
    @Accessor("entitiesInLevel")
    LongSet anticrash$getLoadedChunks();

*///?}

//? if (>=1.21 && <=1.21.11) {
/*@Mixin(ChunkMap.class)
public interface ThreadedAnvilChunkStorageAccessorMixin {
*///?}
    //? if (>=1.18 && <=1.21.11) {
    /*@Accessor("updatingChunkMap")
    Long2ObjectLinkedOpenHashMap<ChunkHolder> anticrash$getCurrentChunkHolders();

    *///?}
//? if (>=26.1 && <=26.3) {
@Mixin(ChunkMap.class)
public interface ThreadedAnvilChunkStorageAccessorMixin {
    @Accessor("updatingChunkMap")
    Long2ObjectLinkedOpenHashMap<ChunkHolder> anticrash$getCurrentChunkHolders();

//?}

    /**
     * Holders already taken out of the live map and waiting for their unload task. Their chunk may
     * still hold changes: the unload task is what saves it, one tick or more later.
     */
    @Accessor("pendingUnloads")
    Long2ObjectLinkedOpenHashMap<ChunkHolder> anticrash$getChunksToUnload();

    /**
     * Serializes one already-loaded dirty chunk without joining every ChunkHolder future.
     * CheckpointManager calls this in small, IO-drained batches while simulation is frozen.
     */
    @Invoker("save")
    boolean anticrash$saveChunk(ChunkAccess chunk);
}
