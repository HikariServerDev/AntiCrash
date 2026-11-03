package com.atsukimc.anticrash.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.world.entity.Entity;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.world.entity.Entity;
//?}

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.world.level.entity.EntityTickList;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.world.level.entity.EntityTickList;
//?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

//? if (>=1.18 && <=1.21.11) {
/*@Mixin(EntityTickList.class)
public interface EntityListAccessorMixin {
    @Accessor("active")
    Int2ObjectMap<Entity> anticrash$getEntities();

    *///?} else if (>=26.1 && <=26.3) {
@Mixin(EntityTickList.class)
public interface EntityListAccessorMixin {
    @Accessor("active")
    Int2ObjectMap<Entity> anticrash$getEntities();

    //?}

    //? if (>=1.18 && <=1.21.11) {
    /*@Accessor("iterated")
    Int2ObjectMap<Entity> anticrash$getIterating();
    *///?} else if (>=26.1 && <=26.3) {
    @Accessor("iterated")
    Int2ObjectMap<Entity> anticrash$getIterating();
    //?}

}
