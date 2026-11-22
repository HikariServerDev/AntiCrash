package com.atsukimc.anticrash.mixin;

//? if <=1.21.1 {
/*import net.minecraft.world.item.Item;
*///?}

import net.minecraft.world.item.ItemCooldowns;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(ItemCooldowns.class)
public interface ItemCooldownManagerAccessorMixin {
    //? if <=1.21.1 {
    /*@Accessor("cooldowns")
    Map<Item, Object> anticrash$getEntries();

    *///?} else {
    @Accessor("cooldowns")
    Map<?, Object> anticrash$getEntries();

    //?}

    @Accessor("tickCount")
    int anticrash$getTick();
}
