package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.runtime.RuntimeExtensionNbt;

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.block.BlockState;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.core.BlockPos;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.block.entity.PistonBlockEntity;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.nbt.CompoundTag;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.nbt.NbtCompound;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.Level;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.util.math.BlockPos;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.World;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.block.state.BlockState;
*///?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if (>=1.18 && <=1.21.11) {
@Mixin(PistonBlockEntity.class)
public abstract class PistonBlockEntityMixin {

    //?}
    //? if (>=26.1 && <=26.3) {
    /*@Mixin(PistonMovingBlockEntity.class)
    public abstract class PistonBlockEntityMixin {
*///?}
    @Shadow private float progress;

    //? if (>=1.18 && <=1.21.11) {
    @Shadow private float lastProgress;
    //?}
    //? if (>=26.1 && <=26.3) {
    /*@Shadow private float progressO;
*///?}

    //? if (>=1.18 && <=1.21.11) {
    @Shadow private long savedWorldTime;

    //?}
    //? if (>=26.1 && <=26.3) {
    /*@Shadow private long lastTicked;
*///?}

    /**
     * Vanilla advances piston progress without notifying the owning chunk that its block entity
     * data changed. Keep ordinary saves and AntiCrash checkpoints from retaining stale progress.
     */

    //? if (>=1.18 && <=1.21.11) {
    @Inject(method = "tick", at = @At("TAIL"))
    private static void anticrash$markChunkDirty(
            World world,
            BlockPos pos,
            BlockState state,
            PistonBlockEntity piston,
            CallbackInfo callbackInfo)
                //?}
                //? if (>=26.1 && <=26.3) {
                /*@Inject(method = "tick", at = @At("TAIL"))
                    private static void anticrash$markChunkDirty(
                            Level world,
                            BlockPos pos,
                            BlockState state,
                            PistonMovingBlockEntity piston,
                            CallbackInfo callbackInfo)
*///?}
            {
        // World#markDirty only sets the loaded chunk's needsSaving flag. Calling
        // BlockEntity#markDirty here would also run comparator updates on every piston tick.

        //? if (>=1.18 && <=1.21.11) {
        world.markDirty(pos);
        //?}
        //? if (>=26.1 && <=26.3) {
        /*world.blockEntityChanged(pos);
*///?}

    }

    /**
     * Vanilla saves {@code lastProgress} under "progress" and loads it into both fields, so a
     * piston that was half way (progress 0.5, lastProgress 0.0) or about to finish (1.0, 0.5)
     * restarts half a step behind and arrives one tick late. That one tick stops every flying
     * machine caught mid-move. {@code lastProgress} stays Vanilla's; only the real progress and the
     * tick it was last advanced in ({@code savedWorldTime}, which PistonBlock compares with the
     * world time when a piston is retracted again) are added.
     */

    //? if (>=1.18 && <=1.20.4) {
    @Inject(method = "writeNbt", at = @At("TAIL"))
    private void anticrash$saveExactProgress(NbtCompound nbt, CallbackInfo callbackInfo)
                //?}
                //? if (>=1.20.5 && <=1.21.5) {
                /*@Inject(method = "writeNbt", at = @At("TAIL"))
                    private void anticrash$saveExactProgress(
                            NbtCompound nbt,
                            net.minecraft.registry.RegistryWrapper.WrapperLookup registries,
                            CallbackInfo callbackInfo)
*///?}
                //? if (>=1.21.6 && <=1.21.11) {
                /*@Inject(method = "writeData", at = @At("TAIL"))
                    private void anticrash$saveExactProgress(
                            net.minecraft.storage.WriteView nbt, CallbackInfo callbackInfo)
*///?}
                //? if (>=26.1 && <=26.3) {
                /*@Inject(method = "saveAdditional", at = @At("TAIL"))
                    private void anticrash$saveExactProgress(
                            net.minecraft.world.level.storage.ValueOutput nbt, CallbackInfo callbackInfo)
*///?}
            {
        if (!RuntimeExtensionNbt.savingLoadedChunk()) return;
        RuntimeExtensionNbt.write(
                nbt,
                extension -> {
                    extension.putFloat("Progress", progress);

                    //? if (>=1.18 && <=1.21.11) {
                    extension.putLong("SavedWorldTime", savedWorldTime);
                    //?}
                    //? if (>=26.1 && <=26.3) {
                    /*extension.putLong("SavedWorldTime", lastTicked);
*///?}

                });
    }

    //? if (>=1.18 && <=1.20.4) {
    @Inject(method = "readNbt", at = @At("TAIL"))
    private void anticrash$loadExactProgress(NbtCompound nbt, CallbackInfo callbackInfo)
                //?}
                //? if (>=1.20.5 && <=1.21.5) {
                /*@Inject(method = "readNbt", at = @At("TAIL"))
                    private void anticrash$loadExactProgress(
                            NbtCompound nbt,
                            net.minecraft.registry.RegistryWrapper.WrapperLookup registries,
                            CallbackInfo callbackInfo)
*///?}
                //? if (>=1.21.6 && <=1.21.11) {
                /*@Inject(method = "readData", at = @At("TAIL"))
                    private void anticrash$loadExactProgress(
                            net.minecraft.storage.ReadView nbt, CallbackInfo callbackInfo)
*///?}
                //? if (>=26.1 && <=26.3) {
                /*@Inject(method = "loadAdditional", at = @At("TAIL"))
                    private void anticrash$loadExactProgress(
                            net.minecraft.world.level.storage.ValueInput nbt, CallbackInfo callbackInfo)
*///?}
            {

        //? if (>=1.18 && <=1.21.11) {
        NbtCompound extension = RuntimeExtensionNbt.read(nbt);
        //?}
        //? if (>=26.1 && <=26.3) {
        /*CompoundTag extension = RuntimeExtensionNbt.read(nbt);
*///?}

        //? if (>=1.18 && <=1.21.4) {
        if (extension == null || !extension.contains("Progress", 99))
            //?}
            //? if (>=1.21.5 && <=26.3) {
            /*if (extension == null
                            || !com.atsukimc.anticrash.runtime.VersionNbt.contains(extension, "Progress", 99))
*///?}
            return;

        //? if (>=1.18 && <=1.21.4) {
        float exact = extension.getFloat("Progress");
        //?}
        //? if (>=1.21.5 && <=26.3) {
        /*float exact = extension.getFloat("Progress").orElseThrow();
*///?}

        // Progress only moves forward from lastProgress to 1; anything else is not a state the
        // game can be in, so Vanilla's own value is kept.

        //? if (>=1.18 && <=1.21.11) {
        if (exact >= lastProgress && exact <= 1.0f)
            //?}
            //? if (>=26.1 && <=26.3) {
            /*if (exact >= progressO && exact <= 1.0f)
*///?}
            progress = exact;

        //? if (>=1.18 && <=1.21.4) {
        if (extension.contains("SavedWorldTime", 99))
            savedWorldTime = extension.getLong("SavedWorldTime");
        //?}
        //? if (>=1.21.5 && <=26.3) {
        /*if (com.atsukimc.anticrash.runtime.VersionNbt.contains(extension, "SavedWorldTime", 99))
*///?}
        //? if (>=1.21.5 && <=1.21.11) {
        /*savedWorldTime = extension.getLong("SavedWorldTime").orElseThrow();
*///?}
        //? if (>=26.1 && <=26.3) {
        /*lastTicked = extension.getLong("SavedWorldTime").orElseThrow();
*///?}

    }
}
