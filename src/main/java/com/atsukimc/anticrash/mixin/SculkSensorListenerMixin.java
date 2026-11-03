//? if (>=1.18 && <=1.19.4) {
package com.atsukimc.anticrash.mixin;

//? if (>=1.19 && <=1.19.4) {
/*import net.minecraft.server.world.ServerWorld;
*///?}

//? if (>=1.19 && <=1.19.4) {
/*import net.minecraft.util.math.BlockPos;
*///?}

import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.event.PositionSource;
//? if (>=1.18 && <=1.18.2) {
import net.minecraft.world.event.listener.SculkSensorListener;
//?}
//? if (>=1.19 && <=1.19.4) {
/*import net.minecraft.world.event.listener.VibrationListener;
*///?}

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if (>=1.19 && <=1.19.4) {
/*import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?}

//? if (>=1.18 && <=1.18.2) {
import java.util.Optional;

//?}

//? if (>=1.18 && <=1.18.2) {
@Mixin(SculkSensorListener.class)
public abstract class SculkSensorListenerMixin {

    //?}
    //? if (>=1.19 && <=1.19.4) {
    /*@Mixin(VibrationListener.class)
    public abstract class SculkSensorListenerMixin {
*///?}
    @Shadow @Final protected PositionSource positionSource;

    //? if (>=1.18 && <=1.18.2) {
    @Shadow protected Optional<GameEvent> event;

    //?}
    //? if (>=1.19 && <=1.19.2) {
    /*@Shadow protected VibrationListener.Vibration vibration;
*///?}

    /**
     * The pending vibration counts down without the chunk being told its data changed, so the chunk
     * would not be saved and the countdown would be lost. Marked before the countdown step, so the
     * tick that delivers (and clears) the vibration is saved as well.
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void anticrash$markPendingVibrationDirty(World world, CallbackInfo callbackInfo)
                //? if (>=1.18 && <=1.19.2) {
            {

        //?}
        //? if (>=1.18 && <=1.18.2) {
        if (world.isClient || event.isEmpty())
            //?}
            //? if (>=1.19 && <=1.19.2) {
            /*if (world.isClient || vibration == null)
*///?}
            //? if (>=1.19.3 && <=1.19.4) {
            /*{
                    // The selector may hold a same-tick candidate while the current vibration is still null.
                    if (world.isClient)
*///?}
            return;

        //? if (>=1.18 && <=1.18.2) {
        positionSource.getPos(world).ifPresent(world::markDirty);
        //?}
        //? if (>=1.19 && <=1.19.3) {
        /*positionSource.getPos(world).ifPresent(pos -> world.markDirty(new BlockPos(pos)));
*///?}
        //? if 1.19.4 {
        /*positionSource.getPos(world).ifPresent(pos -> world.markDirty(BlockPos.ofFloored(pos)));
*///?}

    }

    /**
     * A vibration heard after the sensor already ticked this tick must reach a cut at this tick's
     * end.
     */

    //? if (>=1.18 && <=1.18.2) {
    @Inject(
            method =
                    "listen(Lnet/minecraft/world/World;Lnet/minecraft/world/event/GameEvent;"
                        + "Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/BlockPos;)V",
            at = @At("TAIL"))
    private void anticrash$markHeardVibrationDirty(
            World world,
            GameEvent heard,
            net.minecraft.util.math.BlockPos pos,
            net.minecraft.util.math.BlockPos sourcePos,
            CallbackInfo callbackInfo)
                //?}
                //? if (>=1.19 && <=1.19.2) {
                /*@Inject(
                            method =
                                    "listen(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/world/event/GameEvent$Message;)Z",
                            at = @At("RETURN"))
                    private void anticrash$markHeardVibrationDirty(
                            ServerWorld world, GameEvent.Message event, CallbackInfoReturnable<Boolean> ci)
*///?}
                //? if (>=1.19.3 && <=1.19.4) {
                /*@Inject(
                            method =
                                    "listen(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/world/event/GameEvent;Lnet/minecraft/world/event/GameEvent$Emitter;Lnet/minecraft/util/math/Vec3d;)Z",
                            at = @At("RETURN"))
                    private void anticrash$markHeardVibrationDirty(
                            ServerWorld world,
                            GameEvent event,
                            GameEvent.Emitter emitter,
                            net.minecraft.util.math.Vec3d origin,
                            CallbackInfoReturnable<Boolean> ci)
*///?}
            {

        //? if (>=1.18 && <=1.18.2) {
        if (!world.isClient) world.markDirty(pos);
        //?}
        //? if (>=1.19 && <=1.19.4) {
        /*if (ci.getReturnValueZ()) {
*///?}
        //? if (>=1.19 && <=1.19.3) {
        /*positionSource.getPos(world).ifPresent(pos -> world.markDirty(new BlockPos(pos)));
*///?}
        //? if 1.19.4 {
        /*positionSource.getPos(world).ifPresent(pos -> world.markDirty(BlockPos.ofFloored(pos)));
*///?}
        //? if (>=1.19 && <=1.19.4) {

        /*}
*///?}

    }
}
//?}
