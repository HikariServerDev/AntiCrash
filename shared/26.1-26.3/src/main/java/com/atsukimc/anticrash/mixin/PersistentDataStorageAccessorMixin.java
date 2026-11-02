package com.atsukimc.anticrash.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.CompletableFuture;

/** Observes the already scheduled IO chain; does not reschedule saves or replace any state. */
@Mixin(net.minecraft.world.level.storage.SavedDataStorage.class)
public interface PersistentDataStorageAccessorMixin {
    @Accessor("pendingWriteFuture")
    CompletableFuture<?> anticrash$getPendingWrite();
}
