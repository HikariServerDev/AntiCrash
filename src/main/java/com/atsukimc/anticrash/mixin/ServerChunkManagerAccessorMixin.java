package com.atsukimc.anticrash.mixin;

//? if (>=1.18 && <=1.20.4) {
import com.mojang.datafixers.util.Either;
//?}

//? if (>=1.18 && <=1.20.4) {
import net.minecraft.server.world.ChunkHolder;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.server.level.ServerChunkCache;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.server.world.ServerChunkManager;
//?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.chunk.ChunkStatus;
//?}

//? if (>=1.18 && <=1.20.4) {
import net.minecraft.world.chunk.WorldChunk;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.chunk.status.ChunkStatus;
*///?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.concurrent.CompletableFuture;

/**
 * Exposes the non-blocking 1.18.2 chunk future path.
 *
 * <p>{@code getChunkFutureSyncOnMainThread} is not a non-blocking API when called from the server
 * thread: its implementation runs the main-thread executor until the returned future is complete.
 * Recovery must keep the future and return to the normal server loop instead. This invoker targets
 * the private {@code getChunkFuture} method confirmed in the 1.18.2 merged bytecode and never
 * changes the ticket manager's internal state.
 */
//? if (>=1.18 && <=1.21.11) {
@Mixin(ServerChunkManager.class)
public interface ServerChunkManagerAccessorMixin {

    //?}
    //? if (>=1.18 && <=1.20.4) {
    @Invoker("getChunkFuture")
    CompletableFuture<Either<WorldChunk, ChunkHolder.Unloaded>> anticrash$getChunkFuture(
            int chunkX, int chunkZ, ChunkStatus status, boolean addTicket);

    //?}
    //? if (>=1.21.5 && <=1.21.11) {
    /*@org.spongepowered.asm.mixin.gen.Accessor("ticketManager")
        net.minecraft.server.world.ChunkTicketManager anticrash$getTicketManager();
*///?}
    //? if (>=26.1 && <=26.3) {
    /*@Mixin(ServerChunkCache.class)
    public interface ServerChunkManagerAccessorMixin {
        @org.spongepowered.asm.mixin.gen.Accessor("ticketStorage")
        net.minecraft.world.level.TicketStorage anticrash$getTicketManager();
*///?}

    //? if (>=1.20.5 && <=1.21.11) {
    /*@Invoker("getChunkFuture")
        CompletableFuture<net.minecraft.server.world.OptionalChunk<net.minecraft.world.chunk.Chunk>>
                anticrash$getChunkFuture(int chunkX, int chunkZ, ChunkStatus status, boolean addTicket);
*///?}
    //? if (>=26.1 && <=26.3) {
    /*@Invoker("getChunkFutureMainThread")
        CompletableFuture<
                        net.minecraft.server.level.ChunkResult<
                                net.minecraft.world.level.chunk.ChunkAccess>>
                anticrash$getChunkFuture(int chunkX, int chunkZ, ChunkStatus status, boolean addTicket);
*///?}

    //? if (>=1.18 && <=1.21.1) {
    @org.spongepowered.asm.mixin.gen.Accessor("lastMobSpawningTime")
    void anticrash$setLastMobSpawningTime(long time);
    //?}
    //? if (>=1.21.2 && <=1.21.11) {
    /*@org.spongepowered.asm.mixin.gen.Accessor("lastTickTime")
        void anticrash$setLastMobSpawningTime(long time);
*///?}
    //? if (>=26.1 && <=26.2) {
    /*@org.spongepowered.asm.mixin.gen.Accessor("lastInhabitedUpdate")
        void anticrash$setLastMobSpawningTime(long time);
*///?}

}
