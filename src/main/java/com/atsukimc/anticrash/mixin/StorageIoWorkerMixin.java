package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.checkpoint.WriteHold;

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.level.chunk.storage.IOWorker;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.chunk.storage.IOWorker;
*///?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if (>=1.18 && <=1.21.11) {
@Mixin(IOWorker.class)
public abstract class StorageIoWorkerMixin {
    /** Leaves the queued result in place; WriteHold restarts the writes when the cut ends. */
    @Inject(method = "storePendingChunk", at = @At("HEAD"), cancellable = true)
    private void anticrash$deferWriteDuringCut(CallbackInfo callbackInfo)
                //?}
                //? if (>=26.1 && <=26.3) {
                /*@Mixin(IOWorker.class)
                public abstract class StorageIoWorkerMixin {
                    /^* Leaves the queued result in place; WriteHold restarts the writes when the cut ends. ^/
                    @Inject(method = "storePendingChunk", at = @At("HEAD"), cancellable = true)
                    private void anticrash$deferWriteDuringCut(CallbackInfo callbackInfo)
*///?}
            {

        //? if (>=1.18 && <=1.21.11) {
        if (WriteHold.defer((IOWorker) (Object) this))
            //?}
            //? if (>=26.1 && <=26.3) {
            /*if (WriteHold.defer((IOWorker) (Object) this))
*///?}
            callbackInfo.cancel();
    }
}
