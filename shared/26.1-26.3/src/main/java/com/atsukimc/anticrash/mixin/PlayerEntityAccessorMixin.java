package com.atsukimc.anticrash.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Player.class)
public interface PlayerEntityAccessorMixin {
    /** The main-hand stack as of the player's last tick; a change resets the attack cooldown. */
    @Accessor("lastItemInMainHand")
    void anticrash$setSelectedItem(ItemStack stack);
}
