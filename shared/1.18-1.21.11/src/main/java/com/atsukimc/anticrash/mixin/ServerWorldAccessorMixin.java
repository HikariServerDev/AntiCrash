package com.atsukimc.anticrash.mixin;

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.BlockEvent;
import net.minecraft.server.world.ServerEntityManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.EntityList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(ServerWorld.class)
public interface ServerWorldAccessorMixin {
    @Accessor("syncedBlockEventQueue")
    ObjectLinkedOpenHashSet<BlockEvent> anticrash$getSyncedBlockEventQueue();

    @Accessor("blockEventQueue")
    List<BlockEvent> anticrash$getBlockEventQueue();

    @Accessor("entityManager")
    ServerEntityManager<Entity> anticrash$getEntityManager();

    @Accessor("entityList")
    EntityList anticrash$getEntityList();

    /** Ticks this dimension has had neither a player nor a forced chunk; entities stop at 300. */
    @Accessor("idleTimeout")
    int anticrash$getIdleTimeout();

    @Accessor("idleTimeout")
    void anticrash$setIdleTimeout(int ticks);
}
