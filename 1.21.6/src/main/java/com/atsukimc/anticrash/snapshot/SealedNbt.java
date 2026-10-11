package com.atsukimc.anticrash.snapshot;

import net.minecraft.nbt.NbtCompound;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * The queued NbtCompounds a checkpoint cut took from the storage IO queues and will write into its
 * generation after the server resumes. Identity, not equality: only these exact objects are shared
 * with the IO thread.
 */
public final class SealedNbt {
    private static final Set<NbtCompound> SEALED = Collections.newSetFromMap(new IdentityHashMap<>());
    private static volatile boolean any;

    private SealedNbt() {
    }

    public static boolean isSealed(NbtCompound nbt) {
        if (!any || nbt == null) return false;
        synchronized (SEALED) {
            return SEALED.contains(nbt);
        }
    }

    static void seal(Collection<PendingRegionWrite> writes) {
        synchronized (SEALED) {
            // Only one checkpoint cut is ever outstanding; whatever an abandoned one left is stale.
            SEALED.clear();
            for (PendingRegionWrite write : writes) if (write.nbt() != null) SEALED.add(write.nbt());
            any = !SEALED.isEmpty();
        }
    }

    public static void clear() {
        synchronized (SEALED) {
            SEALED.clear();
            any = false;
        }
    }
}
