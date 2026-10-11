package com.atsukimc.anticrash.snapshot;

/**
 * Serializable file-set evidence for one logical generation.
 *
 * <p>Only path metadata and a digest are stored.  No live {@code Path}, stream, NBT object or
 * Minecraft object crosses the barrier/worker boundary.</p>
 */
public final class GenerationFileEntry {
    public String path;
    public String state;
    public String captureKind;
    public long size;
    public long modifiedTime;
    public long changeTime;
    public String sha256;

    public GenerationFileEntry() {
    }

    public GenerationFileEntry(String path, GenerationFileState state,
                               GenerationCaptureKind captureKind, long size,
                               long modifiedTime, long changeTime, String sha256) {
        this.path = path;
        this.state = state == null ? null : state.name();
        this.captureKind = captureKind == null ? null : captureKind.name();
        this.size = size;
        this.modifiedTime = modifiedTime;
        this.changeTime = changeTime;
        this.sha256 = sha256;
    }

    public static GenerationFileEntry fromSnapshot(SnapshotFile file) {
        if (file == null) return null;
        return new GenerationFileEntry(file.path, GenerationFileState.PRESENT,
                file.reusedFromParent ? GenerationCaptureKind.INHERITED : GenerationCaptureKind.SEALED,
                file.size, file.modifiedTime, file.changeTime, file.sha256);
    }
}
