//? if (>=1.20 && <=26.3) {
package com.atsukimc.anticrash.mixin;

//? if (>=1.20 && <=1.21.11) {
/*import net.minecraft.server.level.ServerLevel;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.core.BlockPos;
//?}

//? if (>=1.20 && <=1.21.11) {
/*import net.minecraft.core.BlockPos;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.server.level.ServerLevel;
//?}

//? if (>=1.20 && <=1.21.11) {
/*import net.minecraft.world.phys.Vec3;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.world.level.gameevent.GameEvent;
//?}

//? if (>=1.20 && <=1.21.11) {
/*import net.minecraft.world.level.gameevent.GameEvent;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.world.level.gameevent.PositionSource;
//?}

//? if (>=1.20 && <=1.21.11) {
/*import net.minecraft.world.level.gameevent.PositionSource;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
//?}

//? if (>=1.20 && <=1.21.11) {
/*import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.world.phys.Vec3;
//?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

//? if (>=1.20 && <=1.21.11) {
/*@Mixin(VibrationSystem.Listener.class)
public abstract class VibrationListenerMutationMixin {
    @Shadow
    public abstract PositionSource getListenerSource();
*///?} else if (>=26.1 && <=26.3) {
@Mixin(VibrationSystem.Listener.class)
public abstract class VibrationListenerMutationMixin {
    @Shadow
    public abstract PositionSource getListenerSource();

    //?}

    //? if (>=1.20 && <=1.20.4) {
    /*@Inject(
            method =
                    "handleGameEvent(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/gameevent/GameEvent;Lnet/minecraft/world/level/gameevent/GameEvent$Context;Lnet/minecraft/world/phys/Vec3;)Z",
            at = @At("RETURN"))
    private void anticrash$dirtyListen(
            ServerLevel world,
            GameEvent event,
            GameEvent.Context emitter,
            Vec3 origin,
            CallbackInfoReturnable<Boolean> ci)
*///?} else if (>=1.20.5 && <=1.21.11) {
    /*@Inject(
            method =
                    "handleGameEvent(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/Holder;Lnet/minecraft/world/level/gameevent/GameEvent$Context;Lnet/minecraft/world/phys/Vec3;)Z",
            at = @At("RETURN"))
    private void anticrash$dirtyListen(
            ServerLevel world,
            net.minecraft.core.Holder<GameEvent> event,
            GameEvent.Context emitter,
            Vec3 origin,
            CallbackInfoReturnable<Boolean> ci)
*///?} else if (>=26.1 && <=26.3) {
    @Inject(
            method =
                    "handleGameEvent(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/Holder;Lnet/minecraft/world/level/gameevent/GameEvent$Context;Lnet/minecraft/world/phys/Vec3;)Z",
            at = @At("RETURN"))
    private void anticrash$dirtyListen(
            ServerLevel world,
            net.minecraft.core.Holder<GameEvent> event,
            GameEvent.Context emitter,
            Vec3 origin,
            CallbackInfoReturnable<Boolean> ci)
                //?}
            {
        if (ci.getReturnValueZ())

            //? if (>=1.20 && <=1.21.11) {
            /*getListenerSource()
                    .getPosition(world)
                    .ifPresent(pos -> world.blockEntityChanged(BlockPos.containing(pos)));
*///?} else if (>=26.1 && <=26.3) {
            getListenerSource()
                    .getPosition(world)
                    .ifPresent(pos -> world.blockEntityChanged(BlockPos.containing(pos)));
        //?}

    }

    //? if (>=1.20 && <=1.20.4) {
    /*@Inject(method = "forceScheduleVibration", at = @At("TAIL"))
    private void anticrash$dirtyForcedListen(
            ServerLevel world,
            GameEvent event,
            GameEvent.Context emitter,
            Vec3 origin,
            CallbackInfo ci)
*///?} else if (>=1.20.5 && <=1.21.11) {
    /*@Inject(method = "forceScheduleVibration", at = @At("TAIL"))
    private void anticrash$dirtyForcedListen(
            ServerLevel world,
            net.minecraft.core.Holder<GameEvent> event,
            GameEvent.Context emitter,
            Vec3 origin,
            CallbackInfo ci)
*///?} else if (>=26.1 && <=26.3) {
    @Inject(method = "forceScheduleVibration", at = @At("TAIL"))
    private void anticrash$dirtyForcedListen(
            ServerLevel world,
            net.minecraft.core.Holder<GameEvent> event,
            GameEvent.Context emitter,
            Vec3 origin,
            CallbackInfo ci)
                //?}
            {

        //? if (>=1.20 && <=1.21.11) {
        /*getListenerSource()
                .getPosition(world)
                .ifPresent(pos -> world.blockEntityChanged(BlockPos.containing(pos)));
*///?} else if (>=26.1 && <=26.3) {
        getListenerSource()
                .getPosition(world)
                .ifPresent(pos -> world.blockEntityChanged(BlockPos.containing(pos)));
        //?}

    }
}
//?}
