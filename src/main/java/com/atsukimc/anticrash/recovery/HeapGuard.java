package com.atsukimc.anticrash.recovery;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.util.function.LongSupplier;

/**
 * Decides whether recovery may load another chunk, and when the heap is really exhausted.
 *
 * <p>"Used" heap includes garbage nobody has collected yet. Right after a restore the heap is full
 * of it (checkpoint verification, the Runtime Capsule and manifest JSON), so comparing used heap
 * with the maximum made a 1 GB server give up on its very first chunk although the same chunks had
 * fitted before the crash. A shortage therefore counts only after a collection has been requested
 * and has not helped. While memory is short the caller loads one chunk at a time and waits; it
 * gives up only when the shortage persists with nothing left to finish.
 */
final class HeapGuard {
    enum Decision {
        /** Enough free heap: load at the configured concurrency. */
        PROCEED,
        /**
         * Memory is short: do not start another chunk now, let running loads and the collector
         * finish.
         */
        WAIT,
        /** The shortage did not go away: the plan does not fit this heap. */
        EXHAUSTED
    }

    private static final long COLLECT_INTERVAL_NANOS = 1_000_000_000L;
    static final long GIVE_UP_NANOS = 30_000_000_000L;

    private final long headroomBytes;
    private final LongSupplier maxBytes;
    private final LongSupplier usedBytes;
    private final Runnable collect;
    private final LongSupplier nanoTime;
    private long lastCollectNanos;
    private boolean collected;
    private long shortSinceNanos;
    private boolean shortNow;
    private boolean everShort;

    HeapGuard(long headroomBytes) {
        this(
                headroomBytes,
                () -> Runtime.getRuntime().maxMemory(),
                () -> Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(),
                System::gc,
                System::nanoTime);
    }

    HeapGuard(
            long headroomBytes,
            LongSupplier maxBytes,
            LongSupplier usedBytes,
            Runnable collect,
            LongSupplier nanoTime) {
        this.headroomBytes = headroomBytes;
        this.maxBytes = maxBytes;
        this.usedBytes = usedBytes;
        this.collect = collect;
        this.nanoTime = nanoTime;
    }

    /**
     * True once memory has been short during this recovery; the caller then loads one chunk at a
     * time.
     */
    boolean lowMemoryMode() {
        return everShort;
    }

    private boolean hasHeadroom() {
        long max = maxBytes.getAsLong();
        if (max <= 0 || max == Long.MAX_VALUE) return true;
        return max - usedBytes.getAsLong() >= headroomBytes;
    }

    /**
     * @param workInFlight whether chunk loads are still running; their completion can free memory,
     *     so the shortage is not final while any remain
     */
    Decision check(boolean workInFlight) {
        if (hasHeadroom()) {
            shortNow = false;
            return Decision.PROCEED;
        }
        long now = nanoTime.getAsLong();
        if (!collected || now - lastCollectNanos >= COLLECT_INTERVAL_NANOS) {
            // What looks like a full heap is usually uncollected garbage; reclaim before judging.
            collect.run();
            collected = true;
            lastCollectNanos = now;
            if (hasHeadroom()) {
                shortNow = false;
                return Decision.PROCEED;
            }
        }
        everShort = true;
        if (!shortNow || workInFlight) {
            shortNow = true;
            shortSinceNanos = now;
            return Decision.WAIT;
        }
        return now - shortSinceNanos >= GIVE_UP_NANOS ? Decision.EXHAUSTED : Decision.WAIT;
    }

    /** Heap still occupied after the most recent collections, for the log; -1 if unknown. */
    static long liveBytesAfterCollection() {
        long total = 0;
        boolean known = false;
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() != MemoryType.HEAP) continue;
            MemoryUsage usage = pool.getCollectionUsage();
            if (usage == null) continue;
            total += usage.getUsed();
            known = true;
        }
        return known ? total : -1L;
    }
}
