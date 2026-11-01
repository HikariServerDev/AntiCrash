package com.atsukimc.anticrash.checkpoint;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Monotonic timing for one checkpoint transaction.
 *
 * <p>The server thread records the cut/barrier phases and hands this object to the background
 * finalizer only after the cut is sealed. No wall clock is used for durations, so an NTP/timezone
 * adjustment cannot make a phase appear negative. Values exposed to JSON are milliseconds while the
 * internal accounting retains nanoseconds until the phase is closed.
 */
public final class CheckpointTiming {
    private final long startedAtNanos = System.nanoTime();
    private final Map<String, Long> phases = new LinkedHashMap<>();
    private final Map<String, Long> open = new LinkedHashMap<>();
    private final Map<String, Long> counts = new LinkedHashMap<>();
    private JvmSample beforeJvm = JvmSample.capture();
    private JvmSample afterJvm;

    public synchronized void begin(String phase) {
        if (phase == null || phase.isBlank() || open.containsKey(phase)) return;
        open.put(phase, System.nanoTime());
    }

    public synchronized long end(String phase) {
        Long started = open.remove(phase);
        if (started == null) return 0L;
        long elapsed = Math.max(0L, System.nanoTime() - started);
        phases.merge(phase, elapsed, Long::sum);
        return elapsed;
    }

    public synchronized void record(String phase, long durationNanos) {
        if (phase == null || phase.isBlank()) return;
        phases.merge(phase, Math.max(0L, durationNanos), Long::sum);
    }

    /** Work done by the cut that is a number rather than a duration (chunks saved, IO waits). */
    public synchronized void count(String name, long value) {
        if (name == null || name.isBlank()) return;
        counts.merge(name, value, Long::sum);
    }

    public synchronized void captureAfterJvm() {
        afterJvm = JvmSample.capture();
    }

    public synchronized Map<String, Long> durationsMs() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : phases.entrySet()) {
            result.put(entry.getKey(), nanosToMillis(entry.getValue()));
        }
        return Collections.unmodifiableMap(result);
    }

    public synchronized long totalMs() {
        return nanosToMillis(Math.max(0L, System.nanoTime() - startedAtNanos));
    }

    public synchronized JvmSample beforeJvm() {
        return beforeJvm;
    }

    public synchronized JvmSample afterJvm() {
        return afterJvm == null ? JvmSample.capture() : afterJvm;
    }

    public synchronized String summary() {
        return "phases="
                + durationsMs()
                + "; counts="
                + new LinkedHashMap<>(counts)
                + "; totalMs="
                + totalMs()
                + "; heapUsed/max="
                + beforeJvm.heapUsedBytes
                + "/"
                + beforeJvm.heapMaxBytes
                + "; gcCount/time="
                + (afterJvm == null ? "-" : afterJvm.gcCount)
                + "/"
                + (afterJvm == null ? "-" : afterJvm.gcTimeMs);
    }

    private static long nanosToMillis(long nanos) {
        return nanos / 1_000_000L;
    }

    /** Small, serializable JVM pressure sample used by diagnostics and the manifest. */
    public static final class JvmSample {
        public long heapUsedBytes;
        public long heapCommittedBytes;
        public long heapMaxBytes;
        public long nonHeapUsedBytes;
        public long gcCount;
        public long gcTimeMs;

        public JvmSample() {}

        private static JvmSample capture() {
            JvmSample result = new JvmSample();
            MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            if (heap != null) {
                result.heapUsedBytes = Math.max(0L, heap.getUsed());
                result.heapCommittedBytes = Math.max(0L, heap.getCommitted());
                result.heapMaxBytes = Math.max(0L, heap.getMax());
            }
            MemoryUsage nonHeap = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
            if (nonHeap != null) result.nonHeapUsedBytes = Math.max(0L, nonHeap.getUsed());
            long count = 0L;
            long time = 0L;
            for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
                if (bean.getCollectionCount() > 0) count += bean.getCollectionCount();
                if (bean.getCollectionTime() > 0) time += bean.getCollectionTime();
            }
            result.gcCount = count;
            result.gcTimeMs = time;
            return result;
        }
    }
}
