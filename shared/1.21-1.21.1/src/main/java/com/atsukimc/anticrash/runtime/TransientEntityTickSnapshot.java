package com.atsukimc.anticrash.runtime;

import com.atsukimc.anticrash.mixin.ServerWorldAccessorMixin;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistent identity sequence for transient physics entities that actually belong to the
 * entity-ticking domain at the checkpoint boundary.
 *
 * <p>{@code ServerWorld.entityList} also contains entities in loaded-only chunks. Those entities
 * are deliberately dormant in 1.18.2 and must not be promoted to active physics merely because a
 * recovery scan happened to see them. The same {@link ServerWorld#shouldTickEntity(BlockPos)}
 * predicate used by the server tick is therefore applied while capturing.</p>
 */
public final class TransientEntityTickSnapshot {
    public static final String TNT = "TNT";
    public static final String FALLING_BLOCK = "FALLING_BLOCK";

    public String dimension;
    public String uuid;
    public String kind;
    public int chunkX;
    public int chunkZ;

    public static List<TransientEntityTickSnapshot> capture(ServerWorld world) {
        List<TransientEntityTickSnapshot> result = new ArrayList<>();
        String dimension = world.getRegistryKey().getValue().toString();
        ((ServerWorldAccessorMixin) world).anticrash$getEntityList().forEach(entity -> {
            String kind = kind(entity);
            if (kind == null || entity.isRemoved()
                    || !world.shouldTickEntity(entity.getBlockPos())) return;
            TransientEntityTickSnapshot snapshot = new TransientEntityTickSnapshot();
            snapshot.dimension = dimension;
            snapshot.uuid = entity.getUuidAsString();
            snapshot.kind = kind;
            snapshot.chunkX = entity.getChunkPos().x;
            snapshot.chunkZ = entity.getChunkPos().z;
            result.add(snapshot);
        });
        return result;
    }

    public static String kind(Entity entity) {
        if (entity instanceof TntEntity) return TNT;
        if (entity instanceof FallingBlockEntity) return FALLING_BLOCK;
        return null;
    }
}
