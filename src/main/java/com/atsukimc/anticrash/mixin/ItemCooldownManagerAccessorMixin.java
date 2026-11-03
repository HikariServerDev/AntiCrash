package com.atsukimc.anticrash.mixin;

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.item.ItemCooldowns;
//?}

//? if (>=1.18 && <=1.21.1) {
import net.minecraft.world.item.Item;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.item.ItemCooldowns;
*///?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

//? if (>=1.18 && <=1.21.11) {
@Mixin(ItemCooldowns.class)
public interface ItemCooldownManagerAccessorMixin {

    //?}
    //? if (>=1.18 && <=1.21.1) {
    @Accessor("cooldowns")
    Map<Item, Object> anticrash$getEntries();

    //?}
    //? if (>=1.21.2 && <=1.21.11) {
    /*@Accessor("cooldowns")
        //? if (>=1.21.2 && <=1.21.10) {
        /^Map<net.minecraft.resources.ResourceLocation, Object> anticrash$getEntries();
        ^///?}
        //? if 1.21.11 {
        /^Map<net.minecraft.resources.Identifier, Object> anticrash$getEntries();
        ^///?}
*///?}
    //? if (>=26.1 && <=26.3) {
    /*@Mixin(ItemCooldowns.class)
    public interface ItemCooldownManagerAccessorMixin {
        @Accessor("cooldowns")
        Map<net.minecraft.resources.Identifier, Object> anticrash$getEntries();
*///?}

    //? if (>=1.18 && <=1.21.11) {
    @Accessor("tickCount")
    int anticrash$getTick();
    //?}
    //? if (>=26.1 && <=26.3) {
    /*@Accessor("tickCount")
        int anticrash$getTick();
*///?}

}
