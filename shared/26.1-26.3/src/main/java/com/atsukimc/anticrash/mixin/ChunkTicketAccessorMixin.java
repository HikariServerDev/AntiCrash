package com.atsukimc.anticrash.mixin;

import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access to ticket metadata; the ticket manager is never rewritten. */
@Mixin(Ticket.class)
public interface ChunkTicketAccessorMixin {
    @Accessor("type")
    TicketType anticrash$getType();

    @Accessor("ticksLeft")
    long anticrash$getTicksLeft();

    @Accessor("ticksLeft")
    void anticrash$setTicksLeft(long ticksLeft);
}
