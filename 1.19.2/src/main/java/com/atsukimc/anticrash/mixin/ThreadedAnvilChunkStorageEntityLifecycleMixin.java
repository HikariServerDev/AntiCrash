package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.snapshot.logical.EntityLifecycleTracker;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * EntityManager unload is a persistence boundary rather than a removal.  The logical engine
 * records it and falls back unless a future semantic entity-region capture is available.
 */
@Mixin(ThreadedAnvilChunkStorage.class)
public abstract class ThreadedAnvilChunkStorageEntityLifecycleMixin {
    @Inject(method = "unloadEntity", at = @At("HEAD"))
    private void anticrash$entityUnload(Entity entity, CallbackInfo callbackInfo) {
        EntityLifecycleTracker.recordUnload(entity);
    }

    /**
     * Runtime extensions are written only while a chunk stays loaded. A chunk that was saved that
     * way and is then unloaded without having changed would keep the extension on disk, and a
     * later load in the same game would restore state Vanilla drops on an unload (a piston half a
     * step further than Vanilla puts it). The unload therefore rewrites such a chunk, which this
     * time is serialised without the extension.
     */
    @Inject(method = "save(Lnet/minecraft/world/chunk/Chunk;)Z", at = @At("HEAD"))
    private void anticrash$rewriteExtensionOnUnload(net.minecraft.world.chunk.Chunk chunk,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> callbackInfo) {
        if (chunk instanceof net.minecraft.world.chunk.WorldChunk worldChunk
                && !((WorldChunkAccessorMixin) worldChunk).anticrash$isLoadedToWorld()
                && ((com.atsukimc.anticrash.runtime.ExtensionCarrier) worldChunk).anticrash$extensionOnDisk()) {
            worldChunk.setNeedsSaving(true);
        }
    }
}
