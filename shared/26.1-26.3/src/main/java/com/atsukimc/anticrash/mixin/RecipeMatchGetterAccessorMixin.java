package com.atsukimc.anticrash.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Audited anonymous MatchGetter implementation, not a replacement of recipe-manager maps. */
@Mixin(targets = "net.minecraft.world.item.crafting.RecipeManager$1")
public interface RecipeMatchGetterAccessorMixin {
    @Accessor("lastRecipe")
    net.minecraft.resources.ResourceKey<net.minecraft.world.item.crafting.Recipe<?>>
            anticrash$getRecipeId();

    @Accessor("lastRecipe")
    void anticrash$setRecipeId(
            net.minecraft.resources.ResourceKey<net.minecraft.world.item.crafting.Recipe<?>> id);
}
