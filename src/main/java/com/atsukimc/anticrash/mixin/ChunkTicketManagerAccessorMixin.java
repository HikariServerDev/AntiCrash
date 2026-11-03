package com.atsukimc.anticrash.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.server.world.ChunkTicket;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.server.level.Ticket;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.server.world.ChunkTicketManager;
//?}

//? if (>=1.18 && <=1.21.4) {
import net.minecraft.util.collection.SortedArraySet;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.TicketStorage;
*///?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only ticket ledger accessor verified against the 1.18.2 mapped class. */
//? if (>=1.18 && <=1.21.11) {
@Mixin(ChunkTicketManager.class)
public interface ChunkTicketManagerAccessorMixin {

    //?}
    //? if (>=1.18 && <=1.21.4) {
    @Accessor("ticketsByPosition")
    Long2ObjectOpenHashMap<SortedArraySet<ChunkTicket<?>>> anticrash$getTicketsByPosition();

    //?}

    //? if (>=1.18 && <=1.21.4) {
    @Accessor("age")
    long anticrash$getAge();
    //?}
    //? if (>=1.21.5 && <=1.21.11) {
    /*@Accessor("tickets")
        Long2ObjectOpenHashMap<java.util.List<ChunkTicket>> anticrash$getTicketsByPosition();
*///?}
    //? if (>=26.1 && <=26.3) {
    /*@Mixin(TicketStorage.class)
    public interface ChunkTicketManagerAccessorMixin {
        @Accessor("tickets")
        Long2ObjectOpenHashMap<java.util.List<Ticket>> anticrash$getTicketsByPosition();
*///?}

}
