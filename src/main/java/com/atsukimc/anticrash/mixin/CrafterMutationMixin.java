//? if (>=1.20.3 && <=26.3) {
/*package com.atsukimc.anticrash.mixin;

//? if (>=1.20.3 && <=1.21.11) {
/^import net.minecraft.world.level.block.state.BlockState;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.core.BlockPos;
^///?}

//? if (>=1.20.3 && <=1.21.11) {
/^import net.minecraft.world.level.block.entity.CrafterBlockEntity;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.level.Level;
^///?}

//? if (>=1.20.3 && <=1.21.11) {
/^import net.minecraft.core.BlockPos;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.level.block.entity.CrafterBlockEntity;
^///?}

//? if (>=1.20.3 && <=1.21.11) {
/^import net.minecraft.world.level.Level;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.level.block.state.BlockState;
^///?}


import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CrafterBlockEntity.class)
public abstract class CrafterMutationMixin {

    // Vanilla persists the countdown but tickCrafting does not mark intermediate ticks dirty.

//? if (>=1.20.3 && <=1.21.11) {
/^@Inject(method = "serverTick", at = @At("HEAD"))
    private static void anticrash$dirty(
            Level world,
            BlockPos pos,
            BlockState state,
            CrafterBlockEntity crafter,
            CallbackInfo ci)
^///?}
//? if (>=26.1 && <=26.3) {
/^@Inject(method = "serverTick", at = @At("HEAD"))
    private static void anticrash$dirty(
            Level world,
            BlockPos pos,
            BlockState state,
            CrafterBlockEntity crafter,
            CallbackInfo ci)
^///?}
{

//? if (>=1.20.3 && <=1.21.8) {
/^if (!world.isClientSide
                && ((CrafterAccessorMixin) crafter).anticrash$craftingTicksRemaining() > 0)

^///?}
//? if (>=1.21.9 && <=1.21.11) {
/^if (!world.isClientSide()
                && ((CrafterAccessorMixin) crafter).anticrash$craftingTicksRemaining() > 0)

^///?}
//? if (>=1.20.3 && <=1.21.11) {
/^world.blockEntityChanged(pos);
^///?}
//? if (>=26.1 && <=26.3) {
/^if (!world.isClientSide()
                && ((CrafterAccessorMixin) crafter).anticrash$craftingTicksRemaining() > 0)
            world.blockEntityChanged(pos);
^///?}

    }
}
*///?}
