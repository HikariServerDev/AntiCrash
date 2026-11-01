package com.atsukimc.anticrash.runtime;

/**
 * Implemented on WorldChunk by WorldChunkExtensionMixin: whether the chunk's last save wrote any
 * AntiCrash runtime extension into its block entities. Lives outside the mixin package because
 * classes there cannot be loaded as ordinary classes.
 */
public interface ExtensionCarrier {
    boolean anticrash$extensionOnDisk();

    void anticrash$setExtensionOnDisk(boolean onDisk);
}
