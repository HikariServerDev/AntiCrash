package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.snapshot.logical.EntityLifecycleTracker;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes removal and chunk-crossing edges without replacing EntityList or ServerEntityManager. */
@Mixin(Entity.class)
public abstract class EntityLifecycleMixin {
    @Inject(method = "remove(Lnet/minecraft/world/entity/Entity$RemovalReason;)V", at = @At("HEAD"))
    private void anticrash$removed(RemovalReason reason, CallbackInfo callbackInfo) {
        EntityLifecycleTracker.recordRemoval((Entity) (Object) this,
                reason == null ? "unknown" : reason.name());
    }

    @Inject(method = "setPos(DDD)V", at = @At("HEAD"))
    private void anticrash$setPosition(double x, double y, double z, CallbackInfo callbackInfo) {
        recordMove(x, z);
    }

    @Inject(method = "snapTo(DDDFF)V", at = @At("HEAD"))
    private void anticrash$refreshPosition(double x, double y, double z, float yaw, float pitch,
                                           CallbackInfo callbackInfo) {
        recordMove(x, z);
    }

    private void recordMove(double x, double z) {
        // Runs for every entity movement; return before any allocation when nothing records it.
        if (!com.atsukimc.anticrash.snapshot.logical.LogicalTracking.enabled()) return;
        Entity entity = (Entity) (Object) this;
        if (entity.level() == null) return;
        long oldChunk = entity.chunkPosition().pack();
        long newChunk = ChunkPos.pack(net.minecraft.util.Mth.floor(x) >> 4,
                net.minecraft.util.Mth.floor(z) >> 4);
        EntityLifecycleTracker.recordMove(entity, oldChunk, newChunk);
    }
}
