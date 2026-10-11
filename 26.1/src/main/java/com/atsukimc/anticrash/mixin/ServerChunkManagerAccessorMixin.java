package com.atsukimc.anticrash.mixin;

import com.mojang.datafixers.util.Either;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Exposes the non-blocking 1.18.2 chunk future path.
 *
 * <p>{@code getChunkFutureSyncOnMainThread} is not a non-blocking API when called from the
 * server thread: its implementation runs the main-thread executor until the returned future is
 * complete. Recovery must keep the future and return to the normal server loop instead. This
 * invoker targets the private {@code getChunkFuture} method confirmed in the 1.18.2 merged
 * bytecode and never changes the ticket manager's internal state.</p>
 */
@Mixin(ServerChunkCache.class)
public interface ServerChunkManagerAccessorMixin {
    @org.spongepowered.asm.mixin.gen.Accessor("ticketStorage")
    net.minecraft.world.level.TicketStorage anticrash$getTicketManager();

    @Invoker("getChunkFutureMainThread")
    CompletableFuture<net.minecraft.server.level.ChunkResult<net.minecraft.world.level.chunk.ChunkAccess>> anticrash$getChunkFuture(
            int chunkX, int chunkZ, ChunkStatus status, boolean addTicket);

    @org.spongepowered.asm.mixin.gen.Accessor("lastInhabitedUpdate")
    void anticrash$setLastMobSpawningTime(long time);
}
