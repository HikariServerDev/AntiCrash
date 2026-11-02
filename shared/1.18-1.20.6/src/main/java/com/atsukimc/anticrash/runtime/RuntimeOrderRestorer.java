package com.atsukimc.anticrash.runtime;

import com.atsukimc.anticrash.mixin.ChunkTicketAccessorMixin;
import com.atsukimc.anticrash.mixin.ChunkTicketManagerAccessorMixin;
import com.atsukimc.anticrash.mixin.EntityListAccessorMixin;
import com.atsukimc.anticrash.mixin.ServerWorldAccessorMixin;
import com.atsukimc.anticrash.mixin.WorldAccessorMixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ChunkTicket;
import net.minecraft.server.world.ChunkTicketManager;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.collection.SortedArraySet;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.BlockEntityTickInvoker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Puts the tick order and the short-lived tickets of the checkpoint back before the first resumed
 * tick. After a restart Minecraft rebuilds these from the order chunks happened to load in, which
 * changes which hopper, piston or TNT acts first and so changes what a machine does.
 *
 * <p>Everything here is tolerant: whatever still exists is put in the checkpoint's order, and
 * anything the checkpoint did not know (a player who joined during recovery) keeps its place at the
 * end. Nothing is created, removed or ticked.
 */
public final class RuntimeOrderRestorer {
    public record Result(int restored, int unknownLive, int missing) {}

    private static final Pattern BLOCK_POS = Pattern.compile("x=(-?\\d+), y=(-?\\d+), z=(-?\\d+)");

    private RuntimeOrderRestorer() {}

    private static String tickerKey(int x, int y, int z, String name) {
        return x + "," + y + "," + z + "|" + name;
    }

    /**
     * @return null when the live lists are not in a state that can be reordered safely
     */
    public static Result blockEntityTickers(
            ServerWorld world, List<BlockEntityTickerSnapshot> expected) {
        WorldAccessorMixin accessor = (WorldAccessorMixin) world;
        List<BlockEntityTickInvoker> current = accessor.anticrash$getBlockEntityTickers();
        List<BlockEntityTickInvoker> pending = accessor.anticrash$getPendingBlockEntityTickers();
        Map<String, BlockEntityTickInvoker> live = new LinkedHashMap<>();
        for (List<BlockEntityTickInvoker> list : List.of(current, pending)) {
            for (BlockEntityTickInvoker ticker : list) {
                // Vanilla drops a removed ticker unrun on its next pass; it carries no order.
                if (BlockEntityTickerSnapshot.removed(ticker)) continue;
                BlockPos pos = ticker.getPos();
                if (live.put(
                                tickerKey(pos.getX(), pos.getY(), pos.getZ(), ticker.getName()),
                                ticker)
                        != null) {
                    return null;
                }
            }
        }
        // World#tickBlockEntities appends PENDING to CURRENT and then runs CURRENT in order, so
        // where a live ticker sits today does not matter, only the two lists' final contents.
        List<BlockEntityTickInvoker> orderedCurrent = new ArrayList<>();
        List<BlockEntityTickInvoker> orderedPending = new ArrayList<>();
        int missing = 0;
        for (BlockEntityTickerSnapshot snapshot : expected) {
            BlockEntityTickInvoker ticker =
                    live.remove(tickerKey(snapshot.x, snapshot.y, snapshot.z, snapshot.name));
            if (ticker == null) {
                missing++;
            } else if (BlockEntityTickerSnapshot.PENDING.equals(snapshot.queue)) {
                orderedPending.add(ticker);
            } else {
                orderedCurrent.add(ticker);
            }
        }
        int restored = orderedCurrent.size() + orderedPending.size();
        int unknown = live.size();
        orderedPending.addAll(live.values());
        current.clear();
        current.addAll(orderedCurrent);
        pending.clear();
        pending.addAll(orderedPending);
        return new Result(restored, unknown, missing);
    }

    /**
     * @return null while the entity list is being iterated
     */
    public static Result entities(ServerWorld world, List<EntityRuntimeSnapshot> expected) {
        EntityListAccessorMixin accessor =
                (EntityListAccessorMixin)
                        ((ServerWorldAccessorMixin) world).anticrash$getEntityList();
        if (accessor.anticrash$getIterating() != null) return null;
        Int2ObjectMap<Entity> entities = accessor.anticrash$getEntities();
        Map<String, Entity> live = new LinkedHashMap<>();
        for (Entity entity : entities.values()) {
            if (live.put(entity.getUuidAsString(), entity) != null) return null;
        }
        List<Entity> ordered = new ArrayList<>(live.size());
        int missing = 0;
        for (EntityRuntimeSnapshot snapshot : expected) {
            Entity entity = snapshot.uuid == null ? null : live.remove(snapshot.uuid);
            if (entity == null) missing++;
            else ordered.add(entity);
        }
        int restored = ordered.size();
        int unknown = live.size();
        ordered.addAll(live.values());
        // The map is insertion-ordered and keyed by network id; EntityList iterates it as is.
        entities.clear();
        for (Entity entity : ordered) entities.put(entity.getId(), entity);
        return new Result(restored, unknown, missing);
    }

