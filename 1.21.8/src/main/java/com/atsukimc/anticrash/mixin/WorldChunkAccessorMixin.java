package com.atsukimc.anticrash.mixin;

import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WorldChunk.class)
public interface WorldChunkAccessorMixin {
    /** False once ServerChunkLoadingManager has started unloading the chunk. */
    @Accessor("loadedToWorld")
    boolean anticrash$isLoadedToWorld();
}
