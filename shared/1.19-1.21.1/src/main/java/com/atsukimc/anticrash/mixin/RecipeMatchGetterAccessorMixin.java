package com.atsukimc.anticrash.mixin;

import net.minecraft.util.Identifier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Audited anonymous MatchGetter implementation, not a replacement of recipe-manager maps. */
@Mixin(targets = "net.minecraft.recipe.RecipeManager$1")
public interface RecipeMatchGetterAccessorMixin {
    @Accessor("id")
    Identifier anticrash$getRecipeId();

    @Accessor("id")
    void anticrash$setRecipeId(Identifier id);
}
