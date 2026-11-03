//? if (>=1.19 && <=26.3) {
/*package com.atsukimc.anticrash.mixin;

//? if (>=1.19 && <=1.21.1) {
/^import net.minecraft.util.Identifier;
^///?}


import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/^* Audited anonymous MatchGetter implementation, not a replacement of recipe-manager maps. ^/
//? if (>=1.19 && <=1.21.1) {
/^@Mixin(targets = "net.minecraft.recipe.RecipeManager$1")
public interface RecipeMatchGetterAccessorMixin {
    @Accessor("id")
    Identifier anticrash$getRecipeId();
^///?}
//? if (>=1.21.2 && <=1.21.11) {
/^@Mixin(targets = "net.minecraft.recipe.ServerRecipeManager$1")
public interface RecipeMatchGetterAccessorMixin {
    @Accessor("id")
    net.minecraft.registry.RegistryKey<net.minecraft.recipe.Recipe<?>> anticrash$getRecipeId();
^///?}
//? if (>=26.1 && <=26.3) {
/^@Mixin(targets = "net.minecraft.world.item.crafting.RecipeManager$1")
public interface RecipeMatchGetterAccessorMixin {
    @Accessor("lastRecipe")
    net.minecraft.resources.ResourceKey<net.minecraft.world.item.crafting.Recipe<?>>
            anticrash$getRecipeId();
^///?}



//? if (>=1.19 && <=1.21.1) {
/^@Accessor("id")
    void anticrash$setRecipeId(Identifier id);
^///?}
//? if (>=1.21.2 && <=1.21.11) {
/^@Accessor("id")
    void anticrash$setRecipeId(
            net.minecraft.registry.RegistryKey<net.minecraft.recipe.Recipe<?>> id);
^///?}
//? if (>=26.1 && <=26.3) {
/^@Accessor("lastRecipe")
    void anticrash$setRecipeId(
            net.minecraft.resources.ResourceKey<net.minecraft.world.item.crafting.Recipe<?>> id);
^///?}

}
*///?}
