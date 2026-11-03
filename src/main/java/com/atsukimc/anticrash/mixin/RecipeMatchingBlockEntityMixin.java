//? if (>=1.19 && <=26.3) {
/*package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.runtime.RuntimeExtensionNbt;

//? if (>=1.19 && <=1.21.11) {
/^import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.nbt.CompoundTag;
^///?}

//? if (>=1.19 && <=1.21.11) {
/^import net.minecraft.nbt.NbtCompound;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.resources.Identifier;
^///?}

//? if (>=1.19 && <=1.21.1) {
/^import net.minecraft.recipe.RecipeManager;
^///?}

//? if (>=1.19 && <=1.21.11) {
/^import net.minecraft.util.Identifier;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
^///?}


import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/^* Restores only the semantic cached identifier on the newly constructed Vanilla MatchGetter. ^/
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class RecipeMatchingBlockEntityMixin {

//? if (>=1.19 && <=1.21.1) {
/^@Shadow @Final private RecipeManager.MatchGetter matchGetter;
^///?}
//? if (>=1.21.2 && <=1.21.11) {
/^@Shadow @Final private net.minecraft.recipe.ServerRecipeManager.MatchGetter matchGetter;
^///?}
//? if (>=26.1 && <=26.3) {
/^@Shadow @Final private net.minecraft.world.item.crafting.RecipeManager.CachedCheck quickCheck;
^///?}



//? if (>=1.19 && <=1.20.4) {
/^@Inject(method = "writeNbt", at = @At("TAIL"))
    private void anticrash$saveRecipeMatch(NbtCompound nbt, CallbackInfo ci)
^///?}
//? if (>=1.20.5 && <=1.21.5) {
/^@Inject(method = "writeNbt", at = @At("TAIL"))
    private void anticrash$saveRecipeMatch(
            NbtCompound nbt,
            net.minecraft.registry.RegistryWrapper.WrapperLookup registries,
            CallbackInfo ci)
^///?}
//? if (>=1.21.6 && <=1.21.11) {
/^@Inject(method = "writeData", at = @At("TAIL"))
    private void anticrash$saveRecipeMatch(net.minecraft.storage.WriteView nbt, CallbackInfo ci)
^///?}
//? if (>=26.1 && <=26.3) {
/^@Inject(method = "saveAdditional", at = @At("TAIL"))
    private void anticrash$saveRecipeMatch(
            net.minecraft.world.level.storage.ValueOutput nbt, CallbackInfo ci)
^///?}
{
        if (!RuntimeExtensionNbt.savingLoadedChunk()) return;

//? if (>=1.19 && <=1.21.1) {
/^Identifier id = ((RecipeMatchGetterAccessorMixin) matchGetter).anticrash$getRecipeId();
^///?}
//? if (>=1.21.2 && <=1.21.11) {
/^var id = ((RecipeMatchGetterAccessorMixin) matchGetter).anticrash$getRecipeId();
^///?}
//? if (>=26.1 && <=26.3) {
/^var id = ((RecipeMatchGetterAccessorMixin) quickCheck).anticrash$getRecipeId();
^///?}

        RuntimeExtensionNbt.write(
                nbt,
                extension -> {
                    extension.remove("RecipeMatch");
                    if (id != null)
//? if (>=1.19 && <=1.21.1) {
/^extension.putString("RecipeMatch", id.toString());
^///?}
//? if (>=1.21.2 && <=1.21.11) {
/^extension.putString("RecipeMatch", id.getValue().toString());
^///?}
//? if (>=26.1 && <=26.3) {
/^extension.putString("RecipeMatch", id.identifier().toString());
^///?}

                });
    }


//? if (>=1.19 && <=1.20.4) {
/^@Inject(method = "readNbt", at = @At("TAIL"))
    private void anticrash$loadRecipeMatch(NbtCompound nbt, CallbackInfo ci)
^///?}
//? if (>=1.20.5 && <=1.21.5) {
/^@Inject(method = "readNbt", at = @At("TAIL"))
    private void anticrash$loadRecipeMatch(
            NbtCompound nbt,
            net.minecraft.registry.RegistryWrapper.WrapperLookup registries,
            CallbackInfo ci)
^///?}
//? if (>=1.21.6 && <=1.21.11) {
/^@Inject(method = "readData", at = @At("TAIL"))
    private void anticrash$loadRecipeMatch(net.minecraft.storage.ReadView nbt, CallbackInfo ci)
^///?}
//? if (>=26.1 && <=26.3) {
/^@Inject(method = "loadAdditional", at = @At("TAIL"))
    private void anticrash$loadRecipeMatch(
            net.minecraft.world.level.storage.ValueInput nbt, CallbackInfo ci)
^///?}
{

//? if (>=1.19 && <=1.21.11) {
/^NbtCompound extension = RuntimeExtensionNbt.read(nbt);
^///?}
//? if (>=26.1 && <=26.3) {
/^CompoundTag extension = RuntimeExtensionNbt.read(nbt);
^///?}

        if (extension == null) return;

//? if (>=1.19 && <=1.21.1) {
/^((RecipeMatchGetterAccessorMixin) matchGetter)
                .anticrash$setRecipeId(
                        extension.contains("RecipeMatch", 8)
                                ? Identifier.tryParse(extension.getString("RecipeMatch"))
                                : null);
^///?}
//? if (>=1.21.2 && <=1.21.4) {
/^((RecipeMatchGetterAccessorMixin) matchGetter)
                .anticrash$setRecipeId(
                        extension.contains("RecipeMatch", 8)
                                ? net.minecraft.registry.RegistryKey.of(
                                        net.minecraft.registry.RegistryKeys.RECIPE,
                                        Identifier.of(extension.getString("RecipeMatch")))
                                : null);
^///?}
//? if (>=1.21.5 && <=1.21.11) {
/^((RecipeMatchGetterAccessorMixin) matchGetter)
                .anticrash$setRecipeId(
                        com.atsukimc.anticrash.runtime.VersionNbt.contains(
                                        extension, "RecipeMatch", 8)
                                ? net.minecraft.registry.RegistryKey.of(
                                        net.minecraft.registry.RegistryKeys.RECIPE,
                                        Identifier.of(
                                                extension.getString("RecipeMatch").orElseThrow()))
                                : null);
^///?}
//? if (>=26.1 && <=26.3) {
/^((RecipeMatchGetterAccessorMixin) quickCheck)
                .anticrash$setRecipeId(
                        com.atsukimc.anticrash.runtime.VersionNbt.contains(
                                        extension, "RecipeMatch", 8)
                                ? net.minecraft.resources.ResourceKey.create(
                                        net.minecraft.core.registries.Registries.RECIPE,
                                        Identifier.parse(
                                                extension.getString("RecipeMatch").orElseThrow()))
                                : null);
^///?}

    }
}
*///?}
