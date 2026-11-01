package com.atsukimc.anticrash.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class DeferredPayloadsTest {
    /**
     * Regression test: every custom payload was dropped while recovery was frozen, including the
     * handshake that makes Carpet send its rules to a client, so creativeNoClip and other
     * client-synced features stayed off for whoever joined during the freeze.
     */
    @Test
    void handshakesPassWhileFrozenAndOnlyThose() {
        assertTrue(DeferredPayloads.immediate("carpet:hello"));
        assertTrue(DeferredPayloads.immediate("minecraft:register"));
        assertTrue(DeferredPayloads.immediate("minecraft:unregister"));
        assertTrue(DeferredPayloads.immediate("minecraft:brand"));
        assertFalse(DeferredPayloads.immediate("litematica-server-paster:network_v2"));
        assertFalse(DeferredPayloads.immediate("worldedit:cui"));
        assertFalse(DeferredPayloads.immediate(null));
    }

    @Test
    void everythingElseIsDeliveredInArrivalOrderAfterTheStart() {
        DeferredPayloads<String, String> payloads = new DeferredPayloads<>();
        assertTrue(payloads.isEmpty());
        payloads.defer("alice", "first");
        payloads.defer("bob", "second");
        payloads.defer("alice", "third");
        assertFalse(payloads.isEmpty());

        List<String> seen = new ArrayList<>();
        assertEquals(3, payloads.deliver((handler, payload) -> seen.add(handler + ":" + payload)));
        assertEquals(List.of("alice:first", "bob:second", "alice:third"), seen);
        assertTrue(payloads.isEmpty());
        assertEquals(0, payloads.deliver((handler, payload) -> seen.add("again")));
    }

    @Test
    void oneFailingHandlerDoesNotCostTheOthersTheirPayloads() {
        DeferredPayloads<String, String> payloads = new DeferredPayloads<>();
        payloads.defer("alice", "ok");
        payloads.defer("bob", "broken");
        payloads.defer("carol", "ok");
        List<String> seen = new ArrayList<>();
        int delivered =
                payloads.deliver((handler, payload) -> {
                    if (payload.equals("broken"))
                        throw new IllegalStateException("mod handler failed");
                    seen.add(handler);
                });
        assertEquals(2, delivered);
        assertEquals(List.of("alice", "carol"), seen);
    }

    @Test
    void theQueueIsBoundedAndSaysWhatItRefused() {
        DeferredPayloads<String, Integer> payloads = new DeferredPayloads<>();
        for (int i = 0; i < DeferredPayloads.LIMIT; i++) assertTrue(payloads.defer("spammer", i));
        assertFalse(payloads.defer("spammer", -1));
        assertFalse(payloads.defer("spammer", -2));
        assertEquals(2, payloads.takeDropped());
        assertEquals(0, payloads.takeDropped());
        assertEquals(DeferredPayloads.LIMIT, payloads.deliver((handler, payload) -> {}));
        assertTrue(payloads.defer("spammer", 1));
    }
}
