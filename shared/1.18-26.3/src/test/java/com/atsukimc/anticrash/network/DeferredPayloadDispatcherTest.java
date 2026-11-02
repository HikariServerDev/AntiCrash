package com.atsukimc.anticrash.network;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

class DeferredPayloadDispatcherTest {
    @Test
    void distinguishesSchedulingDisconnectedAndHandlerFailures() {
        List<Runnable> tasks = new ArrayList<>();
        Executor executor = tasks::add;
        try (var dispatcher =
                new DeferredPayloadDispatcher<Integer, String>(
                        id -> id != 0,
                        id -> {
                            if (id == 2) throw new RejectedExecutionException("closed");
                            return executor;
                        },
                        (id, packet) -> {
                            if (packet.equals("bad")) throw new IllegalStateException("handler");
                        },
                        null)) {
            dispatcher.defer(0, "discard");
            dispatcher.defer(2, "reject");
            dispatcher.defer(1, "bad");
            dispatcher.defer(1, "good");
            var scheduled = dispatcher.drain();
            assertEquals(new DeferredPayloadDispatcher.Statistics(2, 1, 1, 0, 0), scheduled);
            tasks.forEach(Runnable::run);
            assertFalse(dispatcher.isIdle());
            assertEquals(
                    new DeferredPayloadDispatcher.Statistics(0, 0, 0, 0, 1), dispatcher.drain());
            assertTrue(dispatcher.isIdle());
        }
    }

    @Test
    void preservesFirstCauseAndKeepsLaterPackets() {
        var queue = new DeferredPayloads<Integer, String>();
        RuntimeException expected = new IllegalArgumentException("original cause");
        queue.defer(1, "bad");
        queue.defer(2, "discard");
        queue.defer(3, "good");
        var result =
                queue.drain((id, packet) -> {
                    if (id == 1) throw expected;
                    return id == 2
                            ? DeferredPayloads.Disposition.DISCARDED
                            : DeferredPayloads.Disposition.ACCEPTED;
                });
        assertEquals(1, result.accepted());
        assertEquals(1, result.failed());
        assertEquals(1, result.discarded());
        assertSame(expected, result.firstFailure());
        assertTrue(queue.isEmpty());
    }

    @Test
    void directExecutorReportsHandlerFailureExactlyOnce() {
        try (var dispatcher =
                new DeferredPayloadDispatcher<Integer, String>(
                        id -> true,
                        id -> Runnable::run,
                        (id, p) -> {
                            throw new IllegalStateException("cause");
                        },
                        null)) {
            dispatcher.defer(1, "a");
            dispatcher.defer(1, "b");
            assertEquals(
                    new DeferredPayloadDispatcher.Statistics(2, 0, 0, 0, 2), dispatcher.drain());
            assertEquals(0, dispatcher.drain().handlerFailed());
        }
    }
}
