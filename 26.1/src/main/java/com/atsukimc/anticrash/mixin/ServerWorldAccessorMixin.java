package com.atsukimc.anticrash.mixin;

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.entity.EntityTickList;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;

@Mixin(ServerLevel.class)
public interface ServerWorldAccessorMixin {
    @Accessor("blockEvents")
    ObjectLinkedOpenHashSet<BlockEventData> anticrash$getSyncedBlockEventQueue();

    @Accessor("blockEventsToReschedule")
    List<BlockEventData> anticrash$getBlockEventQueue();

    @Accessor("entityManager")
    PersistentEntitySectionManager<Entity> anticrash$getEntityManager();

    @Accessor("entityTickList")
    EntityTickList anticrash$getEntityList();

    /** Ticks this dimension has had neither a player nor a forced chunk; entities stop at 300. */
    @Accessor("emptyTime")
    int anticrash$getIdleTimeout();

    @Accessor("emptyTime")
    void anticrash$setIdleTimeout(int ticks);
}
