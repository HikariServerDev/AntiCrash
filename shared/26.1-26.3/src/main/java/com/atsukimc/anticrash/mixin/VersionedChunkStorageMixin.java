package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.snapshot.SealedNbt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(SimpleRegionStorage.class)
public abstract class VersionedChunkStorageMixin {
    /**
     * A chunk read while its save is still queued gets the queued NbtCompound itself, and this
     * method writes a "__context" key into it. Vanilla races its own IO thread there; a sealed
     * checkpoint also writes that object into its generation, so it gets a private copy instead.
     */
    @ModifyVariable(method = "upgradeChunkTag", at = @At("HEAD"), argsOnly = true)
    private CompoundTag anticrash$copySealedChunk(CompoundTag nbt) {
        return SealedNbt.isSealed(nbt) ? nbt.copy() : nbt;
    }
}
