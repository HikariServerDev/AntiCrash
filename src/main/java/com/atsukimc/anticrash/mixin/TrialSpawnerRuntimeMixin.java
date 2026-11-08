//? if (>=1.20.3 && <=26.3) {
package com.atsukimc.anticrash.mixin;

import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if (>=1.20.3 && <=1.21.11) {
/*@Mixin(TrialSpawner.class)
public abstract class TrialSpawnerRuntimeMixin {

*///?}
    //? if (>=1.20.3 && <=1.20.4) {
    /*@Inject(method = "tickServer", at = @At("HEAD"))
    private void anticrash$dirty(ServerLevel world, BlockPos pos, CallbackInfo ci)
    *///?}
    //? if (>=1.20.5 && <=1.21.11) {
    /*@Inject(method = "tickServer", at = @At("HEAD"))
    private void anticrash$dirty(ServerLevel world, BlockPos pos, boolean ominous, CallbackInfo ci)
    *///?}
//? if (>=26.1 && <=26.3) {
@Mixin(TrialSpawner.class)
public abstract class TrialSpawnerRuntimeMixin {
    @Inject(method = "tickServer", at = @At("HEAD"))
    private void anticrash$dirty(ServerLevel world, BlockPos pos, boolean ominous, CallbackInfo ci)
//?}
            {
        // Codec-owned spawn deadlines, player and mob sets may change without a block transition.

        world.blockEntityChanged(pos);
    }
}
//?}
