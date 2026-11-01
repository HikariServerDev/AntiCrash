package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.runtime.RuntimeExtensionNbt;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Restores only the semantic cached identifier on the newly constructed Vanilla MatchGetter. */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class RecipeMatchingBlockEntityMixin {
    @Shadow @Final private net.minecraft.world.item.crafting.RecipeManager.CachedCheck quickCheck;

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void anticrash$saveRecipeMatch(
            net.minecraft.world.level.storage.ValueOutput nbt, CallbackInfo ci) {
        if (!RuntimeExtensionNbt.savingLoadedChunk()) return;
        var id = ((RecipeMatchGetterAccessorMixin) quickCheck).anticrash$getRecipeId();
        RuntimeExtensionNbt.write(
                nbt,
                extension -> {
                    extension.remove("RecipeMatch");
                    if (id != null) extension.putString("RecipeMatch", id.identifier().toString());
                });
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void anticrash$loadRecipeMatch(
            net.minecraft.world.level.storage.ValueInput nbt, CallbackInfo ci) {
        CompoundTag extension = RuntimeExtensionNbt.read(nbt);
        if (extension == null) return;
        ((RecipeMatchGetterAccessorMixin) quickCheck)
                .anticrash$setRecipeId(
                        com.atsukimc.anticrash.runtime.VersionNbt.contains(
                                        extension, "RecipeMatch", 8)
                                ? net.minecraft.resources.ResourceKey.create(
                                        net.minecraft.core.registries.Registries.RECIPE,
                                        Identifier.parse(
                                                extension.getString("RecipeMatch").orElseThrow()))
                                : null);
    }
}