    /**
     * Re-creates the checkpoint's expiring tickets with the lifetime they had left: PORTAL (300
     * ticks after something used a portal), POST_TELEPORT (5 ticks after a teleport) and UNKNOWN (1
     * tick after a chunk was fetched by code such as a hopper or an entity looking into the next
     * chunk). Vanilla saves none of them. Each is added through the same ChunkTicketManager method
     * Vanilla uses for that type and expires by itself, so nothing can leak. LIGHT tickets are
     * owned by the lighting engine, which removes them itself, and are never re-created.
     *
     * <p>Call it right before the first resumed tick: the manager ages tickets whenever the chunk
     * manager is pumped, also while recovery is frozen. Adding a ticket that already exists only
     * refreshes it, so calling this twice is harmless.
     *
     * @return the number of tickets restored
     */
    private static String expiringKey(
            String dimension, int x, int z, String type, int level, String argument) {
        // A post-teleport ticket's argument is a network entity id, which a restart renumbers.
        return dimension
                + "|"
                + x
                + ","
                + z
                + "|"
                + type
                + "|"
                + level
                + "|"
                + ("post_teleport".equals(type) ? "-" : argument);
    }

    /**
     * The key of every expiring ticket of the checkpoint that {@link #expiringTickets} re-creates.
     */
    static java.util.Set<String> expiringKeys(List<TicketLedgerSnapshot.Entry> ledger) {
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (TicketLedgerSnapshot.Entry entry : ledger) {
            if (entry == null || entry.type == null || entry.remainingTicks == null) continue;
            keys.add(
                    expiringKey(
                            entry.dimension,
                            entry.x,
                            entry.z,
                            entry.type,
                            entry.level,
                            entry.argument));
        }
        return keys;
    }

    /**
     * Removes the short-lived tickets that appeared while recovery was frozen.
     *
     * <p>Every chunk lookup on the server thread that misses a four-entry cache leaves a one-tick
     * UNKNOWN ticket behind, and such a lookup of a chunk that is not loaded loads it. Restoring
     * FakePlayers, a player joining, or a command during the freeze all do that, and nothing ages
     * those tickets until the world ticks again. They are not part of the checkpoint: left alone,
     * the first tick would start with a few chunks loaded that the checkpoint did not have.
     *
     * @return the number of tickets removed
     */
    public static int removeStrayExpiringTickets(
            Map<String, ServerWorld> worlds, List<TicketLedgerSnapshot.Entry> ledger) {
        java.util.Set<String> expected = expiringKeys(ledger);
        int removed = 0;
        for (Map.Entry<String, ServerWorld> world : worlds.entrySet()) {
            ChunkTicketManager manager =
                    world.getValue().getChunkManager().threadedAnvilChunkStorage.getTicketManager();
            ChunkTicketManagerAccessorMixin accessor = (ChunkTicketManagerAccessorMixin) manager;
            List<Object[]> stray = new ArrayList<>();
            for (var position : accessor.anticrash$getTicketsByPosition().long2ObjectEntrySet()) {
                ChunkPos chunk = new ChunkPos(position.getLongKey());
                for (ChunkTicket<?> ticket : position.getValue()) {
                    ChunkTicketType<?> type = ticket.getType();
                    if (type != ChunkTicketType.UNKNOWN
                            && type != ChunkTicketType.POST_TELEPORT
                            && type != ChunkTicketType.PORTAL) continue;
                    Object argument =
                            ((ChunkTicketAccessorMixin) (Object) ticket).anticrash$getArgument();
                    if (!expected.contains(
                            expiringKey(
                                    world.getKey(),
                                    chunk.x,
                                    chunk.z,
                                    String.valueOf(type),
                                    ticket.getLevel(),
                                    String.valueOf(argument)))) {
                        stray.add(new Object[] {type, chunk, ticket.getLevel(), argument});
                    }
                }
            }
            for (Object[] ticket : stray) {
                removeTicket(
                        manager,
                        (ChunkTicketType<?>) ticket[0],
                        (ChunkPos) ticket[1],
                        (Integer) ticket[2],
                        ticket[3]);
                removed++;
            }
        }
        return removed;
    }

    @SuppressWarnings("unchecked")
    private static <T> void removeTicket(
            ChunkTicketManager manager,
            ChunkTicketType<?> type,
            ChunkPos chunk,
            int level,
            Object argument) {
        manager.removeTicketWithLevel((ChunkTicketType<T>) type, chunk, level, (T) argument);
    }

