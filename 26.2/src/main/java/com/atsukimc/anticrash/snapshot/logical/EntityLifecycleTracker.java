package com.atsukimc.anticrash.snapshot.logical;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.Entity;

/**
 * Small, bounded observer for entity lifecycle edges that a complete entity-region replacement
 * cannot infer from the current loaded set alone. It is intentionally not a universal entity WAL:
 * ordinary field changes are captured at the cut, while remove and cross-chunk edges become
 * explicit tombstone evidence.
 */
public final class EntityLifecycleTracker {
    private static final int MAX_EVENTS = 4096;
    private static final EntityLifecycleTracker GLOBAL = new EntityLifecycleTracker();

    private final List<Event> events = new ArrayList<>();
    private long nextSequence;
    private boolean overflow;

    public static EntityLifecycleTracker global() {
        return GLOBAL;
    }

    public static void recordRemoval(Entity entity, String reason) {
        if (!persisted(entity)) return;
        GLOBAL.record("ENTITY_REMOVED", entity.level().dimension().identifier().toString(),
                entity.chunkPosition().pack(), entity.getStringUUID(), reason);
    }

    public static void recordMove(Entity entity, long oldChunk, long newChunk) {
        if (oldChunk == newChunk || !persisted(entity)) return;
        GLOBAL.record("ENTITY_MOVED_FROM", entity.level().dimension().identifier().toString(),
                oldChunk, entity.getStringUUID(), Long.toString(newChunk));
    }

    /**
     * A ServerEntityManager unload is not a despawn: the entity may still be persisted to an
     * entity region even though it is no longer present in {@code iterateEntities()}.  Without a
     * final immutable entity-region capture, inheriting the parent would be ambiguous, so the
     * logical path treats this boundary as a safe fallback condition.
     */
    public static void recordUnload(Entity entity) {
        if (!persisted(entity)) return;
        GLOBAL.record("ENTITY_UNLOADED", entity.level().dimension().identifier().toString(),
                entity.chunkPosition().pack(), entity.getStringUUID(),
                "server_entity_manager_unload");
    }

    /**
     * Players, fishing bobbers and other non-saveable types never appear in an entity region, so
     * their movement is not evidence about region contents. Ignoring them also keeps ordinary
     * player movement from filling the bounded event list.
     */
    private static boolean persisted(Entity entity) {
        return LogicalTracking.enabled() && entity != null && entity.level() != null && !entity.level().isClientSide()
                && entity.getType().canSerialize();
    }

    private synchronized void record(String kind, String dimension, long chunk,
                                     String uuid, String detail) {
        nextSequence++;
        if (events.size() >= MAX_EVENTS) {
            overflow = true;
            return;
        }
        events.add(new Event(nextSequence, kind, dimension, chunk, uuid, detail));
    }

    public synchronized Capture snapshot() {
        return new Capture(nextSequence, List.copyOf(events), overflow);
    }

    public synchronized void clearThrough(long sequence) {
        events.removeIf(event -> event.sequence() <= sequence);
        if (events.isEmpty()) overflow = false;
    }

    public synchronized void clearAll() {
        events.clear();
        overflow = false;
    }

    public record Event(long sequence, String kind, String dimension, long chunk,
                        String uuid, String detail) {
    }

    public record Capture(long sequence, List<Event> events, boolean overflow) {
    }
}
