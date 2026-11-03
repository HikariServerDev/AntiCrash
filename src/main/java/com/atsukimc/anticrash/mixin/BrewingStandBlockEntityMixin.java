package com.atsukimc.anticrash.mixin;

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.world.level.block.state.BlockState;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.core.BlockPos;
//?}

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
*///?}
//? if (>=26.1 && <=26.2) {
/*import net.minecraft.world.level.Level;
*///?}

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.core.BlockPos;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
//?}

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.world.level.Level;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.world.level.block.state.BlockState;
//?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** {@code BrewTime} counts down every tick without marking the chunk for saving. */
@Mixin(BrewingStandBlockEntity.class)
public abstract class BrewingStandBlockEntityMixin {

    //? if (>=1.18 && <=1.21.11) {
    /*@Inject(method = "serverTick", at = @At("HEAD"))
    private static void anticrash$markBrewTimeDirty(
            Level world,
            BlockPos pos,
            BlockState state,
            BrewingStandBlockEntity stand,
            CallbackInfo callbackInfo)
                *///?} else if (>=26.1 && <=26.2) {
    /*@Inject(method = "serverTick", at = @At("HEAD"))
                    private static void anticrash$markBrewTimeDirty(
                            Level world,
                            BlockPos pos,
                            BlockState state,
                            BrewingStandBlockEntity stand,
                            CallbackInfo callbackInfo)
*///?} else if 26.3 {
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

