package com.atsukimc.anticrash.runtime;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/** Semantic position/type/order representation of World block-entity ticker lists. */
public final class BlockEntityTickerSnapshot {
    public static final String CURRENT = "CURRENT";
    public static final String PENDING = "PENDING";

    public int formatVersion = 1;
    public String dimension;
    public String queue;
    public int x;
    public int y;
    public int z;
    public String type;
    public String name;
    public int order;

    public BlockPos position() {
        return new BlockPos(x, y, z);
    }

    public String key() {
        return queue + "|" + x + ',' + y + ',' + z + "|" + type + "|" + name;
    }

    public String canonical() {
        return order + "|" + dimension + "|" + key();
    }

    public static String keyFor(ServerLevel world, String queue, TickingBlockEntity ticker) {
        if (ticker == null) return null;
        BlockPos position = ticker.getPos();
        return queue + "|" + position.getX() + ',' + position.getY() + ',' + position.getZ()
                + "|" + typeId(loadedBlockEntity(world, position), null) + "|" + ticker.getType();
    }

    /**
     * True for a ticker Vanilla will drop without running it.
     *
     * <p>Removing a block entity does not take its ticker out of the World list: WorldChunk swaps
     * the wrapped invoker for a shared empty one (position 0,0,0, name {@code <null>}), and
     * World#tickBlockEntities discards it on the next pass. Two removals in the same tick
     * therefore left two identical rows, which the capsule validator rejected as a duplicate and
     * failed the whole checkpoint. A removed ticker never runs again, so it carries no order.</p>
     */
    public static boolean removed(TickingBlockEntity ticker) {
        return ticker == null || ticker.isRemoved();
    }

    public static List<BlockEntityTickerSnapshot> capture(ServerLevel world) {
        List<BlockEntityTickerSnapshot> result = new ArrayList<>();
        com.atsukimc.anticrash.mixin.WorldAccessorMixin accessor =
                (com.atsukimc.anticrash.mixin.WorldAccessorMixin) world;
        Map<BlockEntityType<?>, String> typeIds = new IdentityHashMap<>();
        captureList(result, world, CURRENT, accessor.anticrash$getBlockEntityTickers(), typeIds);
        captureList(result, world, PENDING, accessor.anticrash$getPendingBlockEntityTickers(), typeIds);
        return result;
    }

    private static void captureList(List<BlockEntityTickerSnapshot> output, ServerLevel world,
                                    String queue, List<TickingBlockEntity> tickers,
                                    Map<BlockEntityType<?>, String> typeIds) {
        String dimension = world.dimension().identifier().toString();
        int order = 0;
        for (TickingBlockEntity ticker : tickers) {
            if (removed(ticker)) continue;
            BlockEntityTickerSnapshot snapshot = new BlockEntityTickerSnapshot();
            BlockPos position = ticker.getPos();
            snapshot.dimension = dimension;
            snapshot.queue = queue;
            snapshot.x = position.getX();
            snapshot.y = position.getY();
            snapshot.z = position.getZ();
            snapshot.name = ticker.getType();
            snapshot.type = typeId(loadedBlockEntity(world, position), typeIds);
            // Ordering is local to each Vanilla list. PENDING is copied into CURRENT by
            // World#tickBlockEntities at the next boundary; sharing one global counter would make
            // a valid pending list look offset even though its relative order is unchanged.
            snapshot.order = order++;
            output.add(snapshot);
        }
    }

    /**
     * Reads the block entity already stored in a loaded chunk.
     *
     * <p>World#getBlockEntity is not a read: it asks for the chunk with loading enabled and uses
     * {@code CreationType.IMMEDIATE}, which instantiates a missing block entity or one still held
     * as pending NBT. A capture that runs inside the checkpoint barrier must not mutate the world,
     * and the plain map lookup is also several times cheaper for thousands of tickers.</p>
     */
    public static BlockEntity loadedBlockEntity(ServerLevel world, BlockPos position) {
        LevelChunk chunk = world.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
        return chunk == null ? null : chunk.getBlockEntities().get(position);
    }

    private static String typeId(BlockEntity blockEntity, Map<BlockEntityType<?>, String> cache) {
        if (blockEntity == null) return "<unknown>";
        BlockEntityType<?> type = blockEntity.getType();
        if (cache != null) {
            String cached = cache.get(type);
            if (cached != null) return cached;
        }
        Identifier id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
        String value = id == null ? "<unknown>" : id.toString();
        if (cache != null) cache.put(type, value);
        return value;
    }
}
