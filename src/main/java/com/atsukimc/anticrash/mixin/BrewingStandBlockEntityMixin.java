package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.runtime.VersionNbt;
import com.atsukimc.anticrash.util.VersionIds;

//? if (>=1.18 && <=26.2) {
/*import net.minecraft.world.level.Level;
*///?}

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** {@code BrewTime} counts down every tick without marking the chunk for saving. */
@Mixin(BrewingStandBlockEntity.class)
public abstract class BrewingStandBlockEntityMixin {

    //? if (>=1.18 && <=26.2) {
    /*@Inject(method = "serverTick", at = @At("HEAD"))
    private static void anticrash$markBrewTimeDirty(
            Level world,
            BlockPos pos,
            BlockState state,
            BrewingStandBlockEntity stand,
            CallbackInfo callbackInfo)
    *///?}
    //? if >=26.3 {
    @Inject(method = "serverTick", at = @At("HEAD"))
    private static void anticrash$markBrewTimeDirty(
            net.minecraft.server.level.ServerLevel world,
            BlockPos pos,
            BlockState state,
            BrewingStandBlockEntity stand,
            CallbackInfo callbackInfo)
    //?}
            {
        if (((BrewingStandBlockEntityTimerAccessor) stand).anticrash$getBrewTime() > 0)
            world.blockEntityChanged(pos);
    }

    @org.spongepowered.asm.mixin.Shadow private int brewTime;

    @org.spongepowered.asm.mixin.Shadow private net.minecraft.world.item.Item ingredient;

    /**
     * Vanilla does not save which ingredient the running brew started with. After a load it is
     * null, the first tick sees "ingredient changed" and resets the brew to zero, so a brew in
     * progress starts over (up to 400 ticks late).
     */

    //? if (>=1.18 && <=1.20.4) {
    /*@Inject(method = "saveAdditional", at = @At("TAIL"))
    private void anticrash$saveBrewingItem(
            net.minecraft.nbt.CompoundTag nbt, CallbackInfo callbackInfo)
    *///?} else if <=1.21.5 {
    /*@Inject(method = "saveAdditional", at = @At("TAIL"))
    private void anticrash$saveBrewingItem(
            net.minecraft.nbt.CompoundTag nbt,
            net.minecraft.core.HolderLookup.Provider registries,
            CallbackInfo callbackInfo)
    *///?} else {
    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void anticrash$saveBrewingItem(
            net.minecraft.world.level.storage.ValueOutput nbt, CallbackInfo callbackInfo)
    //?}
            {
        if (!com.atsukimc.anticrash.runtime.RuntimeExtensionNbt.savingLoadedChunk()) return;
        com.atsukimc.anticrash.runtime.RuntimeExtensionNbt.write(
                nbt,
                extension -> {
                    extension.remove("ItemBrewing");
                    if (brewTime > 0 && ingredient != null) {
                        extension.putString(
                                "ItemBrewing", VersionIds.id(VersionIds.items(), ingredient));
                    }
                });
    }

    //? if (>=1.18 && <=1.20.4) {
    /*@Inject(method = "load", at = @At("TAIL"))
    private void anticrash$loadBrewingItem(
            net.minecraft.nbt.CompoundTag nbt, CallbackInfo callbackInfo)
    *///?} else if <=1.21.5 {
    /*@Inject(method = "loadAdditional", at = @At("TAIL"))
    private void anticrash$loadBrewingItem(
            net.minecraft.nbt.CompoundTag nbt,
            net.minecraft.core.HolderLookup.Provider registries,
            CallbackInfo callbackInfo)
    *///?} else {
    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void anticrash$loadBrewingItem(
            net.minecraft.world.level.storage.ValueInput nbt, CallbackInfo callbackInfo)
    //?}
            {
        net.minecraft.nbt.CompoundTag extension =
                com.atsukimc.anticrash.runtime.RuntimeExtensionNbt.read(nbt);

        if (extension == null || !VersionNbt.contains(extension, "ItemBrewing", 8) || brewTime <= 0)
            return;

        var id = VersionIds.tryParse(VersionNbt.getString(extension, "ItemBrewing"));

        if (id != null && VersionIds.items().containsKey(id)) {
            ingredient = VersionIds.value(VersionIds.items(), id);
        }
    }
}
