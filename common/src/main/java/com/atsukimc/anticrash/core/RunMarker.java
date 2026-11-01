package com.atsukimc.anticrash.core;

public final class RunMarker {
    public int formatVersion = 1;
    public String runId;
    public boolean cleanShutdown;
    public String startedAtUtc;
    public String endedAtUtc;
    public String previousRunId;
    public String crashCause;
    public String previousCrashCause;

    /**
     * False until this run began loading the world. Null in markers written before the field
     * existed, which are treated as having loaded it.
     */
    public Boolean worldOpened;

    /** Whether the world was in a cleanly saved state when this run began. */
    public Boolean worldCleanAtStart;

    /** The run that left the world dirty, when {@link #worldCleanAtStart} is false. */
    public String carriedRunId;

    public String carriedStartedAtUtc;

    /**
     * The run that last had the world open.
     *
     * <p>The marker is written while mods initialise, long before the world is loaded. When the JVM
     * ends there (another mod refuses to start, the port is taken), that run changed nothing in the
     * world, so it says nothing about the world's state: the answer is whatever was true when it
     * began. Without this, a failed start after a clean stop was reported as a crash and offered a
     * rollback of a world that had been saved normally.
     */
    public static RunMarker effectivePrevious(RunMarker raw) {
        if (raw == null || raw.cleanShutdown || !Boolean.FALSE.equals(raw.worldOpened)) return raw;
        RunMarker carried = new RunMarker();
        carried.formatVersion = raw.formatVersion;
        carried.cleanShutdown = Boolean.TRUE.equals(raw.worldCleanAtStart);
        carried.runId = raw.carriedRunId != null ? raw.carriedRunId : raw.runId;
        carried.startedAtUtc =
                raw.carriedStartedAtUtc != null ? raw.carriedStartedAtUtc : raw.startedAtUtc;
        carried.previousRunId = raw.previousRunId;
        carried.worldOpened = Boolean.TRUE;
        return carried;
    }
}
