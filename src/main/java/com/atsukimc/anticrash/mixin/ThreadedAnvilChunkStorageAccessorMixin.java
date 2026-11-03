package com.atsukimc.anticrash.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;

//? if (>=26.1 && <=26.3) {
import net.minecraft.server.level.ChunkHolder;
//?}

//? if (>=1.18 && <=1.20.6) {
/*import it.unimi.dsi.fastutil.longs.LongSet;
*///?}

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.server.level.ChunkHolder;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.server.level.ChunkMap;
//?}

//? if (>=1.18 && <=1.20.6) {
/*import net.minecraft.server.level.ChunkMap;
*///?}
//? if (>=1.21 && <=1.21.11) {
/*import net.minecraft.server.level.ChunkMap;
*///?}

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.world.level.chunk.ChunkAccess;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.world.level.chunk.ChunkAccess;
//?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

//? if (>=1.18 && <=1.20.6) {
/*@Mixin(ChunkMap.class)
public interface ThreadedAnvilChunkStorageAccessorMixin {
    @Accessor("entitiesInLevel")
    LongSet anticrash$getLoadedChunks();

    *///?} else if (>=1.21 && <=1.21.11) {
/*@Mixin(ChunkMap.class)
    public interface ThreadedAnvilChunkStorageAccessorMixin {
*///?}
//? if (>=1.18 && <=1.21.11) {
/*@Accessor("updatingChunkMap")
    Long2ObjectLinkedOpenHashMap<ChunkHolder> anticrash$getCurrentChunkHolders();

    *///?} else if (>=26.1 && <=26.3) {
@Mixin(ChunkMap.class)
public interface ThreadedAnvilChunkStorageAccessorMixin {
    @Accessor("updatingChunkMap")
    Long2ObjectLinkedOpenHashMap<ChunkHolder> anticrash$getCurrentChunkHolders();

    //?}

    /**
     * Holders already taken out of the live map and waiting for their unload task. Their chunk may
     * still hold changes: the unload task is what saves it, one tick or more later.
     */

    //? if (>=1.18 && <=1.21.11) {
    /*@Accessor("pendingUnloads")
    Long2ObjectLinkedOpenHashMap<ChunkHolder> anticrash$getChunksToUnload();

    *///?} else if (>=26.1 && <=26.3) {
    @Accessor("pendingUnloads")
    Long2ObjectLinkedOpenHashMap<ChunkHolder> anticrash$getChunksToUnload();

    //?}

    /**
     * Serializes one already-loaded dirty chunk without joining every ChunkHolder future.
     * CheckpointManager calls this in small, IO-drained batches while simulation is frozen.
     */

    //? if (>=1.18 && <=1.21.11) {
    /*@Invoker("save")
    boolean anticrash$saveChunk(ChunkAccess chunk);
    *///?} else if (>=26.1 && <=26.3) {
    @Invoker("save")
    boolean anticrash$saveChunk(ChunkAccess chunk);
    //?}

}