            //? if (>=1.18 && <=1.21.11) {
            /*world.blockEntityChanged(pos);
        *///?} else if (>=26.1 && <=26.3) {
            world.blockEntityChanged(pos);
        //?}

    }

    @org.spongepowered.asm.mixin.Shadow private int brewTime;

    //? if (>=1.18 && <=1.21.11) {
    /*@org.spongepowered.asm.mixin.Shadow private net.minecraft.world.item.Item ingredient;

    *///?} else if (>=26.1 && <=26.3) {
    @org.spongepowered.asm.mixin.Shadow private net.minecraft.world.item.Item ingredient;

    //?}

    /**
     * Vanilla does not save which ingredient the running brew started with. After a load it is
     * null, the first tick sees "ingredient changed" and resets the brew to zero, so a brew in
     * progress starts over (up to 400 ticks late).
     */

    //? if (>=1.18 && <=1.20.4) {
    /*@Inject(method = "saveAdditional", at = @At("TAIL"))
    private void anticrash$saveBrewingItem(
            net.minecraft.nbt.CompoundTag nbt, CallbackInfo callbackInfo)
                *///?} else if (>=1.20.5 && <=1.21.5) {
    /*@Inject(method = "saveAdditional", at = @At("TAIL"))
                    private void anticrash$saveBrewingItem(
                            net.minecraft.nbt.CompoundTag nbt,
                            net.minecraft.core.HolderLookup.Provider registries,
                            CallbackInfo callbackInfo)
*///?} else if (>=1.21.6 && <=1.21.11) {
    /*@Inject(method = "saveAdditional", at = @At("TAIL"))
                    private void anticrash$saveBrewingItem(
                            net.minecraft.world.level.storage.ValueOutput nbt, CallbackInfo callbackInfo)
*///?} else if (>=26.1 && <=26.3) {
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

                    //? if (>=1.18 && <=1.21.11) {
                    /*if (brewTime > 0 && ingredient != null)
                    *///?} else if (>=26.1 && <=26.3) {
                    if (brewTime > 0 && ingredient != null)
                    //?}
                    {

                        //? if (>=1.18 && <=1.19.2) {
                        /*extension.putString(
                                "ItemBrewing",
                                net.minecraft.core.Registry.ITEM.getKey(ingredient).toString());
                        *///?} else if (>=1.19.3 && <=1.21.11) {
                        /*extension.putString(
                                                        "ItemBrewing",
                                                        net.minecraft.core.registries.BuiltInRegistries.ITEM
                                                                .getKey(ingredient)
                                                                .toString());
*///?} else if (>=26.1 && <=26.3) {
                        extension.putString(
                                "ItemBrewing",
                                net.minecraft.core.registries.BuiltInRegistries.ITEM
                                        .getKey(ingredient)
                                        .toString());
                        //?}

                    }
                });
    }

    //? if (>=1.18 && <=1.20.4) {
    /*@Inject(method = "load", at = @At("TAIL"))
    private void anticrash$loadBrewingItem(
            net.minecraft.nbt.CompoundTag nbt, CallbackInfo callbackInfo)
                *///?} else if (>=1.20.5 && <=1.21.5) {
    /*@Inject(method = "loadAdditional", at = @At("TAIL"))
                    private void anticrash$loadBrewingItem(
                            net.minecraft.nbt.CompoundTag nbt,
                            net.minecraft.core.HolderLookup.Provider registries,
                            CallbackInfo callbackInfo)
*///?} else if (>=1.21.6 && <=1.21.11) {
    /*@Inject(method = "loadAdditional", at = @At("TAIL"))
                    private void anticrash$loadBrewingItem(
                            net.minecraft.world.level.storage.ValueInput nbt, CallbackInfo callbackInfo)
*///?} else if (>=26.1 && <=26.3) {
    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void anticrash$loadBrewingItem(
            net.minecraft.world.level.storage.ValueInput nbt, CallbackInfo callbackInfo)
                //?}
            {

        //? if (>=1.18 && <=1.21.11) {
        /*net.minecraft.nbt.CompoundTag extension =
                com.atsukimc.anticrash.runtime.RuntimeExtensionNbt.read(nbt);
        *///?} else if (>=26.1 && <=26.3) {
        net.minecraft.nbt.CompoundTag extension =
                com.atsukimc.anticrash.runtime.RuntimeExtensionNbt.read(nbt);
        //?}

        //? if (>=1.18 && <=1.21.4) {
        /*if (extension == null || !extension.contains("ItemBrewing", 8) || brewTime <= 0)
            *///?} else if (>=1.21.5 && <=26.3) {
        if (extension == null
                || !com.atsukimc.anticrash.runtime.VersionNbt.contains(extension, "ItemBrewing", 8)
                || brewTime <= 0)
            //?}
            return;

        //? if (>=1.18 && <=1.21.4) {
        /*net.minecraft.resources.ResourceLocation id =
                net.minecraft.resources.ResourceLocation.tryParse(
                        extension.getString("ItemBrewing"));
        *///?} else if (>=1.21.5 && <=1.21.10) {
        /*net.minecraft.resources.ResourceLocation id =
        *///?} else if 1.21.11 {
        /*net.minecraft.resources.Identifier id =
        *///?}
        //? if (>=1.21.5 && <=1.21.10) {
        /*net.minecraft.resources.ResourceLocation.tryParse(
        *///?} else if 1.21.11 {
        /*net.minecraft.resources.Identifier.tryParse(
        *///?}
        //? if (>=1.21.5 && <=1.21.11) {
        /*extension.getString("ItemBrewing").orElseThrow());
*///?} else if (>=26.1 && <=26.3) {
        net.minecraft.resources.Identifier id =
                net.minecraft.resources.Identifier.tryParse(
                        extension.getString("ItemBrewing").orElseThrow());
        //?} else if (>=1.18 && <=1.19.2) {
        /*if (id != null && net.minecraft.core.Registry.ITEM.containsKey(id))
        *///?}
        //? if (>=1.19.3 && <=1.21.11) {
        /*if (id != null && net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id))
*///?} else if (>=26.1 && <=26.3) {
        if (id != null && net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id))
        //?}
        {

            //? if (>=1.18 && <=1.19.2) {
            /*ingredient = net.minecraft.core.Registry.ITEM.get(id);
            *///?} else if (>=1.19.3 && <=1.21.1) {
            /*ingredient = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);
            *///?} else if (>=1.21.2 && <=1.21.11) {
            /*ingredient = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(id);
            *///?} else if (>=26.1 && <=26.3) {
            ingredient = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(id);
            //?}

        }
    }
}
