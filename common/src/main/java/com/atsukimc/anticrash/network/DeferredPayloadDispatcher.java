package com.atsukimc.anticrash.network;

import com.atsukimc.anticrash.logging.AntiCrashLogger;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** Owns postponed network work; platform adapters only supply connection and packet operations. */
public final class DeferredPayloadDispatcher<H, P> implements AutoCloseable {
    private final DeferredPayloads<H, P> queue = new DeferredPayloads<>();
    private final Predicate<H> connected;
    private final Function<H, Executor> connectionExecutor;
    private final BiConsumer<H, P> receiver;
    private final AntiCrashLogger logger;
    private int handlerFailures;
    private RuntimeException firstHandlerFailure;
    private ExecutorService fallback;

    public record Statistics(
            int scheduled,
            int schedulingFailed,
            int disconnected,
            int queueRejected,
            int handlerFailed) {}

    public DeferredPayloadDispatcher(
            Predicate<H> connected,
            Function<H, Executor> executor,
            BiConsumer<H, P> receiver,
            AntiCrashLogger logger) {
        this.connected = connected;
        this.connectionExecutor = executor;
        this.receiver = receiver;
        this.logger = logger;
    }

    public boolean defer(H handler, P payload) {
        return queue.defer(handler, payload);
    }

    public synchronized boolean isIdle() {
        return queue.isEmpty() && handlerFailures == 0;
    }

    public Statistics drain() {
        var result =
                queue.drain((handler, payload) -> {
                    if (!connected.test(handler)) return DeferredPayloads.Disposition.DISCARDED;
                    Executor executor = connectionExecutor.apply(handler);
                    if (executor == null) executor = fallback();
                    executor.execute(() -> {
                        try {
                            receiver.accept(handler, payload);
                        } catch (RuntimeException failure) {
                            recordHandlerFailure(failure);
                        }
                    });
                    return DeferredPayloads.Disposition.ACCEPTED;
                });
        FailureBatch batch = takeHandlerFailures();
        int handlerFailed = batch.count();
        RuntimeException failure = result.firstFailure();
        RuntimeException handlerFailure = batch.first();
        if (failure == null) failure = handlerFailure;
        Statistics stats =
                new Statistics(
                        result.accepted(),
                        result.failed(),
                        result.discarded(),
                        queue.takeDropped(),
                        handlerFailed);
        if (failure != null && logger != null)
            logger.failureKey(
                    "recovery.payload_dispatch_failed",
                    failure,
                    stats.schedulingFailed(),
                    stats.handlerFailed());
        if (logger != null
                && (stats.scheduled()
                                + stats.schedulingFailed()
                                + stats.disconnected()
                                + stats.queueRejected()
                                + stats.handlerFailed()
                        > 0))
            logger.normalKey(
                    "recovery.payload_dispatch_counts",
                    stats.scheduled(),
                    stats.schedulingFailed(),
                    stats.disconnected(),
                    stats.queueRejected(),
                    stats.handlerFailed());
        return stats;
    }

    private record FailureBatch(int count, RuntimeException first) {}

    private synchronized void recordHandlerFailure(RuntimeException failure) {
        if (firstHandlerFailure == null) firstHandlerFailure = failure;
        if (handlerFailures < Integer.MAX_VALUE) handlerFailures++;
    }

    private synchronized FailureBatch takeHandlerFailures() {
        FailureBatch batch = new FailureBatch(handlerFailures, firstHandlerFailure);
        handlerFailures = 0;
        firstHandlerFailure = null;
        return batch;
    }

    private synchronized ExecutorService fallback() {
        if (fallback == null)
            fallback =
                    Executors.newSingleThreadExecutor(task -> {
                        Thread thread = new Thread(task, "AntiCrash-Payload-Delivery");
                        thread.setDaemon(true);
                        return thread;
                    });
        return fallback;
    }

    @Override
    public synchronized void close() {
        if (fallback != null) fallback.shutdown();
    }
}
