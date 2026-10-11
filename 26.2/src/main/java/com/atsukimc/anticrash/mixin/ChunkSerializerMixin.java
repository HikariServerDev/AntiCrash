package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.runtime.ExtensionCarrier;
import com.atsukimc.anticrash.runtime.RuntimeExtensionNbt;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import org.spongepowered.asm.mixin.Mixin;

/** Tells block entities whether their chunk is saved while it stays loaded; see RuntimeExtensionNbt. */
@Mixin(SerializableChunkData.class)
public abstract class ChunkSerializerMixin {
    /**
     * A wrap rather than a HEAD/RETURN pair: the scope must end when serialisation throws, or the
     * next unrelated writeNbt on this thread would be taken for a save of a loaded chunk.
     */
    @WrapMethod(method = "copyOf")
    private static SerializableChunkData anticrash$scopeChunkSave(ServerLevel world, ChunkAccess chunk,
                                                        Operation<SerializableChunkData> original) {
        LevelChunk worldChunk = chunk instanceof LevelChunk loaded ? loaded : null;
        boolean staysLoaded = worldChunk != null
                && ((WorldChunkAccessorMixin) worldChunk).anticrash$isLoadedToWorld();
        RuntimeExtensionNbt.loadedChunkSave(staysLoaded);
        try {
            SerializableChunkData nbt = original.call(world, chunk);
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
