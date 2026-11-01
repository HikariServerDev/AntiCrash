package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.runtime.CampfireRecipeState;
import com.atsukimc.anticrash.runtime.RecipeBoundTicker;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class WorldChunkRecipeBindingMixin {
    @Inject(method = "createTicker", at = @At("HEAD"))
    private <T extends BlockEntity> void anticrash$bind(T entity, BlockEntityTicker<T> ticker,
            CallbackInfoReturnable<TickingBlockEntity> cir) {
        if (entity instanceof CampfireBlockEntity && ticker instanceof RecipeBoundTicker<?> bound)
            ((CampfireRecipeState) entity).anticrash$bindRecipeGetter(bound.getter());
    }
}
