package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.util.VersionLevels;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;

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

    @Shadow private int output;

    @Inject(method = "setOutputSignal", at = @At("HEAD"))
    private void anticrash$markSignalDirty(int signal, CallbackInfo callbackInfo) {
        BlockEntity self = (BlockEntity) (Object) this;

        //? if (>=1.18 && <=1.21.8) {
        /*if (signal != output
                && self.hasLevel()
                && self.getLevel() != null
                && !VersionLevels.isClientSide(self.getLevel())) {
        *///?} else {
        if (signal != output
                && self.hasLevel()
                && self.getLevel() != null
                && !VersionLevels.isClientSide(self.getLevel())) {
        //?}
            self.getLevel().blockEntityChanged(self.getBlockPos());
        }
    }
}
