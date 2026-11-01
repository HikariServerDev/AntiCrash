package com.atsukimc.anticrash.mixin;

import net.minecraft.world.item.ItemCooldowns;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(ItemCooldowns.class)
public interface ItemCooldownManagerAccessorMixin {
    @Accessor("cooldowns")
    Map<net.minecraft.resources.Identifier, Object> anticrash$getEntries();

    @Accessor("tickCount")
    int anticrash$getTick();
}
