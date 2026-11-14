//? if >=1.19 {
package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.runtime.RuntimeExtensionNbt;
import com.atsukimc.anticrash.runtime.VersionNbt;
import com.atsukimc.anticrash.util.VersionIds;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Restores only the semantic cached identifier on the newly constructed Vanilla CachedCheck. */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class RecipeMatchingBlockEntityMixin {

    @Shadow @Final private RecipeManager.CachedCheck quickCheck;

    //? if <=1.20.4 {
    /*@Inject(method = "saveAdditional", at = @At("TAIL"))
    private void anticrash$saveRecipeMatch(CompoundTag nbt, CallbackInfo ci) {
    *///?} else if <=1.21.5 {
    /*@Inject(method = "saveAdditional", at = @At("TAIL"))
    private void anticrash$saveRecipeMatch(
            CompoundTag nbt, net.minecraft.core.HolderLookup.Provider registries, CallbackInfo ci) {
    *///?} else {
    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void anticrash$saveRecipeMatch(
            net.minecraft.world.level.storage.ValueOutput nbt, CallbackInfo ci) {
    //?}
        if (!RuntimeExtensionNbt.savingLoadedChunk()) return;

        var id = ((RecipeMatchGetterAccessorMixin) quickCheck).anticrash$getRecipeId();

        RuntimeExtensionNbt.write(
                nbt,
                extension -> {
                    extension.remove("RecipeMatch");
                    //? if <=1.21.1 {
                    /*if (id != null) extension.putString("RecipeMatch", id.toString());
                    *///?} else {
                    if (id != null) extension.putString("RecipeMatch", VersionIds.key(id));
                    //?}

                });
    }

    //? if <=1.20.4 {
    /*@Inject(method = "load", at = @At("TAIL"))
    private void anticrash$loadRecipeMatch(CompoundTag nbt, CallbackInfo ci) {
    *///?} else if <=1.21.5 {
    /*@Inject(method = "loadAdditional", at = @At("TAIL"))
    private void anticrash$loadRecipeMatch(
            CompoundTag nbt, net.minecraft.core.HolderLookup.Provider registries, CallbackInfo ci) {
    *///?} else {
    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void anticrash$loadRecipeMatch(
            net.minecraft.world.level.storage.ValueInput nbt, CallbackInfo ci) {
    //?}

        CompoundTag extension = RuntimeExtensionNbt.read(nbt);

        if (extension == null) return;

        //? if <=1.21.1 {
        /*((RecipeMatchGetterAccessorMixin) quickCheck)
                .anticrash$setRecipeId(
                        VersionNbt.contains(extension, "RecipeMatch", 8)
                                ? VersionIds.tryParse(
                                        VersionNbt.getString(extension, "RecipeMatch"))
                                : null);
        *///?} else {
        ((RecipeMatchGetterAccessorMixin) quickCheck)
                .anticrash$setRecipeId(
                        VersionNbt.contains(extension, "RecipeMatch", 8)
                                ? net.minecraft.resources.ResourceKey.create(
                                        net.minecraft.core.registries.Registries.RECIPE,
                                        VersionIds.parse(
                                                VersionNbt.getString(extension, "RecipeMatch")))
                                : null);
        //?}

    }
}
//?}
