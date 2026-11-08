package com.atsukimc.anticrash.runtime;

import com.atsukimc.anticrash.util.VersionIds;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Safe, observational representation of the ServerLevel EntityTickList iteration order.
 *
 * <p>EntityTickList is actively used by ServerLevel#tick. Its internal map is not replaced during
 * recovery: Minecraft's own add/remove invariants remain authoritative. This snapshot is used to
 * detect an order change and stop STRICT recovery rather than silently reshuffling live entities.
 */
public final class EntityRuntimeSnapshot {
    public int formatVersion = 1;
    public String dimension;
    public String uuid;
    public String type;
    public int networkEntityId;
    public int chunkX;
    public int chunkZ;
    public int order;

    /**
     * EntityTickList can retain a removed entry until the owning tick removes it. Keep its count/order.
     */
    public boolean removed;

    public String canonical() {
        return canonicalAt(order);
    }

    /** Canonical form with a comparison-local order after optional entity filtering. */
    public String canonicalAt(int comparisonOrder) {
        return comparisonOrder
                + "|"
                + dimension
                + "|"
                + uuid
                + "|"
                + type
                + "|"
                + networkEntityId
                + "|"
                + chunkX
                + ','
                + chunkZ
                + "|removed="
                + removed;
    }

    public static List<EntityRuntimeSnapshot> capture(ServerLevel world) {
        List<EntityRuntimeSnapshot> result = new ArrayList<>();

        String dimension = VersionIds.dimension(world);

        ((com.atsukimc.anticrash.mixin.ServerWorldAccessorMixin) world)
                .anticrash$getEntityList()
                .forEach(entity -> {
                    if (entity == null) return;
                    EntityRuntimeSnapshot snapshot = new EntityRuntimeSnapshot();
                    snapshot.dimension = dimension;

                    snapshot.uuid = entity.getStringUUID();

                    String id = VersionIds.id(VersionIds.entityTypes(), entity.getType());

                    snapshot.type = id == null ? entity.getType().toString() : id.toString();
                    snapshot.networkEntityId = entity.getId();

                    ChunkPos chunk = entity.chunkPosition();

                    //? if (>=1.18 && <=1.21.11) {
                    /*snapshot.chunkX = chunk.x;
                    *///?}
                    //? if (>=26.1 && <=26.3) {
                    snapshot.chunkX = chunk.x();
                    //?}

                    //? if (>=1.18 && <=1.21.11) {
                    /*snapshot.chunkZ = chunk.z;
                    *///?}
                    //? if (>=26.1 && <=26.3) {
                    snapshot.chunkZ = chunk.z();
                    //?}

                    snapshot.order = result.size();
                    snapshot.removed = entity.isRemoved();
                    result.add(snapshot);
                });
        return result;
    }
}
