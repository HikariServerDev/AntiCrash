package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.runtime.RuntimeExtensionNbt;

import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 26.3 data-driven brewing: preserve the semantic recipe preference, not manager objects. */
@Mixin(BrewingStandBlockEntity.class)
public abstract class BrewingRecipeBindingMixin {
    @Shadow @Final private net.minecraft.world.item.crafting.RecipeManager.CachedCheck quickCheck;

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void anticrash$saveRecipe(ValueOutput output, CallbackInfo ci) {
        if (!RuntimeExtensionNbt.savingLoadedChunk()) return;
        var key = ((RecipeMatchGetterAccessorMixin) quickCheck).anticrash$getRecipeId();
        RuntimeExtensionNbt.write(
                output,
                extension -> {
                    extension.remove("BrewingRecipeMatch");
                    if (key != null)
                        extension.putString("BrewingRecipeMatch", key.identifier().toString());
                });
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void anticrash$loadRecipe(ValueInput input, CallbackInfo ci) {
        var extension = RuntimeExtensionNbt.read(input);
        if (extension == null) return;
        var id =
                extension.contains("BrewingRecipeMatch")
                        ? net.minecraft.resources.Identifier.parse(
                                extension.getString("BrewingRecipeMatch").orElseThrow())
                        : null;
        ((RecipeMatchGetterAccessorMixin) quickCheck)
                .anticrash$setRecipeId(
                        id == null
                                ? null
                                : net.minecraft.resources.ResourceKey.create(
                                        net.minecraft.core.registries.Registries.RECIPE, id));
    }
}
