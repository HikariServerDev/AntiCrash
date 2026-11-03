//? if (>=1.21.2 && <=26.3) {
/*package com.atsukimc.anticrash.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.CompletableFuture;

/^* Observes the already scheduled IO chain; does not reschedule saves or replace any state. ^/
//? if (>=1.21.2 && <=1.21.11) {
/^@Mixin(net.minecraft.world.PersistentStateManager.class)
public interface PersistentDataStorageAccessorMixin {
    @Accessor("savingFuture")
    CompletableFuture<?> anticrash$getPendingWrite();
^///?}
//? if (>=26.1 && <=26.3) {
/^@Mixin(net.minecraft.world.level.storage.SavedDataStorage.class)
public interface PersistentDataStorageAccessorMixin {
    @Accessor("pendingWriteFuture")
    CompletableFuture<?> anticrash$getPendingWrite();
^///?}

}
*///?}
