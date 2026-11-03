package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.snapshot.logical.ChunkMutationTracker;

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.util.math.BlockPos;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.core.BlockPos;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.World;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.Level;
*///?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Covers World#markDirty, including piston progress that bypasses BlockEntity#markDirty. */
//? if (>=1.18 && <=1.21.11) {
@Mixin(World.class)
public abstract class WorldMutationMixin {
    @Inject(method = "markDirty", at = @At("HEAD"))
    private void anticrash$worldDirty(BlockPos pos, CallbackInfo callbackInfo)
                //?}
                //? if (>=26.1 && <=26.3) {
                /*@Mixin(Level.class)
                public abstract class WorldMutationMixin {
                    @Inject(method = "blockEntityChanged", at = @At("HEAD"))
                    private void anticrash$worldDirty(BlockPos pos, CallbackInfo callbackInfo)
*///?}
            {
        if (!com.atsukimc.anticrash.snapshot.logical.LogicalTracking.enabled()) return;

        //? if (>=1.18 && <=1.21.11) {
        ChunkMutationTracker.record(
                (World) (Object) this,
                new net.minecraft.util.math.ChunkPos(pos),
                "world.markDirty");
        //?}
        //? if (>=26.1 && <=26.3) {
        /*ChunkMutationTracker.record(
                        (Level) (Object) this,
                        net.minecraft.world.level.ChunkPos.containing(pos),
                        "world.markDirty");
*///?}

    }
}
