package com.atsukimc.anticrash.mixin;

//? if <=1.21.10 {
/*import net.minecraft.world.level.chunk.storage.ChunkStorage;
*///?} else {
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
//?}

import com.atsukimc.anticrash.snapshot.SealedNbt;

import net.minecraft.nbt.CompoundTag;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

//? if <=1.21.10 {
/*@Mixin(ChunkStorage.class)
public abstract class VersionedChunkStorageMixin {
*///?} else {
@Mixin(SimpleRegionStorage.class)
public abstract class VersionedChunkStorageMixin {
//?}
    /**
     * A chunk read while its save is still queued gets the queued CompoundTag itself, and this
     * method writes a "__context" key into it. Vanilla races its own IO thread there; a sealed
     * checkpoint also writes that object into its generation, so it gets a private copy instead.
     */
    // The first CompoundTag is the chunk payload (slot 1); newer overloads add a data-fix context
    // tag after it. Select it explicitly instead of relying on Mixin's single-candidate
    // discriminator.
    @ModifyVariable(method = "upgradeChunkTag", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private CompoundTag anticrash$copySealedChunk(CompoundTag nbt) {
        return SealedNbt.isSealed(nbt) ? nbt.copy() : nbt;
    }
}
