package com.atsukimc.anticrash.snapshot;

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.nbt.CompoundTag;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.nbt.CompoundTag;
//?}

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.world.level.ChunkPos;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.world.level.ChunkPos;
//?}

import java.nio.file.Path;

/**
 * A chunk, entity or POI write that was queued but not yet on disk at a checkpoint cut.
 *
 * @param directory the absolute region storage directory it belongs to
 * @param nbt the queued data; null deletes the chunk, as in Vanilla
 */
public record PendingRegionWrite(
        Path directory,
        ChunkPos pos,
        //? if (>=1.18 && <=1.21.11) {
        /*CompoundTag nbt
        *///?} else if (>=26.1 && <=26.3) {
        CompoundTag nbt
        //?}
        ) {
    public Path regionFile() {
        return directory.resolve("r." + pos.getRegionX() + "." + pos.getRegionZ() + ".mca");
    }

    public Path externalChunkFile() {

        //? if (>=1.18 && <=1.21.11) {
        /*return directory.resolve("c." + pos.x + "." + pos.z + ".mcc");
        *///?} else if (>=26.1 && <=26.3) {
        return directory.resolve("c." + pos.x() + "." + pos.z() + ".mcc");
        //?}

    }
}
