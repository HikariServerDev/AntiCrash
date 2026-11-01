package com.atsukimc.anticrash.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.SculkCatalystBlockEntity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SculkCatalystBlockEntity.CatalystListener.class)
public abstract class CatalystListenerMutationMixin {
    @Shadow
    public abstract PositionSource getListenerSource();

    @Inject(method = "handleGameEvent", at = @At("RETURN"))
    private void anticrash$dirtyCursor(
            ServerLevel world,
            net.minecraft.core.Holder<GameEvent> event,
            GameEvent.Context emitter,
            Vec3 origin,
            CallbackInfoReturnable<Boolean> ci) {
        if (ci.getReturnValueZ())
            getListenerSource()
                    .getPosition(world)
                    .ifPresent(pos -> world.blockEntityChanged(BlockPos.containing(pos)));
    }
}
