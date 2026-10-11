package com.atsukimc.anticrash.recovery;

import com.atsukimc.anticrash.runtime.ChunkStateSnapshot;
import com.atsukimc.anticrash.runtime.MovingPistonTickSnapshot;
import com.atsukimc.anticrash.runtime.RuntimeCapsule;
import com.atsukimc.anticrash.runtime.ScheduledTickSnapshot;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds a bounded, de-duplicated chunk recovery plan without touching Minecraft state.
 *
 * <p>The default {@link PreloadMode#ALL} brings every chunk that was loaded at the checkpoint back
 * to the level it had there (entity ticking, block ticking or merely loaded) before the first
 * tick. A machine that spans chunks only behaves as before when all of its chunks resume on the
 * same tick: with a subset, the ticking half runs against a frozen or missing half (hoppers
 * pushing into a chunk that is not ticking yet, redstone cut at a chunk border) until the rest
 * loads over the following seconds. This is the same set the server held resident right before
 * the checkpoint, so it fits the heap that held it. {@link PreloadMode#CRITICAL} and
 * {@link PreloadMode#ENTITY_TICKING} remain for servers that cannot afford it.</p>
 */
public final class ChunkRecoveryPlan {
    /** Vanilla 1.18.2 uses level 33 for a target chunk at FULL status. */
    public static final int FULL_TICKET_LEVEL = 33;
    /** Level 32 is the block-ticking boundary in 1.18.2. */
    public static final int BLOCK_TICKING_TICKET_LEVEL = 32;
    /** Vanilla's own forced-chunk ticket uses level 31 (entity ticking). */
    public static final int ENTITY_TICKING_TICKET_LEVEL = 31;
    /** Minecraft 1.21.5 ServerChunkManager.addTicket accepts the level directly, not radius. */
    public static int ticketArgumentForLevel(int ticketLevel) {
        if (ticketLevel < 0 || ticketLevel > FULL_TICKET_LEVEL) {
            throw new IllegalArgumentException("Invalid Vanilla chunk ticket level: " + ticketLevel);
        }
        return ticketLevel;
    }
    /** Hard upper bound for any explicitly requested preload plan. */
    public static final int MAX_REQUIRED_CHUNKS = 100_000;
    /** Default bound: the checkpoint's own chunk set, up to the hard upper bound. */
    public static final int DEFAULT_MAX_PRELOAD_CHUNKS = MAX_REQUIRED_CHUNKS;

    private ChunkRecoveryPlan() {
    }

    /**
     * Keeps the old all-state planner available for compatibility and unit tests. Runtime
     * recovery uses {@link #select(RuntimeCapsule, PreloadMode, Set, int)} instead.
     */
    public static List<Entry> from(RuntimeCapsule capsule) {
        return select(capsule, PreloadMode.ALL, Set.of(), MAX_REQUIRED_CHUNKS).entries();
    }

    public static Selection select(RuntimeCapsule capsule, PreloadMode mode,
                                   Set<Anchor> anchors, int maxPreloadChunks) {
        if (capsule == null || capsule.chunks == null) {
            return new Selection(List.of(), 0, 0, 0, 0, 0, false);
        }
        if (mode == null) throw new IllegalArgumentException("Chunk preload mode is missing");
        if (maxPreloadChunks < 1 || maxPreloadChunks > MAX_REQUIRED_CHUNKS) {
            throw new IllegalArgumentException("Chunk preload limit must be between 1 and "
                    + MAX_REQUIRED_CHUNKS);
        }

        Map<Key, Entry> entries = new LinkedHashMap<>();
        Set<Key> loaded = new LinkedHashSet<>();
        Set<Key> entityTicking = new LinkedHashSet<>();
        Set<Key> blockTicking = new LinkedHashSet<>();
        Set<Key> forced = new LinkedHashSet<>();
        Set<Key> transientState = new LinkedHashSet<>();
        Set<Key> movingPistonChunks = new LinkedHashSet<>();
        Set<Key> scheduledTickChunks = new LinkedHashSet<>();
        for (ChunkStateSnapshot snapshot : capsule.chunks) {
            validateSnapshot(snapshot, capsule.formatVersion);
            collect(loaded, snapshot.dimension, snapshot.loadedChunks);
            collect(entityTicking, snapshot.dimension, snapshot.entityTickingChunks);
            if (snapshot.blockTickingChunks != null) {
                collect(blockTicking, snapshot.dimension, snapshot.blockTickingChunks);
            }
            collect(forced, snapshot.dimension, snapshot.forcedChunks);
            if (capsule.formatVersion >= 2) {
                collect(transientState, snapshot.dimension, snapshot.transientStateChunks);
            }
        }
        // Scheduled ticks and moving pistons are exact persisted runtime state and must not be
        // dropped just because their chunk was FULL-only. A scheduled tick remains dormant until
        // Vanilla naturally promotes its chunk to block-ticking; a piston needs only the
        // block-ticking boundary. Transient TNT/FallingBlock entities are different: promoting a
        // dormant entity would change gameplay, so that marker remains limited to the observed
        // entity-ticking set below.
        if (capsule.scheduledTicks != null) {
            for (ScheduledTickSnapshot tick : capsule.scheduledTicks) {
                if (tick == null || tick.dimension == null || tick.dimension.isBlank()) {
                    throw new IllegalArgumentException("Invalid scheduled-tick recovery state");
                }
                scheduledTickChunks.add(new Key(tick.dimension,
                        new net.minecraft.world.level.ChunkPos(tick.x >> 4, tick.z >> 4).pack()));
            }
        }
        if (capsule.movingPistonTickOrder != null) {
            for (MovingPistonTickSnapshot piston : capsule.movingPistonTickOrder) {
                if (piston == null || piston.dimension == null || piston.dimension.isBlank()) {
                    throw new IllegalArgumentException("Invalid moving-piston recovery state");
                }
                movingPistonChunks.add(new Key(piston.dimension,
                        new net.minecraft.world.level.ChunkPos(piston.x >> 4, piston.z >> 4).pack()));
            }
        }
        transientState.retainAll(entityTicking);

        Set<Anchor> safeAnchors = anchors == null ? Set.of() : anchors;
        switch (mode) {
            case CRITICAL -> {
                addAll(entries, forced, ENTITY_TICKING_TICKET_LEVEL, true, maxPreloadChunks);
                addObserved(entries, movingPistonChunks, BLOCK_TICKING_TICKET_LEVEL,
                        entityTicking, blockTicking, maxPreloadChunks);
                addObserved(entries, scheduledTickChunks, FULL_TICKET_LEVEL,
                        entityTicking, blockTicking, maxPreloadChunks);
                addAll(entries, transientState, ENTITY_TICKING_TICKET_LEVEL, false,
                        maxPreloadChunks);
                addAnchors(entries, safeAnchors, entityTicking, maxPreloadChunks);
            }
            case ENTITY_TICKING -> {
                addAll(entries, entityTicking, ENTITY_TICKING_TICKET_LEVEL, false, maxPreloadChunks);
                addAll(entries, forced, ENTITY_TICKING_TICKET_LEVEL, true, maxPreloadChunks);
                addObserved(entries, movingPistonChunks, BLOCK_TICKING_TICKET_LEVEL,
                        entityTicking, blockTicking, maxPreloadChunks);
                addObserved(entries, scheduledTickChunks, FULL_TICKET_LEVEL,
                        entityTicking, blockTicking, maxPreloadChunks);
                addAll(entries, transientState, ENTITY_TICKING_TICKET_LEVEL, false,
                        maxPreloadChunks);
                addAnchors(entries, safeAnchors, entityTicking, maxPreloadChunks);
            }
            case ALL -> {
                addAll(entries, loaded, FULL_TICKET_LEVEL, false, maxPreloadChunks);
                addAll(entries, blockTicking, BLOCK_TICKING_TICKET_LEVEL, false, maxPreloadChunks);
                addAll(entries, entityTicking, ENTITY_TICKING_TICKET_LEVEL, false, maxPreloadChunks);
                addAll(entries, forced, ENTITY_TICKING_TICKET_LEVEL, true, maxPreloadChunks);
                addObserved(entries, movingPistonChunks, BLOCK_TICKING_TICKET_LEVEL,
                        entityTicking, blockTicking, maxPreloadChunks);
                addObserved(entries, scheduledTickChunks, FULL_TICKET_LEVEL,
                        entityTicking, blockTicking, maxPreloadChunks);
                addAll(entries, transientState, ENTITY_TICKING_TICKET_LEVEL, false,
                        maxPreloadChunks);
                addAnchors(entries, safeAnchors, entityTicking, maxPreloadChunks);
            }
        }

        int deferredLoaded = countNotSelected(loaded, entries);
        int deferredEntityTicking = countNotSelected(entityTicking, entries);
        return new Selection(List.copyOf(entries.values()), deferredLoaded, deferredEntityTicking,
                loaded.size(), entityTicking.size(), forced.size(), false);
    }

    /**
     * Selects every observable checkpoint-domain row for STRICT recovery. This method intentionally
     * does not apply {@code max_preload_chunks}; the caller receives a selection marked as
     * {@link Selection#limitExceeded()} and must keep recovery frozen instead of truncating the
     * domain.
     */
    public static Selection selectStrict(RuntimeCapsule capsule, int maxPreloadChunks) {
        if (capsule == null || capsule.chunks == null) {
            return new Selection(List.of(), 0, 0, 0, 0, 0, false);
        }
        if (capsule.formatVersion < 6) {
            throw new IllegalArgumentException("STRICT recovery requires Runtime Capsule v6");
        }
        if (maxPreloadChunks < 1 || maxPreloadChunks > MAX_REQUIRED_CHUNKS) {
            throw new IllegalArgumentException("Chunk recovery preload limit must be between 1 and "
                    + MAX_REQUIRED_CHUNKS);
        }
        Map<Key, Entry> entries = new LinkedHashMap<>();
        int loaded = 0;
        int entityTicking = 0;
        int forced = 0;
        for (ChunkStateSnapshot snapshot : capsule.chunks) {
            validateSnapshot(snapshot, capsule.formatVersion);
            if (snapshot.domain == null) {
                throw new IllegalArgumentException("STRICT chunk domain is missing for "
                        + snapshot.dimension);
            }
            for (ChunkStateSnapshot.ChunkReadiness readiness : snapshot.domain) {
                if (readiness == null) throw new IllegalArgumentException("Null STRICT chunk domain row");
                boolean required = readiness.loaded || readiness.full || readiness.blockTicking
                        || readiness.entityTicking || readiness.forced;
                if (!required) continue;
                Key key = new Key(snapshot.dimension, readiness.packedPosition);
                int level = readiness.entityTicking || readiness.forced
                        ? ENTITY_TICKING_TICKET_LEVEL
                        : readiness.blockTicking ? BLOCK_TICKING_TICKET_LEVEL
                        : FULL_TICKET_LEVEL;
                Entry previous = entries.putIfAbsent(key,
                        new Entry(snapshot.dimension, readiness.packedPosition, level, readiness.forced));
                if (previous != null) {
                    int strongest = Math.min(previous.ticketLevel(), level);
                    entries.put(key, new Entry(snapshot.dimension, readiness.packedPosition,
                            strongest, previous.forced() || readiness.forced));
                }
            }
            loaded += snapshot.loadedChunks.size();
            entityTicking += snapshot.entityTickingChunks.size();
            forced += snapshot.forcedChunks.size();
        }
        List<Entry> selected = List.copyOf(entries.values());
        return new Selection(selected, 0, 0, loaded, entityTicking, forced,
                selected.size() > maxPreloadChunks);
    }

    private static void validateSnapshot(ChunkStateSnapshot snapshot, int capsuleVersion) {
        if (snapshot == null || snapshot.dimension == null || snapshot.dimension.isBlank()
                || snapshot.loadedChunks == null || snapshot.entityTickingChunks == null
                || snapshot.forcedChunks == null
                || (capsuleVersion >= 2 && snapshot.transientStateChunks == null)) {
            throw new IllegalArgumentException("Invalid chunk recovery snapshot");
        }
    }

    private static void collect(Set<Key> output, String dimension, List<Long> positions) {
        for (Long packed : positions) {
            if (packed == null) {
                throw new IllegalArgumentException("Null chunk position in recovery snapshot");
            }
            output.add(new Key(dimension, packed));
        }
    }

    private static void addAll(Map<Key, Entry> entries, Set<Key> positions, int ticketLevel,
                               boolean forced, int maxPreloadChunks) {
        for (Key key : positions) add(entries, key.dimension(), key.packedPosition(), ticketLevel,
                forced, maxPreloadChunks);
    }

    /**
     * Prepares a transient-state chunk at the level it actually had at the checkpoint. A clock or
     * chunk loader whose scheduled ticks sat in a ticking chunk must tick again after /ac start;
     * holding it at FULL left it dormant until the temporary ticket was released, at which point
     * the machine had already stopped for good. A chunk is never raised above its observed level.
     */
    private static void addObserved(Map<Key, Entry> entries, Set<Key> positions, int minimumLevel,
                                    Set<Key> entityTicking, Set<Key> blockTicking,
                                    int maxPreloadChunks) {
        for (Key key : positions) {
            int level = entityTicking.contains(key) ? ENTITY_TICKING_TICKET_LEVEL
                    : blockTicking.contains(key)
                    ? Math.min(minimumLevel, BLOCK_TICKING_TICKET_LEVEL) : minimumLevel;
            add(entries, key.dimension(), key.packedPosition(), level, false, maxPreloadChunks);
        }
    }

    private static void addAnchors(Map<Key, Entry> entries, Set<Anchor> anchors,
                                   Set<Key> entityTicking, int maxPreloadChunks) {
        for (Anchor anchor : anchors) {
            if (anchor == null || anchor.dimension() == null || anchor.dimension().isBlank()) {
                throw new IllegalArgumentException("Invalid chunk recovery anchor");
            }
            Key key = new Key(anchor.dimension(), anchor.packedPosition());
            // Preserve whether this anchor was active or merely loaded at the checkpoint.
            int ticketLevel = entityTicking.contains(key)
                    ? ENTITY_TICKING_TICKET_LEVEL : FULL_TICKET_LEVEL;
            add(entries, anchor.dimension(), anchor.packedPosition(), ticketLevel, false,
                    maxPreloadChunks);
        }
    }

    private static void add(Map<Key, Entry> entries, String dimension, long packed, int ticketLevel,
                            boolean forced, int maxPreloadChunks) {
        Key key = new Key(dimension, packed);
        Entry previous = entries.get(key);
        if (previous == null) {
            if (entries.size() >= maxPreloadChunks) {
                throw new IllegalArgumentException("Chunk recovery preload exceeds the configured "
                        + "limit of " + maxPreloadChunks);
            }
            entries.put(key, new Entry(dimension, packed, ticketLevel, forced));
        } else {
            entries.put(key, new Entry(dimension, packed,
                    Math.min(previous.ticketLevel(), ticketLevel), previous.forced() || forced));
        }
    }

    private static int countNotSelected(Set<Key> source, Map<Key, Entry> selected) {
        int count = 0;
        for (Key key : source) if (!selected.containsKey(key)) count++;
        return count;
    }

    public enum PreloadMode {
        /** Forced, transient-state and exact player/FakePlayer/Block Event anchor chunks. */
        CRITICAL,
        /** Forced chunks, anchors and the checkpoint's entity-ticking set. */
        ENTITY_TICKING,
        /** Every loaded chunk of the capsule at its observed level: entity, block or FULL. */
        ALL;

        public static PreloadMode parse(String value) {
            for (PreloadMode mode : values()) {
                if (mode.name().equalsIgnoreCase(value)) return mode;
            }
            throw new IllegalArgumentException("Unknown chunk preload mode: " + value);
        }
    }

    public record Anchor(String dimension, long packedPosition) {
    }

    public record Entry(String dimension, long packedPosition, int ticketLevel, boolean forced) {
    }

    public record Selection(List<Entry> entries, int deferredLoaded, int deferredEntityTicking,
                            int totalLoaded, int totalEntityTicking, int totalForced,
                            boolean limitExceeded) {
        public Selection {
            entries = List.copyOf(entries);
        }

        public int requiredCount() { return entries.size(); }
    }

    private record Key(String dimension, long packedPosition) {
    }
}
