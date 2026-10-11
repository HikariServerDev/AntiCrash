package com.atsukimc.anticrash.snapshot;

public final class SnapshotFilters {
    private SnapshotFilters() {
    }

    /** These Vanilla files are logically outside the world snapshot by default. */
    public static boolean excluded(String relative) {
        String path = relative.replace('\\', '/');
        int slash = path.indexOf('/');
        String first = slash < 0 ? path : path.substring(0, slash);
        return first.equals("playerdata") || first.equals("stats") || first.equals("advancements") || path.equals("session.lock");
    }
}
