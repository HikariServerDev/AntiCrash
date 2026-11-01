package com.atsukimc.anticrash.runtime;

import com.atsukimc.anticrash.mixin.ChunkTicketAccessorMixin;
import com.atsukimc.anticrash.mixin.ChunkTicketManagerAccessorMixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.minecraft.server.world.ChunkTicket;
import net.minecraft.server.world.ChunkTicketManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;

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

    public static List<Entry> capture(Iterable<ServerWorld> worlds) {
        List<Entry> result = new ArrayList<>();
        for (ServerWorld world : worlds) {
            ChunkTicketManager manager =
                    ((com.atsukimc.anticrash.mixin.ServerChunkManagerAccessorMixin)
                                    world.getChunkManager())
                            .anticrash$getTicketManager();
            ChunkTicketManagerAccessorMixin accessor = (ChunkTicketManagerAccessorMixin) manager;
            Long2ObjectOpenHashMap<List<ChunkTicket>> tickets =
                    accessor.anticrash$getTicketsByPosition();
            for (Long2ObjectMap.Entry<List<ChunkTicket>> positionEntry :
                    tickets.long2ObjectEntrySet()) {
                ChunkPos pos = new ChunkPos(positionEntry.getLongKey());
                for (ChunkTicket ticket : positionEntry.getValue()) {
                    ChunkTicketAccessorMixin ticketAccessor =
                            (ChunkTicketAccessorMixin) (Object) ticket;
                    if (ticket.getType()
                            == com.atsukimc.anticrash.recovery.ChunkRecoveryManager.RECOVERY_TICKET)
                        continue;
                    var typeId =
                            net.minecraft.registry.Registries.TICKET_TYPE.getId(ticket.getType());
                    if (typeId == null)
                        throw new IllegalStateException("Unregistered chunk ticket type");
                    String type =
                            "minecraft".equals(typeId.getNamespace())
                                    ? typeId.getPath()
                                    : typeId.toString();
                    // The recovery ticket exists only in the new JVM and is deliberately not a
                    // checkpoint state category.
                    if ("anticrash_recovery".equals(type)) continue;
                    Entry entry = new Entry();
                    entry.dimension = world.getRegistryKey().getValue().toString();
                    entry.x = pos.x;
                    entry.z = pos.z;
                    entry.type = type;
                    entry.level = ticket.getLevel();
                    entry.argument =
                            "-"; // 1.21.5 no longer has per-ticket arguments or creation age.
                    entry.tickCreated = 0;
                    entry.expiryTicks = ticket.getType().expiryTicks();
                    if (entry.expiryTicks > 0) {
                        entry.remainingTicks = ticketAccessor.anticrash$getTicksLeft();
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
