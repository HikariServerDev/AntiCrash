package com.atsukimc.anticrash.mixin;

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.world.entity.player.Player;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.world.entity.player.Player;
//?}

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.world.item.ItemStack;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.world.item.ItemStack;
//?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

//? if (>=1.18 && <=1.21.11) {
/*@Mixin(Player.class)
public interface PlayerEntityAccessorMixin {
    /^* The main-hand stack as of the player's last tick; a change resets the attack cooldown. ^/
    @Accessor("lastItemInMainHand")
    void anticrash$setSelectedItem(ItemStack stack);
    *///?} else if (>=26.1 && <=26.3) {
@Mixin(Player.class)
public interface PlayerEntityAccessorMixin {
    /** The main-hand stack as of the player's last tick; a change resets the attack cooldown. */
    @Accessor("lastItemInMainHand")
    void anticrash$setSelectedItem(ItemStack stack);
    //?}

}
