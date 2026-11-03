package com.atsukimc.anticrash.snapshot;

import java.io.IOException;

/**
 * A file the new generation inherits no longer matches the parent checkpoint's record. The parent
 * can no longer serve as the base of an incremental generation and must be looked up again.
 */
public final class ParentCheckpointChangedException extends IOException {
    public ParentCheckpointChangedException(String message) {
        super(message);
    }
}
