package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.runtime.ScheduledTickMutationTracer;
import com.atsukimc.anticrash.snapshot.logical.ChunkMutationTracker;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes Vanilla scheduler mutations without altering queue ownership or ordering. */
@Mixin(LevelTicks.class)
public abstract class WorldTickSchedulerMutationMixin {
    @Inject(method = "schedule", at = @At("HEAD"))
    private void anticrash$schedule(ScheduledTick<?> tick, CallbackInfo callbackInfo) {
        if (ScheduledTickMutationTracer.isActive()) {
            ScheduledTickMutationTracer.record("world.schedule",
                    "dimension=" + String.valueOf(ChunkMutationTracker.schedulerDimension(this))
                            + " position=" + tick.pos() + " tick=" + tick);
        }
        if (com.atsukimc.anticrash.snapshot.logical.LogicalTracking.enabled()) {
            ChunkMutationTracker.recordScheduler(this, ChunkPos.containing(tick.pos()), "world.schedule");
        }
    }

    @Inject(method = "addContainer", at = @At("HEAD"))
    private void anticrash$addChunk(ChunkPos pos, LevelChunkTicks<?> scheduler,
                                    CallbackInfo callbackInfo) {
        if (ScheduledTickMutationTracer.isActive()) {
            ScheduledTickMutationTracer.record("world.attach",
                    "dimension=" + String.valueOf(ChunkMutationTracker.schedulerDimension(this))
                            + " position=" + pos);
        }
        ChunkMutationTracker.recordScheduler(this, pos, "world.attach");
    }

    @Inject(method = "removeContainer", at = @At("HEAD"))
    private void anticrash$removeChunk(ChunkPos pos, CallbackInfo callbackInfo) {
        if (ScheduledTickMutationTracer.isActive()) {
            ScheduledTickMutationTracer.record("world.detach",
                    "dimension=" + String.valueOf(ChunkMutationTracker.schedulerDimension(this))
                            + " position=" + pos);
        }
        ChunkMutationTracker.recordScheduler(this, pos, "world.detach");
    }

    /** Box-wide command APIs can mutate many scheduler entries without exposing exact positions. */
    @Inject(method = "clearArea", at = @At("HEAD"))
    private void anticrash$clearNextTicks(BoundingBox box, CallbackInfo callbackInfo) {
        if (ScheduledTickMutationTracer.isActive()) {
            ScheduledTickMutationTracer.record("world.clearNextTicks",
                    "dimension=" + String.valueOf(ChunkMutationTracker.schedulerDimension(this))
                            + " box=" + box);
        }
        ChunkMutationTracker.markSchedulerUncertain("clearNextTicks");
    }

    @Inject(method = "copyArea(Lnet/minecraft/world/level/levelgen/structure/BoundingBox;Lnet/minecraft/core/Vec3i;)V", at = @At("HEAD"))
    private void anticrash$scheduleTicks(BoundingBox box, Vec3i offset, CallbackInfo callbackInfo) {
        if (ScheduledTickMutationTracer.isActive()) {
            ScheduledTickMutationTracer.record("world.scheduleTicks",
                    "dimension=" + String.valueOf(ChunkMutationTracker.schedulerDimension(this))
                            + " box=" + box + " offset=" + offset);
        }
        ChunkMutationTracker.markSchedulerUncertain("scheduleTicks");
    }

    @Inject(method = "copyAreaFrom(Lnet/minecraft/world/ticks/LevelTicks;Lnet/minecraft/world/level/levelgen/structure/BoundingBox;Lnet/minecraft/core/Vec3i;)V", at = @At("HEAD"))
    private void anticrash$copyTicks(LevelTicks<?> source, BoundingBox box, Vec3i offset, CallbackInfo ci) {
        // The three-argument API may be called directly by mods/clone commands.
        ChunkMutationTracker.markSchedulerUncertain("copy.scheduleTicks");
    }
}
