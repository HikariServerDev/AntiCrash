package com.atsukimc.anticrash.runtime;

//? if (>=1.19.3 && <=1.21.11) {
/*import net.minecraft.registry.Registries;
*///?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.core.registries.BuiltInRegistries;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.server.world.ServerWorld;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.resources.Identifier;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.util.Identifier;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.server.level.ServerLevel;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.util.math.ChunkPos;
//?}

//? if (>=1.18 && <=1.19.2) {
import net.minecraft.util.registry.Registry;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.ChunkPos;
*///?}

import java.util.ArrayList;
import java.util.List;

/**
 * Safe, observational representation of the ServerWorld EntityList iteration order.
 *
 * <p>EntityList is actively used by ServerWorld#tick. Its internal map is not replaced during
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
     * EntityList can retain a removed entry until the owning tick removes it. Keep its count/order.
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

    //? if (>=1.18 && <=1.21.11) {
    public static List<EntityRuntimeSnapshot> capture(ServerWorld world)
                //?}
                //? if (>=26.1 && <=26.3) {
                /*public static List<EntityRuntimeSnapshot> capture(ServerLevel world)
*///?}
            {
        List<EntityRuntimeSnapshot> result = new ArrayList<>();

        //? if (>=1.18 && <=1.21.11) {
        String dimension = world.getRegistryKey().getValue().toString();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*String dimension = world.dimension().identifier().toString();
*///?}

        ((com.atsukimc.anticrash.mixin.ServerWorldAccessorMixin) world)
                .anticrash$getEntityList()
                .forEach(entity -> {
                    if (entity == null) return;
                    EntityRuntimeSnapshot snapshot = new EntityRuntimeSnapshot();
                    snapshot.dimension = dimension;

                    //? if (>=1.18 && <=1.21.11) {
                    snapshot.uuid = entity.getUuidAsString();
                    //?}
                    //? if (>=26.1 && <=26.3) {
                    /*snapshot.uuid = entity.getStringUUID();
*///?}

                    //? if (>=1.18 && <=1.19.2) {
                    Identifier id = Registry.ENTITY_TYPE.getId(entity.getType());
                    //?}
                    //? if (>=1.19.3 && <=1.21.11) {
                    /*Identifier id = Registries.ENTITY_TYPE.getId(entity.getType());
*///?}
                    //? if (>=26.1 && <=26.3) {
                    /*Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
*///?}

                    snapshot.type = id == null ? entity.getType().toString() : id.toString();
                    snapshot.networkEntityId = entity.getId();

                    //? if (>=1.18 && <=1.21.11) {
                    ChunkPos chunk = entity.getChunkPos();
                    //?}
                    //? if (>=26.1 && <=26.3) {
                    /*ChunkPos chunk = entity.chunkPosition();
*///?}

                    //? if (>=1.18 && <=1.21.11) {
                    snapshot.chunkX = chunk.x;
                    //?}
                    //? if (>=26.1 && <=26.3) {
                    /*snapshot.chunkX = chunk.x();
*///?}

                    //? if (>=1.18 && <=1.21.11) {
                    snapshot.chunkZ = chunk.z;
                    //?}
                    //? if (>=26.1 && <=26.3) {
                    /*snapshot.chunkZ = chunk.z();
*///?}

                    snapshot.order = result.size();
                    snapshot.removed = entity.isRemoved();
                    result.add(snapshot);
                });
        return result;
    }
}
