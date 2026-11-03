package com.atsukimc.anticrash.mixin;

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.world.entity.LivingEntity;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.world.entity.LivingEntity;
//?}

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.world.item.ItemStack;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.world.item.ItemStack;
//?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessorMixin {

    //? if 1.21.11 {
    /*@Accessor("lastKineticHitFeedbackTime")
        long anticrash$lastKineticAttackTime();
*///?} else if (>=26.1 && <=26.3) {
    @Accessor("lastKineticHitFeedbackTime")
    long anticrash$lastKineticAttackTime();

    //?}

    //? if 1.21.11 {
    /*@Accessor("lastKineticHitFeedbackTime")
        void anticrash$setLastKineticAttackTime(long value);
*///?} else if (>=26.1 && <=26.3) {
    @Accessor("lastKineticHitFeedbackTime")
    void anticrash$setLastKineticAttackTime(long value);

    //?}

    //? if (>=1.18 && <=1.21.11) {
    /*@Accessor("lastHurt")
    float anticrash$getLastDamageTaken();

    *///?} else if (>=26.1 && <=26.3) {
    @Accessor("lastHurt")
    float anticrash$getLastDamageTaken();

    //?}

    //? if (>=1.18 && <=1.21.11) {
    /*@Accessor("lastHurt")
    void anticrash$setLastDamageTaken(float value);

    *///?} else if (>=26.1 && <=26.3) {
    @Accessor("lastHurt")
    void anticrash$setLastDamageTaken(float value);

    //?}

    //? if (>=1.18 && <=1.21.10) {
    /*@Accessor("attackStrengthTicker")
    int anticrash$getLastAttackedTicks();

    *///?} else if 1.21.11 {
    /*@Accessor("attackStrengthTicker")
        int anticrash$getLastAttackedTicks();
*///?} else if (>=26.1 && <=26.3) {
    @Accessor("attackStrengthTicker")
    int anticrash$getLastAttackedTicks();

    //?}

    //? if (>=1.18 && <=1.21.10) {
    /*@Accessor("attackStrengthTicker")
    void anticrash$setLastAttackedTicks(int ticks);

    *///?} else if 1.21.11 {
    /*@Accessor("attackStrengthTicker")
        void anticrash$setLastAttackedTicks(int ticks);
*///?} else if (>=26.1 && <=26.3) {
    @Accessor("attackStrengthTicker")
    void anticrash$setLastAttackedTicks(int ticks);

    //?}

    //? if (>=1.18 && <=1.21.11) {
    /*@Accessor("useItemRemaining")
    int anticrash$getItemUseTimeLeft();

    *///?} else if (>=26.1 && <=26.3) {
    @Accessor("useItemRemaining")
    int anticrash$getItemUseTimeLeft();

    //?}

    //? if (>=1.18 && <=1.21.11) {
    /*@Accessor("useItemRemaining")
    void anticrash$setItemUseTimeLeft(int ticks);

    *///?} else if (>=26.1 && <=26.3) {
    @Accessor("useItemRemaining")
    void anticrash$setItemUseTimeLeft(int ticks);

    //?}

    //? if (>=1.18 && <=1.21.11) {
    /*@Accessor("useItem")
    ItemStack anticrash$getActiveItemStack();
    *///?} else if (>=26.1 && <=26.3) {
    @Accessor("useItem")
    ItemStack anticrash$getActiveItemStack();
    //?}

}
