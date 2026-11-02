package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.snapshot.SealedNbt;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.storage.VersionedChunkStorage;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(VersionedChunkStorage.class)
public abstract class VersionedChunkStorageMixin {
    /**
     * A chunk read while its save is still queued gets the queued NbtCompound itself, and this
     * method writes a "__context" key into it. Vanilla races its own IO thread there; a sealed
     * checkpoint also writes that object into its generation, so it gets a private copy instead.
     */
    // Newer overloads also take a context NBT. Slot 1 is the chunk payload; select the first
    // NBT argument explicitly instead of relying on Mixin's single-candidate discriminator.
    @ModifyVariable(method = "updateChunkNbt", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private NbtCompound anticrash$copySealedChunk(NbtCompound nbt) {
        return SealedNbt.isSealed(nbt) ? nbt.copy() : nbt;
    }
}
