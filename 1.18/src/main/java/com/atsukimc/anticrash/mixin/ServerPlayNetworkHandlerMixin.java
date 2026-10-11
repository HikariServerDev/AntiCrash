package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.AntiCrashController;
import com.atsukimc.anticrash.AntiCrashMod;
import com.atsukimc.anticrash.command.RecoveryCommandPolicy;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerActionResponseS2CPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Packet-level recovery lock. Keepalive and read-only protocol traffic are not intercepted.
 *
 * <p>Only an operator-controlled recovery freeze refuses packets here, and every refusal is
 * answered so the client can undo its prediction. A checkpoint never refuses anything: its packets
 * wait in the held server task queue (see MinecraftServerMixin) and run once the cut is taken.</p>
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {
    private AntiCrashController anticrash$controller() { return AntiCrashMod.controller(); }
    private ServerPlayerEntity anticrash$player() { return ((ServerPlayNetworkHandler)(Object)this).player; }
    private boolean anticrash$frozen() { AntiCrashController controller=anticrash$controller(); return controller!=null&&controller.isRecoveryFrozen(); }
    private ServerPlayNetworkHandler anticrash$handler() { return (ServerPlayNetworkHandler)(Object)this; }

    /**
     * Answers a refused interaction the way Vanilla does.
     *
     * <p>Minecraft never drops a block or inventory action silently: {@code onPlayerAction} always
     * replies with {@link PlayerActionResponseS2CPacket}, {@code onPlayerInteractBlock} re-sends the
     * two affected block states, and {@code onClickSlot} calls {@link ScreenHandler#syncState()}.
     * The client needs that answer to undo the change it already predicted locally. Cancelling the
     * packet at HEAD skipped every one of those replies, so a block mined or placed during a
     * checkpoint barrier or a recovery freeze stayed on the client forever as a ghost block.</p>
     *
     * <p>A HEAD injection also runs on the Netty thread, before Minecraft moves the packet to the
     * server thread, so the world must only be read from inside the scheduled task.</p>
     */
    private void anticrash$resyncBlocks(BlockPos first, BlockPos second) {
        ServerPlayerEntity player = anticrash$player();
        if (player == null) return;
        player.server.execute(() -> {
            anticrash$handler().sendPacket(new BlockUpdateS2CPacket(player.getWorld(), first));
            if (second != null) {
                anticrash$handler().sendPacket(new BlockUpdateS2CPacket(player.getWorld(), second));
            }
        });
    }

    /** Tells the client the mining action was not approved so it restores the block it removed. */
    private void anticrash$resyncBlockBreak(PlayerActionC2SPacket packet) {
        ServerPlayerEntity player = anticrash$player();
        if (player == null) return;
        BlockPos pos = packet.getPos();
        PlayerActionC2SPacket.Action action = packet.getAction();
        player.server.execute(() -> anticrash$handler().sendPacket(new PlayerActionResponseS2CPacket(
                pos, player.getWorld().getBlockState(pos), action, false, "AntiCrash freeze")));
    }

    /** Re-sends the authoritative ability flags after a refused toggle. */
    private void anticrash$resyncAbilities() {
        ServerPlayerEntity player = anticrash$player();
        if (player == null) return;
        player.server.execute(player::sendAbilitiesUpdate);
    }

    /** Restores the client's predicted inventory, cursor stack and container contents. */
    private void anticrash$resyncInventory() {
        ServerPlayerEntity player = anticrash$player();
        if (player == null) return;
        player.server.execute(() -> {
            ScreenHandler handler = player.currentScreenHandler;
            if (handler != null) handler.syncState();
        });
    }

    /** Records a mutation only when the controller exists, matching the freeze checks above. */
    private void anticrash$record(String type, ServerPlayerEntity player, String source,
                                  String destination, BlockPos position, ItemStack stack) {
        // Mutation packets pass this HEAD injection twice: on the network thread and again
        // after Minecraft re-queues them to the server thread. Record only the authoritative pass.
        if (player == null || !player.server.isOnThread()) return;
        AntiCrashController controller = anticrash$controller();
        if (controller != null) controller.recordTransaction(type, player, source, destination, position, stack);
    }
    /**
     * Stops the player's own tick while a checkpoint holds the world at its cut.
     *
     * <p>The connection tick runs from MinecraftServer#tick, outside the frozen world tick, and
     * {@code playerTick} moves, damages and feeds the player and picks up items. Letting it run
     * would change the world after the cut; its packets are already waiting in the held task
     * queue. Pausing it keeps the whole barrier equivalent to a single long server tick, which is
     * also what Vanilla's movement checks expect when the queued packets are processed.</p>
     */
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void anticrash$holdPlayerTick(CallbackInfo info){
        AntiCrashController controller=anticrash$controller();
        if(controller!=null&&controller.holdsServerTasks())info.cancel();
    }

    @Inject(method="onChatMessage",at=@At("HEAD"),cancellable=true)
    private void anticrash$chat(ChatMessageC2SPacket packet,CallbackInfo info){
        AntiCrashController controller=anticrash$controller();
        String message=packet.getChatMessage();
        if(controller!=null&&controller.isRecoveryAnswerCandidate(anticrash$player(),message)){
            // Plain chat is handled on the network thread. Answering schedules a restore and
            // stops the server, which must happen on the server thread like /ac y does.
            info.cancel();
            ServerPlayerEntity player=anticrash$player();
            player.server.execute(()->controller.answerRecoveryFromChat(player,message));
            return;
        }
        // A checkpoint barrier needs no filter here: a command reaches the world only through the
        // server task queue, which the barrier holds until its cut is taken.
        if(!anticrash$frozen())return;
        var lock=controller.config().recovery.lock;
        if((message.startsWith("/")&&!RecoveryCommandPolicy.allowed(message,lock.allowCommands))
                ||(!message.startsWith("/")&&!lock.allowChat))info.cancel();
    }

    @Inject(method="onPlayerMove",at=@At("HEAD"),cancellable=true)
    private void anticrash$move(PlayerMoveC2SPacket packet,CallbackInfo info){
        if(!anticrash$frozen())return;
        var lock=anticrash$controller().config().recovery.lock;
        if((packet.changesPosition()&&!lock.allowPlayerMovement)||(packet.changesLook()&&!lock.allowPlayerLook))info.cancel();
    }

    @Inject(method="onPlayerInput",at=@At("HEAD"),cancellable=true)
    private void anticrash$input(PlayerInputC2SPacket packet,CallbackInfo info){
        if(!anticrash$frozen())return;
        var lock=anticrash$controller().config().recovery.lock;
        if((packet.isSneaking()&&!lock.allowSneak)||(!lock.allowPlayerMovement&&(packet.getForward()!=0||packet.getSideways()!=0||packet.isJumping())))info.cancel();
    }

    @Inject(method="onVehicleMove",at=@At("HEAD"),cancellable=true)
    private void anticrash$vehicleMove(VehicleMoveC2SPacket packet,CallbackInfo info){
        if(anticrash$frozen()&&!anticrash$controller().config().recovery.lock.allowPlayerMovement)info.cancel();
    }

    @Inject(method="onPlayerAction",at=@At("HEAD"),cancellable=true)
    private void anticrash$action(PlayerActionC2SPacket packet,CallbackInfo info){
        var action=packet.getAction();
        boolean block=action==PlayerActionC2SPacket.Action.START_DESTROY_BLOCK
                ||action==PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK
                ||action==PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK;
        boolean drop=action==PlayerActionC2SPacket.Action.DROP_ITEM||action==PlayerActionC2SPacket.Action.DROP_ALL_ITEMS;
        boolean use=action==PlayerActionC2SPacket.Action.RELEASE_USE_ITEM||action==PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND;
        AntiCrashController controller=anticrash$controller();
        boolean refused=false;
        if(anticrash$frozen()){
            var lock=controller.config().recovery.lock;
            refused=(block&&!lock.allowBlockBreak)||(drop&&!lock.allowItemDrop)||(use&&!lock.allowItemUse);
        }
        if(refused){
            info.cancel();
            if(block)anticrash$resyncBlockBreak(packet);
            else anticrash$resyncInventory();
            return;
        }
        // Actual block breaks are recorded at ServerPlayerInteractionManager.tryBreakBlock.
        if(drop)anticrash$record("ITEM_DROP",anticrash$player(),"player","world",packet.getPos(),anticrash$player().getMainHandStack());
    }

    @Inject(method="onPlayerInteractBlock",at=@At("HEAD"),cancellable=true)
    private void anticrash$blockInteract(PlayerInteractBlockC2SPacket packet,CallbackInfo info){
        ServerPlayerEntity player=anticrash$player();
        BlockPos pos=packet.getBlockHitResult().getBlockPos();
        BlockPos placement=pos.offset(packet.getBlockHitResult().getSide());
        AntiCrashController controller=anticrash$controller();
        boolean place=player.getStackInHand(packet.getHand()).getItem() instanceof BlockItem;
        if(anticrash$frozen()){
            var lock=controller.config().recovery.lock;
            boolean refused=place?!lock.allowBlockPlace:!lock.allowItemUse;
            if(!refused&&!lock.allowContainerInteraction){
                // Only a container changes the outcome here, and identifying one reads the world.
                // That read is safe only once Minecraft has moved this packet to the server thread,
                // so let the packet through and decide again on that second pass.
                if(Thread.currentThread()!=player.server.getThread())return;
                refused=player.getWorld().getBlockEntity(pos)!=null;
            }
            if(refused){
                info.cancel();
                anticrash$resyncBlocks(pos,placement);
                anticrash$resyncInventory();
                return;
            }
        }
        anticrash$record(place?"BLOCK_PLACE":"ITEM_USE",player,"player","world",pos,player.getStackInHand(packet.getHand()));
    }

    @Inject(method="onPlayerInteractItem",at=@At("HEAD"),cancellable=true)
    private void anticrash$itemInteract(PlayerInteractItemC2SPacket packet,CallbackInfo info){
        if(anticrash$frozen()&&!anticrash$controller().config().recovery.lock.allowItemUse){
            info.cancel();anticrash$resyncInventory();return;}
        anticrash$record("ITEM_USE",anticrash$player(),"player","world",null,
                anticrash$player().getStackInHand(packet.getHand()));
    }

    @Inject(method="onPlayerInteractEntity",at=@At("HEAD"),cancellable=true)
    private void anticrash$entityInteract(PlayerInteractEntityC2SPacket packet,CallbackInfo info){
        boolean attack=anticrash$entityActionIsAttack(packet);
        if(anticrash$frozen()){
            var lock=anticrash$controller().config().recovery.lock;
            if((attack&&!lock.allowEntityAttack)||(!attack&&!lock.allowEntityInteract)){
                info.cancel();anticrash$resyncInventory();return;}
        }
        anticrash$record(attack?"ENTITY_ATTACK":"ENTITY_INTERACT",anticrash$player(),"player","entity",null,anticrash$player().getMainHandStack());
    }

    @Inject(method="onClickSlot",at=@At("HEAD"),cancellable=true)
    private void anticrash$click(ClickSlotC2SPacket packet,CallbackInfo info){
        boolean drop=packet.getActionType()==SlotActionType.THROW;
        if(anticrash$frozen()){
            var lock=anticrash$controller().config().recovery.lock;
            if((drop&&!lock.allowItemDrop)||(!drop&&!lock.allowContainerInteraction)){
                info.cancel();anticrash$resyncInventory();return;}
        }
        anticrash$record(drop?"ITEM_DROP":"CONTAINER_INTERACTION",anticrash$player(),"player","container",null,packet.getStack());
    }

    @Inject(method="onCreativeInventoryAction",at=@At("HEAD"),cancellable=true)
    private void anticrash$creative(CreativeInventoryActionC2SPacket packet,CallbackInfo info){
        boolean drop=packet.getSlot()<0;
        if(anticrash$frozen()){
            var lock=anticrash$controller().config().recovery.lock;
            if((drop&&!lock.allowItemDrop)||(!drop&&!lock.allowContainerInteraction)){
                info.cancel();anticrash$resyncInventory();return;}
        }
        anticrash$record(drop?"ITEM_DROP":"CREATIVE_INVENTORY",anticrash$player(),"player",drop?"world":"inventory",null,packet.getItemStack());
    }

    @Inject(method="onCloseHandledScreen",at=@At("HEAD"),cancellable=true)
    private void anticrash$closeScreen(CloseHandledScreenC2SPacket packet,CallbackInfo info){
        if(anticrash$frozen()&&!anticrash$controller().config().recovery.lock.allowContainerInteraction){
            info.cancel();anticrash$resyncInventory();return;}
        anticrash$record("CONTAINER_CLOSE",anticrash$player(),"container","player",null,anticrash$player().getMainHandStack());
    }

    @Inject(method="onSelectMerchantTrade",at=@At("HEAD"),cancellable=true)
    private void anticrash$merchant(SelectMerchantTradeC2SPacket packet,CallbackInfo info){
        if(anticrash$frozen()&&!anticrash$controller().config().recovery.lock.allowContainerInteraction){
            info.cancel();anticrash$resyncInventory();return;}
        anticrash$record("MERCHANT_SELECTION",anticrash$player(),"player","merchant",null,anticrash$player().getMainHandStack());
    }

    @Inject(method="onPickFromInventory",at=@At("HEAD"),cancellable=true)
    private void anticrash$pickInventory(PickFromInventoryC2SPacket packet,CallbackInfo info){
        if(anticrash$frozen()&&!anticrash$controller().config().recovery.lock.allowContainerInteraction){
            info.cancel();anticrash$resyncInventory();return;}
        anticrash$record("INVENTORY_PICK",anticrash$player(),"inventory","hotbar",null,anticrash$player().getMainHandStack());
    }

    @Inject(method="onSpectatorTeleport",at=@At("HEAD"),cancellable=true)
    private void anticrash$spectator(SpectatorTeleportC2SPacket packet,CallbackInfo info){if(anticrash$frozen()&&!anticrash$controller().config().recovery.lock.allowPlayerMovement)info.cancel();}
    @Inject(method="onClientStatus",at=@At("HEAD"),cancellable=true)
    private void anticrash$status(ClientStatusC2SPacket packet,CallbackInfo info){if(anticrash$frozen())info.cancel();}
    @Inject(method="onUpdatePlayerAbilities",at=@At("HEAD"),cancellable=true)
    private void anticrash$abilities(UpdatePlayerAbilitiesC2SPacket packet,CallbackInfo info){
        // Vanilla only copies the client's flying flag onto the player here. Dropping it left the
        // server-side abilities.flying stale, which is exactly the flag Carpet's creativeNoClip
        // reads, so only the explicit recovery lock refuses it (and then re-sends the flags).
        if(!anticrash$frozen())return;
        if(!anticrash$controller().config().recovery.lock.allowPlayerMovement){
            info.cancel();
            anticrash$resyncAbilities();
        }
    }
    @Inject(method="onUpdateDifficulty",at=@At("HEAD"),cancellable=true)
    private void anticrash$difficulty(UpdateDifficultyC2SPacket packet,CallbackInfo info){if(anticrash$frozen())info.cancel();}
    @Inject(method="onUpdateDifficultyLock",at=@At("HEAD"),cancellable=true)
    private void anticrash$difficultyLock(UpdateDifficultyLockC2SPacket packet,CallbackInfo info){if(anticrash$frozen())info.cancel();}

    @Inject(method="onUpdateCommandBlock",at=@At("HEAD"),cancellable=true) private void anticrash$commandBlock(CallbackInfo info){if(anticrash$frozen())info.cancel();}
    @Inject(method="onUpdateCommandBlockMinecart",at=@At("HEAD"),cancellable=true) private void anticrash$commandBlockMinecart(CallbackInfo info){if(anticrash$frozen())info.cancel();}
    @Inject(method="onUpdateBeacon",at=@At("HEAD"),cancellable=true) private void anticrash$beacon(CallbackInfo info){if(anticrash$frozen())info.cancel();}
    @Inject(method="onUpdateStructureBlock",at=@At("HEAD"),cancellable=true) private void anticrash$structure(CallbackInfo info){if(anticrash$frozen())info.cancel();}
    @Inject(method="onUpdateJigsaw",at=@At("HEAD"),cancellable=true) private void anticrash$jigsaw(CallbackInfo info){if(anticrash$frozen())info.cancel();}
    @Inject(method="onJigsawGenerating",at=@At("HEAD"),cancellable=true) private void anticrash$jigsawGenerate(CallbackInfo info){if(anticrash$frozen())info.cancel();}
    @Inject(method="onBookUpdate",at=@At("HEAD"),cancellable=true) private void anticrash$book(CallbackInfo info){if(anticrash$frozen())info.cancel();}
    @Inject(method="onRenameItem",at=@At("HEAD"),cancellable=true) private void anticrash$rename(CallbackInfo info){if(anticrash$frozen()){info.cancel();anticrash$resyncInventory();}}
    @Inject(method="onUpdateSign",at=@At("HEAD"),cancellable=true) private void anticrash$sign(CallbackInfo info){if(anticrash$frozen())info.cancel();}
    @Inject(method="onCraftRequest",at=@At("HEAD"),cancellable=true) private void anticrash$craft(CallbackInfo info){if(anticrash$frozen()&&!anticrash$controller().config().recovery.lock.allowContainerInteraction){info.cancel();anticrash$resyncInventory();}}
    @Inject(method="onButtonClick",at=@At("HEAD"),cancellable=true) private void anticrash$button(CallbackInfo info){if(anticrash$frozen()&&!anticrash$controller().config().recovery.lock.allowContainerInteraction){info.cancel();anticrash$resyncInventory();}}
    @Inject(method="onClientCommand",at=@At("HEAD"),cancellable=true)
    private void anticrash$clientCommand(ClientCommandC2SPacket packet,CallbackInfo info){
        if(!anticrash$frozen())return;
        var lock=anticrash$controller().config().recovery.lock;
        boolean allowed=switch(packet.getMode()){
            case PRESS_SHIFT_KEY, RELEASE_SHIFT_KEY -> lock.allowSneak;
            case START_SPRINTING, STOP_SPRINTING, START_RIDING_JUMP,
                    STOP_RIDING_JUMP, START_FALL_FLYING -> lock.allowPlayerMovement;
            case OPEN_INVENTORY -> lock.allowContainerInteraction;
            case STOP_SLEEPING -> false;
        };
        if(!allowed)info.cancel();
    }
    @Inject(method="onBoatPaddleState",at=@At("HEAD"),cancellable=true) private void anticrash$boatPaddle(CallbackInfo info){if(anticrash$frozen()&&!anticrash$controller().config().recovery.lock.allowPlayerMovement)info.cancel();}
    /**
     * Mod payloads are not handled while recovery is frozen, but they are not thrown away either:
     * handshakes pass at once and the rest is delivered after the start; see DeferredPayloads.
     */
    @Inject(method="onCustomPayload",at=@At("HEAD"),cancellable=true)
    private void anticrash$customPayload(CustomPayloadC2SPacket packet,CallbackInfo info){
        if(!anticrash$frozen())return;
        if(com.atsukimc.anticrash.network.DeferredPayloads.immediate(String.valueOf(packet.getChannel())))return;
        anticrash$controller().deferCustomPayload(anticrash$handler(),packet);
        info.cancel();
    }

    private static boolean anticrash$entityActionIsAttack(PlayerInteractEntityC2SPacket packet){
        boolean[] attack={false};
        packet.handle(new PlayerInteractEntityC2SPacket.Handler(){
            @Override public void interact(Hand hand) { }
            @Override public void interactAt(Hand hand, Vec3d position) { }
            @Override public void attack() { attack[0]=true; }
        });
        return attack[0];
    }
}
