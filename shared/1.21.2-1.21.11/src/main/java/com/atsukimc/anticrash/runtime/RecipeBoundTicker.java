package com.atsukimc.anticrash.runtime;

import com.atsukimc.anticrash.mixin.RecipeMatchGetterAccessorMixin;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** A native ticker delegate plus its semantic recipe cache; never serialized as a Java object. */
public record RecipeBoundTicker<T extends BlockEntity>(BlockEntityTicker<T> delegate,
        RecipeMatchGetterAccessorMixin getter) implements BlockEntityTicker<T> {
    @Override public void tick(World world, BlockPos pos, BlockState state, T entity) {
        delegate.tick(world, pos, state, entity);
    }
}
