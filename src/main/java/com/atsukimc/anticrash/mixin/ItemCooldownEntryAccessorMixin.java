package com.atsukimc.anticrash.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

//? if (>=1.18 && <=1.21.11) {
@Mixin(targets = "net.minecraft.world.item.ItemCooldowns$CooldownInstance")
public interface ItemCooldownEntryAccessorMixin {
    @Accessor("endTime")
    int anticrash$getEndTick();
    //?}
    //? if (>=26.1 && <=26.3) {
    /*@Mixin(targets = "net.minecraft.world.item.ItemCooldowns$CooldownInstance")
    public interface ItemCooldownEntryAccessorMixin {
        @Accessor("endTime")
        int anticrash$getEndTick();
*///?}

}
