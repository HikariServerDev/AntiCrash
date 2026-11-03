package com.atsukimc.anticrash.mixin;

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.server.world.ChunkTicket;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.server.level.Ticket;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.server.world.ChunkTicketType;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.server.level.TicketType;
*///?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access to ticket metadata; the ticket manager is never rewritten. */
//? if (>=1.18 && <=1.21.11) {
@Mixin(ChunkTicket.class)
public interface ChunkTicketAccessorMixin {

    //?}
    //? if (>=1.18 && <=1.21.4) {
    @Accessor("type")
    ChunkTicketType<?> anticrash$getType();

    //?}
    //? if (>=1.21.5 && <=1.21.11) {
    /*@Accessor("type")
        ChunkTicketType anticrash$getType();
*///?}
    //? if (>=26.1 && <=26.3) {
    /*@Mixin(Ticket.class)
    public interface ChunkTicketAccessorMixin {
        @Accessor("type")
        TicketType anticrash$getType();
*///?}

    //? if (>=1.18 && <=1.21.4) {
    @Accessor("argument")
    Object anticrash$getArgument();

    //?}
    //? if (>=1.21.5 && <=26.3) {
    /*@Accessor("ticksLeft")
        long anticrash$getTicksLeft();
*///?}

    //? if (>=1.18 && <=1.21.4) {
    @Accessor("tickCreated")
    long anticrash$getTickCreated();

    //?}

    //? if (>=1.18 && <=1.21.4) {
    @Accessor("tickCreated")
    void anticrash$setTickCreated(long tickCreated);
    //?}
    //? if (>=1.21.5 && <=26.3) {
    /*@Accessor("ticksLeft")
        void anticrash$setTicksLeft(long ticksLeft);
*///?}

}
