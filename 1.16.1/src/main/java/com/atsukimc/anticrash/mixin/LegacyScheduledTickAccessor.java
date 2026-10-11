package com.atsukimc.anticrash.mixin;

import net.minecraft.world.ScheduledTick;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exact 1.16.1 fields; observational only. Restore setters/factory are not yet connected. */
@Mixin(ScheduledTick.class)
public interface LegacyScheduledTickAccessor {
    @Accessor("id") long anticrash$sequence();
    @Accessor("idCounter") static long anticrash$nextSequence() { throw new AssertionError(); }
}
