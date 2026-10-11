package com.atsukimc.anticrash.mixin;

import net.minecraft.block.entity.SculkCatalystBlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.event.PositionSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SculkCatalystBlockEntity.Listener.class)
public abstract class CatalystListenerMutationMixin {
    @Shadow public abstract PositionSource getPositionSource();
    @Inject(method = "listen", at = @At("RETURN"))
    private void anticrash$dirtyCursor(ServerWorld world, GameEvent event, GameEvent.Emitter emitter,
            Vec3d origin, CallbackInfoReturnable<Boolean> ci) {
        if (ci.getReturnValueZ()) getPositionSource().getPos(world).ifPresent(pos -> world.markDirty(BlockPos.ofFloored(pos)));
    }
}
