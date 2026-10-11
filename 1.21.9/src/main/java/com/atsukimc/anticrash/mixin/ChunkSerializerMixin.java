package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.runtime.ExtensionCarrier;
import com.atsukimc.anticrash.runtime.RuntimeExtensionNbt;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.chunk.SerializedChunk;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;

/** Tells block entities whether their chunk is saved while it stays loaded; see RuntimeExtensionNbt. */
@Mixin(SerializedChunk.class)
public abstract class ChunkSerializerMixin {
    /**
     * A wrap rather than a HEAD/RETURN pair: the scope must end when serialisation throws, or the
     * next unrelated writeNbt on this thread would be taken for a save of a loaded chunk.
     */
    @WrapMethod(method = "fromChunk")
    private static SerializedChunk anticrash$scopeChunkSave(ServerWorld world, Chunk chunk,
                                                        Operation<SerializedChunk> original) {
        WorldChunk worldChunk = chunk instanceof WorldChunk loaded ? loaded : null;
        boolean staysLoaded = worldChunk != null
                && ((WorldChunkAccessorMixin) worldChunk).anticrash$isLoadedToWorld();
        RuntimeExtensionNbt.loadedChunkSave(staysLoaded);
        try {
            SerializedChunk nbt = original.call(world, chunk);
            // Remembered so that an unload rewrites the chunk without the extension even when
            // nothing else changed; see ThreadedAnvilChunkStorageEntityLifecycleMixin.
            if (worldChunk != null) {
                ((ExtensionCarrier) worldChunk).anticrash$setExtensionOnDisk(RuntimeExtensionNbt.extensionWritten());
            }
            return nbt;
        } finally {
            RuntimeExtensionNbt.loadedChunkSave(false);
        }
    }
}
