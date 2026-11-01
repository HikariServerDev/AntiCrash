package com.atsukimc.anticrash.runtime;

import com.atsukimc.anticrash.mixin.BrewingStandBlockEntityTimerAccessor;
import com.atsukimc.anticrash.mixin.EndGatewayBlockEntityAccessorMixin;
import com.atsukimc.anticrash.mixin.EntityRuntimeAccessorMixin;
import com.atsukimc.anticrash.mixin.LivingEntityAccessorMixin;
import com.atsukimc.anticrash.mixin.SculkSensorListenerAccessorMixin;
import com.atsukimc.anticrash.mixin.ServerWorldAccessorMixin;
import com.atsukimc.anticrash.mixin.WorldAccessorMixin;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BrewingStandBlockEntity;
import net.minecraft.block.entity.EndGatewayBlockEntity;
import net.minecraft.block.entity.SculkSensorBlockEntity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.chunk.BlockEntityTickInvoker;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The state AntiCrash adds to chunk/entity data and the Runtime Capsule beyond what Vanilla saves,
 * as canonical lines per category. Taken at the checkpoint cut and again after a restore, right
 * before the first tick; equal lines mean that state really came back. Only a digest and a count
 * per category are stored, so the capsule does not grow with the number of entities.
 */
public final class RuntimeExtensionSnapshot {
    public static final String BREWING = "BREWING_RUNTIME";
    public static final String END_GATEWAY = "END_GATEWAY_RUNTIME";
    public static final String SCULK = "SCULK_RUNTIME";
    public static final String PORTAL = "PORTAL_ENTITY_RUNTIME";
    public static final String ENTITY_TRANSIENT = "ENTITY_TRANSIENT_RUNTIME";
    public static final String TICKETS = "SHORT_LIVED_TICKETS";
    public static final String DIMENSION = "DIMENSION_RUNTIME";
    public static final String WEATHER = "WEATHER_RUNTIME";
    public static final String CHUNK = "CHUNK_RUNTIME";
    public static final String ENTITY_ID = "ENTITY_ID_PHASE";

    /** Every id-dependent rule AntiCrash keeps exact uses the id modulo 4 or modulo 40. */
    public static final int ENTITY_ID_MODULUS = 40;

    public static final List<String> CATEGORIES =
            List.of(
                    BREWING,
                    END_GATEWAY,
                    SCULK,
                    PORTAL,
                    ENTITY_TRANSIENT,
                    TICKETS,
                    DIMENSION,
                    WEATHER,
                    CHUNK,
                    ENTITY_ID);

    private RuntimeExtensionSnapshot() {}

