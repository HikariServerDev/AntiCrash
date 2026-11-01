package com.atsukimc.anticrash.snapshot;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.math.ChunkPos;

import java.nio.file.Path;

/**
 * A chunk, entity or POI write that was queued but not yet on disk at a checkpoint cut.
 *
 * @param directory the absolute region storage directory it belongs to
 * @param nbt       the queued data; null deletes the chunk, as in Vanilla
 */
public record PendingRegionWrite(Path directory, ChunkPos pos, CompoundTag nbt) {
    public Path regionFile() {
        return directory.resolve("r." + pos.getRegionX() + "." + pos.getRegionZ() + ".mca");
    }

    public Path externalChunkFile() {
        return directory.resolve("c." + pos.x + "." + pos.z + ".mcc");
    }
}
