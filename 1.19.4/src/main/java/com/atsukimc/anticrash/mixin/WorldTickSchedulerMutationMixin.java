package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.runtime.ScheduledTickMutationTracer;
import com.atsukimc.anticrash.snapshot.logical.ChunkMutationTracker;

import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.tick.ChunkTickScheduler;
import net.minecraft.world.tick.OrderedTick;
import net.minecraft.world.tick.WorldTickScheduler;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes Vanilla scheduler mutations without altering queue ownership or ordering. */
@Mixin(WorldTickScheduler.class)
public abstract class WorldTickSchedulerMutationMixin {
    @Inject(method = "scheduleTick", at = @At("HEAD"))
    private void anticrash$schedule(OrderedTick<?> tick, CallbackInfo callbackInfo) {
        if (ScheduledTickMutationTracer.isActive()) {
            ScheduledTickMutationTracer.record(
                    "world.schedule",
                    "dimension="
                            + String.valueOf(ChunkMutationTracker.schedulerDimension(this))
                            + " position="
                            + tick.pos()
                            + " tick="
                            + tick);
        }
        if (com.atsukimc.anticrash.snapshot.logical.LogicalTracking.enabled()) {
            ChunkMutationTracker.recordScheduler(this, new ChunkPos(tick.pos()), "world.schedule");
        }
    }

    @Inject(method = "addChunkTickScheduler", at = @At("HEAD"))
    private void anticrash$addChunk(
            ChunkPos pos, ChunkTickScheduler<?> scheduler, CallbackInfo callbackInfo) {
        if (ScheduledTickMutationTracer.isActive()) {
            ScheduledTickMutationTracer.record(
                    "world.attach",
                    "dimension="
                            + String.valueOf(ChunkMutationTracker.schedulerDimension(this))
                            + " position="
                            + pos);
        }
        ChunkMutationTracker.recordScheduler(this, pos, "world.attach");
    }

    @Inject(method = "removeChunkTickScheduler", at = @At("HEAD"))
    private void anticrash$removeChunk(ChunkPos pos, CallbackInfo callbackInfo) {
        if (ScheduledTickMutationTracer.isActive()) {
            ScheduledTickMutationTracer.record(
                    "world.detach",
                    "dimension="
                            + String.valueOf(ChunkMutationTracker.schedulerDimension(this))
                            + " position="
                            + pos);
        }
        ChunkMutationTracker.recordScheduler(this, pos, "world.detach");
    }

    /** Box-wide command APIs can mutate many scheduler entries without exposing exact positions. */
    @Inject(method = "clearNextTicks", at = @At("HEAD"))
    private void anticrash$clearNextTicks(BlockBox box, CallbackInfo callbackInfo) {
        if (ScheduledTickMutationTracer.isActive()) {
            ScheduledTickMutationTracer.record(
                    "world.clearNextTicks",
                    "dimension="
                            + String.valueOf(ChunkMutationTracker.schedulerDimension(this))
                            + " box="
                            + box);
        }
        ChunkMutationTracker.markSchedulerUncertain("clearNextTicks");
    }

    @Inject(
            method =
                    "scheduleTicks(Lnet/minecraft/util/math/BlockBox;Lnet/minecraft/util/math/Vec3i;)V",
            at = @At("HEAD"))
    private void anticrash$scheduleTicks(BlockBox box, Vec3i offset, CallbackInfo callbackInfo) {
        if (ScheduledTickMutationTracer.isActive()) {
            ScheduledTickMutationTracer.record(
                    "world.scheduleTicks",
                    "dimension="
                            + String.valueOf(ChunkMutationTracker.schedulerDimension(this))
                            + " box="
                            + box
                            + " offset="
                            + offset);
        }
        ChunkMutationTracker.markSchedulerUncertain("scheduleTicks");
    }

    @Inject(
            method =
                    "scheduleTicks(Lnet/minecraft/world/tick/WorldTickScheduler;Lnet/minecraft/util/math/BlockBox;Lnet/minecraft/util/math/Vec3i;)V",
            at = @At("HEAD"))
    private void anticrash$copyTicks(
            WorldTickScheduler<?> source, BlockBox box, Vec3i offset, CallbackInfo ci) {
        // The three-argument API may be called directly by mods/clone commands.
        ChunkMutationTracker.markSchedulerUncertain("copy.scheduleTicks");
    }
}
