package com.atsukimc.anticrash.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.minecraft.server.level.Ticket;

//? if (>=1.18 && <=1.21.4) {
/*import net.minecraft.server.level.DistanceManager;
import net.minecraft.util.SortedArraySet;
*///?} else {
import net.minecraft.world.level.TicketStorage;
//?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only ticket ledger accessor verified against the 1.18.2 mapped class. */
//? if (>=1.18 && <=1.21.4) {
/*@Mixin(DistanceManager.class)
public interface ChunkTicketManagerAccessorMixin {

    @Accessor("tickets")
    Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> anticrash$getTicketsByPosition();

    @Accessor("ticketTickCounter")
    long anticrash$getAge();
*///?} else {
@Mixin(TicketStorage.class)
public interface ChunkTicketManagerAccessorMixin {

    @Accessor("tickets")
    Long2ObjectOpenHashMap<java.util.List<Ticket>> anticrash$getTicketsByPosition();
//?}
}
