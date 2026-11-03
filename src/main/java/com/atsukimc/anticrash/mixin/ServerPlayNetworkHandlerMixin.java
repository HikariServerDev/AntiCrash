package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.AntiCrashController;
import com.atsukimc.anticrash.AntiCrashMod;
import com.atsukimc.anticrash.command.RecoveryCommandPolicy;

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.world.item.BlockItem;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.core.BlockPos;
//?}

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.world.item.ItemStack;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.network.protocol.game.*;
//?}

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.network.protocol.game.*;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
//?}

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
//?}

//? if (>=1.18 && <=1.21.11) {
/*//? if (>=1.18 && <=1.18.2) {
/^import net.minecraft.network.protocol.game.ClientboundBlockBreakAckPacket;
^///?}
//? if (>=1.19 && <=1.21.11) {
/^import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
^///?}
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.network.protocol.game.ServerboundChangeDifficultyPacket;
//?}

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.world.inventory.AbstractContainerMenu;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
//?}

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.world.inventory.ClickType;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
//?}

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.server.network.ServerGamePacketListenerImpl;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
//?}

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.server.level.ServerPlayer;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
//?}

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.world.InteractionHand;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.network.protocol.game.ServerboundLockDifficultyPacket;
//?}

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.core.BlockPos;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
//?}

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.world.phys.Vec3;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
//?}

//? if (>=26.1 && <=26.3) {
import net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket;
//?}

//? if (>=26.1 && <=26.3) {
import net.minecraft.network.protocol.game.ServerboundPickItemFromEntityPacket;
//?}

//? if (>=26.1 && <=26.3) {
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
//?}

//? if (>=26.1 && <=26.3) {
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
//?}

//? if (>=26.1 && <=26.3) {
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
//?}

//? if (>=26.1 && <=26.3) {
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
//?}

//? if (>=26.1 && <=26.3) {
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
//?}

//? if (>=26.1 && <=26.3) {
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
//?}

//? if (>=26.1 && <=26.3) {
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;
//?}

//? if (>=26.1 && <=26.3) {
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
//?}

//? if (>=26.1 && <=26.3) {
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
//?}

//? if (>=26.1 && <=26.3) {
import net.minecraft.server.level.ServerPlayer;
//?}

//? if (>=26.1 && <=26.3) {
import net.minecraft.server.network.ServerGamePacketListenerImpl;
//?}

//? if (>=26.1 && <=26.3) {
import net.minecraft.world.inventory.AbstractContainerMenu;
//?}

//? if (>=26.1 && <=26.3) {
import net.minecraft.world.inventory.ContainerInput;
//?}

//? if (>=26.1 && <=26.3) {
import net.minecraft.world.item.BlockItem;
//?}

//? if (>=26.1 && <=26.3) {
import net.minecraft.world.item.ItemStack;
//?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Packet-level recovery lock. Keepalive and read-only protocol traffic are not intercepted.
 *
 * <p>Only an operator-controlled recovery freeze refuses packets here, and every refusal is
 * answered so the client can undo its prediction. A checkpoint never refuses anything: its packets
 * wait in the held server task queue (see MinecraftServerMixin) and run once the cut is taken.
 */
