//? if (>=1.19 && <=26.3) {
/*package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.runtime.RuntimeExtensionNbt;

//? if (>=1.19 && <=1.21.11) {
/^import net.minecraft.block.entity.CampfireBlockEntity;
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
/^import net.minecraft.world.level.block.entity.CampfireBlockEntity;
^///?}


//? if (>=1.19 && <=1.21.1) {
/^import org.spongepowered.asm.mixin.Final;
^///?}

import org.spongepowered.asm.mixin.Mixin;
//? if (>=1.19 && <=1.21.1) {
/^import org.spongepowered.asm.mixin.Shadow;
^///?}

import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/^* Restores only the semantic cached identifier on the newly constructed Vanilla MatchGetter. ^/
//? if (>=1.19 && <=1.21.1) {
/^@Mixin(CampfireBlockEntity.class)
public abstract class CampfireRecipeMatchingMixin {
    @Shadow @Final private RecipeManager.MatchGetter matchGetter;
^///?}
//? if (>=1.21.2 && <=26.3) {
/^@Mixin(CampfireBlockEntity.class)
public abstract class CampfireRecipeMatchingMixin
        implements com.atsukimc.anticrash.runtime.CampfireRecipeState {
    @org.spongepowered.asm.mixin.Unique private RecipeMatchGetterAccessorMixin anticrash$getter;
^///?}



//? if (>=1.19 && <=1.20.4) {
/^@Inject(method = "writeNbt", at = @At("TAIL"))
    private void anticrash$saveRecipeMatch(NbtCompound nbt, CallbackInfo ci)
^///?}
//? if (>=1.21.2 && <=1.21.11) {
/^@org.spongepowered.asm.mixin.Unique
    private net.minecraft.registry.RegistryKey<net.minecraft.recipe.Recipe<?>> anticrash$savedId;
^///?}
//? if (>=26.1 && <=26.3) {
/^@org.spongepowered.asm.mixin.Unique
    private net.minecraft.resources.ResourceKey<net.minecraft.world.item.crafting.Recipe<?>>
            anticrash$savedId;
^///?}



//? if (>=1.21.2 && <=26.3) {
/^@org.spongepowered.asm.mixin.Unique private boolean anticrash$pending;
^///?}



//? if (>=1.21.2 && <=26.3) {
/^@Override
    public void anticrash$bindRecipeGetter(RecipeMatchGetterAccessorMixin getter) {
        if (anticrash$pending && getter != null) {
            getter.anticrash$setRecipeId(anticrash$savedId);
^///?}


//? if (>=1.21.2 && <=26.3) {
/^anticrash$pending = false;
        }
^///?}


//? if (>=1.21.2 && <=26.3) {
/^anticrash$getter = getter;
^///?}


//? if (>=1.21.2 && <=26.3) {
/^if (getter == null) {
            anticrash$savedId = null;
^///?}


//? if (>=1.21.2 && <=26.3) {
/^anticrash$pending = false;
        }
    }
^///?}



//? if (>=1.21.2 && <=1.21.11) {
/^@Inject(method = "litServerTick", at = @At("HEAD"))
    private static void anticrash$restoreRecipeBeforeFirstTick(
            net.minecraft.server.world.ServerWorld world,
            net.minecraft.util.math.BlockPos pos,
            net.minecraft.block.BlockState state,
            CampfireBlockEntity campfire,
            net.minecraft.recipe.ServerRecipeManager.MatchGetter getter,
            CallbackInfo ci)
^///?}
//? if (>=26.1 && <=26.3) {
/^@Inject(method = "cookTick", at = @At("HEAD"))
    private static void anticrash$restoreRecipeBeforeFirstTick(
            net.minecraft.server.level.ServerLevel world,
            net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state,
            CampfireBlockEntity campfire,
            net.minecraft.world.item.crafting.RecipeManager.CachedCheck getter,
            CallbackInfo ci)
^///?}
//? if (>=1.21.2 && <=26.3) {
/^{
        ((com.atsukimc.anticrash.runtime.CampfireRecipeState) campfire)
                .anticrash$bindRecipeGetter((RecipeMatchGetterAccessorMixin) getter);
    }
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
//? if (>=1.21.2 && <=26.3) {
/^var id =
                anticrash$getter == null
                        ? anticrash$savedId
                        : anticrash$getter.anticrash$getRecipeId();
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
/^anticrash$savedId =
                extension.contains("RecipeMatch", 8)
                        ? net.minecraft.registry.RegistryKey.of(
                                net.minecraft.registry.RegistryKeys.RECIPE,
                                Identifier.of(extension.getString("RecipeMatch")))
                        : null;
^///?}
//? if (>=1.21.5 && <=1.21.11) {
/^anticrash$savedId =
                com.atsukimc.anticrash.runtime.VersionNbt.contains(extension, "RecipeMatch", 8)
                        ? net.minecraft.registry.RegistryKey.of(
                                net.minecraft.registry.RegistryKeys.RECIPE,
                                Identifier.of(extension.getString("RecipeMatch").orElseThrow()))
                        : null;
^///?}
//? if (>=26.1 && <=26.3) {
/^anticrash$savedId =
                com.atsukimc.anticrash.runtime.VersionNbt.contains(extension, "RecipeMatch", 8)
                        ? net.minecraft.resources.ResourceKey.create(
                                net.minecraft.core.registries.Registries.RECIPE,
                                Identifier.parse(extension.getString("RecipeMatch").orElseThrow()))
                        : null;
^///?}


//? if (>=1.21.2 && <=26.3) {
/^anticrash$pending = true;
^///?}

    }
}
*///?}
