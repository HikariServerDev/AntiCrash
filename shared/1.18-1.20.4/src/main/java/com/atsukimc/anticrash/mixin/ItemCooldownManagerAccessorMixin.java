package com.atsukimc.anticrash.mixin;

import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.Item;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(ItemCooldownManager.class)
public interface ItemCooldownManagerAccessorMixin {
    @Accessor("entries")
    Map<Item, Object> anticrash$getEntries();

    @Accessor("tick")
    int anticrash$getTick();
}
