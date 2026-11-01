package com.atsukimc.anticrash.mixin;

import java.util.*;
import java.util.function.Function;
import net.minecraft.world.ScheduledTick;
import net.minecraft.server.world.ServerTickScheduler;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerTickScheduler.class)
public interface LegacyServerTickSchedulerAccessor<T> {
    @Accessor("scheduledTickActions") Set<ScheduledTick<T>> anticrash$scheduled();
    @Accessor("scheduledTickActionsInOrder") TreeSet<ScheduledTick<T>> anticrash$ordered();
    @Accessor("currentTickActions") Queue<ScheduledTick<T>> anticrash$current();
    @Accessor("consumedTickActions") List<ScheduledTick<T>> anticrash$consumed();
    @Accessor("idToName") Function<T, Identifier> anticrash$registryNames();
}
