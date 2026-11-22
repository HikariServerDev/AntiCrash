package com.atsukimc.anticrash.mixin;

//? if <=1.20.4 {
/*import com.mojang.datafixers.util.Either;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
*///?} else {
import net.minecraft.world.level.chunk.status.ChunkStatus;
//?}

import net.minecraft.server.level.ServerChunkCache;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.concurrent.CompletableFuture;

/**
 * Exposes the non-blocking 1.18.2 chunk future path.
 *
 * <p>{@code getChunkFuture} is not a non-blocking API when called from the server thread: its
 * implementation runs the main-thread executor until the returned future is complete. Recovery must
 * keep the future and return to the normal server loop instead. This invoker targets the private
 * {@code getChunkFutureMainThread} method confirmed in the 1.18.2 merged bytecode and never changes
 * the ticket manager's internal state.
 */
@Mixin(ServerChunkCache.class)
public interface ServerChunkManagerAccessorMixin {
    //? if <=1.20.4 {
    /*@Invoker("getChunkFutureMainThread")
    CompletableFuture<Either<LevelChunk, ChunkHolder.ChunkLoadingFailure>> anticrash$getChunkFuture(
            int chunkX, int chunkZ, ChunkStatus status, boolean addTicket);

    *///?} else {
    @Invoker("getChunkFutureMainThread")
    CompletableFuture<
                    net.minecraft.server.level.ChunkResult<
                            net.minecraft.world.level.chunk.ChunkAccess>>
            anticrash$getChunkFuture(int chunkX, int chunkZ, ChunkStatus status, boolean addTicket);

    //?}

    //? if >=1.21.5 {
    @Accessor("ticketStorage")
    net.minecraft.world.level.TicketStorage anticrash$getTicketManager();

    //?}

    //? if <=26.2 {
    /*@Accessor("lastInhabitedUpdate")
    void anticrash$setLastMobSpawningTime(long time);
    *///?}

}
