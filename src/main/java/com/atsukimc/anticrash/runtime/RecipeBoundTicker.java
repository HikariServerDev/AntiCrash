//? if (>=1.21.2 && <=26.3) {
/*package com.atsukimc.anticrash.runtime;

import com.atsukimc.anticrash.mixin.RecipeMatchGetterAccessorMixin;

//? if (>=1.21.2 && <=1.21.11) {
/^import net.minecraft.block.BlockState;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.core.BlockPos;
^///?}

//? if (>=1.21.2 && <=1.21.11) {
/^import net.minecraft.block.entity.BlockEntity;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.level.Level;
^///?}

//? if (>=1.21.2 && <=1.21.11) {
/^import net.minecraft.block.entity.BlockEntityTicker;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.level.block.entity.BlockEntity;
^///?}

//? if (>=1.21.2 && <=1.21.11) {
/^import net.minecraft.util.math.BlockPos;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.level.block.entity.BlockEntityTicker;
^///?}

//? if (>=1.21.2 && <=1.21.11) {
/^import net.minecraft.world.World;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.level.block.state.BlockState;
^///?}


/^* A native ticker delegate plus its semantic recipe cache; never serialized as a Java object. ^/
public record RecipeBoundTicker<T extends BlockEntity>(
        BlockEntityTicker<T> delegate, RecipeMatchGetterAccessorMixin getter)
        implements BlockEntityTicker<T> {

//? if (>=1.21.2 && <=1.21.11) {
/^@Override
    public void tick(World world, BlockPos pos, BlockState state, T entity)
^///?}
//? if (>=26.1 && <=26.3) {
/^@Override
    public void tick(Level world, BlockPos pos, BlockState state, T entity)
^///?}
{
        delegate.tick(world, pos, state, entity);
    }
}
*///?}
