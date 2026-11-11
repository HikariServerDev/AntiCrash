//? if (>=1.18 && <=1.19.4) {
/*package com.atsukimc.anticrash.mixin;

//? if (>=1.18 && <=1.18.2) {
/^import java.util.Optional;
^///?} else {
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//?}

import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.level.gameevent.vibrations.VibrationListener;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VibrationListener.class)
public abstract class SculkSensorListenerMixin {

    @Shadow @Final protected PositionSource listenerSource;

    //? if (>=1.18 && <=1.18.2) {
    /^@Shadow protected Optional<GameEvent> receivingEvent;

    ^///?}
    //? if (>=1.19 && <=1.19.2) {
    /^@Shadow protected VibrationListener.ReceivingEvent receivingEvent;

    ^///?}

    /^*
     * The pending vibration counts down without the chunk being told its data changed, so the chunk
     * would not be saved and the countdown would be lost. Marked before the countdown step, so the
     * tick that delivers (and clears) the vibration is saved as well.
     ^/
    @Inject(method = "tick", at = @At("HEAD"))
    private void anticrash$markPendingVibrationDirty(Level world, CallbackInfo callbackInfo)
            //? if (>=1.18 && <=1.19.2) {
            /^{

            ^///?}
        //? if (>=1.18 && <=1.18.2) {
        /^if (world.isClientSide() || receivingEvent.isEmpty())
        ^///?}
        //? if (>=1.19 && <=1.19.2) {
        /^if (world.isClientSide() || receivingEvent == null)
        ^///?}
            //? if (>=1.19.3 && <=1.19.4) {
            /^{
        // The selector may hold a same-tick candidate while the current vibration is still null.
        if (world.isClientSide())
            ^///?}
            return;

        //? if (>=1.18 && <=1.18.2) {
        /^listenerSource.getPosition(world).ifPresent(world::blockEntityChanged);
        ^///?}
        //? if (>=1.19 && <=1.19.3) {
        /^listenerSource
                .getPosition(world)
                .ifPresent(pos -> world.blockEntityChanged(new BlockPos(pos)));
        ^///?}
        //? if 1.19.4 {
        /^listenerSource
                .getPosition(world)
                .ifPresent(pos -> world.blockEntityChanged(BlockPos.containing(pos)));
        ^///?}

    }

    /^*
     * A vibration heard after the sensor already ticked this tick must reach a cut at this tick's
     * end.
     ^/

    //? if (>=1.18 && <=1.18.2) {
    /^@Inject(
            method =
                    "sendSignal(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/gameevent/GameEvent;"
                            + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)V",
            at = @At("TAIL"))
    private void anticrash$markHeardVibrationDirty(
            Level world,
            GameEvent heard,
            net.minecraft.core.BlockPos pos,
            net.minecraft.core.BlockPos sourcePos,
            CallbackInfo callbackInfo)
    ^///?}
    //? if (>=1.19 && <=1.19.2) {
    /^@Inject(
            method =
                    "handleGameEvent(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/gameevent/GameEvent$Message;)Z",
            at = @At("RETURN"))
    private void anticrash$markHeardVibrationDirty(
            ServerLevel world, GameEvent.Message event, CallbackInfoReturnable<Boolean> ci)
    ^///?}
    //? if (>=1.19.3 && <=1.19.4) {
    /^@Inject(
            method =
                    "handleGameEvent(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/gameevent/GameEvent;Lnet/minecraft/world/level/gameevent/GameEvent$Context;Lnet/minecraft/world/phys/Vec3;)Z",
            at = @At("RETURN"))
    private void anticrash$markHeardVibrationDirty(
            ServerLevel world,
            GameEvent event,
            GameEvent.Context emitter,
            net.minecraft.world.phys.Vec3 origin,
            CallbackInfoReturnable<Boolean> ci)
    ^///?}
            {

        //? if (>=1.18 && <=1.18.2) {
        /^if (!world.isClientSide()) world.blockEntityChanged(pos);
        ^///?}
        //? if (>=1.19 && <=1.19.4) {
        /^if (ci.getReturnValueZ()) {
        ^///?}
            //? if (>=1.19 && <=1.19.3) {
            /^listenerSource
                    .getPosition(world)
                    .ifPresent(pos -> world.blockEntityChanged(new BlockPos(pos)));
            ^///?}
            //? if 1.19.4 {
            /^listenerSource
                    .getPosition(world)
                    .ifPresent(pos -> world.blockEntityChanged(BlockPos.containing(pos)));
            ^///?}
        //? if (>=1.19 && <=1.19.4) {

        /^}
        ^///?}

    }
}
*///?}