//? if (>=1.18 && <=1.21.11) {
/*@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerPlayNetworkHandlerMixin {

    *///?} else if (>=26.1 && <=26.3) {
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerPlayNetworkHandlerMixin {
    //?}
    private AntiCrashController anticrash$controller() {
        return AntiCrashMod.controller();
    }

    //? if (>=1.18 && <=1.21.11) {
    /*private ServerPlayer anticrash$player()
                *///?} else if (>=26.1 && <=26.3) {
    private ServerPlayer anticrash$player()
                //?}
            {

        //? if (>=1.18 && <=1.21.11) {
        /*return ((ServerGamePacketListenerImpl) (Object) this).player;
        *///?} else if (>=26.1 && <=26.3) {
        return ((ServerGamePacketListenerImpl) (Object) this).player;
        //?}

    }

    private boolean anticrash$frozen() {
        AntiCrashController controller = anticrash$controller();
        return controller != null && controller.isRecoveryFrozen();
    }

    //? if (>=1.18 && <=1.21.11) {
    /*private ServerGamePacketListenerImpl anticrash$handler()
                *///?} else if (>=26.1 && <=26.3) {
    private ServerGamePacketListenerImpl anticrash$handler()
                //?}
            {

        //? if (>=1.18 && <=1.21.11) {
        /*return (ServerGamePacketListenerImpl) (Object) this;
        *///?} else if (>=26.1 && <=26.3) {
        return (ServerGamePacketListenerImpl) (Object) this;
        //?}

    }

    /** Runs on the server thread; malformed input must not throw from an unwrapped task. */

    //? if (>=1.19 && <=26.3) {
    private void anticrash$ackPrediction(int sequence) {
        if (sequence < 0) {
            //?}
            //? if (>=1.19 && <=1.21.11) {
            /*anticrash$handler()
                        .disconnect(
                                net.minecraft.network.chat.Component.translatable(
                                        "multiplayer.disconnect.invalid_packet"));
*///?} else if (>=26.1 && <=26.3) {
            anticrash$handler()
                    .disconnect(
                            net.minecraft.network.chat.Component.translatable(
                                    "multiplayer.disconnect.invalid_packet"));
            //?}
            //? if (>=1.19 && <=26.3) {

        } else {
            //?}
            //? if (>=1.19 && <=1.21.11) {
            /*anticrash$handler().ackBlockChangesUpTo(sequence);
*///?} else if (>=26.1 && <=26.3) {
            anticrash$handler().ackBlockChangesUpTo(sequence);
            //?}
            //? if (>=1.19 && <=26.3) {

        }
    }

    //?}

    /**
     * Answers a refused interaction the way Vanilla does.
     *
     * <p>Minecraft never drops a block or inventory action silently: {@code onPlayerAction} always
     * replies with {@link ClientboundBlockChangedAckPacket}, {@code handleUseItemOn} re-sends
     * the two affected block states, and {@code onClickSlot} calls {@link
     * AbstractContainerMenu#syncState()}. The client needs that answer to undo the change it already
     * predicted locally. Cancelling the packet at HEAD skipped every one of those replies, so a
     * block mined or placed during a checkpoint barrier or a recovery freeze stayed on the client
     * forever as a ghost block.
     *
     * <p>A HEAD injection also runs on the Netty thread, before Minecraft moves the packet to the
     * server thread, so the world must only be read from inside the scheduled task.
     */

    /**
     * Answers a refused interaction the way Vanilla does.
     *
     * <p>Minecraft never drops a block or inventory action silently: {@code onPlayerAction} always
     * replies with {@link ClientboundBlockChangedAckPacket}, {@code handleUseItemOn} re-sends
     * the two affected block states, and {@code onClickSlot} calls {@link
     * AbstractContainerMenu#sendAllDataToRemote()}. The client needs that answer to undo the change
     * it already predicted locally. Cancelling the packet at HEAD skipped every one of those
     * replies, so a block mined or placed during a checkpoint barrier or a recovery freeze stayed
     * on the client forever as a ghost block.
     *
     * <p>A HEAD injection also runs on the Netty thread, before Minecraft moves the packet to the
     * server thread, so the world must only be read from inside the scheduled task.
     */
    private void anticrash$resyncBlocks(BlockPos first, BlockPos second) {

        //? if (>=1.18 && <=1.21.11) {
        /*ServerPlayer player = anticrash$player();
        *///?} else if (>=26.1 && <=26.3) {
        ServerPlayer player = anticrash$player();
        //?}

        if (player == null) return;

        //? if (>=1.18 && <=1.21.5) {
        /*player.server.execute(
                () -> {
                    anticrash$handler()
                            //? if (>=1.18 && <=1.19.4) {
                            /^.send(new ClientboundBlockUpdatePacket(player.getLevel(), first));
                    ^///?} else if (>=1.20 && <=1.21.5) {
                    /^.send(new ClientboundBlockUpdatePacket(player.level(), first));
                            ^///?}
                    *///?} else if (>=1.21.6 && <=1.21.8) {
        /*player.getServer()
                                    .execute(() ->
*///?} else if (>=1.21.9 && <=1.21.11) {
        /*player.level()
                                    .getServer()
                                    .execute(() ->
*///?} else if (>=26.1 && <=26.3) {
        player.level()
                .getServer()
                .execute(
                        () ->
                        //?}
                        //? if (>=1.21.6 && <=26.3) {
                        {
                            //?}
                            //? if (>=1.21.6 && <=1.21.8) {
                            /*anticrash$handler()
                                                .send(new ClientboundBlockUpdatePacket(player.level(), first));
*///?} else if (>=1.21.9 && <=1.21.11) {
                            /*anticrash$handler()
                                                .send(new ClientboundBlockUpdatePacket(player.level(), first));
*///?} else if (>=26.1 && <=26.3) {
                            anticrash$handler()
                                    .send(new ClientboundBlockUpdatePacket(player.level(), first));
                            //?}

                            if (second != null)
                            //? if (>=1.18 && <=1.21.5) {
                            /*{
                        anticrash$handler()
                                //? if (>=1.18 && <=1.19.4) {
                                /^.send(new ClientboundBlockUpdatePacket(player.getLevel(), second));
                        ^///?} else if (>=1.20 && <=1.21.5) {
                        /^.send(new ClientboundBlockUpdatePacket(player.level(), second));
                                ^///?}
                    }
                }
                *///?} else if (>=1.21.6 && <=26.3) {
                            {
                                //?}
                                //? if (>=1.21.6 && <=1.21.8) {
                                /*anticrash$handler()
                                                .send(new ClientboundBlockUpdatePacket(player.level(), second));
*///?} else if (>=1.21.9 && <=1.21.11) {
                                /*anticrash$handler()
                                                .send(
                                                        new ClientboundBlockUpdatePacket(player.level(), second));
*///?} else if (>=26.1 && <=26.3) {
                                anticrash$handler()
                                        .send(
                                                new ClientboundBlockUpdatePacket(
                                                        player.level(), second));
                                //?}
                                //? if (>=1.21.6 && <=26.3) {

                            }
                        }
                        //?}
                        );
    }

    /** Tells the client the mining action was not approved so it restores the block it removed. */

    //? if (>=1.18 && <=1.21.11) {
    /*private void anticrash$resyncBlockBreak(ServerboundPlayerActionPacket packet)
                *///?} else if (>=26.1 && <=26.3) {
    private void anticrash$resyncBlockBreak(ServerboundPlayerActionPacket packet)
                //?}
            {

        //? if (>=1.18 && <=1.21.11) {
        /*ServerPlayer player = anticrash$player();
        *///?} else if (>=26.1 && <=26.3) {
        ServerPlayer player = anticrash$player();
        //?}

        if (player == null) return;
        BlockPos pos = packet.getPos();

        //? if (>=1.18 && <=1.18.2) {
        /*ServerboundPlayerActionPacket.Action action = packet.getAction();
        *///?}

        //? if (>=1.18 && <=1.18.2) {
        /*player.server.execute(() ->
                anticrash$handler()
                        .send(
                                new ClientboundBlockBreakAckPacket(
                                        pos,
                                        player.getLevel().getBlockState(pos),
                                        action,
                                        false,
                                        "AntiCrash freeze")));
        *///?} else if (>=1.19 && <=1.21.5) {
        /*player.server.execute(() -> {
                    // 1.19 replaced per-block approvals with a cumulative prediction sequence ACK.
                    //? if (>=1.19 && <=1.19.4) {
                    /^anticrash$handler().send(new ClientboundBlockUpdatePacket(player.getLevel(), pos));
                    ^///?} else if (>=1.20 && <=1.21.5) {
                    /^anticrash$handler().send(new ClientboundBlockUpdatePacket(player.level(), pos));
                    ^///?}
*///?} else if (>=1.21.6 && <=1.21.8) {
        /*player.getServer()
                        .execute(() ->
*///?} else if (>=1.21.9 && <=1.21.11) {
        /*player.level()
                        .getServer()
                        .execute(() ->
*///?} else if (>=26.1 && <=26.3) {
        player.level()
                .getServer()
                .execute(
                        () ->
                        //?}
                        //? if (>=1.21.6 && <=26.3) {
                        {
                            // 1.19 replaced per-block approvals with a cumulative prediction
                            // sequence ACK.
                            //?}
                            //? if (>=1.21.6 && <=1.21.8) {
                            /*anticrash$handler()
                                    .send(new ClientboundBlockUpdatePacket(player.level(), pos));
*///?} else if (>=1.21.9 && <=1.21.11) {
                            /*anticrash$handler()
                                    .send(new ClientboundBlockUpdatePacket(player.level(), pos));
*///?} else if (>=26.1 && <=26.3) {
                            anticrash$handler()
                                    .send(new ClientboundBlockUpdatePacket(player.level(), pos));
                            //?}

                            //? if (>=1.19 && <=26.3) {
                            anticrash$ackPrediction(packet.getSequence());
                            //?}
                            //? if (>=1.19 && <=1.21.5) {

                            /*}
*///?} else if (>=1.21.6 && <=26.3) {

                        }
                        //?}
                        //? if (>=1.19 && <=26.3) {
                        );
        //?}

    }

    /** Re-sends the authoritative ability flags after a refused toggle. */
    private void anticrash$resyncAbilities() {

        //? if (>=1.18 && <=1.21.11) {
        /*ServerPlayer player = anticrash$player();
        *///?} else if (>=26.1 && <=26.3) {
        ServerPlayer player = anticrash$player();
        //?}

        if (player == null) return;

        //? if (>=1.18 && <=1.21.5) {
        /*player.server.execute(player::onUpdateAbilities);
        *///?} else if (>=1.21.6 && <=1.21.8) {
        /*player.getServer().execute(player::onUpdateAbilities);
*///?} else if (>=1.21.9 && <=1.21.11) {
        /*player.level().getServer().execute(player::onUpdateAbilities);
*///?} else if (>=26.1 && <=26.3) {
        player.level().getServer().execute(player::onUpdateAbilities);
        //?}

    }

    /** Restores the client's predicted inventory, cursor stack and container contents. */
    private void anticrash$resyncInventory() {

        //? if (>=1.18 && <=1.21.11) {
        /*ServerPlayer player = anticrash$player();
        *///?} else if (>=26.1 && <=26.3) {
        ServerPlayer player = anticrash$player();
        //?}

        if (player == null) return;

        //? if (>=1.18 && <=1.21.5) {
        /*player.server.execute(
                () -> {

                    *///?} else if (>=1.21.6 && <=1.21.8) {
        /*player.getServer()
                                    .execute(() ->
*///?} else if (>=1.21.9 && <=1.21.11) {
        /*player.level()
                                    .getServer()
                                    .execute(() ->
*///?} else if (>=26.1 && <=26.3) {
        player.level()
                .getServer()
                .execute(
                        () ->
                        //?}
                        //? if (>=1.21.6 && <=26.3) {
                        {
                            //?}
                            //? if (>=1.18 && <=1.21.11) {
                            /*AbstractContainerMenu handler = player.containerMenu;
                    *///?} else if (>=26.1 && <=26.3) {
                            AbstractContainerMenu handler = player.containerMenu;
                            //?}

                            if (handler != null)
                                //? if (>=1.18 && <=1.21.11) {
                                /*handler.sendAllDataToRemote();
                    *///?}
                                //? if (>=1.18 && <=1.21.5) {

                                /*}
                *///?} else if (>=26.1 && <=26.3) {
                                handler.sendAllDataToRemote();
                            //?}
                            //? if (>=1.21.6 && <=26.3) {

                        }
                        //?}
                        );
    }

    /** Records a mutation only when the controller exists, matching the freeze checks above. */

    //? if (>=1.18 && <=1.21.11) {
    /*private void anticrash$record(
            String type,
            ServerPlayer player,
            String source,
            String destination,
            BlockPos position,
            ItemStack stack)
                *///?} else if (>=26.1 && <=26.3) {
    private void anticrash$record(
            String type,
            ServerPlayer player,
            String source,
            String destination,
            BlockPos position,
            ItemStack stack)
                //?}
            {
        // Mutation packets pass this HEAD injection twice: on the network thread and again
        // after Minecraft re-queues them to the server thread. Record only the authoritative pass.

        //? if (>=1.18 && <=1.21.5) {
        /*if (player == null || !player.server.isSameThread())
            *///?} else if (>=1.21.6 && <=1.21.8) {
        /*if (player == null || !player.getServer().isSameThread())
*///?} else if (>=1.21.9 && <=1.21.11) {
        /*if (player == null || !player.level().getServer().isSameThread())
*///?} else if (>=26.1 && <=26.3) {
        if (player == null || !player.level().getServer().isSameThread())
            //?}
            return;
        AntiCrashController controller = anticrash$controller();
        if (controller != null)
            controller.recordTransaction(type, player, source, destination, position, stack);
    }

    /**
     * Stops the player's own tick while a checkpoint holds the world at its cut.
     *
     * <p>The connection tick runs from MinecraftServer#tick, outside the frozen world tick, and
     * {@code playerTick} moves, damages and feeds the player and picks up items. Letting it run
     * would change the world after the cut; its packets are already waiting in the held task queue.
     * Pausing it keeps the whole barrier equivalent to a single long server tick, which is also
     * what Vanilla's movement checks expect when the queued packets are processed.
     */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void anticrash$holdPlayerTick(CallbackInfo info) {
        AntiCrashController controller = anticrash$controller();
        if (controller != null && controller.holdsServerTasks()) info.cancel();
    }

    //? if (>=1.18 && <=1.19) {
    /*@Inject(
            method = "handleChat(Lnet/minecraft/network/protocol/game/ServerboundChatPacket;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void anticrash$chat(ServerboundChatPacket packet, CallbackInfo info)
                *///?}

    // Consume only after Vanilla has validated the signature, unpacked the chain and
    // processed
    // ACKs.

    //? if (>=1.19.1 && <=1.21.11) {
    /*@Inject(method = "broadcastChatMessage", at = @At("HEAD"), cancellable = true)
                    private void anticrash$chat(
                            net.minecraft.network.chat.PlayerChatMessage packet, CallbackInfo info)
*///?} else if (>=26.1 && <=26.3) {
    @Inject(method = "broadcastChatMessage", at = @At("HEAD"), cancellable = true)
    private void anticrash$chat(
            net.minecraft.network.chat.PlayerChatMessage packet, CallbackInfo info)
                //?}
            {
        AntiCrashController controller = anticrash$controller();

        //? if (>=1.18 && <=1.19) {
        /*String message = packet.getMessage();
        *///?} else if (>=1.19.1 && <=1.19.2) {
        /*String message = packet.signedContent().plain();
*///?} else if (>=1.19.3 && <=1.21.11) {
        /*String message = packet.signedContent();
*///?} else if (>=26.1 && <=26.3) {
        String message = packet.signedContent();
        //?}

        if (controller != null
                && controller.isRecoveryAnswerCandidate(anticrash$player(), message)) {
            // Plain chat is handled on the network thread. Answering schedules a restore and
            // stops the server, which must happen on the server thread like /ac y does.
            info.cancel();

            //? if (>=1.18 && <=1.21.11) {
            /*ServerPlayer player = anticrash$player();
            *///?} else if (>=26.1 && <=26.3) {
            ServerPlayer player = anticrash$player();
            //?}

            //? if (>=1.18 && <=1.21.5) {
            /*player.server.execute(() -> controller.answerRecoveryFromChat(player, message));
            *///?} else if (>=1.21.6 && <=1.21.8) {
            /*player.getServer().execute(() -> controller.answerRecoveryFromChat(player, message));
*///?} else if (>=1.21.9 && <=1.21.11) {
            /*player.level()
                                .getServer()
                                .execute(() -> controller.answerRecoveryFromChat(player, message));
*///?} else if (>=26.1 && <=26.3) {
            player.level()
                    .getServer()
                    .execute(() -> controller.answerRecoveryFromChat(player, message));
            //?}

            return;
        }
        // A checkpoint barrier needs no filter here: a command reaches the world only through the
        // server task queue, which the barrier holds until its cut is taken.
        if (!anticrash$frozen()) return;
        var lock = controller.config().recovery.lock;
        if ((message.startsWith("/") && !RecoveryCommandPolicy.allowed(message, lock.allowCommands))
                || (!message.startsWith("/") && !lock.allowChat)) info.cancel();
    }

    //? if (>=1.18 && <=1.21.11) {
    /*@Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
    private void anticrash$move(ServerboundMovePlayerPacket packet, CallbackInfo info)
                *///?} else if (>=26.1 && <=26.3) {
    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
    private void anticrash$move(ServerboundMovePlayerPacket packet, CallbackInfo info)
                //?}
            {
        if (!anticrash$frozen()) return;
        var lock = anticrash$controller().config().recovery.lock;

        //? if (>=1.18 && <=1.21.11) {
        /*if ((packet.hasPosition() && !lock.allowPlayerMovement)
                || (packet.hasRotation() && !lock.allowPlayerLook))
            *///?} else if (>=26.1 && <=26.3) {
        if ((packet.hasPosition() && !lock.allowPlayerMovement)
                || (packet.hasRotation() && !lock.allowPlayerLook))
            //?}
            info.cancel();
    }

    //? if (>=1.18 && <=1.21.5) {
    /*@Inject(method = "handlePlayerInput", at = @At("HEAD"), cancellable = true)
    private void anticrash$input(ServerboundPlayerInputPacket packet, CallbackInfo info)
                *///?} else if (>=1.21.6 && <=1.21.11) {
    /*@Inject(
                            method = "handlePlayerInput",
                            at =
                                    @At(
                                            value = "INVOKE",
                                            target =
                                                    "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
                                            shift = At.Shift.AFTER),
                            cancellable = true)
                    private void anticrash$input(ServerboundPlayerInputPacket packet, CallbackInfo info)
*///?} else if (>=26.1 && <=26.3) {
    @Inject(
            method = "handlePlayerInput",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
                            shift = At.Shift.AFTER),
            cancellable = true)
    private void anticrash$input(ServerboundPlayerInputPacket packet, CallbackInfo info)
                //?}
            {
        if (!anticrash$frozen()) return;
        var lock = anticrash$controller().config().recovery.lock;

        //? if (>=1.18 && <=1.21.1) {
        /*if ((packet.isShiftKeyDown() && !lock.allowSneak)
                || (!lock.allowPlayerMovement
                        && (packet.getZza() != 0 || packet.getXxa() != 0 || packet.isJumping())))
            *///?} else if (>=1.21.2 && <=26.3) {
        var input = packet.input();
        //?}

        //? if (>=1.21.2 && <=1.21.5) {
        /*if ((input.shift() && !lock.allowSneak)
                            || (!lock.allowPlayerMovement
                                    && (input.forward()
                                            || input.backward()
                                            || input.left()
                                            || input.right()
                                            || input.jump())))
*///?} else if (>=1.21.6 && <=1.21.11) {
        /*if (!com.atsukimc.anticrash.integration.carpet.PlayerInputCodec.allowedDuringFreeze(
                            anticrash$player().getLastClientInput(),
                            input,
                            lock.allowPlayerMovement,
                            lock.allowSneak))
*///?} else if (>=26.1 && <=26.3) {
        if (!com.atsukimc.anticrash.integration.carpet.PlayerInputCodec.allowedDuringFreeze(
                anticrash$player().getLastClientInput(),
                input,
                lock.allowPlayerMovement,
                lock.allowSneak))
            //?}
            info.cancel();
    }

    //? if (>=1.18 && <=1.21.11) {
    /*@Inject(method = "handleMoveVehicle", at = @At("HEAD"), cancellable = true)
    private void anticrash$vehicleMove(ServerboundMoveVehiclePacket packet, CallbackInfo info)
                *///?} else if (>=26.1 && <=26.3) {
    @Inject(method = "handleMoveVehicle", at = @At("HEAD"), cancellable = true)
    private void anticrash$vehicleMove(ServerboundMoveVehiclePacket packet, CallbackInfo info)
                //?}
            {
        if (anticrash$frozen()
                && !anticrash$controller().config().recovery.lock.allowPlayerMovement)
            info.cancel();
    }

    //? if (>=1.18 && <=1.21.11) {
    /*@Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void anticrash$action(ServerboundPlayerActionPacket packet, CallbackInfo info)
                *///?} else if (>=26.1 && <=26.3) {
    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void anticrash$action(ServerboundPlayerActionPacket packet, CallbackInfo info)
                //?}
            {
        var action = packet.getAction();

        //? if (>=1.18 && <=1.21.11) {
        /*boolean block =
                action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK
                        || action == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK
                        || action == ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK;
        *///?} else if (>=26.1 && <=26.3) {
        boolean block =
                action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK
                        || action == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK
                        || action == ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK;
        //?}

        //? if (>=1.18 && <=1.21.11) {
        /*boolean drop =
                action == ServerboundPlayerActionPacket.Action.DROP_ITEM
                        || action == ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS;
        *///?} else if (>=26.1 && <=26.3) {
        boolean drop =
                action == ServerboundPlayerActionPacket.Action.DROP_ITEM
                        || action == ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS;
        //?}

        //? if (>=1.18 && <=1.21.11) {
        /*boolean use =
                action == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM
                        || action == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND;
        *///?} else if (>=26.1 && <=26.3) {
        boolean use =
                action == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM
                        || action == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND;
        //?}

        AntiCrashController controller = anticrash$controller();
        boolean refused = false;
        if (anticrash$frozen()) {
            var lock = controller.config().recovery.lock;
            refused =
                    (block && !lock.allowBlockBreak)
                            || (drop && !lock.allowItemDrop)
                            || (use && !lock.allowItemUse);
        }
        if (refused) {
            info.cancel();
            if (block) anticrash$resyncBlockBreak(packet);
            else anticrash$resyncInventory();
            return;
        }
        // Actual block breaks are recorded at ServerPlayerGameMode.tryBreakBlock.
        if (drop)

            //? if (>=1.18 && <=1.21.11) {
            /*anticrash$record(
                    "ITEM_DROP",
                    anticrash$player(),
                    "player",
                    "world",
                    packet.getPos(),
                    anticrash$player().getMainHandItem());
        *///?} else if (>=26.1 && <=26.3) {
            anticrash$record(
                    "ITEM_DROP",
                    anticrash$player(),
                    "player",
                    "world",
                    packet.getPos(),
                    anticrash$player().getMainHandItem());
        //?}

    }

    //? if (>=1.18 && <=1.21.11) {
    /*@Inject(method = "handleUseItemOn", at = @At("HEAD"), cancellable = true)
    private void anticrash$blockInteract(ServerboundUseItemOnPacket packet, CallbackInfo info)
                *///?} else if (>=26.1 && <=26.3) {
    @Inject(method = "handleUseItemOn", at = @At("HEAD"), cancellable = true)
    private void anticrash$blockInteract(ServerboundUseItemOnPacket packet, CallbackInfo info)
                //?}
            {

        //? if (>=1.18 && <=1.21.11) {
        /*ServerPlayer player = anticrash$player();
        *///?} else if (>=26.1 && <=26.3) {
        ServerPlayer player = anticrash$player();
        //?}

        //? if (>=1.18 && <=1.21.11) {
        /*BlockPos pos = packet.getHitResult().getBlockPos();
        *///?} else if (>=26.1 && <=26.2) {
        /*BlockPos pos = packet.getHitResult().getBlockPos();
*///?} else if 26.3 {
        BlockPos pos = packet.hitResult().getBlockPos();
        //?}

        //? if (>=1.18 && <=1.21.11) {
        /*BlockPos placement = pos.relative(packet.getHitResult().getDirection());
        *///?} else if (>=26.1 && <=26.2) {
        /*BlockPos placement = pos.relative(packet.getHitResult().getDirection());
*///?} else if 26.3 {
        BlockPos placement = pos.relative(packet.hitResult().getDirection());
        //?}

        AntiCrashController controller = anticrash$controller();

        //? if (>=1.18 && <=1.21.11) {
        /*boolean place = player.getItemInHand(packet.getHand()).getItem() instanceof BlockItem;
        *///?} else if (>=26.1 && <=26.2) {
        /*boolean place = player.getItemInHand(packet.getHand()).getItem() instanceof BlockItem;
*///?} else if 26.3 {
        boolean place = player.getItemInHand(packet.hand()).getItem() instanceof BlockItem;
        //?}

        if (anticrash$frozen()) {
            var lock = controller.config().recovery.lock;
            boolean refused = place ? !lock.allowBlockPlace : !lock.allowItemUse;
            if (!refused && !lock.allowContainerInteraction) {
                // Only a container changes the outcome here, and identifying one reads the world.
                // That read is safe only once Minecraft has moved this packet to the server thread,
                // so let the packet through and decide again on that second pass.

                //? if (>=1.18 && <=1.21.5) {
                /*if (Thread.currentThread() != player.server.getRunningThread())
                    *///?} else if (>=1.21.6 && <=1.21.8) {
                /*if (Thread.currentThread() != player.getServer().getRunningThread())
*///?} else if (>=1.21.9 && <=1.21.11) {
                /*if (Thread.currentThread() != player.level().getServer().getRunningThread())
*///?} else if (>=26.1 && <=26.3) {
                if (Thread.currentThread() != player.level().getServer().getRunningThread())
                    //?}
                    return;

                //? if (>=1.18 && <=1.21.8) {
                /*//? if (>=1.18 && <=1.19.4) {
                /^refused = player.getLevel().getBlockEntity(pos) != null;
                ^///?} else if (>=1.20 && <=1.21.8) {
                /^refused = player.level().getBlockEntity(pos) != null;
                ^///?}
                *///?} else if (>=1.21.9 && <=1.21.11) {
                /*refused = player.level().getBlockEntity(pos) != null;
*///?} else if (>=26.1 && <=26.3) {
                refused = player.level().getBlockEntity(pos) != null;
                //?}

            }
            if (refused) {
                info.cancel();
                anticrash$resyncBlocks(pos, placement);

                //? if (>=1.19 && <=1.21.5) {
                /*player.server.execute(() -> anticrash$ackPrediction(packet.getSequence()));
*///?} else if (>=1.21.6 && <=1.21.8) {
                /*player.getServer().execute(() -> anticrash$ackPrediction(packet.getSequence()));
*///?} else if (>=1.21.9 && <=1.21.11) {
                /*player.level()
                                        .getServer()
                                        .execute(() -> anticrash$ackPrediction(packet.getSequence()));
*///?} else if (>=26.1 && <=26.2) {
                /*player.level()
                                        .getServer()
                                        .execute(() -> anticrash$ackPrediction(packet.getSequence()));
*///?} else if 26.3 {
                player.level()
                        .getServer()
                        .execute(() -> anticrash$ackPrediction(packet.sequence()));
                //?}

                anticrash$resyncInventory();
                return;
            }
        }

        //? if (>=1.18 && <=1.21.11) {
        /*anticrash$record(
                place ? "BLOCK_PLACE" : "ITEM_USE",
                player,
                "player",
                "world",
                pos,
                player.getItemInHand(packet.getHand()));
        *///?} else if (>=26.1 && <=26.2) {
        /*anticrash$record(
                        place ? "BLOCK_PLACE" : "ITEM_USE",
                        player,
                        "player",
                        "world",
                        pos,
                        player.getItemInHand(packet.getHand()));
*///?} else if 26.3 {
        anticrash$record(
                place ? "BLOCK_PLACE" : "ITEM_USE",
                player,
                "player",
                "world",
                pos,
                player.getItemInHand(packet.hand()));
        //?}

    }

    //? if (>=1.18 && <=1.21.11) {
    /*@Inject(method = "handleUseItem", at = @At("HEAD"), cancellable = true)
    private void anticrash$itemInteract(ServerboundUseItemPacket packet, CallbackInfo info)
                *///?} else if (>=26.1 && <=26.3) {
    @Inject(method = "handleUseItem", at = @At("HEAD"), cancellable = true)
    private void anticrash$itemInteract(ServerboundUseItemPacket packet, CallbackInfo info)
                //?}
            {
        if (anticrash$frozen() && !anticrash$controller().config().recovery.lock.allowItemUse) {
            info.cancel();
            anticrash$resyncInventory();

            //? if (>=1.19 && <=1.21.11) {
            /*ServerPlayer player = anticrash$player();
*///?} else if (>=26.1 && <=26.3) {
            ServerPlayer player = anticrash$player();
            //?}

            //? if (>=1.19 && <=1.21.5) {
            /*player.server.execute(() -> anticrash$ackPrediction(packet.getSequence()));
*///?} else if (>=1.21.6 && <=1.21.8) {
            /*player.getServer().execute(() -> anticrash$ackPrediction(packet.getSequence()));
*///?} else if (>=1.21.9 && <=1.21.11) {
            /*player.level()
                                .getServer()
                                .execute(() -> anticrash$ackPrediction(packet.getSequence()));
*///?} else if (>=26.1 && <=26.2) {
            /*player.level().getServer().execute(() -> anticrash$ackPrediction(packet.getSequence()));
*///?} else if 26.3 {
            player.level().getServer().execute(() -> anticrash$ackPrediction(packet.sequence()));
            //?}

            return;
        }

        //? if (>=1.18 && <=1.21.11) {
        /*anticrash$record(
                "ITEM_USE",
                anticrash$player(),
                "player",
                "world",
                null,
                anticrash$player().getItemInHand(packet.getHand()));
        *///?} else if (>=26.1 && <=26.2) {
        /*anticrash$record(
                        "ITEM_USE",
                        anticrash$player(),
                        "player",
                        "world",
                        null,
                        anticrash$player().getItemInHand(packet.getHand()));
*///?} else if 26.3 {
        anticrash$record(
                "ITEM_USE",
                anticrash$player(),
                "player",
                "world",
                null,
                anticrash$player().getItemInHand(packet.hand()));
        //?}

    }

    //? if (>=1.18 && <=1.21.11) {
    /*@Inject(method = "handleInteract", at = @At("HEAD"), cancellable = true)
    private void anticrash$entityInteract(ServerboundInteractPacket packet, CallbackInfo info)
                *///?} else if (>=26.1 && <=26.3) {
    @Inject(
            method = "handleInteract",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
                            shift = At.Shift.AFTER),
            cancellable = true)
    private void anticrash$entityInteract(ServerboundInteractPacket packet, CallbackInfo info)
                //?}
            {

        //? if (>=1.18 && <=1.21.11) {
        /*boolean attack = anticrash$entityActionIsAttack(packet);
        *///?}

        if (anticrash$frozen()) {
            var lock = anticrash$controller().config().recovery.lock;

            //? if (>=1.18 && <=1.21.11) {
            /*if ((attack && !lock.allowEntityAttack) || (!attack && !lock.allowEntityInteract))
            *///?} else if (>=26.1 && <=26.3) {
            if (!lock.allowEntityInteract)
            //?}
            {
                info.cancel();
                anticrash$resyncInventory();
                return;
            }
        }

        //? if (>=1.18 && <=1.21.11) {
        /*anticrash$record(
                attack ? "ENTITY_ATTACK" : "ENTITY_INTERACT",
                anticrash$player(),
                "player",
                "entity",
                null,
                anticrash$player().getMainHandItem());
        *///?} else if (>=26.1 && <=26.3) {
        anticrash$record(
                "ENTITY_INTERACT",
                anticrash$player(),
                "player",
                "entity",
                null,
                anticrash$player().getMainHandItem());
    }

    //?}

    // 26.1 has a separate attack packet. Cancelling only Interact no longer prevents attacks.

    //? if (>=26.1 && <=26.3) {
    @Inject(
            method = "handleAttack",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
                            shift = At.Shift.AFTER),
            cancellable = true)
    private void anticrash$entityAttack(ServerboundAttackPacket packet, CallbackInfo info) {
        if (anticrash$frozen()
                && !anticrash$controller().config().recovery.lock.allowEntityAttack) {
            info.cancel();
            //?}

            //? if (>=26.1 && <=26.3) {
            anticrash$resyncInventory();
            //?}

            //? if (>=26.1 && <=26.3) {
            return;
        }
        //?}

        //? if (>=26.1 && <=26.3) {
        anticrash$record(
                "ENTITY_ATTACK",
                anticrash$player(),
                "player",
                "entity",
                null,
                anticrash$player().getMainHandItem());
        //?}

    }

    //? if (>=1.18 && <=1.21.4) {
    /*@Inject(method = "handleContainerClick", at = @At("HEAD"), cancellable = true)
    private void anticrash$click(ServerboundContainerClickPacket packet, CallbackInfo info)
                *///?} else if (>=1.21.5 && <=1.21.11) {
    /*@Inject(
                            method = "handleContainerClick",
                            at =
                                    @At(
                                            value = "INVOKE",
                                            target =
                                                    "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
                                            shift = At.Shift.AFTER),
                            cancellable = true)
                    private void anticrash$click(ServerboundContainerClickPacket packet, CallbackInfo info)
*///?} else if (>=26.1 && <=26.3) {
    @Inject(
            method = "handleContainerClick",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
                            shift = At.Shift.AFTER),
            cancellable = true)
    private void anticrash$click(ServerboundContainerClickPacket packet, CallbackInfo info)
                //?}
            {

        //? if (>=1.18 && <=1.21.4) {
        /*boolean drop = packet.getClickType() == ClickType.THROW;
        *///?} else if (>=1.21.5 && <=1.21.11) {
        /*boolean drop = packet.clickType() == ClickType.THROW;
*///?} else if (>=26.1 && <=26.3) {
        boolean drop = packet.containerInput() == ContainerInput.THROW;
        //?}

        //? if (>=26.1 && <=26.3) {
        if (anticrash$frozen()) {
            var lock = anticrash$controller().config().recovery.lock;
            //?}

            //? if (>=26.1 && <=26.3) {
            if ((drop && !lock.allowItemDrop) || (!drop && !lock.allowContainerInteraction)) {
                info.cancel();
                //?}

                //? if (>=26.1 && <=26.3) {
                anticrash$resyncInventory();
                //?}

                //? if (>=26.1 && <=26.3) {
                return;
            }
        }
        //?}

        // The packet now contains hashes, not trusted ItemStacks. Log the server-owned cursor.

        //? if (>=26.1 && <=26.3) {
        anticrash$record(
                drop ? "ITEM_DROP" : "CONTAINER_INTERACTION",
                anticrash$player(),
                "player",
                "container",
                null,
                anticrash$player().containerMenu.getCarried());
    }

    //?}

    //? if (>=26.1 && <=26.3) {
    @Inject(method = "handleSetCreativeModeSlot", at = @At("HEAD"), cancellable = true)
    private void anticrash$creative(
            ServerboundSetCreativeModeSlotPacket packet, CallbackInfo info) {
        boolean drop = packet.slotNum() < 0;
        //?}

        if (anticrash$frozen()) {
            var lock = anticrash$controller().config().recovery.lock;
            if ((drop && !lock.allowItemDrop) || (!drop && !lock.allowContainerInteraction)) {
                info.cancel();
                anticrash$resyncInventory();
                return;
            }
        }

        //? if (>=1.18 && <=1.21.4) {
        /*anticrash$record(
                drop ? "ITEM_DROP" : "CONTAINER_INTERACTION",
                anticrash$player(),
                "player",
                "container",
                null,
                packet.getCarriedItem());
        *///?}

        // The packet now contains hashes, not trusted ItemStacks. Log the server-owned cursor.

        //? if (>=1.21.5 && <=1.21.11) {
        /*anticrash$record(
                        drop ? "ITEM_DROP" : "CONTAINER_INTERACTION",
                        anticrash$player(),
                        "player",
                        "container",
                        null,
                        anticrash$player().containerMenu.getCarried());
*///?} else if (>=26.1 && <=26.3) {
        anticrash$record(
                drop ? "ITEM_DROP" : "CREATIVE_INVENTORY",
                anticrash$player(),
                "player",
                drop ? "world" : "inventory",
                null,
                packet.itemStack());
        //?}

    }

    //? if (>=1.18 && <=1.21.11) {
    /*@Inject(method = "handleSetCreativeModeSlot", at = @At("HEAD"), cancellable = true)
    private void anticrash$creative(
            ServerboundSetCreativeModeSlotPacket packet, CallbackInfo info) {

        *///?}
    //? if (>=1.18 && <=1.20.4) {
    /*boolean drop = packet.getSlotNum() < 0;
        *///?} else if (>=1.20.5 && <=1.21.11) {
    /*boolean drop = packet.slotNum() < 0;
*///?}

    //? if (>=1.18 && <=1.21.11) {
    /*if (anticrash$frozen()) {
            var lock = anticrash$controller().config().recovery.lock;
            *///?}

    //? if (>=1.18 && <=1.21.11) {
    /*if ((drop && !lock.allowItemDrop) || (!drop && !lock.allowContainerInteraction)) {
                info.cancel();
                *///?}

    //? if (>=1.18 && <=1.21.11) {
    /*anticrash$resyncInventory();
                *///?}

    //? if (>=1.18 && <=1.21.11) {
    /*return;
            }
        }
        *///?}

    //? if (>=1.18 && <=1.20.1) {
    /*anticrash$record(
                drop ? "ITEM_DROP" : "CREATIVE_INVENTORY",
                anticrash$player(),
                "player",
                drop ? "world" : "inventory",
                null,
                packet.getItem());
        *///?} else if (>=1.20.2 && <=1.20.4) {
    /*anticrash$record(
                        drop ? "ITEM_DROP" : "CREATIVE_INVENTORY",
                        anticrash$player(),
                        "player",
                        drop ? "world" : "inventory",
                        null,
                        packet.getItem());
*///?} else if (>=1.20.5 && <=1.21.11) {
    /*anticrash$record(
                        drop ? "ITEM_DROP" : "CREATIVE_INVENTORY",
                        anticrash$player(),
                        "player",
                        drop ? "world" : "inventory",
                        null,
                        packet.itemStack());
*///?}
    //? if (>=1.18 && <=1.21.11) {

    /*}

    *///?}

    //? if (>=1.18 && <=1.21.11) {
    /*@Inject(method = "handleContainerClose", at = @At("HEAD"), cancellable = true)
    private void anticrash$closeScreen(ServerboundContainerClosePacket packet, CallbackInfo info)
                *///?} else if (>=26.1 && <=26.3) {
    @Inject(method = "handleContainerClose", at = @At("HEAD"), cancellable = true)
    private void anticrash$closeScreen(ServerboundContainerClosePacket packet, CallbackInfo info)
                //?}
            {
        if (anticrash$frozen()
                && !anticrash$controller().config().recovery.lock.allowContainerInteraction) {
            info.cancel();
            anticrash$resyncInventory();
            return;
        }

        //? if (>=1.18 && <=1.21.11) {
        /*anticrash$record(
                "CONTAINER_CLOSE",
                anticrash$player(),
                "container",
                "player",
                null,
                anticrash$player().getMainHandItem());
        *///?} else if (>=26.1 && <=26.3) {
        anticrash$record(
                "CONTAINER_CLOSE",
                anticrash$player(),
                "container",
                "player",
                null,
                anticrash$player().getMainHandItem());
        //?}

    }

    //? if (>=1.18 && <=1.21.11) {
    /*@Inject(method = "handleSelectTrade", at = @At("HEAD"), cancellable = true)
    private void anticrash$merchant(ServerboundSelectTradePacket packet, CallbackInfo info)
                *///?} else if (>=26.1 && <=26.3) {
    @Inject(method = "handleSelectTrade", at = @At("HEAD"), cancellable = true)
    private void anticrash$merchant(ServerboundSelectTradePacket packet, CallbackInfo info)
                //?}
            {
        if (anticrash$frozen()
                && !anticrash$controller().config().recovery.lock.allowContainerInteraction) {
            info.cancel();
            anticrash$resyncInventory();
            return;
        }

        //? if (>=1.18 && <=1.21.11) {
        /*anticrash$record(
                "MERCHANT_SELECTION",
                anticrash$player(),
                "player",
                "merchant",
                null,
                anticrash$player().getMainHandItem());
        *///?} else if (>=26.1 && <=26.3) {
        anticrash$record(
                "MERCHANT_SELECTION",
                anticrash$player(),
                "player",
                "merchant",
                null,
                anticrash$player().getMainHandItem());
        //?}

    }

    //? if (>=1.18 && <=1.21.3) {
    /*@Inject(method = "handlePickItem", at = @At("HEAD"), cancellable = true)
    private void anticrash$pickInventory(ServerboundPickItemPacket packet, CallbackInfo info)
                *///?} else if (>=1.21.4 && <=1.21.11) {
    /*@Inject(
                            method = "handlePickItemFromBlock",
                            at =
                                    @At(
                                            value = "INVOKE",
                                            target =
                                                    "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
                                            shift = At.Shift.AFTER),
                            cancellable = true)
                    private void anticrash$pickBlock(ServerboundPickItemFromBlockPacket packet, CallbackInfo info)
*///?} else if (>=26.1 && <=26.3) {
    @Inject(
            method = "handlePickItemFromBlock",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
                            shift = At.Shift.AFTER),
            cancellable = true)
    private void anticrash$pickBlock(ServerboundPickItemFromBlockPacket packet, CallbackInfo info)
                //?}
                //? if (>=1.21.4 && <=26.3) {
            {
        anticrash$pickInventory(info);
    }

    //?}

    //? if (>=1.21.4 && <=1.21.11) {
    /*@Inject(
                            method = "handlePickItemFromEntity",
                            at =
                                    @At(
                                            value = "INVOKE",
                                            target =
                                                    "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
                                            shift = At.Shift.AFTER),
                            cancellable = true)
                    private void anticrash$pickEntity(ServerboundPickItemFromEntityPacket packet, CallbackInfo info)
*///?} else if (>=26.1 && <=26.3) {
    @Inject(
            method = "handlePickItemFromEntity",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
                            shift = At.Shift.AFTER),
            cancellable = true)
    private void anticrash$pickEntity(
            ServerboundPickItemFromEntityPacket packet, CallbackInfo info)
                //?}
                //? if (>=1.21.4 && <=26.3) {
            {
        anticrash$pickInventory(info);
    }

    //?}

    //? if (>=1.21.4 && <=26.3) {
    private void anticrash$pickInventory(CallbackInfo info)
                //?}
            {
        if (anticrash$frozen()
                && !anticrash$controller().config().recovery.lock.allowContainerInteraction) {
            info.cancel();
            anticrash$resyncInventory();
            return;
        }

        //? if (>=1.18 && <=1.21.11) {
        /*anticrash$record(
                "INVENTORY_PICK",
                anticrash$player(),
                "inventory",
                "hotbar",
                null,
                anticrash$player().getMainHandItem());
        *///?} else if (>=26.1 && <=26.3) {
        anticrash$record(
                "INVENTORY_PICK",
                anticrash$player(),
                "inventory",
                "hotbar",
                null,
                anticrash$player().getMainHandItem());
        //?}

    }

    //? if (>=1.18 && <=1.21.11) {
    /*@Inject(method = "handleTeleportToEntityPacket", at = @At("HEAD"), cancellable = true)
    private void anticrash$spectator(ServerboundTeleportToEntityPacket packet, CallbackInfo info)
                *///?} else if (>=26.1 && <=26.3) {
    @Inject(method = "handleTeleportToEntityPacket", at = @At("HEAD"), cancellable = true)
    private void anticrash$spectator(ServerboundTeleportToEntityPacket packet, CallbackInfo info)
                //?}
            {
        if (anticrash$frozen()
                && !anticrash$controller().config().recovery.lock.allowPlayerMovement)
            info.cancel();
    }

    //? if (>=1.18 && <=1.21.11) {
    /*@Inject(method = "handleClientCommand", at = @At("HEAD"), cancellable = true)
    private void anticrash$status(ServerboundClientCommandPacket packet, CallbackInfo info)
                *///?} else if (>=26.1 && <=26.3) {
    @Inject(method = "handleClientCommand", at = @At("HEAD"), cancellable = true)
    private void anticrash$status(ServerboundClientCommandPacket packet, CallbackInfo info)
                //?}
            {
        if (anticrash$frozen()) info.cancel();
    }

    //? if (>=1.18 && <=1.21.11) {
    /*@Inject(method = "handlePlayerAbilities", at = @At("HEAD"), cancellable = true)
    private void anticrash$abilities(ServerboundPlayerAbilitiesPacket packet, CallbackInfo info)
                *///?} else if (>=26.1 && <=26.3) {
    @Inject(method = "handlePlayerAbilities", at = @At("HEAD"), cancellable = true)
    private void anticrash$abilities(ServerboundPlayerAbilitiesPacket packet, CallbackInfo info)
                //?}
            {
        // Vanilla only copies the client's flying flag onto the player here. Dropping it left the
        // server-side abilities.flying stale, which is exactly the flag Carpet's creativeNoClip
        // reads, so only the explicit recovery lock refuses it (and then re-sends the flags).
        if (!anticrash$frozen()) return;
        if (!anticrash$controller().config().recovery.lock.allowPlayerMovement) {
            info.cancel();
            anticrash$resyncAbilities();
        }
    }

    //? if (>=1.18 && <=1.21.11) {
    /*@Inject(method = "handleChangeDifficulty", at = @At("HEAD"), cancellable = true)
    private void anticrash$difficulty(ServerboundChangeDifficultyPacket packet, CallbackInfo info)
                *///?} else if (>=26.1 && <=26.3) {
    @Inject(method = "handleChangeDifficulty", at = @At("HEAD"), cancellable = true)
    private void anticrash$difficulty(ServerboundChangeDifficultyPacket packet, CallbackInfo info)
                //?}
            {
        if (anticrash$frozen()) info.cancel();
    }

    //? if (>=1.18 && <=1.21.11) {
    /*@Inject(method = "handleLockDifficulty", at = @At("HEAD"), cancellable = true)
    private void anticrash$difficultyLock(
            ServerboundLockDifficultyPacket packet, CallbackInfo info)
                *///?} else if (>=26.1 && <=26.3) {
    @Inject(method = "handleLockDifficulty", at = @At("HEAD"), cancellable = true)
    private void anticrash$difficultyLock(
            ServerboundLockDifficultyPacket packet, CallbackInfo info)
                //?}
            {
        if (anticrash$frozen()) info.cancel();
    }

    //? if (>=1.18 && <=1.21.11) {
    /*@Inject(method = "handleSetCommandBlock", at = @At("HEAD"), cancellable = true)
    private void anticrash$commandBlock(CallbackInfo info)
                *///?} else if (>=26.1 && <=26.3) {
    @Inject(method = "handleSetCommandBlock", at = @At("HEAD"), cancellable = true)
    private void anticrash$commandBlock(CallbackInfo info)
                //?}
            {
        if (anticrash$frozen()) info.cancel();
    }

    //? if (>=1.18 && <=1.21.11) {
    /*@Inject(method = "handleSetCommandMinecart", at = @At("HEAD"), cancellable = true)
    private void anticrash$commandBlockMinecart(CallbackInfo info)
                *///?} else if (>=26.1 && <=26.3) {
    @Inject(method = "handleSetCommandMinecart", at = @At("HEAD"), cancellable = true)
    private void anticrash$commandBlockMinecart(CallbackInfo info)
                //?}
            {
        if (anticrash$frozen()) info.cancel();
    }

    //? if (>=1.18 && <=1.21.11) {
    /*@Inject(method = "handleSetBeaconPacket", at = @At("HEAD"), cancellable = true)
    private void anticrash$beacon(CallbackInfo info)
                *///?} else if (>=26.1 && <=26.3) {
    @Inject(method = "handleSetBeaconPacket", at = @At("HEAD"), cancellable = true)
    private void anticrash$beacon(CallbackInfo info)
                //?}
            {
        if (anticrash$frozen()) info.cancel();
    }

    //? if (>=1.18 && <=1.21.11) {
    /*@Inject(method = "handleSetStructureBlock", at = @At("HEAD"), cancellable = true)
    private void anticrash$structure(CallbackInfo info)
                *///?} else if (>=26.1 && <=26.3) {
    @Inject(method = "handleSetStructureBlock", at = @At("HEAD"), cancellable = true)
    private void anticrash$structure(CallbackInfo info)
                //?}
            {
        if (anticrash$frozen()) info.cancel();
    }

    //? if (>=1.18 && <=1.21.11) {
    /*@Inject(method = "handleSetJigsawBlock", at = @At("HEAD"), cancellable = true)
    private void anticrash$jigsaw(CallbackInfo info)
                *///?} else if (>=26.1 && <=26.3) {
    @Inject(method = "handleSetJigsawBlock", at = @At("HEAD"), cancellable = true)
    private void anticrash$jigsaw(CallbackInfo info)
                //?}
            {
        if (anticrash$frozen()) info.cancel();
    }

    //? if (>=1.18 && <=1.21.11) {
    /*@Inject(method = "handleJigsawGenerate", at = @At("HEAD"), cancellable = true)
    private void anticrash$jigsawGenerate(CallbackInfo info)
                *///?} else if (>=26.1 && <=26.3) {
    @Inject(method = "handleJigsawGenerate", at = @At("HEAD"), cancellable = true)
    private void anticrash$jigsawGenerate(CallbackInfo info)
                //?}
            {
        if (anticrash$frozen()) info.cancel();
    }

    //? if (>=1.18 && <=1.21.11) {
    /*@Inject(method = "handleEditBook", at = @At("HEAD"), cancellable = true)
    private void anticrash$book(CallbackInfo info)
                *///?} else if (>=26.1 && <=26.3) {
    @Inject(method = "handleEditBook", at = @At("HEAD"), cancellable = true)
    private void anticrash$book(CallbackInfo info)
                //?}
            {
        if (anticrash$frozen()) info.cancel();
    }

    //? if (>=1.18 && <=1.21.11) {
    /*@Inject(method = "handleRenameItem", at = @At("HEAD"), cancellable = true)
    private void anticrash$rename(CallbackInfo info)
                *///?} else if (>=26.1 && <=26.3) {
    @Inject(method = "handleRenameItem", at = @At("HEAD"), cancellable = true)
    private void anticrash$rename(CallbackInfo info)
                //?}
            {
        if (anticrash$frozen()) {
            info.cancel();
            anticrash$resyncInventory();
        }
    }

    //? if (>=1.18 && <=1.21.11) {
    /*@Inject(method = "handleSignUpdate", at = @At("HEAD"), cancellable = true)
    private void anticrash$sign(CallbackInfo info)
                *///?} else if (>=26.1 && <=26.3) {
    @Inject(method = "handleSignUpdate", at = @At("HEAD"), cancellable = true)
    private void anticrash$sign(CallbackInfo info)
                //?}
            {
        if (anticrash$frozen()) info.cancel();
    }

    //? if (>=1.18 && <=1.21.11) {
    /*@Inject(method = "handlePlaceRecipe", at = @At("HEAD"), cancellable = true)
    private void anticrash$craft(CallbackInfo info)
                *///?} else if (>=26.1 && <=26.3) {
    @Inject(method = "handlePlaceRecipe", at = @At("HEAD"), cancellable = true)
    private void anticrash$craft(CallbackInfo info)
                //?}
            {
        if (anticrash$frozen()
                && !anticrash$controller().config().recovery.lock.allowContainerInteraction) {
            info.cancel();
            anticrash$resyncInventory();
        }
    }

    //? if (>=1.18 && <=1.21.11) {
    /*@Inject(method = "handleContainerButtonClick", at = @At("HEAD"), cancellable = true)
    private void anticrash$button(CallbackInfo info)
                *///?} else if (>=26.1 && <=26.3) {
    @Inject(method = "handleContainerButtonClick", at = @At("HEAD"), cancellable = true)
    private void anticrash$button(CallbackInfo info)
                //?}
            {
        if (anticrash$frozen()
                && !anticrash$controller().config().recovery.lock.allowContainerInteraction) {
            info.cancel();
            anticrash$resyncInventory();
        }
    }

    //? if (>=1.18 && <=1.21.11) {
    /*@Inject(method = "handlePlayerCommand", at = @At("HEAD"), cancellable = true)
    private void anticrash$clientCommand(ServerboundPlayerCommandPacket packet, CallbackInfo info)
                *///?} else if (>=26.1 && <=26.3) {
    @Inject(method = "handlePlayerCommand", at = @At("HEAD"), cancellable = true)
    private void anticrash$clientCommand(ServerboundPlayerCommandPacket packet, CallbackInfo info)
                //?}
            {
        if (!anticrash$frozen()) return;
        var lock = anticrash$controller().config().recovery.lock;

        //? if (>=1.18 && <=1.21.11) {
        /*boolean allowed =
                switch (packet.getAction()) {

                    *///?}
        //? if (>=1.18 && <=1.21.5) {
        /*case PRESS_SHIFT_KEY, RELEASE_SHIFT_KEY -> lock.allowSneak;
                    *///?} else if (>=26.1 && <=26.3) {
        boolean allowed =
                switch (packet.getAction()) {
                    //?}
                    case START_SPRINTING,
                            STOP_SPRINTING,
                            START_RIDING_JUMP,
                            STOP_RIDING_JUMP,
                            START_FALL_FLYING ->
                            lock.allowPlayerMovement;
                    case OPEN_INVENTORY -> lock.allowContainerInteraction;
                    case STOP_SLEEPING -> false;
                };
        if (!allowed) info.cancel();
    }

    //? if (>=1.18 && <=1.21.11) {
    /*@Inject(method = "handlePaddleBoat", at = @At("HEAD"), cancellable = true)
    private void anticrash$boatPaddle(CallbackInfo info)
                *///?} else if (>=26.1 && <=26.3) {
    @Inject(method = "handlePaddleBoat", at = @At("HEAD"), cancellable = true)
    private void anticrash$boatPaddle(CallbackInfo info)
                //?}
            {
        if (anticrash$frozen()
                && !anticrash$controller().config().recovery.lock.allowPlayerMovement)
            info.cancel();
    }

    /**
     * Mod payloads are not handled while recovery is frozen, but they are not thrown away either:
     * handshakes pass at once and the rest is delivered after the start; see DeferredPayloads.
     */

    //? if (>=1.18 && <=1.20.1) {
    /*@Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void anticrash$customPayload(ServerboundCustomPayloadPacket packet, CallbackInfo info) {
        if (!anticrash$frozen()) return;
        *///?}

    //? if (>=1.18 && <=1.20.1) {
    /*if (com.atsukimc.anticrash.network.DeferredPayloads.immediate(
                String.valueOf(packet.getIdentifier()))) return;
        *///?}

    //? if (>=1.18 && <=1.20.1) {
    /*anticrash$controller().deferCustomPayload(anticrash$handler(), packet);
        *///?}

    //? if (>=1.18 && <=1.20.1) {
    /*info.cancel();
    }

    *///?}

    //? if (>=1.18 && <=1.21.11) {
    /*private static boolean anticrash$entityActionIsAttack(ServerboundInteractPacket packet) {
        boolean[] attack = {false};
        *///?}

    //? if (>=1.18 && <=1.21.11) {
    /*packet.dispatch(
                new ServerboundInteractPacket.Handler() {
                    @Override
                    public void onInteraction(InteractionHand hand) {}

                    *///?}

    //? if (>=1.18 && <=1.21.11) {
    /*@Override
                    public void onInteraction(InteractionHand hand, Vec3 position) {}

                    *///?}

    //? if (>=1.18 && <=1.21.11) {
    /*@Override
                    public void onAttack() {
                        attack[0] = true;
                    }
                });
        *///?}

    //? if (>=1.18 && <=1.21.11) {
    /*return attack[0];
    }
}
*///?} else if (>=26.1 && <=26.3) {

    /**
         * Mod payloads are not handled while recovery is frozen, but they are not thrown away either:
         * handshakes pass at once and the rest is delivered after the start; see DeferredPayloads.
         */
}
        //?}
