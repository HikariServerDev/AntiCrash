//? if (>=1.20 && <=26.3) {
/*package com.atsukimc.anticrash.mixin;

//? if (>=1.20 && <=1.21.11) {
/^import net.minecraft.core.BlockPos;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.core.BlockPos;
^///?}

//? if (>=1.20 && <=1.21.11) {
/^import net.minecraft.world.level.Level;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.level.Level;
^///?}

//? if (>=1.20 && <=1.21.11) {
/^import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
^///?}


import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/^* 1.20 separates ListenerData/Ticker/Callback; countdown and pending selector must be dirty. ^/
//? if (>=1.20 && <=1.21.11) {
/^@Mixin(VibrationSystem.Ticker.class)
public interface VibrationTickerMutationMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private static void anticrash$dirtyVibrationTick(
            Level world,
            VibrationSystem.Data data,
            VibrationSystem.User callback,
            CallbackInfo ci)
^///?}
//? if (>=26.1 && <=26.3) {
/^@Mixin(VibrationSystem.Ticker.class)
public interface VibrationTickerMutationMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private static void anticrash$dirtyVibrationTick(
            Level world,
            VibrationSystem.Data data,
            VibrationSystem.User callback,
            CallbackInfo ci)
^///?}
{

//? if (>=1.20 && <=1.21.8) {
/^if (!world.isClientSide)

^///?}
//? if (>=1.21.9 && <=1.21.11) {
/^if (!world.isClientSide())

^///?}
//? if (>=1.20 && <=1.21.11) {
/^callback.getPositionSource()
                    .getPosition(world)
                    .ifPresent(pos -> world.blockEntityChanged(BlockPos.containing(pos)));
^///?}
//? if (>=26.1 && <=26.3) {
/^if (!world.isClientSide())
            callback.getPositionSource()
                    .getPosition(world)
                    .ifPresent(pos -> world.blockEntityChanged(BlockPos.containing(pos)));
^///?}

    }
}
*///?}
