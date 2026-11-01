package com.atsukimc.anticrash.snapshot;

/**
 * Explicit experimental backend. CRIU cannot safely capture a live Minecraft JVM as a portable
 * world checkpoint, so this backend uses the same barrier-protected file copy as JAVA_INCREMENTAL
 * and records its conservative mode.
 *
 * <p>Support detection is deliberately inherited: overriding it with a symlink-following {@code
 * Files.isDirectory} check would make this backend accept a world path that JAVA_INCREMENTAL
 * refuses.
 */
public final class CriuBackend extends JavaIncrementalBackend {
    public CriuBackend(java.util.function.IntSupplier configuredThreads) {
        super(configuredThreads);
    }

    @Override
    public String name() {
        return "CRIU_EXPERIMENTAL";
    }
}
