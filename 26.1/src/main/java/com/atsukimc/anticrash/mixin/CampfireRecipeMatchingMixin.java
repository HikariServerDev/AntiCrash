package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.runtime.RuntimeExtensionNbt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Restores only the semantic cached identifier on the newly constructed Vanilla MatchGetter. */
@Mixin(CampfireBlockEntity.class)
public abstract class CampfireRecipeMatchingMixin implements com.atsukimc.anticrash.runtime.CampfireRecipeState {
    @org.spongepowered.asm.mixin.Unique private RecipeMatchGetterAccessorMixin anticrash$getter;
    @org.spongepowered.asm.mixin.Unique private net.minecraft.resources.ResourceKey<net.minecraft.world.item.crafting.Recipe<?>> anticrash$savedId;
    @org.spongepowered.asm.mixin.Unique private boolean anticrash$pending;

    @Override
    public void anticrash$bindRecipeGetter(RecipeMatchGetterAccessorMixin getter) {
        if (anticrash$pending && getter != null) {
            getter.anticrash$setRecipeId(anticrash$savedId);
            anticrash$pending = false;
        }
        anticrash$getter = getter;
        if (getter == null) { anticrash$savedId = null; anticrash$pending = false; }
    }

    @Inject(method = "cookTick", at = @At("HEAD"))
    private static void anticrash$restoreRecipeBeforeFirstTick(net.minecraft.server.level.ServerLevel world,
            net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state,
            CampfireBlockEntity campfire, net.minecraft.world.item.crafting.RecipeManager.CachedCheck getter, CallbackInfo ci) {
        ((com.atsukimc.anticrash.runtime.CampfireRecipeState) campfire)
                .anticrash$bindRecipeGetter((RecipeMatchGetterAccessorMixin) getter);
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void anticrash$saveRecipeMatch(net.minecraft.world.level.storage.ValueOutput nbt, CallbackInfo ci) {
        if (!RuntimeExtensionNbt.savingLoadedChunk()) return;
        var id = anticrash$getter == null ? anticrash$savedId : anticrash$getter.anticrash$getRecipeId();
        RuntimeExtensionNbt.write(nbt, extension -> {
            extension.remove("RecipeMatch");
            if (id != null) extension.putString("RecipeMatch", id.identifier().toString());
        });
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void anticrash$loadRecipeMatch(net.minecraft.world.level.storage.ValueInput nbt, CallbackInfo ci) {
        CompoundTag extension = RuntimeExtensionNbt.read(nbt);
        if (extension == null) return;
        anticrash$savedId = com.atsukimc.anticrash.runtime.VersionNbt.contains(extension, "RecipeMatch", 8)
                ? net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE,
                        Identifier.parse(extension.getString("RecipeMatch").orElseThrow())) : null;
        anticrash$pending = true;
    }
}
