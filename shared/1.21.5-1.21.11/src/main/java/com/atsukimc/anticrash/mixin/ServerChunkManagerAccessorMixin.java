package com.atsukimc.anticrash.mixin;

import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.world.chunk.ChunkStatus;

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
@Mixin(ServerChunkManager.class)
public interface ServerChunkManagerAccessorMixin {
    @org.spongepowered.asm.mixin.gen.Accessor("ticketManager")
    net.minecraft.server.world.ChunkTicketManager anticrash$getTicketManager();

    @Invoker("getChunkFuture")
    CompletableFuture<net.minecraft.server.world.OptionalChunk<net.minecraft.world.chunk.Chunk>>
            anticrash$getChunkFuture(int chunkX, int chunkZ, ChunkStatus status, boolean addTicket);

    @org.spongepowered.asm.mixin.gen.Accessor("lastTickTime")
    void anticrash$setLastMobSpawningTime(long time);
}
