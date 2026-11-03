//? if (>=1.21.2 && <=26.3) {
/*package com.atsukimc.anticrash.mixin;

//? if (>=1.21.2 && <=1.21.11) {
/^import net.minecraft.entity.player.HungerManager;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.food.FoodData;
^///?}


import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/^* 1.21.2 removed the public exhaustion accessors, not the persisted exhaustion field. ^/
//? if (>=1.21.2 && <=1.21.11) {
/^@Mixin(HungerManager.class)
public interface HungerManagerAccessorMixin {
    @Accessor("exhaustion")
    float anticrash$getExhaustion();
^///?}
//? if (>=26.1 && <=26.3) {
/^@Mixin(FoodData.class)
public interface HungerManagerAccessorMixin {
    @Accessor("exhaustionLevel")
    float anticrash$getExhaustion();
^///?}



//? if (>=1.21.2 && <=1.21.11) {
/^@Accessor("exhaustion")
    void anticrash$setExhaustion(float value);
^///?}
//? if (>=26.1 && <=26.3) {
/^@Accessor("exhaustionLevel")
    void anticrash$setExhaustion(float value);
^///?}

}
*///?}
