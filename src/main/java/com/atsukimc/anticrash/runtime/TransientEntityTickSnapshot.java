package com.atsukimc.anticrash.runtime;

import com.atsukimc.anticrash.mixin.ServerWorldAccessorMixin;
import com.atsukimc.anticrash.util.VersionIds;

import net.minecraft.world.entity.Entity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.PrimedTnt;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistent identity sequence for transient physics entities that actually belong to the
 * entity-ticking domain at the checkpoint boundary.
 *
 * <p>{@code ServerLevel.entityList} also contains entities in loaded-only chunks. Those entities
 * are deliberately dormant in 1.18.2 and must not be promoted to active physics merely because a
 * recovery scan happened to see them. The same {@link ServerLevel#shouldTickEntity(BlockPos)}
 * predicate used by the server tick is therefore applied while capturing.
 */

/**
 * Persistent identity sequence for transient physics entities that actually belong to the
 * entity-ticking domain at the checkpoint boundary.
 *
 * <p>{@code ServerLevel.entityList} also contains entities in loaded-only chunks. Those entities
 * are deliberately dormant in 1.18.2 and must not be promoted to active physics merely because a
 * recovery scan happened to see them. The same {@link ServerLevel#shouldTickEntityAt(BlockPos)}
 * predicate used by the server tick is therefore applied while capturing.
 */

/**
 * Persistent identity sequence for transient physics entities that actually belong to the
 * entity-ticking domain at the checkpoint boundary.
 *
 * <p>{@code ServerLevel.entityList} also contains entities in loaded-only chunks. Those entities
 * are deliberately dormant in 1.18.2 and must not be promoted to active physics merely because a
 * recovery scan happened to see them. The same {@link
 * ServerLevel#isPositionEntityTicking(BlockPos)} predicate used by the server tick is therefore
 * applied while capturing.
 */
public final class TransientEntityTickSnapshot {
    public static final String TNT = "TNT";
    public static final String FALLING_BLOCK = "FALLING_BLOCK";

    public String dimension;
    public String uuid;
    public String kind;
    public int chunkX;
    public int chunkZ;

    public static List<TransientEntityTickSnapshot> capture(ServerLevel world) {
        List<TransientEntityTickSnapshot> result = new ArrayList<>();

        String dimension = VersionIds.dimension(world);

        ((ServerWorldAccessorMixin) world)
                .anticrash$getEntityList()
                .forEach(entity -> {
                    String kind = kind(entity);

                    if (kind == null
                            || entity.isRemoved()
                            || !world.isPositionEntityTicking(entity.blockPosition())) return;
                    TransientEntityTickSnapshot snapshot = new TransientEntityTickSnapshot();
                    snapshot.dimension = dimension;

                    snapshot.uuid = entity.getStringUUID();

                    snapshot.kind = kind;

                    //? if (>=1.18 && <=1.21.11) {
                    /*snapshot.chunkX = entity.chunkPosition().x;
                    *///?}
                    //? if (>=26.1 && <=26.3) {
                    snapshot.chunkX = entity.chunkPosition().x();
                    //?}

                    //? if (>=1.18 && <=1.21.11) {
                    /*snapshot.chunkZ = entity.chunkPosition().z;
                    *///?}
                    //? if (>=26.1 && <=26.3) {
                    snapshot.chunkZ = entity.chunkPosition().z();
                    //?}

                    result.add(snapshot);
                });
        return result;
    }

    public static String kind(Entity entity) {

        if (entity instanceof PrimedTnt) return TNT;
        if (entity instanceof FallingBlockEntity) return FALLING_BLOCK;
        return null;
    }
}
