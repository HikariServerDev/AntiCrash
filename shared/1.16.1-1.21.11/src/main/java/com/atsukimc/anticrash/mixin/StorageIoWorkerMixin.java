package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.checkpoint.WriteHold;

import net.minecraft.world.storage.StorageIoWorker;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StorageIoWorker.class)
public abstract class StorageIoWorkerMixin {
    /** Leaves the queued result in place; WriteHold restarts the writes when the cut ends. */
    @Inject(method = "writeResult", at = @At("HEAD"), cancellable = true)
    private void anticrash$deferWriteDuringCut(CallbackInfo callbackInfo) {
        if (WriteHold.defer((StorageIoWorker) (Object) this)) callbackInfo.cancel();
    }
}
