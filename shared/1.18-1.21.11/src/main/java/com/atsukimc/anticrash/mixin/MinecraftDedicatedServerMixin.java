package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.AntiCrashController;
import com.atsukimc.anticrash.AntiCrashMod;

import net.minecraft.server.dedicated.MinecraftDedicatedServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftDedicatedServer.class)
public abstract class MinecraftDedicatedServerMixin {
    /**
     * AntiCrash's pre-world coordinator has already made the disk decision in the Fabric
     * entrypoint. Refuse to enter vanilla setup when that decision failed or is unresolved. This
     * injection is at setupServer HEAD, before the dedicated server creates its console
     * BufferedReader, binds a listener, or calls MinecraftServer.loadWorld().
     */
    @Inject(method = "setupServer", at = @At("HEAD"), cancellable = true)
    private void anticrash$gateWorldStartup(CallbackInfoReturnable<Boolean> callbackInfo) {
        AntiCrashController controller = AntiCrashMod.controller();
        if (controller != null && !controller.allowWorldLoadBeforeServer()) {
            callbackInfo.setReturnValue(false);
        }
    }

    /**
     * Leaves console commands queued while a checkpoint holds the world at its cut.
     *
     * <p>The dedicated server drains its console queue at the end of every tick, including the
     * frozen ticks of a long checkpoint. A command run there would change the world after the cut,
     * so CommandManager had to refuse it and the operator had to type it again. The queue is left
     * intact instead and runs on the first tick after the checkpoint, in its original order.
     */
    @Inject(method = "executeQueuedCommands", at = @At("HEAD"), cancellable = true)
    private void anticrash$holdConsoleCommands(CallbackInfo callbackInfo) {
        AntiCrashController controller = AntiCrashMod.controller();
        if (controller != null && controller.holdsServerTasks()) callbackInfo.cancel();
    }
}
