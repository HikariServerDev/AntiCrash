package com.atsukimc.anticrash.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * Custom payloads a client sent while recovery kept the world frozen.
 *
 * <p>A custom payload can be anything a mod wants, including a request that changes the world, so
 * none is handled while the world waits for {@code /ac start}. They used to be dropped. But the
 * first payloads of a connection are handshakes: the Fabric channel registration, the client brand
 * and Carpet's hello, which is what makes Carpet send its rules to that client. A player who joined
 * during the freeze (the usual way to run {@code /ac start}) was therefore never a Carpet client:
 * rules that need both sides, such as creativeNoClip, stayed off until the player reconnected, and
 * every other mod's join handshake was lost the same way.</p>
 *
 * <p>Handshakes that cannot change the world are let through at once. Everything else is kept, in
 * arrival order, and delivered once the world runs again.</p>
 */
public final class DeferredPayloads<H, P> {
    /**
     * Channels handled while frozen. {@code minecraft:register}/{@code unregister} only tell the
     * server which channels the client understands, {@code minecraft:brand} is the client's name.
     * {@code carpet:hello} carries Carpet's handshake and the commands of its client features;
     * those commands go through the command dispatcher, where the recovery command policy applies.
     */
    private static final Set<String> IMMEDIATE = Set.of(
            "minecraft:register", "minecraft:unregister", "minecraft:brand", "carpet:hello");

    /** Bounds what a client can make the server hold; far above what a few joining clients send. */
    static final int LIMIT = 4096;

    private record Entry<H, P>(H handler, P payload) {
    }

    private final ConcurrentLinkedQueue<Entry<H, P>> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger size = new AtomicInteger();
    private final AtomicInteger dropped = new AtomicInteger();

    public static boolean immediate(String channel) {
        return channel != null && IMMEDIATE.contains(channel);
    }

    /** Called from the network thread or the server thread. @return false when the limit is reached */
    public boolean defer(H handler, P payload) {
        if (size.incrementAndGet() > LIMIT) {
            size.decrementAndGet();
            dropped.incrementAndGet();
            return false;
        }
        queue.add(new Entry<>(handler, payload));
        return true;
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /** Payloads refused because the limit was reached since the last call. */
    public int takeDropped() {
        return dropped.getAndSet(0);
    }

    /**
     * Hands every kept payload to {@code delivery} in arrival order. A failing payload does not
     * stop the ones after it.
     *
     * @return the number delivered
     */
    public int deliver(BiConsumer<H, P> delivery) {
        List<Entry<H, P>> pending = new ArrayList<>();
        for (Entry<H, P> entry; (entry = queue.poll()) != null; ) {
            size.decrementAndGet();
            pending.add(entry);
        }
        int delivered = 0;
        for (Entry<H, P> entry : pending) {
            try {
                delivery.accept(entry.handler(), entry.payload());
                delivered++;
            } catch (RuntimeException failure) {
                // One mod's handler must not cost the other clients their handshakes.
            }
        }
        return delivered;
    }
}
