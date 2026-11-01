package com.atsukimc.anticrash.checkpoint;

/**
 * Marks the saves a portable checkpoint runs while it holds the world at its cut.
 *
 * <p>Entities are flushed first, before the Runtime Capsule is taken: the flush waits for entity
 * sections that are still loading and adds them to the world, so that an entity that moved into
 * such a section is saved as well. Doing it first means the capsule and the files describe the same
 * world. The later {@code MinecraftServer#save} (level.dat, PersistentState, mod hooks) then leaves
 * entities alone, and runs without its flush: no fsync of every region file, no forced chunk
 * unloads, no wait for every ChunkHolder, and no ticket processing. While this scope is active
 * ServerEntityManagerMixin also drops the fsync at the end of the entity flush. Server thread only.
 */
public final class CutSave {
    private static boolean active;

    private CutSave() {}

    public static boolean active() {
        return active;
    }

    static void begin() {
        active = true;
    }

    static void end() {
        active = false;
    }
}
