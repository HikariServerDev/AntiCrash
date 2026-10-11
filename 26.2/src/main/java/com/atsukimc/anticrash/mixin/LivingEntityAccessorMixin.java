package com.atsukimc.anticrash.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessorMixin {
    @Accessor("lastKineticHitFeedbackTime") long anticrash$lastKineticAttackTime();
    @Accessor("lastKineticHitFeedbackTime") void anticrash$setLastKineticAttackTime(long value);
    @Accessor("lastHurt")
    float anticrash$getLastDamageTaken();

    @Accessor("lastHurt")
    void anticrash$setLastDamageTaken(float value);

    @Accessor("attackStrengthTicker")
    int anticrash$getLastAttackedTicks();

    @Accessor("attackStrengthTicker")
    void anticrash$setLastAttackedTicks(int ticks);

    @Accessor("useItemRemaining")
    int anticrash$getItemUseTimeLeft();

    @Accessor("useItemRemaining")
    void anticrash$setItemUseTimeLeft(int ticks);

    @Accessor("useItem")
    ItemStack anticrash$getActiveItemStack();
}
