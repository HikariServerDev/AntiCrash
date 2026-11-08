//? if (>=1.19 && <=26.3) {
package com.atsukimc.anticrash.mixin;

//? if (>=1.19 && <=1.21.1) {
/*import net.minecraft.world.item.crafting.RecipeManager;
*///?}

import com.atsukimc.anticrash.runtime.RuntimeExtensionNbt;
import com.atsukimc.anticrash.util.VersionIds;

import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.nbt.CompoundTag;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Restores only the semantic cached identifier on the newly constructed Vanilla CachedCheck. */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class RecipeMatchingBlockEntityMixin {

    //? if (>=1.19 && <=1.21.1) {
    /*@Shadow @Final private RecipeManager.CachedCheck quickCheck;

    *///?}
    //? if (>=1.21.2 && <=26.3) {
    @Shadow @Final private net.minecraft.world.item.crafting.RecipeManager.CachedCheck quickCheck;

    //?}

    //? if (>=1.19 && <=1.20.4) {
    /*@Inject(method = "saveAdditional", at = @At("TAIL"))
    private void anticrash$saveRecipeMatch(CompoundTag nbt, CallbackInfo ci)
    *///?}
    //? if (>=1.20.5 && <=1.21.5) {
    /*@Inject(method = "saveAdditional", at = @At("TAIL"))
    private void anticrash$saveRecipeMatch(
            CompoundTag nbt, net.minecraft.core.HolderLookup.Provider registries, CallbackInfo ci)
    *///?}
    //? if (>=1.21.6 && <=26.3) {
    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void anticrash$saveRecipeMatch(
            net.minecraft.world.level.storage.ValueOutput nbt, CallbackInfo ci)
    //?}
            {
        if (!RuntimeExtensionNbt.savingLoadedChunk()) return;

        //? if (>=1.19 && <=1.21.1) {
        /*var id = ((RecipeMatchGetterAccessorMixin) quickCheck).anticrash$getRecipeId();
        *///?}
        //? if (>=1.21.2 && <=26.3) {
        var id = ((RecipeMatchGetterAccessorMixin) quickCheck).anticrash$getRecipeId();
        //?}

        RuntimeExtensionNbt.write(
                nbt,
                extension -> {
                    extension.remove("RecipeMatch");
                    if (id != null)
                        //? if (>=1.19 && <=1.21.1) {
                        /*extension.putString("RecipeMatch", id.toString());
                        *///?}
                        //? if (>=1.21.2 && <=1.21.10) {
                        /*extension.putString("RecipeMatch", VersionIds.key(id));
                        *///?}
                        //? if (>=1.21.11 && <=26.3) {
                        extension.putString("RecipeMatch", VersionIds.key(id));
                        //?}

                });
    }

    //? if (>=1.19 && <=1.20.4) {
    /*@Inject(method = "load", at = @At("TAIL"))
    private void anticrash$loadRecipeMatch(CompoundTag nbt, CallbackInfo ci)
    *///?}
    //? if (>=1.20.5 && <=1.21.5) {
    /*@Inject(method = "loadAdditional", at = @At("TAIL"))
    private void anticrash$loadRecipeMatch(
            CompoundTag nbt, net.minecraft.core.HolderLookup.Provider registries, CallbackInfo ci)
    *///?}
    //? if (>=1.21.6 && <=26.3) {
    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void anticrash$loadRecipeMatch(
            net.minecraft.world.level.storage.ValueInput nbt, CallbackInfo ci)
    //?}
            {

        CompoundTag extension = RuntimeExtensionNbt.read(nbt);

        if (extension == null) return;

        //? if (>=1.19 && <=1.21.1) {
        /*((RecipeMatchGetterAccessorMixin) quickCheck)
                .anticrash$setRecipeId(
                        extension.contains("RecipeMatch", 8)
                                ? VersionIds.tryParse(extension.getString("RecipeMatch"))
                                : null);
        *///?}
        //? if (>=1.21.2 && <=1.21.4) {
        /*((RecipeMatchGetterAccessorMixin) quickCheck)
                .anticrash$setRecipeId(
                        extension.contains("RecipeMatch", 8)
                                ? net.minecraft.resources.ResourceKey.create(
                                        net.minecraft.core.registries.Registries.RECIPE,
                                        VersionIds.parse(extension.getString("RecipeMatch")))
                                : null);
        *///?}
        //? if (>=1.21.5 && <=1.21.11) {
        /*((RecipeMatchGetterAccessorMixin) quickCheck)
                .anticrash$setRecipeId(
                        com.atsukimc.anticrash.runtime.VersionNbt.contains(
                                        extension, "RecipeMatch", 8)
                                ? net.minecraft.resources.ResourceKey.create(
                                        net.minecraft.core.registries.Registries.RECIPE,
        *///?}
                                        //? if (>=1.21.5 && <=1.21.10) {
                                        /*VersionIds.parse(
                                        *///?}
                                        //? if 1.21.11 {
                                        /*VersionIds.parse(
                                        *///?}
                                                //? if (>=1.21.5 && <=1.21.11) {
                                                /*extension.getString("RecipeMatch").orElseThrow()))
                                : null);
                                                *///?}
        //? if (>=26.1 && <=26.3) {
        ((RecipeMatchGetterAccessorMixin) quickCheck)
                .anticrash$setRecipeId(
                        com.atsukimc.anticrash.runtime.VersionNbt.contains(
                                        extension, "RecipeMatch", 8)
                                ? net.minecraft.resources.ResourceKey.create(
                                        net.minecraft.core.registries.Registries.RECIPE,
                                        VersionIds.parse(
                                                extension.getString("RecipeMatch").orElseThrow()))
                                : null);
        //?}

    }
}
//?}
