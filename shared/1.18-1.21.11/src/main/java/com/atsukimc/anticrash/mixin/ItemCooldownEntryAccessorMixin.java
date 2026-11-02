package com.atsukimc.anticrash.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.entity.player.ItemCooldownManager$Entry")
public interface ItemCooldownEntryAccessorMixin {
    @Accessor("endTick")
    int anticrash$getEndTick();
}
