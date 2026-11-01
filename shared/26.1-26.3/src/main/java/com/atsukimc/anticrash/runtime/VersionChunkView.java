package com.atsukimc.anticrash.runtime;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;

/** Read-only, non-loading view. ChunkHolder#getWorldChunk exposes ticking, not all loaded FULL. */
public final class VersionChunkView {
    private VersionChunkView() {}

    public static LevelChunk worldChunk(ChunkHolder holder) {
        var latest = holder.getLatestChunk();
        if (latest instanceof ImposterProtoChunk wrapper) latest = wrapper.getWrapped();
        return latest instanceof LevelChunk chunk ? chunk : null;
    }
}
