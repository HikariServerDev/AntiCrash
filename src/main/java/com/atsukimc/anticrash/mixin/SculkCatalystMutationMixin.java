//? if (>=1.19 && <=26.3) {
/*package com.atsukimc.anticrash.mixin;

//? if (>=1.19 && <=1.21.11) {
/^import net.minecraft.world.level.block.state.BlockState;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.core.BlockPos;
^///?}

//? if (>=1.19 && <=1.21.11) {
/^import net.minecraft.world.level.block.entity.SculkCatalystBlockEntity;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.level.Level;
^///?}

//? if (>=1.19 && <=1.19.4) {
/^import net.minecraft.server.level.ServerLevel;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.level.block.entity.SculkCatalystBlockEntity;
^///?}

//? if (>=1.19 && <=1.21.11) {
/^import net.minecraft.core.BlockPos;
^///?}

//? if (>=1.19 && <=1.21.11) {
/^import net.minecraft.world.level.Level;
^///?}

//? if (>=1.19 && <=1.19.4) {
/^import net.minecraft.world.level.gameevent.GameEvent;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.level.block.state.BlockState;
^///?}


import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if (>=1.19 && <=1.19.4) {
/^import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
^///?}


/^* Vanilla writes spread cursors but their countdown does not call markDirty (1.19 bytecode). ^/
@Mixin(SculkCatalystBlockEntity.class)
public abstract class SculkCatalystMutationMixin {

//? if (>=1.19 && <=1.21.11) {
/^@Inject(method = "serverTick", at = @At("HEAD"))
    private static void anticrash$markCursorTick(
            Level world,
            BlockPos pos,
            BlockState state,
            SculkCatalystBlockEntity catalyst,
            CallbackInfo ci)
^///?}
//? if (>=26.1 && <=26.3) {
/^@Inject(method = "serverTick", at = @At("HEAD"))
    private static void anticrash$markCursorTick(
            Level world,
            BlockPos pos,
            BlockState state,
            SculkCatalystBlockEntity catalyst,
            CallbackInfo ci)
^///?}
{

//? if (>=1.19 && <=1.21.8) {
/^if (!world.isClientSide)
^///?}
//? if (>=1.21.9 && <=1.21.11) {
/^if (!world.isClientSide())
^///?}
//? if (>=1.19 && <=1.21.11) {
/^world.blockEntityChanged(pos);
^///?}
    //? if (>=1.19 && <=1.21.8) {

    /^}
    ^///?}



//? if (>=1.19 && <=1.19.2) {
/^@Inject(method = "handleGameEvent", at = @At("RETURN"))
    private void anticrash$markNewCursor(
            ServerLevel world, GameEvent.Message message, CallbackInfoReturnable<Boolean> ci)
^///?}
//? if (>=1.19.3 && <=1.19.4) {
/^@Inject(method = "handleGameEvent", at = @At("RETURN"))
    private void anticrash$markNewCursor(
            ServerLevel world,
            GameEvent event,
            GameEvent.Context emitter,
            net.minecraft.world.phys.Vec3 origin,
            CallbackInfoReturnable<Boolean> ci)
^///?}
//? if (>=1.19 && <=1.19.4) {
/^{
        if (ci.getReturnValueZ())
            world.blockEntityChanged(((SculkCatalystBlockEntity) (Object) this).getBlockPos());
^///?}
//? if (>=26.1 && <=26.3) {
/^if (!world.isClientSide()) world.blockEntityChanged(pos);
^///?}
    //? if (>=1.19 && <=1.19.4) || (>=1.21.9 && <=26.3) {

    /^}
    ^///?}

}
*///?}