    public static int expiringTickets(
            Map<String, ServerWorld> worlds, List<TicketLedgerSnapshot.Entry> ledger) {
        int restored = 0;
        for (TicketLedgerSnapshot.Entry entry : ledger) {
            if (entry == null
                    || entry.type == null
                    || entry.remainingTicks == null
                    || entry.remainingTicks < 0
                    || entry.argument == null) continue;
            ServerWorld world = worlds.get(entry.dimension);
            if (world == null || entry.level < 0 || entry.level > 64) continue;
            ChunkPos chunk = new ChunkPos(entry.x, entry.z);
            ChunkTicketManager manager =
                    world.getChunkManager().threadedAnvilChunkStorage.getTicketManager();
            ChunkTicketType<?> type;
            Object argument;
            switch (entry.type) {
                case "portal" -> {
                    Matcher matcher = BLOCK_POS.matcher(entry.argument);
                    if (!matcher.find() || entry.level > 33) continue;
                    BlockPos portal =
                            new BlockPos(
                                    Integer.parseInt(matcher.group(1)),
                                    Integer.parseInt(matcher.group(2)),
                                    Integer.parseInt(matcher.group(3)));
                    manager.addTicket(ChunkTicketType.PORTAL, chunk, 33 - entry.level, portal);
                    type = ChunkTicketType.PORTAL;
                    argument = portal;
                }
                case "post_teleport" -> {
                    Integer id;
                    try {
                        id = Integer.valueOf(entry.argument.trim());
                    } catch (NumberFormatException invalid) {
                        continue;
                    }
                    if (entry.level > 33) continue;
                    manager.addTicket(ChunkTicketType.POST_TELEPORT, chunk, 33 - entry.level, id);
                    type = ChunkTicketType.POST_TELEPORT;
                    argument = id;
                }
                case "unknown" -> {
                    manager.addTicketWithLevel(ChunkTicketType.UNKNOWN, chunk, entry.level, chunk);
                    type = ChunkTicketType.UNKNOWN;
                    argument = chunk;
                }
                default -> {
                    continue;
                }
            }
            ChunkTicketManagerAccessorMixin accessor = (ChunkTicketManagerAccessorMixin) manager;
            SortedArraySet<ChunkTicket<?>> tickets =
                    accessor.anticrash$getTicketsByPosition().get(chunk.toLong());
            if (tickets == null) continue;
            long expiry = type.getExpiryTicks();
            for (ChunkTicket<?> ticket : tickets) {
                ChunkTicketAccessorMixin ticketAccessor =
                        (ChunkTicketAccessorMixin) (Object) ticket;
                if (ticketAccessor.anticrash$getType() == type
                        && ticket.getLevel() == entry.level
                        && argument.equals(ticketAccessor.anticrash$getArgument())) {
                    ticketAccessor.anticrash$setTickCreated(
                            tickCreatedFor(
                                    accessor.anticrash$getAge(), expiry, entry.remainingTicks));
                    restored++;
                }
            }
        }
        return restored;
    }

    /**
     * Vanilla drops a ticket once {@code age - tickCreated > expiry}; a ticket with {@code
     * remaining} ticks left therefore satisfies {@code age - tickCreated == expiry - remaining}.
     */
    static long tickCreatedFor(long age, long expiry, long remaining) {
        return age - (expiry - Math.min(expiry, Math.max(0L, remaining)));
    }

    /**
     * True when every ticket of the checkpoint that Vanilla keeps without expiry (player, forced,
     * start, dragon) exists again at the same level. Then the chunks the checkpoint had loaded are
     * held by the same tickets as before and AntiCrash's own recovery tickets can go before the
     * first tick, instead of keeping chunks loaded that were about to unload.
     */
    public static boolean permanentTicketsCovered(
            Iterable<ServerWorld> worlds, List<TicketLedgerSnapshot.Entry> ledger) {
        return permanentTicketsCovered(TicketLedgerSnapshot.capture(worlds), ledger);
    }

    /**
     * {@code current} is the live ledger; tickets that expire and light tickets are not waited for.
     */
    public static boolean permanentTicketsCovered(
            List<TicketLedgerSnapshot.Entry> current, List<TicketLedgerSnapshot.Entry> ledger) {
        return missingPermanentTickets(current, ledger) == 0;
    }

    /** How many permanent tickets of the checkpoint do not exist in {@code current}. */
    public static int missingPermanentTickets(
            List<TicketLedgerSnapshot.Entry> current, List<TicketLedgerSnapshot.Entry> ledger) {
        java.util.Set<String> live = new java.util.HashSet<>();
        for (TicketLedgerSnapshot.Entry entry : current) live.add(permanentKey(entry));
        int missing = 0;
        for (TicketLedgerSnapshot.Entry entry : ledger) {
            if (entry == null || entry.expiryTicks != 0 || "light".equals(entry.type)) continue;
            if (!live.contains(permanentKey(entry))) missing++;
        }
        return missing;
    }

    private static String permanentKey(TicketLedgerSnapshot.Entry entry) {
        return entry.dimension
                + "|"
                + entry.x
                + ","
                + entry.z
                + "|"
                + entry.type
                + "|"
                + entry.level;
    }

    public static Map<String, ServerWorld> worlds(Iterable<ServerWorld> worlds) {
        Map<String, ServerWorld> byId = new HashMap<>();
        for (ServerWorld world : worlds)
            byId.put(world.getRegistryKey().getValue().toString(), world);
        return byId;
    }
}
