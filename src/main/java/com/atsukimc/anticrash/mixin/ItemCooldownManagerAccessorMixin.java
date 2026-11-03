package com.atsukimc.anticrash.mixin;

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.entity.player.ItemCooldownManager;
//?}

//? if (>=1.18 && <=1.21.1) {
import net.minecraft.item.Item;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.item.ItemCooldowns;
*///?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

//? if (>=1.18 && <=1.21.11) {
@Mixin(ItemCooldownManager.class)
public interface ItemCooldownManagerAccessorMixin {

    //?}
    //? if (>=1.18 && <=1.21.1) {
    @Accessor("entries")
    Map<Item, Object> anticrash$getEntries();

    //?}
    //? if (>=1.21.2 && <=1.21.11) {
    /*@Accessor("entries")
        Map<net.minecraft.util.Identifier, Object> anticrash$getEntries();
*///?}
    //? if (>=26.1 && <=26.3) {
    /*@Mixin(ItemCooldowns.class)
    public interface ItemCooldownManagerAccessorMixin {
        @Accessor("cooldowns")
        Map<net.minecraft.resources.Identifier, Object> anticrash$getEntries();
*///?}

    //? if (>=1.18 && <=1.21.11) {
    @Accessor("tick")
    int anticrash$getTick();
    //?}
    //? if (>=26.1 && <=26.3) {
    /*@Accessor("tickCount")
        int anticrash$getTick();
*///?}

}
