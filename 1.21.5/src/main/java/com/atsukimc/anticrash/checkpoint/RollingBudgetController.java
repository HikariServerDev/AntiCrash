package com.atsukimc.anticrash.checkpoint;

/**
 * Adaptive wall-clock budget for optional running-state preparation.
 *
 * <p>The controller never decides that a file/chunk may be skipped.  It only controls how much
 * safe preparation is attempted in one normal tick; unfinished work remains dirty and is handled
 * by the logical-cut barrier.</p>
 */
public final class RollingBudgetController {
    private final double minimumMs;
    private final double maximumMs;
    private final boolean adaptive;
    private double currentMs;

    public RollingBudgetController(double minimumMs, double maximumMs, boolean adaptive) {
        this.minimumMs = Math.max(0.0, minimumMs);
        this.maximumMs = Math.max(this.minimumMs, maximumMs);
        this.adaptive = adaptive;
        this.currentMs = this.minimumMs;
    }

    public synchronized long budgetNanos() {
        return (long) Math.max(0.0, currentMs * 1_000_000.0);
    }

    public synchronized double currentMs() {
        return currentMs;
    }

    public synchronized void observe(long elapsedNanos) {
        if (!adaptive || currentMs <= 0.0) return;
        double elapsedMs = Math.max(0.0, elapsedNanos / 1_000_000.0);
        if (elapsedMs > currentMs * 1.25) {
            currentMs = Math.max(minimumMs, currentMs * 0.75);
        } else if (elapsedMs < currentMs * 0.50) {
            currentMs = Math.min(maximumMs, currentMs * 1.10);
        }
    }
}
