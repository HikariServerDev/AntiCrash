package com.atsukimc.anticrash.snapshot;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.*;

class PendingStorageWritesTest {
    @Test
    void pendingPreviousWriteCannotBeMistakenForAnEmptyNewSave() {
        CompletableFuture<Void> previous = new CompletableFuture<>();
        assertThrows(
                TimeoutException.class, () -> PendingStorageWrites.await(List.of(previous), 1));
        previous.complete(null);
        assertDoesNotThrow(() -> PendingStorageWrites.await(List.of(previous), 100));
    }

    @Test
    void writeFailureKeepsItsCause() {
        var cause = new java.io.IOException("disk write");
        var error =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                PendingStorageWrites.await(
                                        List.of(CompletableFuture.failedFuture(cause)), 100));
        assertSame(cause, error.getCause());
    }
}
