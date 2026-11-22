//? if >=1.19 {
package com.atsukimc.anticrash.mixin;

//? if <=1.21.1 {
/*import net.minecraft.resources.ResourceLocation;
*///?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Audited anonymous CachedCheck implementation, not a replacement of recipe-manager maps. */
@Mixin(targets = "net.minecraft.world.item.crafting.RecipeManager$1")
public interface RecipeMatchGetterAccessorMixin {
    //? if <=1.21.1 {
    /*@Accessor("lastRecipe")
    ResourceLocation anticrash$getRecipeId();

    @Accessor("lastRecipe")
    void anticrash$setRecipeId(ResourceLocation id);

    *///?} else {
    @Accessor("lastRecipe")
    net.minecraft.resources.ResourceKey<net.minecraft.world.item.crafting.Recipe<?>>
            anticrash$getRecipeId();

    @Accessor("lastRecipe")
    void anticrash$setRecipeId(
            net.minecraft.resources.ResourceKey<net.minecraft.world.item.crafting.Recipe<?>> id);
    //?}

}
//?}
