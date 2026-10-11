package com.atsukimc.anticrash.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CrafterBlockEntity.class)
public abstract class CrafterMutationMixin {

    // Vanilla persists the countdown but tickCrafting does not mark intermediate ticks dirty.
    @Inject(method = "serverTick", at = @At("HEAD"))
    private static void anticrash$dirty(Level world, BlockPos pos, BlockState state,
                                        CrafterBlockEntity crafter, CallbackInfo ci) {
        if (!world.isClientSide() && ((CrafterAccessorMixin) crafter).anticrash$craftingTicksRemaining() > 0)
            world.blockEntityChanged(pos);
    }
}
