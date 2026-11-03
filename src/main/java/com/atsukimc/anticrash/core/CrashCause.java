package com.atsukimc.anticrash.core;

public enum CrashCause {
    JAVA_EXCEPTION,
    WATCHDOG,
    OUT_OF_MEMORY,
    EXTERNAL_TERMINATION,
    CRASH_REPORT,
    PROCESS_EXIT_CODE,
    POWER_LOSS_OR_UNKNOWN,
    UNKNOWN_DIRTY_EXIT
}
