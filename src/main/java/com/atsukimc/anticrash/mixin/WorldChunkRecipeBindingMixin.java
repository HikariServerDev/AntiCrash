//? if (>=1.21.2 && <=26.3) {
package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.runtime.CampfireRecipeState;
import com.atsukimc.anticrash.runtime.RecipeBoundTicker;

//? if (>=1.21.2 && <=1.21.11) {
/*import net.minecraft.world.level.block.entity.*;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.world.level.block.entity.*;
//?}

//? if (>=1.21.2 && <=1.21.11) {
/*import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.chunk.*;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.world.level.block.entity.BlockEntity;
//?}

//? if (>=26.1 && <=26.3) {
import net.minecraft.world.level.block.entity.BlockEntityTicker;
//?}

//? if (>=26.1 && <=26.3) {
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
//?}

//? if (>=26.1 && <=26.3) {
import net.minecraft.world.level.block.entity.TickingBlockEntity;
//?}

//? if (>=26.1 && <=26.3) {
import net.minecraft.world.level.chunk.*;
//?}

//? if (>=26.1 && <=26.3) {
import net.minecraft.world.level.chunk.LevelChunk;
//?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

//? if (>=1.21.2 && <=1.21.11) {
/*@Mixin(LevelChunk.class)
public abstract class WorldChunkRecipeBindingMixin {
    @Inject(method = "createTicker", at = @At("HEAD"))
    private <T extends BlockEntity> void anticrash$bind(
            T entity,
            BlockEntityTicker<T> ticker,
            CallbackInfoReturnable<TickingBlockEntity> cir)
*///?} else if (>=26.1 && <=26.3) {
@Mixin(LevelChunk.class)
public abstract class WorldChunkRecipeBindingMixin {
    @Inject(method = "createTicker", at = @At("HEAD"))
    private <T extends BlockEntity> void anticrash$bind(
            T entity, BlockEntityTicker<T> ticker, CallbackInfoReturnable<TickingBlockEntity> cir)
                //?}
            {
        if (entity instanceof CampfireBlockEntity && ticker instanceof RecipeBoundTicker<?> bound)
            ((CampfireRecipeState) entity).anticrash$bindRecipeGetter(bound.getter());
    }
}
//?}
