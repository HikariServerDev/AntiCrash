package com.atsukimc.anticrash.mixin;

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.block.entity.BlockEntity;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.block.entity.BlockEntity;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.block.entity.ComparatorBlockEntity;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
*///?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A comparator can change its stored {@code OutputSignal} without changing its block state (for
 * example 5 to 7 while staying powered). The stored value decides the next comparator update, so a
 * stale save changes how a restored redstone circuit continues.
 */
@Mixin(ComparatorBlockEntity.class)
public abstract class ComparatorBlockEntityMixin {

    //? if (>=1.18 && <=1.21.11) {
    @Shadow private int outputSignal;

    //?}
    //? if (>=26.1 && <=26.3) {
    /*@Shadow private int output;
*///?}

    @Inject(method = "setOutputSignal", at = @At("HEAD"))
    private void anticrash$markSignalDirty(int signal, CallbackInfo callbackInfo) {
        BlockEntity self = (BlockEntity) (Object) this;

        //? if (>=1.18 && <=1.21.8) {
        if (signal != outputSignal
                && self.hasWorld()
                && self.getWorld() != null
                && !self.getWorld().isClient)
        //?}
        //? if (>=1.21.9 && <=1.21.11) {
        /*if (signal != outputSignal
                        && self.hasWorld()
                        && self.getWorld() != null
                        && !self.getWorld().isClient())
*///?}
        //? if (>=26.1 && <=26.3) {
        /*if (signal != output
                        && self.hasLevel()
                        && self.getLevel() != null
                        && !self.getLevel().isClientSide())
*///?}
        {

            //? if (>=1.18 && <=1.21.11) {
            self.getWorld().markDirty(self.getPos());
            //?}
            //? if (>=26.1 && <=26.3) {
            /*self.getLevel().blockEntityChanged(self.getBlockPos());
*///?}

        }
    }
}
