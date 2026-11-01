package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.runtime.RuntimeExtensionNbt;

import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Restores only the semantic cached identifier on the newly constructed Vanilla MatchGetter. */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class RecipeMatchingBlockEntityMixin {
    @Shadow @Final private net.minecraft.recipe.ServerRecipeManager.MatchGetter matchGetter;

    @Inject(method = "writeData", at = @At("TAIL"))
    private void anticrash$saveRecipeMatch(net.minecraft.storage.WriteView nbt, CallbackInfo ci) {
        if (!RuntimeExtensionNbt.savingLoadedChunk()) return;
        var id = ((RecipeMatchGetterAccessorMixin) matchGetter).anticrash$getRecipeId();
        RuntimeExtensionNbt.write(
                nbt,
                extension -> {
                    extension.remove("RecipeMatch");
                    if (id != null) extension.putString("RecipeMatch", id.getValue().toString());
                });
    }

    @Inject(method = "readData", at = @At("TAIL"))
    private void anticrash$loadRecipeMatch(net.minecraft.storage.ReadView nbt, CallbackInfo ci) {
        NbtCompound extension = RuntimeExtensionNbt.read(nbt);
        if (extension == null) return;
        ((RecipeMatchGetterAccessorMixin) matchGetter)
                .anticrash$setRecipeId(
                        com.atsukimc.anticrash.runtime.VersionNbt.contains(
                                        extension, "RecipeMatch", 8)
                                ? net.minecraft.registry.RegistryKey.of(
                                        net.minecraft.registry.RegistryKeys.RECIPE,
                                        Identifier.of(
                                                extension.getString("RecipeMatch").orElseThrow()))
                                : null);
    }
}
