package com.atsukimc.anticrash.runtime;

import java.util.ArrayList;
import java.util.List;

/**
 * Audit-only observer for scheduled-tick mutations during a logical cut.
 *
 * <p>It never replaces or drains a Vanilla queue. Its sole purpose is to turn the previously opaque
 * queue-change failure into evidence. A non-zero mutation count invalidates the cut; the caller
 * must fall back or fail closed rather than retrying blindly.
 */
public final class ScheduledTickMutationTracer {
    private static final int DEFAULT_EVENT_LIMIT = 64;

    /**
     * Read without the lock by {@link #isActive()}: every scheduled tick in the world passes the
     * mixin, and it must be able to skip building its diagnostic text with a single field read.
     */
    private static volatile Capture active;

    private ScheduledTickMutationTracer() {}

    public static synchronized Capture begin(long epoch) {
        return begin(epoch, DEFAULT_EVENT_LIMIT);
    }

    public static synchronized Capture begin(long epoch, int eventLimit) {
        Capture capture = new Capture(epoch, Math.max(0, Math.min(4096, eventLimit)));
        active = capture;
        return capture;
    }

    public static synchronized void record(String operation, String detail) {
        Capture capture = active;
        if (capture == null) return;
        capture.record(operation, detail);
    }

    public static synchronized void end(Capture capture) {
        if (active == capture) active = null;
        if (capture != null) capture.closed = true;
    }

    public static boolean isActive() {
        return active != null;
    }

    public static final class Capture {
        private final long epoch;
        private final int eventLimit;
        private final List<String> events = new ArrayList<>();
        private long mutations;
        private boolean closed;

        private Capture(long epoch, int eventLimit) {
            this.epoch = epoch;
            this.eventLimit = eventLimit;
        }

        private void record(String operation, String detail) {
            mutations++;
            if (events.size() < eventLimit) {
                events.add(
                        (operation == null ? "unknown" : operation)
                                + " thread="
                                + Thread.currentThread().getName()
                                + (detail == null || detail.isBlank() ? "" : " " + detail)
                                + " callers="
                                + callers());
            }
        }

        private static String callers() {
            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            StringBuilder result = new StringBuilder();
            int included = 0;
            for (int index = 2; index < stack.length && included < 3; index++) {
                StackTraceElement frame = stack[index];
                String className = frame.getClassName();
                if (className.equals(ScheduledTickMutationTracer.class.getName())
                        || className.equals(Capture.class.getName())) continue;
                if (included++ > 0) result.append(" <- ");
                result.append(className.substring(className.lastIndexOf('.') + 1))
                        .append('#')
                        .append(frame.getMethodName())
                        .append(':')
                        .append(frame.getLineNumber());
            }
            return result.toString();
        }

        public long epoch() {
            return epoch;
        }

        public long mutations() {
            return mutations;
        }

        public boolean changed() {
            return mutations != 0L;
        }

        public boolean closed() {
            return closed;
        }

        public List<String> events() {
            return List.copyOf(events);
        }

        public String diagnostic() {
            return "epoch=" + epoch + "; mutations=" + mutations + "; events=" + events;
        }
    }
}
