package com.atsukimc.anticrash.snapshot;

import net.minecraft.server.MinecraftServer;

/** These versions complete PersistentState writes synchronously in the Vanilla save. */
public final class PersistentDataFlush {
    private PersistentDataFlush() {}

    public static void await(MinecraftServer server) {}
}
