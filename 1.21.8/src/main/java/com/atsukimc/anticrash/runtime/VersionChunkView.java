package com.atsukimc.anticrash.runtime;

import net.minecraft.server.world.ChunkHolder;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.WrapperProtoChunk;

/** Read-only, non-loading view. ChunkHolder#getWorldChunk exposes ticking, not all loaded FULL. */
public final class VersionChunkView {
    private VersionChunkView() {}
    public static WorldChunk worldChunk(ChunkHolder holder) {
        var latest = holder.getLatest();
        if (latest instanceof WrapperProtoChunk wrapper) latest = wrapper.getWrappedChunk();
        return latest instanceof WorldChunk chunk ? chunk : null;
    }
}
