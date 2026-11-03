//? if (>=1.19 && <=26.3) {
package com.atsukimc.anticrash.mixin;

//? if (>=1.19 && <=1.21.1) {
/*import net.minecraft.resources.ResourceLocation;
*///?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Audited anonymous CachedCheck implementation, not a replacement of recipe-manager maps. */
//? if (>=1.19 && <=1.21.1) {
/*@Mixin(targets = "net.minecraft.world.item.crafting.RecipeManager$1")
public interface RecipeMatchGetterAccessorMixin {
    @Accessor("lastRecipe")
    ResourceLocation anticrash$getRecipeId();
*///?} else if (>=1.21.2 && <=1.21.11) {
/*@Mixin(targets = "net.minecraft.world.item.crafting.RecipeManager$1")
public interface RecipeMatchGetterAccessorMixin {
    @Accessor("lastRecipe")
    net.minecraft.resources.ResourceKey<net.minecraft.world.item.crafting.Recipe<?>> anticrash$getRecipeId();
*///?} else if (>=26.1 && <=26.3) {
@Mixin(targets = "net.minecraft.world.item.crafting.RecipeManager$1")
public interface RecipeMatchGetterAccessorMixin {
    @Accessor("lastRecipe")
    net.minecraft.resources.ResourceKey<net.minecraft.world.item.crafting.Recipe<?>>
            anticrash$getRecipeId();

    //?}

    //? if (>=1.19 && <=1.21.1) {
    /*@Accessor("lastRecipe")
    void anticrash$setRecipeId(ResourceLocation id);
*///?} else if (>=1.21.2 && <=1.21.11) {
    /*@Accessor("lastRecipe")
    void anticrash$setRecipeId(
            net.minecraft.resources.ResourceKey<net.minecraft.world.item.crafting.Recipe<?>> id);
*///?} else if (>=26.1 && <=26.3) {
    @Accessor("lastRecipe")
    void anticrash$setRecipeId(
            net.minecraft.resources.ResourceKey<net.minecraft.world.item.crafting.Recipe<?>> id);
    //?}

}
//?}
