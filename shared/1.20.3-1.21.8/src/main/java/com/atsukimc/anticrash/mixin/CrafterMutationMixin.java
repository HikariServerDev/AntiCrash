package com.atsukimc.anticrash.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.CrafterBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CrafterBlockEntity.class)
public abstract class CrafterMutationMixin {

    // Vanilla persists the countdown but tickCrafting does not mark intermediate ticks dirty.
    @Inject(method = "tickCrafting", at = @At("HEAD"))
    private static void anticrash$dirty(
            World world,
            BlockPos pos,
            BlockState state,
            CrafterBlockEntity crafter,
            CallbackInfo ci) {
        if (!world.isClient
                && ((CrafterAccessorMixin) crafter).anticrash$craftingTicksRemaining() > 0)
            world.markDirty(pos);
    }
}
