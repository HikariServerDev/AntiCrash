//? if (>=1.21.2 && <=26.3) {
/*package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.runtime.RecipeBoundTicker;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

//? if (>=1.21.2 && <=1.21.11) {
/^import net.minecraft.block.BlockState;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.item.crafting.*;
^///?}

//? if (>=1.21.2 && <=1.21.11) {
/^import net.minecraft.block.CampfireBlock;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.item.crafting.RecipeManager;
^///?}

//? if (>=1.21.2 && <=1.21.11) {
/^import net.minecraft.block.entity.*;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.item.crafting.RecipeType;
^///?}

//? if (>=1.21.2 && <=1.21.11) {
/^import net.minecraft.recipe.*;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.level.Level;
^///?}

//? if (>=1.21.2 && <=1.21.11) {
/^import net.minecraft.world.World;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.level.block.CampfireBlock;
^///?}

//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.level.block.entity.*;
^///?}

//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.level.block.entity.BlockEntity;
^///?}

//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.level.block.entity.BlockEntityTicker;
^///?}

//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.level.block.entity.BlockEntityType;
^///?}

//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.level.block.state.BlockState;
^///?}


import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/^*
 * 1.21.2 getTicker captures MatchGetter in a lambda rather than storing it on the BE. Bind at
 * ticker construction, not first tick: relighting may be checkpointed before any tick.
 ^/
@Mixin(CampfireBlock.class)
public abstract class CampfireTickerBindingMixin {
    @Unique
    private static final ThreadLocal<java.util.ArrayDeque<RecipeMatchGetterAccessorMixin[]>>
            anticrash$scopes = ThreadLocal.withInitial(java.util.ArrayDeque::new);


//? if (>=1.21.2 && <=1.21.11) {
/^@WrapMethod(method = "getTicker")
    private <T extends BlockEntity> BlockEntityTicker<T> anticrash$bind(
            World world,
            BlockState state,
            BlockEntityType<T> type,
            Operation<BlockEntityTicker<T>> original)
^///?}
//? if (>=26.1 && <=26.3) {
/^@WrapMethod(method = "getTicker")
    private <T extends BlockEntity> BlockEntityTicker<T> anticrash$bind(
            Level world,
            BlockState state,
            BlockEntityType<T> type,
            Operation<BlockEntityTicker<T>> original)
^///?}
{
        var stack = anticrash$scopes.get();
        RecipeMatchGetterAccessorMixin[] scope = new RecipeMatchGetterAccessorMixin[1];
        stack.push(scope);
        try {
            BlockEntityTicker<T> ticker = original.call(world, state, type);

//? if (>=1.21.2 && <=1.21.8) {
/^return ticker == null || world.isClient
                    ? ticker
                    : new RecipeBoundTicker<>(ticker, scope[0]);
^///?}
//? if (>=1.21.9 && <=1.21.11) {
/^return ticker == null || world.isClient()
                    ? ticker
                    : new RecipeBoundTicker<>(ticker, scope[0]);
^///?}
//? if (>=26.1 && <=26.3) {
/^return ticker == null || world.isClientSide()
                    ? ticker
                    : new RecipeBoundTicker<>(ticker, scope[0]);
^///?}

        } finally {
            stack.pop();
            if (stack.isEmpty()) anticrash$scopes.remove();
        }
    }


//? if (>=1.21.2 && <=1.21.11) {
/^@WrapOperation(
            method = "getTicker",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/recipe/ServerRecipeManager;createCachedMatchGetter(Lnet/minecraft/recipe/RecipeType;)Lnet/minecraft/recipe/ServerRecipeManager$MatchGetter;"))
    private ServerRecipeManager.MatchGetter<?, ?> anticrash$capture(
            RecipeType<?> type, Operation<ServerRecipeManager.MatchGetter<?, ?>> original)
^///?}
//? if (>=26.1 && <=26.3) {
/^@WrapOperation(
            method = "getTicker",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/item/crafting/RecipeManager;createCheck(Lnet/minecraft/world/item/crafting/RecipeType;)Lnet/minecraft/world/item/crafting/RecipeManager$CachedCheck;"))
    private RecipeManager.CachedCheck<?, ?> anticrash$capture(
            RecipeType<?> type, Operation<RecipeManager.CachedCheck<?, ?>> original)
^///?}
{
        var getter = original.call(type);
        var stack = anticrash$scopes.get();
        if (!stack.isEmpty()) stack.peek()[0] = (RecipeMatchGetterAccessorMixin) getter;
        return getter;
    }
}
*///?}