    public static Map<String, List<String>> capture(
            MinecraftServer server, RuntimeCapsule capsule) {
        Map<String, List<String>> lines = new LinkedHashMap<>();
        for (String category : CATEGORIES) lines.put(category, new ArrayList<>());
        for (ServerWorld world : server.getWorlds()) {
            String dimension = world.getRegistryKey().getValue().toString();
            WorldAccessorMixin accessor = (WorldAccessorMixin) world;
            for (List<BlockEntityTickInvoker> tickers :
                    List.of(
                            accessor.anticrash$getBlockEntityTickers(),
                            accessor.anticrash$getPendingBlockEntityTickers())) {
                for (BlockEntityTickInvoker ticker : tickers) {
                    if (BlockEntityTickerSnapshot.removed(ticker)) continue;
                    BlockPos pos = ticker.getPos();
                    WorldChunk chunk =
                            world.getChunkManager().getWorldChunk(pos.getX() >> 4, pos.getZ() >> 4);
                    BlockEntity blockEntity =
                            chunk == null ? null : chunk.getBlockEntities().get(pos);
                    String where =
                            dimension + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
                    if (blockEntity instanceof BrewingStandBlockEntity) {
                        BrewingStandBlockEntityTimerAccessor stand =
                                (BrewingStandBlockEntityTimerAccessor) blockEntity;
                        if (stand.anticrash$getBrewTime() > 0) {
                            lines.get(BREWING)
                                    .add(
                                            where
                                                    + "|"
                                                    + stand.anticrash$getBrewTime()
                                                    + "|"
                                                    + (stand.anticrash$getItemBrewing() == null
                                                            ? "null"
                                                            : Registry.ITEM.getId(
                                                                    stand
                                                                            .anticrash$getItemBrewing())));
                        }
                    } else if (blockEntity instanceof EndGatewayBlockEntity) {
                        int cooldown =
                                ((EndGatewayBlockEntityAccessorMixin) blockEntity)
                                        .anticrash$getTeleportCooldown();
                        if (cooldown > 0) lines.get(END_GATEWAY).add(where + "|" + cooldown);
                    } else if (blockEntity instanceof SculkSensorBlockEntity sensor) {
                        SculkSensorListenerAccessorMixin listener =
                                (SculkSensorListenerAccessorMixin) sensor.getEventListener();
                        if (listener.anticrash$getEvent() != null
                                && listener.anticrash$getEvent().isPresent()) {
                            lines.get(SCULK)
                                    .add(
                                            where
                                                    + "|"
                                                    + Registry.GAME_EVENT.getId(
                                                            listener.anticrash$getEvent().get())
                                                    + "|"
                                                    + listener.anticrash$getDistance()
                                                    + "|"
                                                    + listener.anticrash$getDelay());
                        }
                    }
                }
            }
            ((ServerWorldAccessorMixin) world)
                    .anticrash$getEntityList()
                    .forEach(entity -> {
                        if (entity == null
                                || entity.isRemoved()
                                || entity instanceof net.minecraft.entity.player.PlayerEntity)
                            return;
                        EntityRuntimeAccessorMixin runtime = (EntityRuntimeAccessorMixin) entity;
                        String id = dimension + "|" + entity.getUuidAsString();
                        if (runtime.anticrash$isInNetherPortal()
                                || runtime.anticrash$getNetherPortalTime() > 0) {
                            lines.get(PORTAL)
                                    .add(
                                            id
                                                    + "|"
                                                    + runtime.anticrash$isInNetherPortal()
                                                    + "|"
                                                    + runtime.anticrash$getNetherPortalTime());
                        }
                        StringBuilder line = new StringBuilder(id);
                        line.append("|ticked=").append(!runtime.anticrash$isFirstUpdate());
                        if (!runtime.anticrash$isFirstUpdate())
                            line.append("|water=").append(runtime.anticrash$isTouchingWater());
                        if (runtime.anticrash$getRidingCooldown() > 0)
                            line.append("|riding=").append(runtime.anticrash$getRidingCooldown());
                        if (entity.timeUntilRegen > 0) {
                            line.append("|regen=").append(entity.timeUntilRegen);
                            if (entity instanceof LivingEntity living) {
                                line.append("|lastDamage=")
                                        .append(
                                                ((LivingEntityAccessorMixin) living)
                                                        .anticrash$getLastDamageTaken());
                            }
                        }
                        if (entity instanceof ItemEntity || entity instanceof ExperienceOrbEntity)
                            line.append("|age=").append(entity.age);
                        if (entity instanceof ItemPhaseHolder item)
                            line.append("|phase=").append(item.anticrash$idPhase());
                        lines.get(ENTITY_TRANSIENT).add(line.toString());
                    });
        }
        if (capsule.ticketLedger != null) {
            for (TicketLedgerSnapshot.Entry ticket : capsule.ticketLedger) {
                if (ticket.remainingTicks == null) continue;
                // A post-teleport ticket's argument is a network entity id, which a restart
                // renumbers.
                String argument = "post_teleport".equals(ticket.type) ? "-" : ticket.argument;
                lines.get(TICKETS)
                        .add(
                                ticket.dimension
                                        + "|"
                                        + ticket.x
                                        + ","
                                        + ticket.z
                                        + "|"
                                        + ticket.type
                                        + "|"
                                        + ticket.level
                                        + "|"
                                        + argument
                                        + "|"
                                        + ticket.remainingTicks);
            }
        }
        for (RuntimeCapsule.WorldRuntime world : capsule.worlds) {
            lines.get(DIMENSION).add(world.dimension + "|idle=" + world.idleTimeout);
            lines.get(WEATHER)
                    .add(
                            world.dimension
                                    + "|rain="
                                    + world.rainGradient
                                    + "|thunder="
                                    + world.thunderGradient);
        }
        if (capsule.entityIdCounter != null) {
            lines.get(ENTITY_ID)
                    .add("next=" + Math.floorMod(capsule.entityIdCounter, ENTITY_ID_MODULUS));
        }
        if (capsule.inhabitedTimes != null) {
            for (RuntimeCapsule.InhabitedTimes inhabited : capsule.inhabitedTimes) {
                for (int index = 0; index < inhabited.chunks.size(); index++) {
                    lines.get(CHUNK)
                            .add(
                                    inhabited.dimension
                                            + "|"
                                            + inhabited.chunks.get(index)
                                            + "|"
                                            + inhabited.times.get(index));
                }
            }
        }
        for (List<String> category : lines.values()) Collections.sort(category);
        return lines;
    }

