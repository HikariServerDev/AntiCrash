package com.atsukimc.anticrash.runtime;

import java.util.ArrayList;
import java.util.List;

/** One comparison result. Details are deliberately bounded by the caller before rendering. */
public final class EquivalenceCheck {
    public String category;
    public EquivalenceStatus status;
    public String expectedFingerprint;
    public String actualFingerprint;
    public int expectedCount;
    public int actualCount;
    public boolean blocking;
    public final List<String> differences = new ArrayList<>();

    public EquivalenceCheck() {
    }

    public EquivalenceCheck(String category, EquivalenceStatus status,
                            String expectedFingerprint, String actualFingerprint,
                            int expectedCount, int actualCount, boolean blocking,
                            List<String> differences) {
        this.category = category;
        this.status = status;
        this.expectedFingerprint = expectedFingerprint;
        this.actualFingerprint = actualFingerprint;
        this.expectedCount = expectedCount;
        this.actualCount = actualCount;
        this.blocking = blocking;
        if (differences != null) this.differences.addAll(differences);
    }
}
