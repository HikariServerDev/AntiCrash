package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.runtime.CampfireRecipeState;
import com.atsukimc.anticrash.runtime.RecipeBoundTicker;

import net.minecraft.block.entity.*;
import net.minecraft.world.chunk.*;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldChunk.class)
public abstract class WorldChunkRecipeBindingMixin {
    @Inject(method = "wrapTicker", at = @At("HEAD"))
    private <T extends BlockEntity> void anticrash$bind(
            T entity,
            BlockEntityTicker<T> ticker,
            CallbackInfoReturnable<BlockEntityTickInvoker> cir) {
        if (entity instanceof CampfireBlockEntity && ticker instanceof RecipeBoundTicker<?> bound)
            ((CampfireRecipeState) entity).anticrash$bindRecipeGetter(bound.getter());
    }
}
