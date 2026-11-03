package com.atsukimc.anticrash.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;

//? if (>=26.1 && <=26.3) {
/*import net.minecraft.server.level.ChunkHolder;
*///?}

//? if (>=1.18 && <=1.20.6) {
import it.unimi.dsi.fastutil.longs.LongSet;
//?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.server.world.ChunkHolder;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.server.level.ChunkMap;
*///?}

//? if (>=1.18 && <=1.20.6) {
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
//?}
//? if (>=1.21 && <=1.21.11) {
/*import net.minecraft.server.world.ServerChunkLoadingManager;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.chunk.Chunk;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.chunk.ChunkAccess;
*///?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

//? if (>=1.18 && <=1.20.6) {
@Mixin(ThreadedAnvilChunkStorage.class)
public interface ThreadedAnvilChunkStorageAccessorMixin {
    @Accessor("loadedChunks")
    LongSet anticrash$getLoadedChunks();

    //?}

    //? if (>=1.21 && <=1.21.11) {
    /*@Mixin(ServerChunkLoadingManager.class)
    public interface ThreadedAnvilChunkStorageAccessorMixin {
*///?}
    //? if (>=1.18 && <=1.21.11) {
    @Accessor("currentChunkHolders")
    Long2ObjectLinkedOpenHashMap<ChunkHolder> anticrash$getCurrentChunkHolders();

    //?}
    //? if (>=26.1 && <=26.3) {
    /*@Mixin(ChunkMap.class)
    public interface ThreadedAnvilChunkStorageAccessorMixin {
        @Accessor("updatingChunkMap")
        Long2ObjectLinkedOpenHashMap<ChunkHolder> anticrash$getCurrentChunkHolders();
*///?}

    /**
     * Holders already taken out of the live map and waiting for their unload task. Their chunk may
     * still hold changes: the unload task is what saves it, one tick or more later.
     */

    //? if (>=1.18 && <=1.21.11) {
    @Accessor("chunksToUnload")
    Long2ObjectLinkedOpenHashMap<ChunkHolder> anticrash$getChunksToUnload();

    //?}
    //? if (>=26.1 && <=26.3) {
    /*@Accessor("pendingUnloads")
        Long2ObjectLinkedOpenHashMap<ChunkHolder> anticrash$getChunksToUnload();
*///?}

    /**
     * Serializes one already-loaded dirty chunk without joining every ChunkHolder future.
     * CheckpointManager calls this in small, IO-drained batches while simulation is frozen.
     */

    //? if (>=1.18 && <=1.21.11) {
    @Invoker("save")
    boolean anticrash$saveChunk(Chunk chunk);
    //?}
    //? if (>=26.1 && <=26.3) {
    /*@Invoker("save")
        boolean anticrash$saveChunk(ChunkAccess chunk);
*///?}

}
