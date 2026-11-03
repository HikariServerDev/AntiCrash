package com.atsukimc.anticrash.core;

import com.atsukimc.anticrash.util.FileSecurity;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Process-lifetime lock for all mutable data under {@code .anticrash}.
 *
 * <p>Vanilla's world session lock is acquired too late to protect AntiCrash's pre-world-load
 * restore. This lock therefore has to be held before run markers or pending restore state are
 * changed.
 */
public final class InternalProcessLock implements AutoCloseable {
    private final FileChannel channel;
    private final FileLock lock;

    private InternalProcessLock(FileChannel channel, FileLock lock) {
        this.channel = channel;
        this.lock = lock;
    }

    public static InternalProcessLock acquire(Path locksDirectory) throws IOException {
        Path directory = locksDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(directory)) {
            throw new IOException("Unsafe AntiCrash locks directory: " + directory);
        }
        Path file = directory.resolve("instance.lock");
        boolean created = !Files.exists(file, LinkOption.NOFOLLOW_LINKS);
        FileChannel channel =
                FileChannel.open(
                        file,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.READ,
                        StandardOpenOption.WRITE,
                        LinkOption.NOFOLLOW_LINKS);
        try {
            if (created) FileSecurity.hardenCreatedFile(file);
            FileLock lock;
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException exception) {
                lock = null;
            }
            if (lock == null)
                throw new IOException("Another AntiCrash server process already owns " + file);
            return new InternalProcessLock(channel, lock);
        } catch (IOException | RuntimeException exception) {
            try {
                channel.close();
            } catch (IOException closeFailure) {
                exception.addSuppressed(closeFailure);
            }
            throw exception;
        }
    }

    @Override
    public void close() throws IOException {
        IOException failure = null;
        try {
            if (lock.isValid()) lock.release();
        } catch (IOException exception) {
            failure = exception;
        }
        try {
            channel.close();
        } catch (IOException exception) {
            if (failure == null) failure = exception;
            else failure.addSuppressed(exception);
        }
        if (failure != null) throw failure;
    }
}
