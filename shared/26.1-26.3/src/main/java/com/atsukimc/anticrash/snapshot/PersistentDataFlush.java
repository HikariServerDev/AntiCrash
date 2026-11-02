package com.atsukimc.anticrash.snapshot;

import com.atsukimc.anticrash.mixin.PersistentDataStorageAccessorMixin;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

/**
 * Vanilla schedules saved-data IO separately from chunk IO. The saved-data future must finish
 * before file revision facts are captured. Calling save/startSaving again is insufficient: an empty
 * dirty map returns a completed future even while a previous write is still pending.
 */
public final class PersistentDataFlush {
    private PersistentDataFlush() {}

    public static void await(MinecraftServer server) throws Exception {
        var writes = new ArrayList<CompletableFuture<?>>();
        writes.add(
                ((PersistentDataStorageAccessorMixin) server.getDataStorage())
                        .anticrash$getPendingWrite());
        for (var world : server.getAllLevels())
            writes.add(
                    ((PersistentDataStorageAccessorMixin) world.getDataStorage())
                            .anticrash$getPendingWrite());
        PendingStorageWrites.await(writes, 10000L);
    }
}
