package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.AntiCrashController;
import com.atsukimc.anticrash.AntiCrashMod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin implements com.atsukimc.anticrash.runtime.ItemPhaseHolder {
    /**
     * A resting item re-checks whether it should move only when (age + network id) % 4 is 0. The
     * network id is handed out again at every load, so that phase changed with each restart. The
     * id's phase at the time the item first existed is kept and used for that one expression; the
     * real network id is never touched.
     */
    @Unique private int anticrash$idPhase = -1;

    @Override
    public int anticrash$idPhase() {
        return anticrash$idPhase >= 0
                ? anticrash$idPhase
                : Math.floorMod(((ItemEntity) (Object) this).getId(), 4);
    }

    @Override
    public void anticrash$setIdPhase(int phase) {
        anticrash$idPhase = Math.floorMod(phase, 4);
    }

    @com.llamalad7.mixinextras.injector.ModifyExpressionValue(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/world/entity/item/ItemEntity;getId()I"))
    private int anticrash$stableMovePhase(int networkId) {
        return anticrash$idPhase >= 0 ? anticrash$idPhase : networkId;
    }

    @Unique private ItemStack anticrash$pickupBefore = ItemStack.EMPTY;

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void anticrash$beforePickup(Player player, CallbackInfo info) {
        anticrash$pickupBefore = ItemStack.EMPTY;
        AntiCrashController controller = AntiCrashMod.controller();
        if (controller != null && controller.isCheckpointFrozen()) {
            info.cancel();
            return;
        }
        if (controller != null
                && controller.isRecoveryFrozen()
                && !controller.config().recovery.lock.allowItemPickup) {
            info.cancel();
            return;
        }
        if (player instanceof ServerPlayer)
            anticrash$pickupBefore = ((ItemEntity) (Object) this).getItem().copy();
    }

    @Inject(method = "playerTouch", at = @At("RETURN"))
    private void anticrash$afterPickup(Player player, CallbackInfo info) {
        ItemStack before = anticrash$pickupBefore;
        anticrash$pickupBefore = ItemStack.EMPTY;
        if (!(player instanceof ServerPlayer serverPlayer) || before.isEmpty()) return;
        ItemEntity item = (ItemEntity) (Object) this;
        int remaining = item.isRemoved() ? 0 : item.getItem().getCount();
        int picked = before.getCount() - remaining;
        if (picked <= 0) return;
        ItemStack pickedStack = before.copy();
        pickedStack.setCount(picked);
        AntiCrashController controller = AntiCrashMod.controller();
        if (controller != null)
            controller.recordTransaction(
                    "ITEM_PICKUP",
                    serverPlayer,
                    "world",
                    "player",
                    BlockPos.containing(item.position()),
                    pickedStack);
    }
}
