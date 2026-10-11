package com.atsukimc.anticrash.mixin;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ComparatorBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A comparator can change its stored {@code OutputSignal} without changing its block state (for
 * example 5 to 7 while staying powered). The stored value decides the next comparator update, so
 * a stale save changes how a restored redstone circuit continues.
 */
@Mixin(ComparatorBlockEntity.class)
public abstract class ComparatorBlockEntityMixin {
    @Shadow private int outputSignal;

    @Inject(method = "setOutputSignal", at = @At("HEAD"))
    private void anticrash$markSignalDirty(int signal, CallbackInfo callbackInfo) {
        BlockEntity self = (BlockEntity) (Object) this;
        if (signal != outputSignal && self.hasWorld() && self.getWorld() != null
                && !self.getWorld().isClient()) {
            self.getWorld().markDirty(self.getPos());
        }
    }
}
