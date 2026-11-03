package com.atsukimc.anticrash.snapshot;

public final class SnapshotFile {
    public String path;
    public long size;
    public long modifiedTime;
    public long changeTime;
    public String sha256;

    /**
     * True when this checkpoint entry is a hard link to the already committed and fsynced file of
     * the parent checkpoint. Its data blocks are therefore durable before this capture starts.
     * Absent in manifests written by older builds, which conservatively deserialize to false.
     */
    public boolean reusedFromParent;

    /** Metadata of the immutable file stored inside the checkpoint, used for cheap reuse checks. */
    public boolean destinationMetadataRecorded;

    public long storedModifiedTime;
    public long storedChangeTime;

    /** Link count recorded after this checkpoint file was created. */
    public long storedLinkCount;

    public SnapshotFile() {}

    public SnapshotFile(String path, long size, long modifiedTime, String sha256) {
        this(path, size, modifiedTime, 0L, sha256);
    }

    public SnapshotFile(String path, long size, long modifiedTime, long changeTime, String sha256) {
        this.path = path;
        this.size = size;
        this.modifiedTime = modifiedTime;
        this.changeTime = changeTime;
        this.sha256 = sha256;
    }
}
