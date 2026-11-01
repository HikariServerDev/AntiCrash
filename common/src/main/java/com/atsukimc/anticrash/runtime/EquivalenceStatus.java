package com.atsukimc.anticrash.runtime;

/** Outcome of one observable part of the Recovery Equivalence Contract. */
public enum EquivalenceStatus {
    MATCH,
    MISMATCH,
    UNSUPPORTED,
    MISSING_FROM_OLD_FORMAT,
    INCOMPLETE,
    NOT_APPLICABLE
}
