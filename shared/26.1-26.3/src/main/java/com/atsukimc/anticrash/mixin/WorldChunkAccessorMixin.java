package com.atsukimc.anticrash.mixin;

import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelChunk.class)
public interface WorldChunkAccessorMixin {
    /** False once ServerChunkLoadingManager has started unloading the chunk. */
    @Accessor("loaded")
    boolean anticrash$isLoadedToWorld();
}
