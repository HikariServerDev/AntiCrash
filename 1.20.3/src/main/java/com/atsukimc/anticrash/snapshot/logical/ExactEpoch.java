package com.atsukimc.anticrash.snapshot.logical;

/**
 * Immutable epoch evidence attached to a semantic snapshot.
 *
 * <p>The value is the server tick observed at the logical cut.  A candidate captured earlier is
 * usable only when its mutation generation is still the same; a timestamp by itself is never
 * treated as proof that a value belongs to the current epoch.</p>
 */
public record ExactEpoch(long value) {
    public boolean isBefore(long other) {
        return value < other;
    }

    public boolean isAt(long other) {
        return value == other;
    }
}
