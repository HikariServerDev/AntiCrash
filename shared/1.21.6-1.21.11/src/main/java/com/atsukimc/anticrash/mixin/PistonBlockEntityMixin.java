package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.runtime.RuntimeExtensionNbt;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.PistonBlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PistonBlockEntity.class)
public abstract class PistonBlockEntityMixin {
    @Shadow private float progress;
    @Shadow private float lastProgress;
    @Shadow private long savedWorldTime;

    /**
     * Vanilla advances piston progress without notifying the owning chunk that its block entity
     * data changed. Keep ordinary saves and AntiCrash checkpoints from retaining stale progress.
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private static void anticrash$markChunkDirty(
            World world,
            BlockPos pos,
            BlockState state,
            PistonBlockEntity piston,
            CallbackInfo callbackInfo) {
        // World#markDirty only sets the loaded chunk's needsSaving flag. Calling
        // BlockEntity#markDirty here would also run comparator updates on every piston tick.
        world.markDirty(pos);
    }

    /**
     * Vanilla saves {@code lastProgress} under "progress" and loads it into both fields, so a
     * piston that was half way (progress 0.5, lastProgress 0.0) or about to finish (1.0, 0.5)
     * restarts half a step behind and arrives one tick late. That one tick stops every flying
     * machine caught mid-move. {@code lastProgress} stays Vanilla's; only the real progress and the
     * tick it was last advanced in ({@code savedWorldTime}, which PistonBlock compares with the
     * world time when a piston is retracted again) are added.
     */
    @Inject(method = "writeData", at = @At("TAIL"))
    private void anticrash$saveExactProgress(
            net.minecraft.storage.WriteView nbt, CallbackInfo callbackInfo) {
        if (!RuntimeExtensionNbt.savingLoadedChunk()) return;
        RuntimeExtensionNbt.write(
                nbt,
                extension -> {
                    extension.putFloat("Progress", progress);
                    extension.putLong("SavedWorldTime", savedWorldTime);
                });
    }

    @Inject(method = "readData", at = @At("TAIL"))
    private void anticrash$loadExactProgress(
            net.minecraft.storage.ReadView nbt, CallbackInfo callbackInfo) {
        NbtCompound extension = RuntimeExtensionNbt.read(nbt);
        if (extension == null
                || !com.atsukimc.anticrash.runtime.VersionNbt.contains(extension, "Progress", 99))
            return;
        float exact = extension.getFloat("Progress").orElseThrow();
        // Progress only moves forward from lastProgress to 1; anything else is not a state the
        // game can be in, so Vanilla's own value is kept.
        if (exact >= lastProgress && exact <= 1.0f) progress = exact;
        if (com.atsukimc.anticrash.runtime.VersionNbt.contains(extension, "SavedWorldTime", 99))
            savedWorldTime = extension.getLong("SavedWorldTime").orElseThrow();
    }
}
