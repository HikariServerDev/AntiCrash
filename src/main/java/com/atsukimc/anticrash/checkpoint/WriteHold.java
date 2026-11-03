package com.atsukimc.anticrash.checkpoint;

import com.atsukimc.anticrash.mixin.StorageIoWorkerAccessorMixin;

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.storage.StorageIoWorker;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.chunk.storage.IOWorker;
*///?}

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps storage IO workers from starting new writes while a checkpoint cut is being taken.
 *
 * <p>{@link IoSeal} has to wait for the write each worker is running. With synchronous chunk
 * writes, and a region file fsynced whenever it drops out of the worker's file cache, one write can
 * take tens of milliseconds, and right after a recovery the workers always have one running.
 * Holding new writes from the start of the cut lets the running one finish while the entities and
 * the Runtime Capsule are captured, so the seal finds every worker idle. Reads are never held.
 * Correctness does not depend on this: the seal alone guarantees the cut.
 */
public final class WriteHold {
    /** Safety net only; every cut releases the hold in a finally block. */
    private static final long MAX_HOLD_NANOS = 5_000_000_000L;

    private static volatile long heldUntil;

    //? if (>=1.18 && <=1.21.11) {
    private static final Set<StorageIoWorker> DEFERRED = ConcurrentHashMap.newKeySet();

    //?}
    //? if (>=26.1 && <=26.3) {
    /*private static final Set<IOWorker> DEFERRED = ConcurrentHashMap.newKeySet();
*///?}

    private WriteHold() {}

    private static boolean held() {
        long until = heldUntil;
        return until != 0L && System.nanoTime() - until < 0L;
    }

    /** Called by a worker about to write one queued result; true means skip it for now. */

    //? if (>=1.18 && <=1.21.11) {
    public static boolean defer(StorageIoWorker worker)
                //?}
                //? if (>=26.1 && <=26.3) {
                /*public static boolean defer(IOWorker worker)
*///?}
            {
        if (!held()) return false;
        DEFERRED.add(worker);
        // A release between the check and the add has already drained the set; write instead.
        if (!held()) {
            DEFERRED.remove(worker);
            return false;
        }
        return true;
    }

    static void hold() {
        heldUntil = System.nanoTime() + MAX_HOLD_NANOS;
    }

    /** Ends the hold and restarts the writes of every worker that skipped one. */
    static void release() {
        heldUntil = 0L;

        //? if (>=1.18 && <=1.21.11) {
        for (StorageIoWorker worker : DEFERRED)
        //?}
        //? if (>=26.1 && <=26.3) {
        /*for (IOWorker worker : DEFERRED)
*///?}
        {
            DEFERRED.remove(worker);
            ((StorageIoWorkerAccessorMixin) worker).anticrash$writeRemainingResults();
        }
    }
}
