package com.atsukimc.anticrash.snapshot.logical;

/**
 * Switches the logical engine's mutation and entity-lifecycle observers on or off.
 *
 * <p>The observers run on every block change, block-entity markDirty, scheduled tick and entity
 * chunk crossing. Their records are read only by the LOGICAL_DELTA engine, which can never produce
 * a checkpoint in a world with POI or PersistentState files. While the engine cannot be used the
 * hooks return immediately; whoever re-enables them must discard any baseline the engine holds,
 * because mutations in between were not recorded.</p>
 */
public final class LogicalTracking {
    private static volatile boolean enabled = true;

    private LogicalTracking() {
    }

    public static boolean enabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }
}
