package com.atsukimc.anticrash.runtime;

import com.atsukimc.anticrash.mixin.RecipeMatchGetterAccessorMixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;

/** A native ticker delegate plus its semantic recipe cache; never serialized as a Java object. */
public record RecipeBoundTicker<T extends BlockEntity>(
        BlockEntityTicker<T> delegate, RecipeMatchGetterAccessorMixin getter)
        implements BlockEntityTicker<T> {
    @Override
    public void tick(Level world, BlockPos pos, BlockState state, T entity) {
        delegate.tick(world, pos, state, entity);
    }
}
