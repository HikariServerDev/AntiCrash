package com.atsukimc.anticrash.snapshot;

import java.util.ArrayList;
import java.util.List;

public final class SnapshotResult {
    public String backend;
    public long totalBytes;
    public int changedFiles;
    public int reusedFiles;
    public final List<SnapshotFile> files = new ArrayList<>();

    public SnapshotResult() {}

    public SnapshotResult(String backend) {
        this.backend = backend;
    }
}
