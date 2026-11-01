package com.atsukimc.anticrash.mixin;

import net.minecraft.server.world.ChunkTicket;
import net.minecraft.server.world.ChunkTicketType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access to ticket metadata; the ticket manager is never rewritten. */
@Mixin(ChunkTicket.class)
public interface ChunkTicketAccessorMixin {
    @Accessor("type")
    ChunkTicketType anticrash$getType();

    @Accessor("ticksLeft")
    long anticrash$getTicksLeft();

    @Accessor("ticksLeft")
    void anticrash$setTicksLeft(long ticksLeft);
}
