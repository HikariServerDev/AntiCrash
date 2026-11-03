package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.snapshot.logical.EntityLifecycleTracker;

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.entity.Entity;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.entity.Entity;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.entity.Entity.RemovalReason;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.entity.Entity.RemovalReason;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.level.ChunkPos;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.ChunkPos;
*///?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Observes removal and chunk-crossing edges without replacing EntityTickList or PersistentEntitySectionManager.
 */
@Mixin(Entity.class)
public abstract class EntityLifecycleMixin {

    //? if (>=1.18 && <=1.21.11) {
    @Inject(method = "remove(Lnet/minecraft/world/entity/Entity$RemovalReason;)V", at = @At("HEAD"))
    private void anticrash$removed(RemovalReason reason, CallbackInfo callbackInfo)
                //?}
                //? if (>=26.1 && <=26.3) {
                /*@Inject(method = "remove(Lnet/minecraft/world/entity/Entity$RemovalReason;)V", at = @At("HEAD"))
                    private void anticrash$removed(RemovalReason reason, CallbackInfo callbackInfo)
*///?}
            {
        EntityLifecycleTracker.recordRemoval(
                (Entity) (Object) this, reason == null ? "unknown" : reason.name());
    }

    //? if (>=1.18 && <=1.21.11) {
    @Inject(method = "setPos(DDD)V", at = @At("HEAD"))
    private void anticrash$setPosition(double x, double y, double z, CallbackInfo callbackInfo)
                //?}
                //? if (>=26.1 && <=26.3) {
                /*@Inject(method = "setPos(DDD)V", at = @At("HEAD"))
                    private void anticrash$setPosition(double x, double y, double z, CallbackInfo callbackInfo)
*///?}
            {
        recordMove(x, z);
    }

    //? if (>=1.18 && <=1.21.11) {
    //? if (>=1.18 && <=1.21.4) {
    @Inject(method = "moveTo(DDDFF)V", at = @At("HEAD"))
    //?}
    //? if (>=1.21.5 && <=1.21.11) {
    /*@Inject(method = "snapTo(DDDFF)V", at = @At("HEAD"))
    *///?}
    private void anticrash$refreshPosition(
            double x, double y, double z, float yaw, float pitch, CallbackInfo callbackInfo)
                //?}
                //? if (>=26.1 && <=26.3) {
                /*@Inject(method = "snapTo(DDDFF)V", at = @At("HEAD"))
                    private void anticrash$refreshPosition(
                            double x, double y, double z, float yaw, float pitch, CallbackInfo callbackInfo)
*///?}
            {
        recordMove(x, z);
    }

    private void recordMove(double x, double z) {
        // Runs for every entity movement; return before any allocation when nothing records it.
        if (!com.atsukimc.anticrash.snapshot.logical.LogicalTracking.enabled()) return;
        Entity entity = (Entity) (Object) this;

        //? if (>=1.18 && <=1.21.8) {
        //? if (>=1.18 && <=1.19.4) {
        if (entity.getLevel() == null)
            //?}
            //? if (>=1.20 && <=1.21.8) {
            /*if (entity.level() == null)
        *///?}
            //?}
            //? if (>=1.21.9 && <=1.21.11) {
            /*if (entity.level() == null)
*///?}
            //? if (>=26.1 && <=26.3) {
            /*if (entity.level() == null)
*///?}
            return;

        //? if (>=1.18 && <=1.21.11) {
        long oldChunk = entity.chunkPosition().toLong();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*long oldChunk = entity.chunkPosition().pack();
*///?}

        //? if (>=1.18 && <=1.21.11) {
        long newChunk =
                ChunkPos.asLong(
                        net.minecraft.util.Mth.floor(x) >> 4, net.minecraft.util.Mth.floor(z) >> 4);
        //?}
        //? if (>=26.1 && <=26.3) {
        /*long newChunk =
                        ChunkPos.pack(
                                net.minecraft.util.Mth.floor(x) >> 4, net.minecraft.util.Mth.floor(z) >> 4);
*///?}

        EntityLifecycleTracker.recordMove(entity, oldChunk, newChunk);
    }
}
