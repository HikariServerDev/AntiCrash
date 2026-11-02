package com.atsukimc.anticrash.recovery;

import net.minecraft.server.world.ServerChunkManager;

/**
 * 1.18.1 mapped bytecode: tick(BooleanSupplier) purges tickets, calls tick():boolean,
 * tickChunks():void, storage.tick(supplier), then clears chunk caches. Unlike 1.18.2, it has no
 * boolean to skip simulation. The Mixin suppresses only its tickChunks call within this scope;
 * ticket/storage progression and ordinary ticks stay unchanged.
 */
public final class VersionChunkLifecycle {
    private static final ThreadLocal<Boolean> DISABLED = new ThreadLocal<>();

    private VersionChunkLifecycle() {}

    public static boolean simulationDisabled() {
        return Boolean.TRUE.equals(DISABLED.get());
    }

    public static void advance(ServerChunkManager manager) {
        withoutSimulation(() -> manager.tick(() -> true));
    }

    public static void withoutSimulation(Runnable work) {
        Boolean previous = DISABLED.get();
        DISABLED.set(true);
        try {
            work.run();
        } finally {
            if (previous == null) DISABLED.remove();
            else DISABLED.set(previous);
        }
    }
}
