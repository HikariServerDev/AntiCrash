package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.runtime.ExtensionCarrier;
import com.atsukimc.anticrash.runtime.RuntimeExtensionNbt;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

//? if (>=1.18 && <=1.21.1) {
import net.minecraft.nbt.NbtCompound;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.server.level.ServerLevel;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.server.world.ServerWorld;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.chunk.ChunkAccess;
*///?}

//? if (>=1.18 && <=1.21.1) {
import net.minecraft.world.ChunkSerializer;
//?}
//? if (>=1.21.2 && <=1.21.11) {
/*import net.minecraft.world.chunk.Chunk;
*///?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.chunk.LevelChunk;
*///?}

//? if (>=1.18 && <=1.21.1) {
import net.minecraft.world.chunk.Chunk;
//?}
//? if (>=1.21.2 && <=1.21.11) {
/*import net.minecraft.world.chunk.SerializedChunk;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.chunk.WorldChunk;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.chunk.storage.SerializableChunkData;
*///?}

import org.spongepowered.asm.mixin.Mixin;

/**
 * Tells block entities whether their chunk is saved while it stays loaded; see RuntimeExtensionNbt.
 */
//? if (>=1.18 && <=1.21.1) {
@Mixin(ChunkSerializer.class)
public abstract class ChunkSerializerMixin {
    /**
     * A wrap rather than a HEAD/RETURN pair: the scope must end when serialisation throws, or the
     * next unrelated writeNbt on this thread would be taken for a save of a loaded chunk.
     */
    @WrapMethod(method = "serialize")
    private static NbtCompound anticrash$scopeChunkSave(
            ServerWorld world, Chunk chunk, Operation<NbtCompound> original)
                //?}
                //? if (>=1.21.2 && <=1.21.11) {
                /*@Mixin(SerializedChunk.class)
                public abstract class ChunkSerializerMixin {
                    /^*
                     * A wrap rather than a HEAD/RETURN pair: the scope must end when serialisation throws, or the
                     * next unrelated writeNbt on this thread would be taken for a save of a loaded chunk.
                     ^/
                    @WrapMethod(method = "fromChunk")
                    private static SerializedChunk anticrash$scopeChunkSave(
                            ServerWorld world, Chunk chunk, Operation<SerializedChunk> original)
*///?}
                //? if (>=26.1 && <=26.3) {
                /*@Mixin(SerializableChunkData.class)
                public abstract class ChunkSerializerMixin {
                    /^*
                     * A wrap rather than a HEAD/RETURN pair: the scope must end when serialisation throws, or the
                     * next unrelated writeNbt on this thread would be taken for a save of a loaded chunk.
                     ^/
                    @WrapMethod(method = "copyOf")
                    private static SerializableChunkData anticrash$scopeChunkSave(
                            ServerLevel world, ChunkAccess chunk, Operation<SerializableChunkData> original)
*///?}
            {

        //? if (>=1.18 && <=1.21.11) {
        WorldChunk worldChunk = chunk instanceof WorldChunk loaded ? loaded : null;
        //?}
        //? if (>=26.1 && <=26.3) {
        /*LevelChunk worldChunk = chunk instanceof LevelChunk loaded ? loaded : null;
*///?}

        boolean staysLoaded =
                worldChunk != null
                        && ((WorldChunkAccessorMixin) worldChunk).anticrash$isLoadedToWorld();
        RuntimeExtensionNbt.loadedChunkSave(staysLoaded);
        try {

            //? if (>=1.18 && <=1.21.1) {
            NbtCompound nbt = original.call(world, chunk);
            //?}
            //? if (>=1.21.2 && <=1.21.11) {
            /*SerializedChunk nbt = original.call(world, chunk);
*///?}
            //? if (>=26.1 && <=26.3) {
            /*SerializableChunkData nbt = original.call(world, chunk);
*///?}

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
