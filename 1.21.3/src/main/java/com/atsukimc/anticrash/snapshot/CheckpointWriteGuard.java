package com.atsukimc.anticrash.snapshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** Portable internal invariant, not an OS sandbox. READY generations must never be rewritten. */
public final class CheckpointWriteGuard {
    private CheckpointWriteGuard() { }
    public static void requireMutable(Path target) throws IOException {
        Path path = target.toAbsolutePath().normalize();
        for (Path ancestor = path; ancestor != null; ancestor = ancestor.getParent()) {
            if (Files.exists(ancestor.resolve("READY"), LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("INTERNAL_INVARIANT: write into READY checkpoint: " + target);
            }
        }
    }
    public static void requireDetached(Path target) throws IOException {
        requireMutable(target);
        FileFacts facts = FileFacts.readOrNull(target);
        if (facts != null && facts.linkCount() > 1) {
            throw new IOException("INTERNAL_INVARIANT: write into shared checkpoint inode: " + target);
        }
    }
}
