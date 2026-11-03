//? if (>=1.19 && <=26.3) {
/*package com.atsukimc.anticrash.mixin;

//? if (>=1.19 && <=1.21.11) {
/^import net.minecraft.block.BlockState;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.core.BlockPos;
^///?}

//? if (>=1.19 && <=1.21.11) {
/^import net.minecraft.block.entity.SculkCatalystBlockEntity;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.level.Level;
^///?}

//? if (>=1.19 && <=1.19.4) {
/^import net.minecraft.server.world.ServerWorld;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.level.block.entity.SculkCatalystBlockEntity;
^///?}

//? if (>=1.19 && <=1.21.11) {
/^import net.minecraft.util.math.BlockPos;
^///?}

//? if (>=1.19 && <=1.21.11) {
/^import net.minecraft.world.World;
^///?}

//? if (>=1.19 && <=1.19.4) {
/^import net.minecraft.world.event.GameEvent;
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
/^@Inject(method = "tick", at = @At("HEAD"))
    private static void anticrash$markCursorTick(
            World world,
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
/^if (!world.isClient)
^///?}
//? if (>=1.21.9 && <=1.21.11) {
/^if (!world.isClient())
^///?}
//? if (>=1.19 && <=1.21.11) {
/^world.markDirty(pos);
^///?}
    //? if (>=1.19 && <=1.21.8) {

    /^}
    ^///?}



//? if (>=1.19 && <=1.19.2) {
/^@Inject(method = "listen", at = @At("RETURN"))
    private void anticrash$markNewCursor(
            ServerWorld world, GameEvent.Message message, CallbackInfoReturnable<Boolean> ci)
^///?}
//? if (>=1.19.3 && <=1.19.4) {
/^@Inject(method = "listen", at = @At("RETURN"))
    private void anticrash$markNewCursor(
            ServerWorld world,
            GameEvent event,
            GameEvent.Emitter emitter,
            net.minecraft.util.math.Vec3d origin,
            CallbackInfoReturnable<Boolean> ci)
^///?}
//? if (>=1.19 && <=1.19.4) {
/^{
        if (ci.getReturnValueZ())
            world.markDirty(((SculkCatalystBlockEntity) (Object) this).getPos());
^///?}
//? if (>=26.1 && <=26.3) {
/^if (!world.isClientSide()) world.blockEntityChanged(pos);
^///?}
    //? if (>=1.19 && <=1.19.4) || (>=1.21.9 && <=26.3) {

    /^}
    ^///?}

}
*///?}
