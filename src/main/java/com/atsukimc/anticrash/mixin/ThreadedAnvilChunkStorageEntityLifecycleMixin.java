package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.snapshot.logical.EntityLifecycleTracker;

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.entity.Entity;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.server.level.ChunkMap;
*///?}

//? if (>=1.18 && <=1.20.6) {
import net.minecraft.server.level.ChunkMap;
//?}
//? if (>=1.21 && <=1.21.11) {
/*import net.minecraft.server.level.ChunkMap;
*///?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.entity.Entity;
*///?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * EntityManager unload is a persistence boundary rather than a removal. The logical engine records
 * it and falls back unless a future semantic entity-region capture is available.
 */
//? if (>=1.18 && <=1.20.6) {
@Mixin(ChunkMap.class)
public abstract class ThreadedAnvilChunkStorageEntityLifecycleMixin {

    //?}
    //? if (>=1.21 && <=1.21.11) {
    /*@Mixin(ChunkMap.class)
    public abstract class ThreadedAnvilChunkStorageEntityLifecycleMixin {
*///?}
    //? if (>=1.18 && <=1.21.11) {
    @Inject(method = "removeEntity", at = @At("HEAD"))
    private void anticrash$entityUnload(Entity entity, CallbackInfo callbackInfo)
                //?}
                //? if (>=26.1 && <=26.3) {
                /*@Mixin(ChunkMap.class)
                public abstract class ThreadedAnvilChunkStorageEntityLifecycleMixin {
                    @Inject(method = "removeEntity", at = @At("HEAD"))
                    private void anticrash$entityUnload(Entity entity, CallbackInfo callbackInfo)
*///?}
            {
        EntityLifecycleTracker.recordUnload(entity);
    }

    /**
     * Runtime extensions are written only while a chunk stays loaded. A chunk that was saved that
     * way and is then unloaded without having changed would keep the extension on disk, and a later
     * load in the same game would restore state Vanilla drops on an unload (a piston half a step
     * further than Vanilla puts it). The unload therefore rewrites such a chunk, which this time is
     * serialised without the extension.
     */

    //? if (>=1.18 && <=1.21.11) {
    @Inject(method = "save(Lnet/minecraft/world/level/chunk/ChunkAccess;)Z", at = @At("HEAD"))
    private void anticrash$rewriteExtensionOnUnload(
            net.minecraft.world.level.chunk.ChunkAccess chunk,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean>
                    callbackInfo)
                //?}
                //? if (>=26.1 && <=26.3) {
                /*@Inject(method = "save(Lnet/minecraft/world/level/chunk/ChunkAccess;)Z", at = @At("HEAD"))
                    private void anticrash$rewriteExtensionOnUnload(
                            net.minecraft.world.level.chunk.ChunkAccess chunk,
                            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean>
                                    callbackInfo)
*///?}
            {

        //? if (>=1.18 && <=1.21.11) {
        if (chunk instanceof net.minecraft.world.level.chunk.LevelChunk worldChunk
                && !((WorldChunkAccessorMixin) worldChunk).anticrash$isLoadedToWorld()
                && ((com.atsukimc.anticrash.runtime.ExtensionCarrier) worldChunk)
                        .anticrash$extensionOnDisk())
        //?}
        //? if (>=26.1 && <=26.3) {
        /*if (chunk instanceof net.minecraft.world.level.chunk.LevelChunk worldChunk
                        && !((WorldChunkAccessorMixin) worldChunk).anticrash$isLoadedToWorld()
                        && ((com.atsukimc.anticrash.runtime.ExtensionCarrier) worldChunk)
                                .anticrash$extensionOnDisk())
*///?}
        {

            //? if (>=1.18.2 && <=1.21.1) {
            worldChunk.setUnsaved(true);
            //?}
            //? if (>=1.18 && <=1.18.1) {
            /*worldChunk.setUnsaved(true);
*///?}
            //? if (>=1.21.2 && <=1.21.11) {
            /*worldChunk.markUnsaved();
*///?}
            //? if (>=26.1 && <=26.3) {
            /*worldChunk.markUnsaved();
*///?}

        }
    }
}
