package com.atsukimc.anticrash.checkpoint;

import com.atsukimc.anticrash.snapshot.ParentCheckpointChangedException;

import java.io.IOException;

/** Server-thread circuit breaker: no automatic retries after OOM or parent invalidation. */
public final class CheckpointFailureGate {
    public enum Category {
        OOM,
        MEMORY_BUDGET,
        PARENT_INVALID,
        SEMANTIC_AUTHORITY_MISSING,
        IO_FAILURE,
        CHECKSUM_FAILURE,
        INTERNAL_INVARIANT
    }

    private Category category;
    private int consecutiveFailures;
    private boolean suspended;
    private long retryAtNanos;

    public static Category classify(Throwable failure) {
        // Worker Futures/IO seals wrap failures; preserve the original fatal category.
        Throwable nested = failure;
        for (int depth = 0; nested != null && depth < 8; depth++, nested = nested.getCause()) {
            if (nested instanceof OutOfMemoryError) return Category.OOM;
            if (nested instanceof ParentCheckpointChangedException) return Category.PARENT_INVALID;
            if (nested instanceof MemoryBudgetException) return Category.MEMORY_BUDGET;
        }
        if (failure instanceof OutOfMemoryError) return Category.OOM;
        if (failure instanceof MemoryBudgetException) return Category.MEMORY_BUDGET;
        if (failure instanceof ParentCheckpointChangedException) return Category.PARENT_INVALID;
        String detail = failure == null || failure.getMessage() == null ? "" : failure.getMessage();
        if (detail.toLowerCase(java.util.Locale.ROOT).contains("checksum"))
            return Category.CHECKSUM_FAILURE;
        return failure instanceof IOException ? Category.IO_FAILURE : Category.INTERNAL_INVARIANT;
    }

    public void failed(Throwable failure, long now) {
        category = classify(failure);
        consecutiveFailures++;
        suspended =
                category == Category.OOM
                        || category == Category.MEMORY_BUDGET
                        || category == Category.PARENT_INVALID
                        || consecutiveFailures >= 3;
        retryAtNanos =
                now + Math.min(300L, 30L << Math.min(3, consecutiveFailures - 1)) * 1_000_000_000L;
    }

    public boolean permits(long now) {
        return !suspended && now - retryAtNanos >= 0;
    }

    public boolean suspended() {
        return suspended;
    }

    public Category category() {
        return category;
    }

    public int consecutiveFailures() {
        return consecutiveFailures;
    }

    public void success() {
        category = null;
        consecutiveFailures = 0;
        suspended = false;
        retryAtNanos = 0;
    }

    public void manualRetry() {
        suspended = false;
        retryAtNanos = 0;
    }

    public static final class MemoryBudgetException extends IOException {
        public MemoryBudgetException(long headroom, long required) {
            super(
                    "Checkpoint memory budget unavailable: headroom="
                            + headroom
                            + " required="
                            + required);
        }
    }
}
