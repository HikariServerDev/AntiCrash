package com.atsukimc.anticrash.snapshot.logical;

import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

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

        //? if (>=1.18 && <=1.21.8) {
        /*GLOBAL.record(
                "ENTITY_REMOVED",
                //? if (>=1.18 && <=1.19.4) {
                /^entity.getLevel().dimension().location().toString(),
                ^///?}
                //? if (>=1.20 && <=1.21.8) {
                /^entity.level().dimension().location().toString(),
                ^///?}
                entity.chunkPosition().toLong(),
                entity.getStringUUID(),
                reason);
        *///?}
        //? if (>=1.21.9 && <=1.21.11) {
        /*GLOBAL.record(
                        "ENTITY_REMOVED",
                        //? if (>=1.21.9 && <=1.21.10) {
                        /^entity.level().dimension().location().toString(),
                        ^///?}
                        //? if 1.21.11 {
                        /^entity.level().dimension().identifier().toString(),
                        ^///?}
                        entity.chunkPosition().toLong(),
                        entity.getStringUUID(),
                        reason);
*///?}
        //? if (>=26.1 && <=26.3) {
        GLOBAL.record(
                "ENTITY_REMOVED",
                entity.level().dimension().identifier().toString(),
                entity.chunkPosition().pack(),
                entity.getStringUUID(),
                reason);
        //?}

    }

    public static void recordMove(Entity entity, long oldChunk, long newChunk) {
        if (oldChunk == newChunk || !persisted(entity)) return;

        //? if (>=1.18 && <=1.21.8) {
        /*GLOBAL.record(
                "ENTITY_MOVED_FROM",
                //? if (>=1.18 && <=1.19.4) {
                /^entity.getLevel().dimension().location().toString(),
                ^///?}
                //? if (>=1.20 && <=1.21.8) {
                /^entity.level().dimension().location().toString(),
                ^///?}
                oldChunk,
                entity.getStringUUID(),
                Long.toString(newChunk));
        *///?}
        //? if (>=1.21.9 && <=1.21.11) {
        /*GLOBAL.record(
                        "ENTITY_MOVED_FROM",
                        //? if (>=1.21.9 && <=1.21.10) {
                        /^entity.level().dimension().location().toString(),
                        ^///?}
                        //? if 1.21.11 {
                        /^entity.level().dimension().identifier().toString(),
                        ^///?}
                        oldChunk,
                        entity.getStringUUID(),
                        Long.toString(newChunk));
*///?}
        //? if (>=26.1 && <=26.3) {
        GLOBAL.record(
                "ENTITY_MOVED_FROM",
                entity.level().dimension().identifier().toString(),
                oldChunk,
                entity.getStringUUID(),
                Long.toString(newChunk));
        //?}

    }

    /**
     * A PersistentEntitySectionManager unload is not a despawn: the entity may still be persisted to an entity
     * region even though it is no longer present in {@code iterateEntities()}. Without a final
     * immutable entity-region capture, inheriting the parent would be ambiguous, so the logical
     * path treats this boundary as a safe fallback condition.
     */
    public static void recordUnload(Entity entity) {
        if (!persisted(entity)) return;

        //? if (>=1.18 && <=1.21.8) {
        /*GLOBAL.record(
                "ENTITY_UNLOADED",
                //? if (>=1.18 && <=1.19.4) {
                /^entity.getLevel().dimension().location().toString(),
                ^///?}
                //? if (>=1.20 && <=1.21.8) {
                /^entity.level().dimension().location().toString(),
                ^///?}
                entity.chunkPosition().toLong(),
                entity.getStringUUID(),
                "server_entity_manager_unload");
        *///?}
        //? if (>=1.21.9 && <=1.21.11) {
        /*GLOBAL.record(
                        "ENTITY_UNLOADED",
                        //? if (>=1.21.9 && <=1.21.10) {
                        /^entity.level().dimension().location().toString(),
                        ^///?}
                        //? if 1.21.11 {
                        /^entity.level().dimension().identifier().toString(),
                        ^///?}
                        entity.chunkPosition().toLong(),
                        entity.getStringUUID(),
                        "server_entity_manager_unload");
*///?}
        //? if (>=26.1 && <=26.3) {
        GLOBAL.record(
                "ENTITY_UNLOADED",
                entity.level().dimension().identifier().toString(),
                entity.chunkPosition().pack(),
                entity.getStringUUID(),
                "server_entity_manager_unload");
        //?}

    }

    /**
     * Players, fishing bobbers and other non-saveable types never appear in an entity region, so
     * their movement is not evidence about region contents. Ignoring them also keeps ordinary
     * player movement from filling the bounded event list.
     */
    private static boolean persisted(Entity entity) {

        //? if (>=1.18 && <=1.21.8) {
        /*return LogicalTracking.enabled()
                && entity != null
                //? if (>=1.18 && <=1.19.4) {
                /^&& entity.getLevel() != null
                ^///?}
                //? if (>=1.20 && <=1.21.8) {
                /^&& entity.level() != null
                ^///?}
                //? if (>=1.18 && <=1.19.4) {
                /^&& !entity.getLevel().isClientSide
                ^///?}
                //? if (>=1.20 && <=1.21.8) {
                /^&& !entity.level().isClientSide
                ^///?}
                && entity.getType().canSerialize();
        *///?}
        //? if (>=1.21.9 && <=26.3) {
        return LogicalTracking.enabled()
                && entity != null
                && entity.level() != null
                && !entity.level().isClientSide()
                && entity.getType().canSerialize();
        //?}

    }

    private synchronized void record(
            String kind, String dimension, long chunk, String uuid, String detail) {
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

    public record Event(
            long sequence, String kind, String dimension, long chunk, String uuid, String detail) {}

    public record Capture(long sequence, List<Event> events, boolean overflow) {}
}
