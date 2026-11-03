package com.atsukimc.anticrash.snapshot;

/** State of a path in the immutable file set at a logical checkpoint cut. */
public enum GenerationFileState {
    PRESENT,
    ABSENT,
    TOMBSTONE
}
