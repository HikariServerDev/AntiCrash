package com.atsukimc.anticrash.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerEntity.class)
public interface PlayerEntityAccessorMixin {
    /** The main-hand stack as of the player's last tick; a change resets the attack cooldown. */
    @Accessor("selectedItem")
    void anticrash$setSelectedItem(ItemStack stack);
}
