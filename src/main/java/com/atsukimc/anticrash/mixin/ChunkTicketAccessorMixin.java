package com.atsukimc.anticrash.mixin;

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.server.level.Ticket;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.server.level.Ticket;
//?}

//? if (>=1.18 && <=1.21.11) {
/*import net.minecraft.server.level.TicketType;
*///?}
//? if (>=26.1 && <=26.3) {
import net.minecraft.server.level.TicketType;
//?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access to ticket metadata; the ticket manager is never rewritten. */
//? if (>=1.18 && <=1.21.11) {
/*@Mixin(Ticket.class)
public interface ChunkTicketAccessorMixin {

    *///?}
//? if (>=1.18 && <=1.21.4) {
/*@Accessor("type")
    TicketType<?> anticrash$getType();

    *///?} else if (>=1.21.5 && <=1.21.11) {
/*@Accessor("type")
        TicketType anticrash$getType();
*///?} else if (>=26.1 && <=26.3) {
@Mixin(Ticket.class)
public interface ChunkTicketAccessorMixin {
    @Accessor("type")
    TicketType anticrash$getType();

    //?}

    //? if (>=1.18 && <=1.21.4) {
    /*@Accessor("key")
    Object anticrash$getArgument();

    *///?} else if (>=1.21.5 && <=26.3) {
    @Accessor("ticksLeft")
    long anticrash$getTicksLeft();

    //?}

    //? if (>=1.18 && <=1.21.4) {
    /*@Accessor("createdTick")
    long anticrash$getTickCreated();

    *///?}

    //? if (>=1.18 && <=1.21.4) {
    /*@Accessor("createdTick")
    void anticrash$setTickCreated(long tickCreated);
    *///?} else if (>=1.21.5 && <=26.3) {
    @Accessor("ticksLeft")
    void anticrash$setTicksLeft(long ticksLeft);
    //?}

}
