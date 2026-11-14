//? if >=1.19 {
package com.atsukimc.anticrash.mixin;

//? if <=1.19.4 {
/*import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.GameEvent;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?}

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.SculkCatalystBlockEntity;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla writes spread cursors but their countdown does not call setChanged (1.19 bytecode). */
@Mixin(SculkCatalystBlockEntity.class)
public abstract class SculkCatalystMutationMixin {

    @Inject(method = "serverTick", at = @At("HEAD"))
    private static void anticrash$markCursorTick(
            Level world,
            BlockPos pos,
            BlockState state,
            SculkCatalystBlockEntity catalyst,
            CallbackInfo ci) {
        if (!world.isClientSide()) world.blockEntityChanged(pos);
    }

    //? if <=1.19.4 {
    /*//? if <=1.19.2 {
    /^@Inject(method = "handleGameEvent", at = @At("RETURN"))
    private void anticrash$markNewCursor(
            ServerLevel world, GameEvent.Message message, CallbackInfoReturnable<Boolean> ci) {
    ^///?} else {
    @Inject(method = "handleGameEvent", at = @At("RETURN"))
    private void anticrash$markNewCursor(
            ServerLevel world,
            GameEvent event,
            GameEvent.Context emitter,
            net.minecraft.world.phys.Vec3 origin,
            CallbackInfoReturnable<Boolean> ci) {
    //?}
        if (ci.getReturnValueZ())
            world.blockEntityChanged(((SculkCatalystBlockEntity) (Object) this).getBlockPos());
    }
    *///?}
}
//?}
