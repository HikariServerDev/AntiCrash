package com.atsukimc.anticrash.snapshot;

import net.minecraft.util.math.ChunkPos;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;

/**
 * Region-format files Minecraft has written since the last checkpoint cut.
 *
 * <p>Every chunk, entity and POI write in 1.18.2 goes through {@code RegionBasedStorage#write} on a
 * storage IO thread, which records the region file and the chunk's external {@code .mcc} file here.
 * A checkpoint that knows this set does not have to stat and compare ten thousand region files
 * while the world is frozen: a file that was not written since the parent's cut still has the
 * parent's bytes and can be inherited from the parent checkpoint by the background worker.
 *
 * <p>The set is only meaningful relative to one cut. {@link #swap()} is called at a cut after all
 * storage IO queues were drained, and returns the writes since the previous swap. Recording starts
 * with the first swap; before it the tracker is disarmed and a checkpoint must compare metadata.
 */
public final class LiveFileTracker {
    private static final LiveFileTracker GLOBAL = new LiveFileTracker();
    private static final Pattern REGION_STORAGE_NAME =
            Pattern.compile("r\\.-?\\d+\\.-?\\d+\\.mca|c\\.-?\\d+\\.-?\\d+\\.mcc");

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private Set<Path> written;

    public static LiveFileTracker global() {
        return GLOBAL;
    }

    /** Called by RegionBasedStorage#write, usually on a storage IO thread. */
    public void recordRegionWrite(Path directory, ChunkPos position) {
        if (directory == null || position == null) return;
        lock.readLock().lock();
        try {
            Set<Path> target = written;
            if (target == null) return;
            Path normalized = directory.toAbsolutePath().normalize();
            target.add(normalized.resolve(regionFileName(position)));
            // An oversized chunk lives in c.<x>.<z>.mcc beside the region file; RegionFile creates
            // or deletes it inside the same write.
            target.add(normalized.resolve("c." + position.x + "." + position.z + ".mcc"));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Starts a new interval at a cut and returns the files written in the previous one, or null
     * when the tracker was not recording (the caller must then fall back to metadata comparison).
     */
    public Set<Path> swap() {
        lock.writeLock().lock();
        try {
            Set<Path> previous = written;
            written = ConcurrentHashMap.newKeySet();
            return previous;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Returns the files of an interval whose checkpoint failed to the current interval, so the next
     * capture still covers every write since the last checkpoint that did commit.
     */
    public void restore(Set<Path> files) {
        if (files == null || files.isEmpty()) return;
        lock.writeLock().lock();
        try {
            if (written != null) written.addAll(files);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Stops recording, e.g. after the live world was replaced by a restore. */
    public void disarm() {
        lock.writeLock().lock();
        try {
            written = null;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean armed() {
        lock.readLock().lock();
        try {
            return written != null;
        } finally {
            lock.readLock().unlock();
        }
    }

    public static String regionFileName(ChunkPos position) {
        return "r." + position.getRegionX() + "." + position.getRegionZ() + ".mca";
    }

    /**
     * True for a file whose every write goes through RegionBasedStorage: a region or external chunk
     * file directly inside a {@code region}, {@code entities} or {@code poi} directory.
     */
    public static boolean isRegionStorageFile(String relative) {
        String path = relative.replace('\\', '/');
        int slash = path.lastIndexOf('/');
        if (slash <= 0) return false;
        String name = path.substring(slash + 1);
        int parentStart = path.lastIndexOf('/', slash - 1) + 1;
        String parent = path.substring(parentStart, slash);
        if (!parent.equals("region") && !parent.equals("entities") && !parent.equals("poi"))
            return false;
        return REGION_STORAGE_NAME.matcher(name).matches();
    }
}
