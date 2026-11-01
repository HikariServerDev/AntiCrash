package com.atsukimc.anticrash.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Furnace, smoker and blast furnace progress ({@code BurnTime}/{@code CookTime}) advances every
 * tick but Vanilla only marks the chunk dirty when an item finishes or the lit state flips. Both
 * values change during this tick whenever either is positive.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceBlockEntityMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private static void anticrash$markProgressDirty(
            World world,
            BlockPos pos,
            BlockState state,
            AbstractFurnaceBlockEntity furnace,
            CallbackInfo callbackInfo) {
        AbstractFurnaceBlockEntityTimerAccessor timer =
                (AbstractFurnaceBlockEntityTimerAccessor) furnace;
        if (timer.anticrash$getBurnTime() > 0 || timer.anticrash$getCookTime() > 0)
            world.markDirty(pos);
    }
}
