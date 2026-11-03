package com.atsukimc.anticrash.mixin;

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.level.chunk.LevelChunk;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.chunk.LevelChunk;
*///?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

//? if (>=1.18 && <=1.20.6) {
@Mixin(LevelChunk.class)
public interface WorldChunkAccessorMixin {
    /** False once ChunkMap has started unloading the chunk. */

    //?}
    //? if (>=1.21 && <=1.21.11) {
    /*@Mixin(LevelChunk.class)
    public interface WorldChunkAccessorMixin {
        /^* False once ChunkMap has started unloading the chunk. ^/
*///?}
    //? if (>=1.18 && <=1.21.11) {
    @Accessor("loaded")
    boolean anticrash$isLoadedToWorld();
    //?}
    //? if (>=26.1 && <=26.3) {
    /*@Mixin(LevelChunk.class)
    public interface WorldChunkAccessorMixin {
        /^* False once ChunkMap has started unloading the chunk. ^/
        @Accessor("loaded")
        boolean anticrash$isLoadedToWorld();
*///?}

}
