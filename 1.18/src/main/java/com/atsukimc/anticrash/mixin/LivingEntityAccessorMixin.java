package com.atsukimc.anticrash.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessorMixin {
    @Accessor("lastDamageTaken")
    float anticrash$getLastDamageTaken();

    @Accessor("lastDamageTaken")
    void anticrash$setLastDamageTaken(float value);

    @Accessor("lastAttackedTicks")
    int anticrash$getLastAttackedTicks();

    @Accessor("lastAttackedTicks")
    void anticrash$setLastAttackedTicks(int ticks);

    @Accessor("itemUseTimeLeft")
    int anticrash$getItemUseTimeLeft();

    @Accessor("itemUseTimeLeft")
    void anticrash$setItemUseTimeLeft(int ticks);

    @Accessor("activeItemStack")
    ItemStack anticrash$getActiveItemStack();
}
