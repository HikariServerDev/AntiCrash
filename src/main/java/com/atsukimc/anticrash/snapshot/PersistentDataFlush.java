package com.atsukimc.anticrash.snapshot;

//? if (>=1.21.2 && <=26.3) {
import com.atsukimc.anticrash.mixin.PersistentDataStorageAccessorMixin;
//?}

import net.minecraft.server.MinecraftServer;

/** These versions complete SavedData writes synchronously in the Vanilla save. */

//? if (>=1.21.2 && <=26.3) {
import java.util.ArrayList;
//?}

//? if (>=1.21.2 && <=26.3) {
import java.util.concurrent.CompletableFuture;

//?}

/**
 * Vanilla schedules saved-data IO separately from chunk IO. The saved-data future must finish
 * before file revision facts are captured. Calling save/startSaving again is insufficient: an empty
 * dirty map returns a completed future even while a previous write is still pending.
 */
public final class PersistentDataFlush {
    private PersistentDataFlush() {}

    //? if (>=1.18 && <=1.21.1) {
    /*public static void await(MinecraftServer server) {}
    *///?} else if (>=1.21.2 && <=26.3) {
    public static void await(MinecraftServer server) throws Exception {
        var writes = new ArrayList<CompletableFuture<?>>();
        //?}

        //? if (>=1.21.2 && <=1.21.11) {
        /*for (var world : server.getAllLevels())
                writes.add(
                        ((PersistentDataStorageAccessorMixin) world.getDataStorage())
                                .anticrash$getPendingWrite());
*///?} else if (>=26.1 && <=26.3) {
        writes.add(
                ((PersistentDataStorageAccessorMixin) server.getDataStorage())
                        .anticrash$getPendingWrite());
        //?}

        //? if (>=26.1 && <=26.3) {
        for (var world : server.getAllLevels())
            writes.add(
                    ((PersistentDataStorageAccessorMixin) world.getDataStorage())
                            .anticrash$getPendingWrite());
        //?}

        //? if (>=1.21.2 && <=26.3) {
        PendingStorageWrites.await(writes, 10000L);
    }
    //?}

}
