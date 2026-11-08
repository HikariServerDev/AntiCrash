package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.AntiCrashController;
import com.atsukimc.anticrash.AntiCrashMod;
import com.atsukimc.anticrash.command.AntiCrashCommand;
import com.atsukimc.anticrash.command.RecoveryCommandPolicy;
import com.atsukimc.anticrash.i18n.MessageCatalog;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if (>=1.18 && <=1.20.2) {
/*import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

*///?}

@Mixin(Commands.class)
public abstract class CommandManagerMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void anticrash$register(CallbackInfo callbackInfo) {
        AntiCrashController controller = AntiCrashMod.controller();
        if (controller != null) {

            CommandDispatcher<CommandSourceStack> dispatcher =
                    ((Commands) (Object) this).getDispatcher();

            AntiCrashCommand.register(dispatcher, controller);
        }
    }

    //? if (>=1.18 && <=1.19) {
    /*@Inject(method = "performCommand", at = @At("HEAD"), cancellable = true)
    private void anticrash$blockCommandsDuringCheckpoint(
            CommandSourceStack source,
            String command,
            CallbackInfoReturnable<Integer> callbackInfo) {
    *///?} else if <=1.20.2 {
    /*@Inject(method = "performCommand", at = @At("HEAD"), cancellable = true)
    private void anticrash$blockCommandsDuringCheckpoint(
            com.mojang.brigadier.ParseResults<CommandSourceStack> parse,
            String command,
            CallbackInfoReturnable<Integer> callbackInfo) {
    *///?} else {
    @Inject(method = "performCommand", at = @At("HEAD"), cancellable = true)
    private void anticrash$blockCommandsDuringCheckpoint(
            com.mojang.brigadier.ParseResults<CommandSourceStack> parse,
            String command,
            CallbackInfo callbackInfo) {
    //?}
        //? if (>=1.19.1 && <=26.3) {
        CommandSourceStack source = parse.getContext().getSource();
        //?}

        AntiCrashController controller = AntiCrashMod.controller();
        if (controller == null) return;
        if (RecoveryCommandPolicy.isInternalExecution()) return;
        String normalized = RecoveryCommandPolicy.normalize(command);
        if (controller.isCheckpointFrozen()) {
            // A clean stop must be able to enter the deferred final-checkpoint path. The other
            // permitted commands are read-only AntiCrash inspection commands. Refusing silently
            // made a command issued inside the sub-second barrier look like a broken feature, so
            // say why and let the operator simply run it again.
            if (!RecoveryCommandPolicy.allowedDuringCheckpoint(normalized)) {

                source.sendFailure(
                        controller.chat(
                                MessageCatalog.text(
                                        controller.config().language, "checkpoint.command_blocked"),
                                null));

                //? if (>=1.18 && <=1.20.2) {
                /*callbackInfo.setReturnValue(0);
                *///?} else {
                callbackInfo.cancel();
                //?}
            }
            return;
        }
        // RCON, console wrappers and mod-issued command sources can mutate the World just as a
        // player can. Recovery safety therefore applies to every source; recovery controls and
        // read-only commands are explicitly allowed by the policy below.
        if (!controller.isRecoveryFrozen()) return;
        if (!RecoveryCommandPolicy.allowed(
                normalized, controller.config().recovery.lock.allowCommands)) {

            source.sendFailure(
                    controller.chat(
                            MessageCatalog.text(
                                    controller.config().language,
                                    "recovery.command_mutation_blocked"),
                            null));

            //? if (>=1.18 && <=1.20.2) {
            /*callbackInfo.setReturnValue(0);
            *///?} else {
            callbackInfo.cancel();
            //?}
        }
    }
}
