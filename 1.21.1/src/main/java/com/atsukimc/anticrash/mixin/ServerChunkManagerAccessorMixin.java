package com.atsukimc.anticrash.mixin;

import com.mojang.datafixers.util.Either;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.concurrent.CompletableFuture;

/**
 * Exposes the non-blocking 1.18.2 chunk future path.
 *
 * <p>{@code getChunkFutureSyncOnMainThread} is not a non-blocking API when called from the
 * server thread: its implementation runs the main-thread executor until the returned future is
 * complete. Recovery must keep the future and return to the normal server loop instead. This
 * invoker targets the private {@code getChunkFuture} method confirmed in the 1.18.2 merged
 * bytecode and never changes the ticket manager's internal state.</p>
 */
@Mixin(ServerChunkManager.class)
public interface ServerChunkManagerAccessorMixin {
    @Invoker("getChunkFuture")
    CompletableFuture<net.minecraft.server.world.OptionalChunk<net.minecraft.world.chunk.Chunk>> anticrash$getChunkFuture(
            int chunkX, int chunkZ, ChunkStatus status, boolean addTicket);

    @org.spongepowered.asm.mixin.gen.Accessor("lastMobSpawningTime")
    void anticrash$setLastMobSpawningTime(long time);
}
