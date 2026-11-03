package com.atsukimc.anticrash.core;

public final class RecoveryStateData {
    public int formatVersion = 1;
    public String state = AntiCrashState.NORMAL.name();

    /** Last committed checkpoint on the currently active recovery timeline. */
    public String activeCheckpointId;

    public String selectedCheckpointId;
    public String pendingRestoreKind;
    public String pendingRestorePath;
    public String crashRawPath;
    public String lastError;
    public boolean preWorldRestoreDone;

    /** True while a RECOVERY_DECISION prompt was raised for an abnormal previous exit. */
    public boolean decisionDirty;

    public long stateChangedAtEpochMs;
}
