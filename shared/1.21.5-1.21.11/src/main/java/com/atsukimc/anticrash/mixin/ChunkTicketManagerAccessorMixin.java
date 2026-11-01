package com.atsukimc.anticrash.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.minecraft.server.world.ChunkTicket;
import net.minecraft.server.world.ChunkTicketManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only ticket ledger accessor verified against the 1.18.2 mapped class. */
@Mixin(ChunkTicketManager.class)
public interface ChunkTicketManagerAccessorMixin {
    @Accessor("tickets")
    Long2ObjectOpenHashMap<java.util.List<ChunkTicket>> anticrash$getTicketsByPosition();
}
