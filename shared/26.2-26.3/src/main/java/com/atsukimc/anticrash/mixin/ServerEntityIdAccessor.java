package com.atsukimc.anticrash.mixin;

import net.minecraft.server.level.ServerLevel;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.atomic.AtomicInteger;

/** 26.2 separates client/server id allocation. getNextEntityId uses this server counter. */
@Mixin(ServerLevel.class)
public interface ServerEntityIdAccessor {
    @Accessor("ENTITY_COUNTER")
    static AtomicInteger anticrash$currentId() {
        throw new AssertionError();
    }
}
