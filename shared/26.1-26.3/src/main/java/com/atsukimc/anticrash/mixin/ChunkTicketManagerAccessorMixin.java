package com.atsukimc.anticrash.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.minecraft.server.level.Ticket;
import net.minecraft.world.level.TicketStorage;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only ticket ledger accessor verified against the 1.18.2 mapped class. */
@Mixin(TicketStorage.class)
public interface ChunkTicketManagerAccessorMixin {
    @Accessor("tickets")
    Long2ObjectOpenHashMap<java.util.List<Ticket>> anticrash$getTicketsByPosition();
}
