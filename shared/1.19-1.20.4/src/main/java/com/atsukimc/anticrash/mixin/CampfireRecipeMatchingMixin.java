package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.runtime.RuntimeExtensionNbt;

import net.minecraft.block.entity.CampfireBlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.util.Identifier;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Restores only the semantic cached identifier on the newly constructed Vanilla MatchGetter. */
@Mixin(CampfireBlockEntity.class)
public abstract class CampfireRecipeMatchingMixin {
    @Shadow @Final private RecipeManager.MatchGetter matchGetter;

    @Inject(method = "writeNbt", at = @At("TAIL"))
    private void anticrash$saveRecipeMatch(NbtCompound nbt, CallbackInfo ci) {
        if (!RuntimeExtensionNbt.savingLoadedChunk()) return;
        Identifier id = ((RecipeMatchGetterAccessorMixin) matchGetter).anticrash$getRecipeId();
        RuntimeExtensionNbt.write(
                nbt,
                extension -> {
                    extension.remove("RecipeMatch");
                    if (id != null) extension.putString("RecipeMatch", id.toString());
                });
    }

    @Inject(method = "readNbt", at = @At("TAIL"))
    private void anticrash$loadRecipeMatch(NbtCompound nbt, CallbackInfo ci) {
        NbtCompound extension = RuntimeExtensionNbt.read(nbt);
        if (extension == null) return;
        ((RecipeMatchGetterAccessorMixin) matchGetter)
                .anticrash$setRecipeId(
                        extension.contains("RecipeMatch", 8)
                                ? Identifier.tryParse(extension.getString("RecipeMatch"))
                                : null);
    }
}
