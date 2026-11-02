package com.atsukimc.anticrash.checkpoint;

import com.atsukimc.anticrash.mixin.RegionBasedStorageAccessorMixin;
import com.atsukimc.anticrash.mixin.StorageIoWorkerAccessorMixin;
import com.atsukimc.anticrash.mixin.StorageIoWorkerResultAccessorMixin;
import com.atsukimc.anticrash.snapshot.PendingRegionWrite;
import com.mojang.datafixers.util.Either;

import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.storage.StorageIoWorker;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Holds every storage IO queue still while a checkpoint cut copies the world files.
 *
 * <p>A cut used to wait until every queued chunk, entity and POI write had reached the disk. With
 * {@code sync-chunk-writes} each of those writes is synchronous, and that wait was most of the
 * freeze players felt. Instead, each worker's actor runs one task that takes the queued writes and
 * then parks until {@link #close()}. While parked, the worker writes nothing, so the files on disk
 * plus the queued writes are exactly the cut. The queued writes are later written into the
 * checkpoint's own copies on the checkpoint worker; the live world is written by Vanilla as usual,
 * with its own durability untouched.
 */
final class IoSeal implements AutoCloseable {
    /**
     * Only a safety net for a server thread that never reaches {@link #close()}, which it always
     * does in a finally block. It must outlast the longest legitimate cut: a first BASE copies the
     * whole world while sealed, and a cut is abandoned after 40 seconds.
     */
    private static final long MAX_HOLD_SECONDS = 120L;

    private final CountDownLatch release = new CountDownLatch(1);
    private final AtomicBoolean expired = new AtomicBoolean();
    private final List<PendingRegionWrite> writes = new ArrayList<>();
    private String slowestDirectory = "-";
    private long slowestNanos;

    private IoSeal() {}

    /**
     * Seals every worker. Each worker first finishes the task it is running and every task queued
     * before this call, so a write submitted before the seal is either on disk or in {@link
     * #writes}.
     */
    static IoSeal seal(List<StorageIoWorker> workers, long timeoutNanos) throws IOException {
        IoSeal seal = new IoSeal();
        List<CompletableFuture<List<PendingRegionWrite>>> captured =
                new ArrayList<>(workers.size());
        List<Path> directories = new ArrayList<>(workers.size());
        long[] sealedAt = new long[workers.size()];
        long started = System.nanoTime();
        for (StorageIoWorker worker : workers) {
            StorageIoWorkerAccessorMixin accessor = (StorageIoWorkerAccessorMixin) worker;
            Path directory =
                    ((RegionBasedStorageAccessorMixin) (Object) accessor.anticrash$getStorage())
                            .anticrash$getDirectory()
                            .toAbsolutePath()
                            .normalize();
            CompletableFuture<List<PendingRegionWrite>> pending = new CompletableFuture<>();
            int index = captured.size();
            captured.add(pending);
            directories.add(directory);
            accessor.anticrash$run(() -> {
                try {
                    List<PendingRegionWrite> queued = new ArrayList<>();
                    for (Map.Entry<ChunkPos, Object> entry :
                            accessor.anticrash$getResults().entrySet()) {
                        queued.add(
                                new PendingRegionWrite(
                                        directory,
                                        entry.getKey(),
                                        ((StorageIoWorkerResultAccessorMixin) entry.getValue())
                                                .anticrash$getNbt()));
                    }
                    sealedAt[index] = System.nanoTime();
                    pending.complete(queued);
                } catch (Throwable failure) {
                    pending.completeExceptionally(failure);
                    return Either.left(null);
                }
                seal.hold();
                return Either.left(null);
            });
        }
        long deadline = System.nanoTime() + timeoutNanos;
        try {
            for (int i = 0; i < captured.size(); i++) {
                long remaining = Math.max(0L, deadline - System.nanoTime());
                seal.writes.addAll(captured.get(i).get(remaining, TimeUnit.NANOSECONDS));
                long waited = sealedAt[i] - started;
                if (waited > seal.slowestNanos) {
                    seal.slowestNanos = waited;
                    seal.slowestDirectory = directories.get(i).toString();
                }
            }
        } catch (TimeoutException timeout) {
            seal.close();
            throw new IOException(
                    "Storage IO queues could not be sealed within "
                            + TimeUnit.NANOSECONDS.toMillis(timeoutNanos)
                            + " ms");
        } catch (InterruptedException interrupted) {
            seal.close();
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while sealing storage IO queues", interrupted);
        } catch (ExecutionException failure) {
            seal.close();
            if (failure.getCause() instanceof OutOfMemoryError oom) throw oom;
            throw new IOException("Could not read a storage IO queue", failure.getCause());
        }
        return seal;
    }

    private void hold() {
        try {
            if (!release.await(MAX_HOLD_SECONDS, TimeUnit.SECONDS)) expired.set(true);
        } catch (InterruptedException interrupted) {
            expired.set(true);
            Thread.currentThread().interrupt();
        }
    }

    /** The queue that took longest to finish its running and earlier tasks, for diagnostics. */
    String slowestQueue() {
        return slowestDirectory + " " + TimeUnit.NANOSECONDS.toMillis(slowestNanos) + " ms";
    }

    List<PendingRegionWrite> writes() {
        return writes;
    }

    /** True while every worker is still parked, i.e. no file changed since the seal. */
    boolean intact() {
        return !expired.get();
    }

    @Override
    public void close() {
        release.countDown();
    }
}
