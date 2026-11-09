package com.atsukimc.anticrash.runtime;

import com.atsukimc.anticrash.mixin.ChunkTicketAccessorMixin;
import com.atsukimc.anticrash.mixin.ChunkTicketManagerAccessorMixin;
import com.atsukimc.anticrash.util.VersionChunkPos;
import com.atsukimc.anticrash.util.VersionIds;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

//? if (>=1.18 && <=1.21.4) {
/*import net.minecraft.server.level.DistanceManager;
import net.minecraft.util.SortedArraySet;
*///?} else {
import net.minecraft.world.level.TicketStorage;
//?}

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Read-only diagnostic view of the 1.18.2 ticket ledger.
 *
 * <p>Vanilla owns ticket expiry and propagation and recreates player, forced and spawn tickets by
 * itself. The ledger is compared after loading so ticket differences are visible. The one kind
 * written back is the portal ticket (see {@link RuntimeOrderRestorer#portalTickets}): it keeps
 * chunks loaded for 300 ticks after something used a portal and is not saved by Vanilla.
 */
public final class TicketLedgerSnapshot {
    public int formatVersion = 1;
    public final List<Entry> entries = new ArrayList<>();

    public static List<Entry> capture(Iterable<ServerLevel> worlds) {
        List<Entry> result = new ArrayList<>();

        for (ServerLevel world : worlds) {

            //? if (>=1.18 && <=1.21.4) {
            /*DistanceManager manager = world.getChunkSource().chunkMap.getDistanceManager();
            *///?}
            //? if (>=1.21.5 && <=26.3) {
            TicketStorage manager =
                    ((com.atsukimc.anticrash.mixin.ServerChunkManagerAccessorMixin)
                                    world.getChunkSource())
                            .anticrash$getTicketManager();
            //?}

            ChunkTicketManagerAccessorMixin accessor = (ChunkTicketManagerAccessorMixin) manager;

            //? if (>=1.18 && <=1.21.4) {
            /*Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> tickets =
                    accessor.anticrash$getTicketsByPosition();

            for (Long2ObjectMap.Entry<SortedArraySet<Ticket<?>>> positionEntry :
                    tickets.long2ObjectEntrySet())
            *///?}
            //? if (>=1.21.5 && <=26.3) {
            Long2ObjectOpenHashMap<List<Ticket>> tickets =
                    accessor.anticrash$getTicketsByPosition();

            for (Long2ObjectMap.Entry<List<Ticket>> positionEntry : tickets.long2ObjectEntrySet())
            //?}
            {

                ChunkPos pos = VersionChunkPos.unpack(positionEntry.getLongKey());

                //? if (>=1.18 && <=1.21.4) {
                /*for (Ticket<?> ticket : positionEntry.getValue())
                *///?}
                //? if (>=1.21.5 && <=26.3) {
                for (Ticket ticket : positionEntry.getValue())
                //?}
                {
                    ChunkTicketAccessorMixin ticketAccessor =
                            (ChunkTicketAccessorMixin) (Object) ticket;

                    //? if (>=1.18 && <=1.21.4) {
                    /*String type = String.valueOf(ticketAccessor.anticrash$getType());
                    *///?}
                    //? if (>=1.21.5 && <=26.3) {
                    if (ticket.getType()
                            == com.atsukimc.anticrash.recovery.ChunkRecoveryManager.RECOVERY_TICKET)
                        continue;

                    var typeId =
                            net.minecraft.core.registries.BuiltInRegistries.TICKET_TYPE.getKey(
                                    ticket.getType());

                    if (typeId == null)
                        throw new IllegalStateException("Unregistered chunk ticket type");

                    String type =
                            "minecraft".equals(typeId.getNamespace())
                                    ? typeId.getPath()
                                    : typeId.toString();
                    //?}

                    // The recovery ticket exists only in the new JVM and is deliberately not a
                    // checkpoint state category.
                    if ("anticrash_recovery".equals(type)) continue;
                    Entry entry = new Entry();

                    entry.dimension = VersionIds.dimension(world);
                    entry.x = VersionChunkPos.x(pos);
                    entry.z = VersionChunkPos.z(pos);
                    entry.type = type;

                    entry.level = ticket.getTicketLevel();

                    //? if (>=1.18 && <=1.21.4) {
                    /*entry.argument = String.valueOf(ticketAccessor.anticrash$getArgument());
                    *///?}
                    //? if (>=1.21.5 && <=26.3) {
                    entry.argument = "-";
                    //?}
                    // 1.21.5 no longer has per-ticket arguments or creation age.

                    //? if (>=1.21.5 && <=26.3) {
                    entry.tickCreated = 0;
                    //?}

                    //? if (>=1.18 && <=1.21.4) {
                    /*entry.tickCreated = ticketAccessor.anticrash$getTickCreated();

                    entry.expiryTicks = ticket.getType().timeout();
                    *///?}
                    //? if (>=1.21.5 && <=26.3) {
                    entry.expiryTicks = ticket.getType().timeout();
                    //?}

                    if (entry.expiryTicks > 0)
                    //? if (>=1.18 && <=1.21.4) {
                    /*{
                        // Vanilla keeps a ticket while age - tickCreated <= expiry.
                        entry.remainingTicks =
                                entry.expiryTicks
                                        - (accessor.anticrash$getAge() - entry.tickCreated);
                    *///?}
                    //? if (>=1.21.5 && <=26.3) {
                    {
                        entry.remainingTicks = ticketAccessor.anticrash$getTicksLeft();
                    //?}

                    }
                    result.add(entry);
                }
            }
        }
        result.sort(Comparator.comparing(Entry::canonical));
        return result;
    }

    public static final class Entry {
        public String dimension;
        public int x;
        public int z;
        public String type;
        public int level;
        public String argument;
        public long tickCreated;
        public long expiryTicks;

        /**
         * Ticks left before an expiring ticket lapses; null for permanent tickets and old capsules.
         */
        public Long remainingTicks;

        public String canonical() {
            return dimension
                    + "|"
                    + x
                    + ','
                    + z
                    + "|"
                    + type
                    + "|"
                    + level
                    + "|"
                    + argument
                    + "|"
                    + tickCreated
                    + "|"
                    + expiryTicks;
        }
    }
}
