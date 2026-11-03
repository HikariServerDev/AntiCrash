package com.atsukimc.anticrash.snapshot;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Waits only for detached saved-data IO, never chunk generation or server-thread tasks. */
public final class PendingStorageWrites {
    private PendingStorageWrites() {}

    public static void await(Collection<CompletableFuture<?>> writes, long timeoutMillis)
            throws Exception {
        CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new))
                .get(timeoutMillis, TimeUnit.MILLISECONDS);
    }
}
