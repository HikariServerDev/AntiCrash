package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.AntiCrashController;
import com.atsukimc.anticrash.AntiCrashMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.function.CommandFunctionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Inject(method = "loadWorld", at = @At("HEAD"))
    private void anticrash$worldLoadBeginning(CallbackInfo callbackInfo) {
        AntiCrashController controller = AntiCrashMod.controller();
        if (controller != null) controller.onWorldLoadBeginning();
    }

    @Inject(method = "loadWorld", at = @At("RETURN"))
    private void anticrash$worldLoaded(CallbackInfo callbackInfo) {
        AntiCrashController controller = AntiCrashMod.controller();
        if (controller != null) controller.onServerStarted((MinecraftServer) (Object) this);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void anticrash$tickEnd(CallbackInfo callbackInfo) {
        AntiCrashController controller = AntiCrashMod.controller();
        if (controller != null) controller.onTickEnd((MinecraftServer) (Object) this);
    }

    /**
     * Keeps queued work, and with it every player packet, waiting while a checkpoint holds the
     * world at its cut.
     *
     * <p>A player packet reaches the server thread as a task in this queue, so while the queue is
     * held nothing a player does can change the world after the cut, yet nothing is lost: every
     * packet runs afterwards, in the order it arrived, exactly like after a slow tick. The old
     * barrier instead cancelled the packets outright. Dropped movement left the server position
     * behind and produced "moved too quickly" rubber-banding, a dropped close-screen packet left
     * containers open, and a dropped START_FALL_FLYING left elytra gliding on the client.</p>
     *
     * <p>{@code runOneTask} is the one entry point for both {@code runTasks()} and
     * {@code runTasks(BooleanSupplier)}; the latter never consults {@code canExecute}, so that is
     * not a usable gate. Returning false makes the tick loop wait for the next tick as usual.</p>
     */
    @Inject(method = "runOneTask", at = @At("HEAD"), cancellable = true)
    private void anticrash$holdTasksDuringCheckpoint(CallbackInfoReturnable<Boolean> callbackInfo) {
        AntiCrashController controller = AntiCrashMod.controller();
        if (controller != null && controller.holdsServerTasks()) callbackInfo.setReturnValue(false);
    }

    @Redirect(method = "tickWorlds", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/function/CommandFunctionManager;tick()V"))
    private void anticrash$skipFunctionsDuringRecovery(CommandFunctionManager manager) {
        AntiCrashController controller = AntiCrashMod.controller();
        if (controller == null || !controller.isWorldFrozen()) manager.tick();
    }

    // Never cancellable: AntiCrash only classifies the stop request here and always lets
    // Minecraft's own stop transition proceed.
    @Inject(method = "stop", at = @At("HEAD"))
    private void anticrash$stopRequested(boolean wait, CallbackInfo callbackInfo) {
        AntiCrashController controller = AntiCrashMod.controller();
        if (controller != null) controller.onStopRequested();
    }

    @Inject(method = "shutdown", at = @At("HEAD"))
    private void anticrash$shutdownBeginning(CallbackInfo callbackInfo) {
        AntiCrashController controller = AntiCrashMod.controller();
        if (controller != null) controller.onShutdownBeginning();
    }

    @Inject(method = "shutdown", at = @At("RETURN"))
    private void anticrash$shutdownCompleted(CallbackInfo callbackInfo) {
        AntiCrashController controller = AntiCrashMod.controller();
        if (controller != null) controller.onShutdownCompleted();
    }
}