    /**
     * Result of comparing one category before the world resumes. A category the checkpoint did not
     * record (a capsule written before the category existed) is never reported as a match.
     */
    public static String verdict(String want, String have, Integer wantCount, Integer haveCount) {
        if (want == null) return "MISSING_FROM_OLD_FORMAT";
        String counts = wantCount == null ? "" : " " + haveCount + "/" + wantCount;
        return (want.equals(have) ? "MATCH" : "MISMATCH") + counts;
    }

    /**
     * The id counter to continue with after a restore: the smallest value not below {@code current}
     * (ids already handed out in the new JVM stay unique) with the checkpoint's remainder.
     */
    public static int nextEntityId(int current, int saved) {
        return current + Math.floorMod(saved - current, ENTITY_ID_MODULUS);
    }

    /**
     * The canonical lines of one verified category as far as the capsule still has them: every base
     * category, and the extension categories whose lines the checkpoint kept. Null when only the
     * digest is known.
     */
    public static List<String> diagnosticLines(RuntimeCapsule capsule, String category) {
        if (capsule == null || category == null) return null;
        switch (category) {
            case EquivalenceCategory.CHUNK_DOMAIN:
                return RuntimeFingerprint.chunkDomainLines(capsule.chunks);
            case EquivalenceCategory.SCHEDULED_TICKS:
                return RuntimeFingerprint.scheduledTickLines(capsule.scheduledTicks);
            case EquivalenceCategory.BLOCK_EVENTS:
                return RuntimeFingerprint.blockEventLines(capsule.blockEvents);
            case EquivalenceCategory.MOVING_PISTONS:
                return RuntimeFingerprint.movingPistonLines(capsule.movingPistonTickOrder);
            case EquivalenceCategory.BLOCK_ENTITY_TICKERS:
                return RuntimeFingerprint.tickerLines(capsule.blockEntityTickerOrder);
            case EquivalenceCategory.REDSTONE_RUNTIME:
                return RuntimeFingerprint.burnoutLines(capsule.redstoneTorchBurnout);
            case EquivalenceCategory.WORLD_RUNTIME:
                return RuntimeFingerprint.worldLines(capsule.worlds);
            case CHUNK:
                {
                    if (capsule.inhabitedTimes == null) return null;
                    List<String> lines = new ArrayList<>();
                    for (RuntimeCapsule.InhabitedTimes inhabited : capsule.inhabitedTimes) {
                        for (int index = 0; index < inhabited.chunks.size(); index++) {
                            long packed = inhabited.chunks.get(index);
                            lines.add(
                                    inhabited.dimension
                                            + "|"
                                            + net.minecraft.util.math.ChunkPos.getPackedX(packed)
                                            + ","
                                            + net.minecraft.util.math.ChunkPos.getPackedZ(packed)
                                            + "|"
                                            + inhabited.times.get(index));
                        }
                    }
                    return lines;
                }
            default:
                return capsule.runtimeExtensionLines == null
                        ? null
                        : capsule.runtimeExtensionLines.get(category);
        }
    }
}
