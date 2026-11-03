package com.atsukimc.anticrash.snapshot.logical;

import com.atsukimc.anticrash.config.AntiCrashConfig;

import java.util.Locale;

/**
 * Conservative status holder for the optional mutation mirror.
 *
 * <p>AntiCrash intentionally does not replace Vanilla's scheduler/ticket structures with a shadow
 * copy. In 1.18.2 there is no safe public operation that can prove a complete scheduler mirror
 * without re-reading the live queues, so this class reports that limitation instead of claiming
 * that an observation-only tracer is a source of truth. It still records tracker uncertainty and
 * exposes periodic audit timestamps for operators.
 */
public final class CheckpointMirror {
    private final boolean enabled;
    private final long intervalNanos;
    private String state;
    private String reason;
    private long lastAuditEpochMs;
    private long lastAuditMonotonicNanos;
    private long audits;
    private long invalidations;

    public CheckpointMirror(AntiCrashConfig.Checkpoint config) {
        enabled = config != null && config.mirrorEnabled;
        intervalNanos =
                config == null
                        ? 300L * 1_000_000_000L
                        : Math.max(0L, config.mirrorAuditIntervalSeconds) * 1_000_000_000L;
        state = enabled ? "UNSUPPORTED" : "DISABLED";
        reason =
                enabled
                        ? "full Vanilla scheduler/ticket mirror is not safely exposed by 1.18.2"
                        : "disabled by config";
    }

    /** Called from the server thread once per tick; it performs no world scan. */
    public synchronized void onTick() {
        if (!enabled || intervalNanos <= 0L) return;
        long now = System.nanoTime();
        if (lastAuditMonotonicNanos == 0L || now - lastAuditMonotonicNanos >= intervalNanos) {
            auditInternal();
        }
    }

    /**
     * Explicit operator audit. This is deliberately diagnostic-only and does not mutate Vanilla.
     */
    public synchronized Snapshot audit() {
        if (enabled) auditInternal();
        return snapshot();
    }

    public synchronized void invalidate(String detail) {
        if (!enabled) return;
        state = "INVALID";
        invalidations++;
        reason = detail == null || detail.isBlank() ? "mirror invalidated" : detail;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(
                enabled,
                state,
                reason,
                lastAuditEpochMs,
                audits,
                invalidations,
                ChunkMutationTracker.global().version(),
                ChunkMutationTracker.global().isUncertain());
    }

    public synchronized String render(String language) {
        Snapshot value = snapshot();
        boolean ja = "ja".equalsIgnoreCase(language);
        return (ja ? "AntiCrash Checkpoint Mirror" : "AntiCrash Checkpoint Mirror")
                + '\n'
                + (ja ? "有効: " : "Enabled: ")
                + value.enabled
                + '\n'
                + (ja ? "状態: " : "State: ")
                + value.state
                + '\n'
                + (ja ? "監査回数: " : "Audits: ")
                + value.audits
                + '\n'
                + (ja ? "最終監査(epoch ms): " : "Last audit(epoch ms): ")
                + value.lastAuditEpochMs
                + '\n'
                + (ja ? "Mutation version: " : "Mutation version: ")
                + value.mutationVersion
                + '\n'
                + (ja ? "理由: " : "Reason: ")
                + value.reason
                + '\n'
                + (ja
                        ? "注意: このmirrorは観測・診断専用で、Vanillaのqueue/ticketを置換しません。"
                        : "Note: this mirror is observation-only; Vanilla queues/tickets are never"
                                + " replaced.");
    }

    private void auditInternal() {
        lastAuditEpochMs = System.currentTimeMillis();
        lastAuditMonotonicNanos = System.nanoTime();
        audits++;
        if (ChunkMutationTracker.global().isUncertain()) {
            state = "INVALID";
            invalidations++;
            reason = "chunk mutation observer is uncertain";
        } else if (!"INVALID".equals(state)) {
            state = enabled ? "UNSUPPORTED" : "DISABLED";
            reason =
                    enabled
                            ? "full Vanilla scheduler/ticket mirror is not safely exposed by 1.18.2"
                            : "disabled by config";
        }
    }

    public record Snapshot(
            boolean enabled,
            String state,
            String reason,
            long lastAuditEpochMs,
            long audits,
            long invalidations,
            long mutationVersion,
            boolean mutationObserverUncertain) {
        public boolean fastPathUsable() {
            return "MATCH".equals(state);
        }

        public String compact() {
            return state.toUpperCase(Locale.ROOT)
                    + "/audits="
                    + audits
                    + "/invalidations="
                    + invalidations;
        }
    }
}
