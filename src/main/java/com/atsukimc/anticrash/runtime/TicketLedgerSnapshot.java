package com.atsukimc.anticrash.runtime;

import com.atsukimc.anticrash.mixin.ChunkTicketAccessorMixin;
import com.atsukimc.anticrash.mixin.ChunkTicketManagerAccessorMixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.server.world.ChunkTicket;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.server.level.ServerLevel;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.server.world.ChunkTicketManager;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.server.level.Ticket;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.server.world.ServerWorld;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.ChunkPos;
*///?}

//? if (>=1.18 && <=1.21.4) {
import net.minecraft.util.collection.SortedArraySet;
//?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.util.math.ChunkPos;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.TicketStorage;
*///?}

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

    //? if (>=1.18 && <=1.21.11) {
    public static List<Entry> capture(Iterable<ServerWorld> worlds)
                //?}
                //? if (>=26.1 && <=26.3) {
                /*public static List<Entry> capture(Iterable<ServerLevel> worlds)
*///?}
            {
        List<Entry> result = new ArrayList<>();

        //? if (>=1.18 && <=1.21.11) {
        for (ServerWorld world : worlds)
        //?}
        //? if (>=26.1 && <=26.3) {
        /*for (ServerLevel world : worlds)
*///?}
        {

            //? if (>=1.18 && <=1.20.6) {
            ChunkTicketManager manager =
                    world.getChunkManager().threadedAnvilChunkStorage.getTicketManager();
            //?}
            //? if (>=1.21 && <=1.21.4) {
            /*ChunkTicketManager manager =
                                world.getChunkManager().chunkLoadingManager.getTicketManager();
*///?}
            //? if (>=1.21.5 && <=1.21.11) {
            /*ChunkTicketManager manager =
                                ((com.atsukimc.anticrash.mixin.ServerChunkManagerAccessorMixin)
                                                world.getChunkManager())
                                        .anticrash$getTicketManager();
*///?}
            //? if (>=26.1 && <=26.3) {
            /*TicketStorage manager =
                                ((com.atsukimc.anticrash.mixin.ServerChunkManagerAccessorMixin)
                                                world.getChunkSource())
                                        .anticrash$getTicketManager();
*///?}

            ChunkTicketManagerAccessorMixin accessor = (ChunkTicketManagerAccessorMixin) manager;

            //? if (>=1.18 && <=1.21.4) {
            Long2ObjectOpenHashMap<SortedArraySet<ChunkTicket<?>>> tickets =
                    accessor.anticrash$getTicketsByPosition();
            //?}
            //? if (>=1.21.5 && <=1.21.11) {
            /*Long2ObjectOpenHashMap<List<ChunkTicket>> tickets =
                                accessor.anticrash$getTicketsByPosition();
*///?}
            //? if (>=26.1 && <=26.3) {
            /*Long2ObjectOpenHashMap<List<Ticket>> tickets =
                                accessor.anticrash$getTicketsByPosition();
*///?}

            //? if (>=1.18 && <=1.21.4) {
            for (Long2ObjectMap.Entry<SortedArraySet<ChunkTicket<?>>> positionEntry :
                    tickets.long2ObjectEntrySet())
            //?}
            //? if (>=1.21.5 && <=1.21.11) {
            /*for (Long2ObjectMap.Entry<List<ChunkTicket>> positionEntry :
                                tickets.long2ObjectEntrySet())
*///?}
            //? if (>=26.1 && <=26.3) {
            /*for (Long2ObjectMap.Entry<List<Ticket>> positionEntry : tickets.long2ObjectEntrySet())
*///?}
            {

                //? if (>=1.18 && <=1.21.11) {
                ChunkPos pos = new ChunkPos(positionEntry.getLongKey());
                //?}
                //? if (>=26.1 && <=26.3) {
                /*ChunkPos pos = ChunkPos.unpack(positionEntry.getLongKey());
*///?}

                //? if (>=1.18 && <=1.21.4) {
                for (ChunkTicket<?> ticket : positionEntry.getValue())
                //?}
                //? if (>=1.21.5 && <=1.21.11) {
                /*for (ChunkTicket ticket : positionEntry.getValue())
*///?}
                //? if (>=26.1 && <=26.3) {
                /*for (Ticket ticket : positionEntry.getValue())
*///?}
                {
                    ChunkTicketAccessorMixin ticketAccessor =
                            (ChunkTicketAccessorMixin) (Object) ticket;

                    //? if (>=1.18 && <=1.21.4) {
                    String type = String.valueOf(ticketAccessor.anticrash$getType());
                    //?}
                    //? if (>=1.21.5 && <=26.3) {
                    /*if (ticket.getType()
                                                == com.atsukimc.anticrash.recovery.ChunkRecoveryManager.RECOVERY_TICKET)
                                            continue;
*///?}

                    //? if (>=1.21.5 && <=1.21.11) {
                    /*var typeId =
                                                net.minecraft.registry.Registries.TICKET_TYPE.getId(ticket.getType());
*///?}
                    //? if (>=26.1 && <=26.3) {
                    /*var typeId =
                                                net.minecraft.core.registries.BuiltInRegistries.TICKET_TYPE.getKey(
                                                        ticket.getType());
*///?}

                    //? if (>=1.21.5 && <=26.3) {
                    /*if (typeId == null)
                                            throw new IllegalStateException("Unregistered chunk ticket type");
*///?}

                    //? if (>=1.21.5 && <=26.3) {
                    /*String type =
                                                "minecraft".equals(typeId.getNamespace())
                                                        ? typeId.getPath()
                                                        : typeId.toString();
*///?}

                    // The recovery ticket exists only in the new JVM and is deliberately not a
                    // checkpoint state category.
                    if ("anticrash_recovery".equals(type)) continue;
                    Entry entry = new Entry();

                    //? if (>=1.18 && <=1.21.11) {
                    entry.dimension = world.getRegistryKey().getValue().toString();
                    //?}
                    //? if (>=26.1 && <=26.3) {
                    /*entry.dimension = world.dimension().identifier().toString();
*///?}

                    //? if (>=1.18 && <=1.21.11) {
                    entry.x = pos.x;
                    //?}
                    //? if (>=26.1 && <=26.3) {
                    /*entry.x = pos.x();
*///?}

                    //? if (>=1.18 && <=1.21.11) {
                    entry.z = pos.z;
                    //?}
                    //? if (>=26.1 && <=26.3) {
                    /*entry.z = pos.z();
*///?}

                    entry.type = type;

                    //? if (>=1.18 && <=1.21.11) {
                    entry.level = ticket.getLevel();
                    //?}
                    //? if (>=26.1 && <=26.3) {
                    /*entry.level = ticket.getTicketLevel();
*///?}

                    //? if (>=1.18 && <=1.21.4) {
                    entry.argument = String.valueOf(ticketAccessor.anticrash$getArgument());
                    //?}
                    //? if (>=1.21.5 && <=26.3) {
                    /*entry.argument =
                                                "-";
*///?}
                    // 1.21.5 no longer has per-ticket arguments or creation age.

                    //? if (>=1.21.5 && <=26.3) {
                    /*entry.tickCreated = 0;
*///?}

                    //? if (>=1.18 && <=1.21.4) {
                    entry.tickCreated = ticketAccessor.anticrash$getTickCreated();
                    //?}

                    //? if (>=1.18 && <=1.21.4) {
                    entry.expiryTicks = ticket.getType().getExpiryTicks();
                    //?}
                    //? if (>=1.21.5 && <=1.21.11) {
                    /*entry.expiryTicks = ticket.getType().expiryTicks();
*///?}
                    //? if (>=26.1 && <=26.3) {
                    /*entry.expiryTicks = ticket.getType().timeout();
*///?}

                    if (entry.expiryTicks > 0)
                    //? if (>=1.18 && <=1.21.4) {
                    {
                        // Vanilla keeps a ticket while age - tickCreated <= expiry.
                        entry.remainingTicks =
                                entry.expiryTicks
                                        - (accessor.anticrash$getAge() - entry.tickCreated);
                        //?}
                        //? if (>=1.21.5 && <=26.3) {
                        /*{
                                                entry.remainingTicks = ticketAccessor.anticrash$getTicksLeft();
*///?}

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
