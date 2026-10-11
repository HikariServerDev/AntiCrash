package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.checkpoint.WriteHold;
import net.minecraft.world.level.chunk.storage.IOWorker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IOWorker.class)
public abstract class StorageIoWorkerMixin {
    /** Leaves the queued result in place; WriteHold restarts the writes when the cut ends. */
    @Inject(method = "storePendingChunk", at = @At("HEAD"), cancellable = true)
    private void anticrash$deferWriteDuringCut(CallbackInfo callbackInfo) {
        if (WriteHold.defer((IOWorker) (Object) this)) callbackInfo.cancel();
    }
}
