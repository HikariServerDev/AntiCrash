package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.snapshot.SealedNbt;

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.nbt.CompoundTag;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.nbt.CompoundTag;
*///?}

//? if (>=1.18 && <=1.21.11) {
//? if (>=1.18 && <=1.21.10) {
import net.minecraft.world.level.chunk.storage.ChunkStorage;
//?}
//? if 1.21.11 {
/*import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
*///?}
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
*///?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

//? if (>=1.18 && <=1.21.11) {
//? if (>=1.18 && <=1.21.10) {
@Mixin(ChunkStorage.class)
//?}
//? if 1.21.11 {
/*@Mixin(SimpleRegionStorage.class)
*///?}
public abstract class VersionedChunkStorageMixin {
    /**
     * A chunk read while its save is still queued gets the queued CompoundTag itself, and this
     * method writes a "__context" key into it. Vanilla races its own IO thread there; a sealed
     * checkpoint also writes that object into its generation, so it gets a private copy instead.
     */
    // Newer overloads also take a context NBT. Slot 1 is the chunk payload; select the first
    // NBT argument explicitly instead of relying on Mixin's single-candidate discriminator.
    @ModifyVariable(method = "upgradeChunkTag", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private CompoundTag anticrash$copySealedChunk(CompoundTag nbt)
                //?}
                //? if (>=26.1 && <=26.3) {
                /*@Mixin(SimpleRegionStorage.class)
                public abstract class VersionedChunkStorageMixin {
                    /^*
                     * A chunk read while its save is still queued gets the queued CompoundTag itself, and this
                     * method writes a "__context" key into it. Vanilla races its own IO thread there; a sealed
                     * checkpoint also writes that object into its generation, so it gets a private copy instead.
                     ^/
                    // The first CompoundTag is chunkTag (slot 1); a later tag is data-fix context, not data.
                    @ModifyVariable(method = "upgradeChunkTag", at = @At("HEAD"), argsOnly = true, ordinal = 0)
                    private CompoundTag anticrash$copySealedChunk(CompoundTag nbt)
*///?}
            {
        return SealedNbt.isSealed(nbt) ? nbt.copy() : nbt;
    }
}
