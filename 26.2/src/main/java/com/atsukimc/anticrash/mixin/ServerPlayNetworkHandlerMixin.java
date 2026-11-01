package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.AntiCrashController;
import com.atsukimc.anticrash.AntiCrashMod;
import com.atsukimc.anticrash.command.RecoveryCommandPolicy;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundLockDifficultyPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromEntityPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

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
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerPlayNetworkHandlerMixin {
    private AntiCrashController anticrash$controller() {
        return AntiCrashMod.controller();
    }

    private ServerPlayer anticrash$player() {
        return ((ServerGamePacketListenerImpl) (Object) this).player;
    }

    private boolean anticrash$frozen() {
        AntiCrashController controller = anticrash$controller();
        return controller != null && controller.isRecoveryFrozen();
    }

    private ServerGamePacketListenerImpl anticrash$handler() {
        return (ServerGamePacketListenerImpl) (Object) this;
    }

    /** Runs on the server thread; malformed input must not throw from an unwrapped task. */
    private void anticrash$ackPrediction(int sequence) {
        if (sequence < 0) {
            anticrash$handler()
                    .disconnect(
                            net.minecraft.network.chat.Component.translatable(
                                    "multiplayer.disconnect.invalid_packet"));
        } else {
            anticrash$handler().ackBlockChangesUpTo(sequence);
        }
    }

    /**
     * Answers a refused interaction the way Vanilla does.
     *
     * <p>Minecraft never drops a block or inventory action silently: {@code onPlayerAction} always
     * replies with {@link ClientboundBlockChangedAckPacket}, {@code onPlayerInteractBlock} re-sends
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
        ServerPlayer player = anticrash$player();
        if (player == null) return;
        player.level()
                .getServer()
                .execute(() -> {
                    anticrash$handler()
                            .send(new ClientboundBlockUpdatePacket(player.level(), first));
                    if (second != null) {
                        anticrash$handler()
                                .send(new ClientboundBlockUpdatePacket(player.level(), second));
                    }
                });
    }

    /** Tells the client the mining action was not approved so it restores the block it removed. */
    private void anticrash$resyncBlockBreak(ServerboundPlayerActionPacket packet) {
        ServerPlayer player = anticrash$player();
        if (player == null) return;
        BlockPos pos = packet.getPos();
        player.level()
                .getServer()
                .execute(() -> {
                    // 1.19 replaced per-block approvals with a cumulative prediction sequence ACK.
                    anticrash$handler().send(new ClientboundBlockUpdatePacket(player.level(), pos));
                    anticrash$ackPrediction(packet.getSequence());
                });
    }

    /** Re-sends the authoritative ability flags after a refused toggle. */
    private void anticrash$resyncAbilities() {
        ServerPlayer player = anticrash$player();
        if (player == null) return;
        player.level().getServer().execute(player::onUpdateAbilities);
    }

    /** Restores the client's predicted inventory, cursor stack and container contents. */
    private void anticrash$resyncInventory() {
        ServerPlayer player = anticrash$player();
        if (player == null) return;
        player.level()
                .getServer()
                .execute(() -> {
                    AbstractContainerMenu handler = player.containerMenu;
                    if (handler != null) handler.sendAllDataToRemote();
                });
    }

    /** Records a mutation only when the controller exists, matching the freeze checks above. */
    private void anticrash$record(
            String type,
            ServerPlayer player,
            String source,
            String destination,
            BlockPos position,
            ItemStack stack) {
        // Mutation packets pass this HEAD injection twice: on the network thread and again
        // after Minecraft re-queues them to the server thread. Record only the authoritative pass.
        if (player == null || !player.level().getServer().isSameThread()) return;
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

    // Consume only after Vanilla has validated the signature, unpacked the chain and processed
    // ACKs.
    @Inject(method = "broadcastChatMessage", at = @At("HEAD"), cancellable = true)
    private void anticrash$chat(
            net.minecraft.network.chat.PlayerChatMessage packet, CallbackInfo info) {
        AntiCrashController controller = anticrash$controller();
        String message = packet.signedContent();
        if (controller != null
                && controller.isRecoveryAnswerCandidate(anticrash$player(), message)) {
            // Plain chat is handled on the network thread. Answering schedules a restore and
            // stops the server, which must happen on the server thread like /ac y does.
            info.cancel();
            ServerPlayer player = anticrash$player();
            player.level()
                    .getServer()
                    .execute(() -> controller.answerRecoveryFromChat(player, message));
            return;
        }
        // A checkpoint barrier needs no filter here: a command reaches the world only through the
        // server task queue, which the barrier holds until its cut is taken.
        if (!anticrash$frozen()) return;
        var lock = controller.config().recovery.lock;
        if ((message.startsWith("/") && !RecoveryCommandPolicy.allowed(message, lock.allowCommands))
                || (!message.startsWith("/") && !lock.allowChat)) info.cancel();
    }

    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
    private void anticrash$move(ServerboundMovePlayerPacket packet, CallbackInfo info) {
        if (!anticrash$frozen()) return;
        var lock = anticrash$controller().config().recovery.lock;
        if ((packet.hasPosition() && !lock.allowPlayerMovement)
                || (packet.hasRotation() && !lock.allowPlayerLook)) info.cancel();
    }

    @Inject(
            method = "handlePlayerInput",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
                            shift = At.Shift.AFTER),
            cancellable = true)
    private void anticrash$input(ServerboundPlayerInputPacket packet, CallbackInfo info) {
        if (!anticrash$frozen()) return;
        var lock = anticrash$controller().config().recovery.lock;
        var input = packet.input();
        if (!com.atsukimc.anticrash.integration.carpet.PlayerInputCodec.allowedDuringFreeze(
                anticrash$player().getLastClientInput(),
                input,
                lock.allowPlayerMovement,
                lock.allowSneak)) info.cancel();
    }

    @Inject(method = "handleMoveVehicle", at = @At("HEAD"), cancellable = true)
    private void anticrash$vehicleMove(ServerboundMoveVehiclePacket packet, CallbackInfo info) {
        if (anticrash$frozen()
                && !anticrash$controller().config().recovery.lock.allowPlayerMovement)
            info.cancel();
    }

    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void anticrash$action(ServerboundPlayerActionPacket packet, CallbackInfo info) {
        var action = packet.getAction();
        boolean block =
                action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK
                        || action == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK
                        || action == ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK;
        boolean drop =
                action == ServerboundPlayerActionPacket.Action.DROP_ITEM
                        || action == ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS;
        boolean use =
                action == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM
                        || action == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND;
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
        // Actual block breaks are recorded at ServerPlayerInteractionManager.tryBreakBlock.
        if (drop)
            anticrash$record(
                    "ITEM_DROP",
                    anticrash$player(),
                    "player",
                    "world",
                    packet.getPos(),
                    anticrash$player().getMainHandItem());
    }

    @Inject(method = "handleUseItemOn", at = @At("HEAD"), cancellable = true)
    private void anticrash$blockInteract(ServerboundUseItemOnPacket packet, CallbackInfo info) {
        ServerPlayer player = anticrash$player();
        BlockPos pos = packet.getHitResult().getBlockPos();
        BlockPos placement = pos.relative(packet.getHitResult().getDirection());
        AntiCrashController controller = anticrash$controller();
        boolean place = player.getItemInHand(packet.getHand()).getItem() instanceof BlockItem;
        if (anticrash$frozen()) {
            var lock = controller.config().recovery.lock;
            boolean refused = place ? !lock.allowBlockPlace : !lock.allowItemUse;
            if (!refused && !lock.allowContainerInteraction) {
                // Only a container changes the outcome here, and identifying one reads the world.
                // That read is safe only once Minecraft has moved this packet to the server thread,
                // so let the packet through and decide again on that second pass.
                if (Thread.currentThread() != player.level().getServer().getRunningThread()) return;
                refused = player.level().getBlockEntity(pos) != null;
            }
            if (refused) {
                info.cancel();
                anticrash$resyncBlocks(pos, placement);
                player.level()
                        .getServer()
                        .execute(() -> anticrash$ackPrediction(packet.getSequence()));
                anticrash$resyncInventory();
                return;
            }
        }
        anticrash$record(
                place ? "BLOCK_PLACE" : "ITEM_USE",
                player,
                "player",
                "world",
                pos,
                player.getItemInHand(packet.getHand()));
    }

    @Inject(method = "handleUseItem", at = @At("HEAD"), cancellable = true)
    private void anticrash$itemInteract(ServerboundUseItemPacket packet, CallbackInfo info) {
        if (anticrash$frozen() && !anticrash$controller().config().recovery.lock.allowItemUse) {
            info.cancel();
            anticrash$resyncInventory();
            ServerPlayer player = anticrash$player();
            player.level().getServer().execute(() -> anticrash$ackPrediction(packet.getSequence()));
            return;
        }
        anticrash$record(
                "ITEM_USE",
                anticrash$player(),
                "player",
                "world",
                null,
                anticrash$player().getItemInHand(packet.getHand()));
    }

    @Inject(
            method = "handleInteract",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
                            shift = At.Shift.AFTER),
            cancellable = true)
    private void anticrash$entityInteract(ServerboundInteractPacket packet, CallbackInfo info) {
        if (anticrash$frozen()) {
            var lock = anticrash$controller().config().recovery.lock;
            if (!lock.allowEntityInteract) {
                info.cancel();
                anticrash$resyncInventory();
                return;
            }
        }
        anticrash$record(
                "ENTITY_INTERACT",
                anticrash$player(),
                "player",
                "entity",
                null,
                anticrash$player().getMainHandItem());
    }

    // 26.1 has a separate attack packet. Cancelling only Interact no longer prevents attacks.
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
            anticrash$resyncInventory();
            return;
        }
        anticrash$record(
                "ENTITY_ATTACK",
                anticrash$player(),
                "player",
                "entity",
                null,
                anticrash$player().getMainHandItem());
    }

    @Inject(
            method = "handleContainerClick",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
                            shift = At.Shift.AFTER),
            cancellable = true)
    private void anticrash$click(ServerboundContainerClickPacket packet, CallbackInfo info) {
        boolean drop = packet.containerInput() == ContainerInput.THROW;
        if (anticrash$frozen()) {
            var lock = anticrash$controller().config().recovery.lock;
            if ((drop && !lock.allowItemDrop) || (!drop && !lock.allowContainerInteraction)) {
                info.cancel();
                anticrash$resyncInventory();
                return;
            }
        }
        // The packet now contains hashes, not trusted ItemStacks. Log the server-owned cursor.
        anticrash$record(
                drop ? "ITEM_DROP" : "CONTAINER_INTERACTION",
                anticrash$player(),
                "player",
                "container",
                null,
                anticrash$player().containerMenu.getCarried());
    }

    @Inject(method = "handleSetCreativeModeSlot", at = @At("HEAD"), cancellable = true)
    private void anticrash$creative(
            ServerboundSetCreativeModeSlotPacket packet, CallbackInfo info) {
        boolean drop = packet.slotNum() < 0;
        if (anticrash$frozen()) {
            var lock = anticrash$controller().config().recovery.lock;
            if ((drop && !lock.allowItemDrop) || (!drop && !lock.allowContainerInteraction)) {
                info.cancel();
                anticrash$resyncInventory();
                return;
            }
        }
        anticrash$record(
                drop ? "ITEM_DROP" : "CREATIVE_INVENTORY",
                anticrash$player(),
                "player",
                drop ? "world" : "inventory",
                null,
                packet.itemStack());
    }

    @Inject(method = "handleContainerClose", at = @At("HEAD"), cancellable = true)
    private void anticrash$closeScreen(ServerboundContainerClosePacket packet, CallbackInfo info) {
        if (anticrash$frozen()
                && !anticrash$controller().config().recovery.lock.allowContainerInteraction) {
            info.cancel();
            anticrash$resyncInventory();
            return;
        }
        anticrash$record(
                "CONTAINER_CLOSE",
                anticrash$player(),
                "container",
                "player",
                null,
                anticrash$player().getMainHandItem());
    }

    @Inject(method = "handleSelectTrade", at = @At("HEAD"), cancellable = true)
    private void anticrash$merchant(ServerboundSelectTradePacket packet, CallbackInfo info) {
        if (anticrash$frozen()
                && !anticrash$controller().config().recovery.lock.allowContainerInteraction) {
            info.cancel();
            anticrash$resyncInventory();
            return;
        }
        anticrash$record(
                "MERCHANT_SELECTION",
                anticrash$player(),
                "player",
                "merchant",
                null,
                anticrash$player().getMainHandItem());
    }

    @Inject(
            method = "handlePickItemFromBlock",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
                            shift = At.Shift.AFTER),
            cancellable = true)
    private void anticrash$pickBlock(ServerboundPickItemFromBlockPacket packet, CallbackInfo info) {
        anticrash$pickInventory(info);
    }

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
            ServerboundPickItemFromEntityPacket packet, CallbackInfo info) {
        anticrash$pickInventory(info);
    }

    private void anticrash$pickInventory(CallbackInfo info) {
        if (anticrash$frozen()
                && !anticrash$controller().config().recovery.lock.allowContainerInteraction) {
            info.cancel();
            anticrash$resyncInventory();
            return;
        }
        anticrash$record(
                "INVENTORY_PICK",
                anticrash$player(),
                "inventory",
                "hotbar",
                null,
                anticrash$player().getMainHandItem());
    }

    @Inject(method = "handleTeleportToEntityPacket", at = @At("HEAD"), cancellable = true)
    private void anticrash$spectator(ServerboundTeleportToEntityPacket packet, CallbackInfo info) {
        if (anticrash$frozen()
                && !anticrash$controller().config().recovery.lock.allowPlayerMovement)
            info.cancel();
    }

    @Inject(method = "handleClientCommand", at = @At("HEAD"), cancellable = true)
    private void anticrash$status(ServerboundClientCommandPacket packet, CallbackInfo info) {
        if (anticrash$frozen()) info.cancel();
    }

    @Inject(method = "handlePlayerAbilities", at = @At("HEAD"), cancellable = true)
    private void anticrash$abilities(ServerboundPlayerAbilitiesPacket packet, CallbackInfo info) {
        // Vanilla only copies the client's flying flag onto the player here. Dropping it left the
        // server-side abilities.flying stale, which is exactly the flag Carpet's creativeNoClip
        // reads, so only the explicit recovery lock refuses it (and then re-sends the flags).
        if (!anticrash$frozen()) return;
        if (!anticrash$controller().config().recovery.lock.allowPlayerMovement) {
            info.cancel();
            anticrash$resyncAbilities();
        }
    }

    @Inject(method = "handleChangeDifficulty", at = @At("HEAD"), cancellable = true)
    private void anticrash$difficulty(ServerboundChangeDifficultyPacket packet, CallbackInfo info) {
        if (anticrash$frozen()) info.cancel();
    }

    @Inject(method = "handleLockDifficulty", at = @At("HEAD"), cancellable = true)
    private void anticrash$difficultyLock(
            ServerboundLockDifficultyPacket packet, CallbackInfo info) {
        if (anticrash$frozen()) info.cancel();
    }

    @Inject(method = "handleSetCommandBlock", at = @At("HEAD"), cancellable = true)
    private void anticrash$commandBlock(CallbackInfo info) {
        if (anticrash$frozen()) info.cancel();
    }

    @Inject(method = "handleSetCommandMinecart", at = @At("HEAD"), cancellable = true)
    private void anticrash$commandBlockMinecart(CallbackInfo info) {
        if (anticrash$frozen()) info.cancel();
    }

    @Inject(method = "handleSetBeaconPacket", at = @At("HEAD"), cancellable = true)
    private void anticrash$beacon(CallbackInfo info) {
        if (anticrash$frozen()) info.cancel();
    }

    @Inject(method = "handleSetStructureBlock", at = @At("HEAD"), cancellable = true)
    private void anticrash$structure(CallbackInfo info) {
        if (anticrash$frozen()) info.cancel();
    }

    @Inject(method = "handleSetJigsawBlock", at = @At("HEAD"), cancellable = true)
    private void anticrash$jigsaw(CallbackInfo info) {
        if (anticrash$frozen()) info.cancel();
    }

    @Inject(method = "handleJigsawGenerate", at = @At("HEAD"), cancellable = true)
    private void anticrash$jigsawGenerate(CallbackInfo info) {
        if (anticrash$frozen()) info.cancel();
    }

    @Inject(method = "handleEditBook", at = @At("HEAD"), cancellable = true)
    private void anticrash$book(CallbackInfo info) {
        if (anticrash$frozen()) info.cancel();
    }

    @Inject(method = "handleRenameItem", at = @At("HEAD"), cancellable = true)
    private void anticrash$rename(CallbackInfo info) {
        if (anticrash$frozen()) {
            info.cancel();
            anticrash$resyncInventory();
        }
    }

    @Inject(method = "handleSignUpdate", at = @At("HEAD"), cancellable = true)
    private void anticrash$sign(CallbackInfo info) {
        if (anticrash$frozen()) info.cancel();
    }

    @Inject(method = "handlePlaceRecipe", at = @At("HEAD"), cancellable = true)
    private void anticrash$craft(CallbackInfo info) {
        if (anticrash$frozen()
                && !anticrash$controller().config().recovery.lock.allowContainerInteraction) {
            info.cancel();
            anticrash$resyncInventory();
        }
    }

    @Inject(method = "handleContainerButtonClick", at = @At("HEAD"), cancellable = true)
    private void anticrash$button(CallbackInfo info) {
        if (anticrash$frozen()
                && !anticrash$controller().config().recovery.lock.allowContainerInteraction) {
            info.cancel();
            anticrash$resyncInventory();
        }
    }

    @Inject(method = "handlePlayerCommand", at = @At("HEAD"), cancellable = true)
    private void anticrash$clientCommand(ServerboundPlayerCommandPacket packet, CallbackInfo info) {
        if (!anticrash$frozen()) return;
        var lock = anticrash$controller().config().recovery.lock;
        boolean allowed =
                switch (packet.getAction()) {
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

    @Inject(method = "handlePaddleBoat", at = @At("HEAD"), cancellable = true)
    private void anticrash$boatPaddle(CallbackInfo info) {
        if (anticrash$frozen()
                && !anticrash$controller().config().recovery.lock.allowPlayerMovement)
            info.cancel();
    }
    /**
     * Mod payloads are not handled while recovery is frozen, but they are not thrown away either:
     * handshakes pass at once and the rest is delivered after the start; see DeferredPayloads.
     */
}
