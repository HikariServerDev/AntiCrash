//? if (>=1.19 && <=26.3) {
package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.runtime.RuntimeExtensionNbt;
import com.atsukimc.anticrash.util.VersionIds;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;

//? if (>=1.19 && <=1.21.1) {
/*import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
*///?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Restores only the semantic cached identifier on the newly constructed Vanilla CachedCheck. */
//? if (>=1.19 && <=1.21.1) {
/*@Mixin(CampfireBlockEntity.class)
public abstract class CampfireRecipeMatchingMixin {
    @Shadow @Final private RecipeManager.CachedCheck quickCheck;

*///?} else {
@Mixin(CampfireBlockEntity.class)
public abstract class CampfireRecipeMatchingMixin
        implements com.atsukimc.anticrash.runtime.CampfireRecipeState {
    @org.spongepowered.asm.mixin.Unique private RecipeMatchGetterAccessorMixin anticrash$getter;

//?}

    //? if (>=1.21.2 && <=26.3) {
    @org.spongepowered.asm.mixin.Unique
    private net.minecraft.resources.ResourceKey<net.minecraft.world.item.crafting.Recipe<?>>
            anticrash$savedId;

    @org.spongepowered.asm.mixin.Unique private boolean anticrash$pending;

    @Override
    public void anticrash$bindRecipeGetter(RecipeMatchGetterAccessorMixin getter) {
        if (anticrash$pending && getter != null) {
            getter.anticrash$setRecipeId(anticrash$savedId);
            anticrash$pending = false;
        }
        anticrash$getter = getter;
        if (getter == null) {
            anticrash$savedId = null;
            anticrash$pending = false;
        }
    }

    @Inject(method = "cookTick", at = @At("HEAD"))
    private static void anticrash$restoreRecipeBeforeFirstTick(
            net.minecraft.server.level.ServerLevel world,
            net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state,
            CampfireBlockEntity campfire,
            net.minecraft.world.item.crafting.RecipeManager.CachedCheck getter,
            CallbackInfo ci) {
        ((com.atsukimc.anticrash.runtime.CampfireRecipeState) campfire)
                .anticrash$bindRecipeGetter((RecipeMatchGetterAccessorMixin) getter);
    }

    //?}

    //? if (>=1.19 && <=1.20.4) {
    /*@Inject(method = "saveAdditional", at = @At("TAIL"))
    private void anticrash$saveRecipeMatch(CompoundTag nbt, CallbackInfo ci)
    *///?} else if <= 1.21.5 {
    /*@Inject(method = "saveAdditional", at = @At("TAIL"))
    private void anticrash$saveRecipeMatch(
            CompoundTag nbt, net.minecraft.core.HolderLookup.Provider registries, CallbackInfo ci)
    *///?} else {
    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void anticrash$saveRecipeMatch(
            net.minecraft.world.level.storage.ValueOutput nbt, CallbackInfo ci)
    //?}
            {
        if (!RuntimeExtensionNbt.savingLoadedChunk()) return;

        //? if (>=1.19 && <=1.21.1) {
        /*var id = ((RecipeMatchGetterAccessorMixin) quickCheck).anticrash$getRecipeId();
        *///?} else {
        var id =
                anticrash$getter == null
                        ? anticrash$savedId
                        : anticrash$getter.anticrash$getRecipeId();
        //?}

        RuntimeExtensionNbt.write(
                nbt,
                extension -> {
                    extension.remove("RecipeMatch");
                    if (id != null)
                        //? if (>=1.19 && <=1.21.1) {
                        /*extension.putString("RecipeMatch", id.toString());
                        *///?} else if <= 1.21.10 {
                        /*extension.putString("RecipeMatch", VersionIds.key(id));
                        *///?} else {
                        extension.putString("RecipeMatch", VersionIds.key(id));
                        //?}
                });
    }

    //? if (>=1.19 && <=1.20.4) {
    /*@Inject(method = "load", at = @At("TAIL"))
    private void anticrash$loadRecipeMatch(CompoundTag nbt, CallbackInfo ci)
    *///?} else if <= 1.21.5 {
    /*@Inject(method = "loadAdditional", at = @At("TAIL"))
    private void anticrash$loadRecipeMatch(
            CompoundTag nbt, net.minecraft.core.HolderLookup.Provider registries, CallbackInfo ci)
    *///?} else {
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
        *///?} else if <= 1.21.4 {
        /*anticrash$savedId =
                extension.contains("RecipeMatch", 8)
                        ? net.minecraft.resources.ResourceKey.create(
                                net.minecraft.core.registries.Registries.RECIPE,
                                VersionIds.parse(extension.getString("RecipeMatch")))
                        : null;
        *///?} else if <= 1.21.10 {
        /*anticrash$savedId =
                com.atsukimc.anticrash.runtime.VersionNbt.contains(extension, "RecipeMatch", 8)
                        ? net.minecraft.resources.ResourceKey.create(
                                net.minecraft.core.registries.Registries.RECIPE,
                                VersionIds.parse(extension.getString("RecipeMatch").orElseThrow()))
                        : null;
        *///?} else {
        anticrash$savedId =
                com.atsukimc.anticrash.runtime.VersionNbt.contains(extension, "RecipeMatch", 8)
                        ? net.minecraft.resources.ResourceKey.create(
                                net.minecraft.core.registries.Registries.RECIPE,
                                VersionIds.parse(extension.getString("RecipeMatch").orElseThrow()))
                        : null;
        //?}

        //? if (>=1.21.2 && <=26.3) {
        anticrash$pending = true;
        //?}
    }
}
//?}
