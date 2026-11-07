package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.runtime.ExtensionCarrier;
import com.atsukimc.anticrash.runtime.RuntimeExtensionNbt;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

//? if (>=1.18 && <=1.21.1) {
/*import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.nbt.CompoundTag;
*///?} else {
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
//?}

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;

import org.spongepowered.asm.mixin.Mixin;

/**
 * Tells block entities whether their chunk is saved while it stays loaded; see RuntimeExtensionNbt.
 */
//? if (>=1.18 && <=1.21.1) {
/*@Mixin(ChunkSerializer.class)
public abstract class ChunkSerializerMixin {
    /^*
     * A wrap rather than a HEAD/RETURN pair: the scope must end when serialisation throws, or the
     * next unrelated saveAdditional on this thread would be taken for a save of a loaded chunk.
     ^/
    @WrapMethod(method = "write")
    private static CompoundTag anticrash$scopeChunkSave(
            ServerLevel world, ChunkAccess chunk, Operation<CompoundTag> original) {
*///?} else {
@Mixin(SerializableChunkData.class)
public abstract class ChunkSerializerMixin {
    /**
     * A wrap rather than a HEAD/RETURN pair: the scope must end when serialisation throws, or the
     * next unrelated saveAdditional on this thread would be taken for a save of a loaded chunk.
     */
    @WrapMethod(method = "copyOf")
    private static SerializableChunkData anticrash$scopeChunkSave(
            ServerLevel world, ChunkAccess chunk, Operation<SerializableChunkData> original) {
//?}

        LevelChunk worldChunk = chunk instanceof LevelChunk loaded ? loaded : null;
        boolean staysLoaded =
                worldChunk != null
                        && ((WorldChunkAccessorMixin) worldChunk).anticrash$isLoadedToWorld();
        RuntimeExtensionNbt.loadedChunkSave(staysLoaded);
        try {

            //? if (>=1.18 && <=1.21.1) {
            /*CompoundTag nbt = original.call(world, chunk);
            *///?} else {
            SerializableChunkData nbt = original.call(world, chunk);
            //?}

            // Remembered so that an unload rewrites the chunk without the extension even when
            // nothing else changed; see ThreadedAnvilChunkStorageEntityLifecycleMixin.
            if (worldChunk != null) {
                ((ExtensionCarrier) worldChunk)
                        .anticrash$setExtensionOnDisk(RuntimeExtensionNbt.extensionWritten());
            }
            return nbt;
        } finally {
            RuntimeExtensionNbt.loadedChunkSave(false);
        }
    }
}
