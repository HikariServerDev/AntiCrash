package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.runtime.RuntimeExtensionNbt;

import net.minecraft.block.entity.CampfireBlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Restores only the semantic cached identifier on the newly constructed Vanilla MatchGetter. */
@Mixin(CampfireBlockEntity.class)
public abstract class CampfireRecipeMatchingMixin
        implements com.atsukimc.anticrash.runtime.CampfireRecipeState {
    @org.spongepowered.asm.mixin.Unique private RecipeMatchGetterAccessorMixin anticrash$getter;

    @org.spongepowered.asm.mixin.Unique
    private net.minecraft.registry.RegistryKey<net.minecraft.recipe.Recipe<?>> anticrash$savedId;

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

    @Inject(method = "litServerTick", at = @At("HEAD"))
    private static void anticrash$restoreRecipeBeforeFirstTick(
            net.minecraft.server.world.ServerWorld world,
            net.minecraft.util.math.BlockPos pos,
            net.minecraft.block.BlockState state,
            CampfireBlockEntity campfire,
            net.minecraft.recipe.ServerRecipeManager.MatchGetter getter,
            CallbackInfo ci) {
        ((com.atsukimc.anticrash.runtime.CampfireRecipeState) campfire)
                .anticrash$bindRecipeGetter((RecipeMatchGetterAccessorMixin) getter);
    }

    @Inject(method = "writeData", at = @At("TAIL"))
    private void anticrash$saveRecipeMatch(net.minecraft.storage.WriteView nbt, CallbackInfo ci) {
        if (!RuntimeExtensionNbt.savingLoadedChunk()) return;
        var id =
                anticrash$getter == null
                        ? anticrash$savedId
                        : anticrash$getter.anticrash$getRecipeId();
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
        anticrash$savedId =
                com.atsukimc.anticrash.runtime.VersionNbt.contains(extension, "RecipeMatch", 8)
                        ? net.minecraft.registry.RegistryKey.of(
                                net.minecraft.registry.RegistryKeys.RECIPE,
                                Identifier.of(extension.getString("RecipeMatch").orElseThrow()))
                        : null;
        anticrash$pending = true;
    }
}
