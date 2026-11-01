package com.atsukimc.anticrash.runtime;

import com.atsukimc.anticrash.mixin.BrewingStandBlockEntityTimerAccessor;
import com.atsukimc.anticrash.mixin.EndGatewayBlockEntityAccessorMixin;
import com.atsukimc.anticrash.mixin.EntityRuntimeAccessorMixin;
import com.atsukimc.anticrash.mixin.LivingEntityAccessorMixin;
import com.atsukimc.anticrash.mixin.ServerWorldAccessorMixin;
import com.atsukimc.anticrash.mixin.WorldAccessorMixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

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
    public static final String NATIVE_TICK = "VANILLA_TICK_RUNTIME";

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
                    ENTITY_ID,
                    NATIVE_TICK);

    private RuntimeExtensionSnapshot() {}

    public static Map<String, List<String>> capture(
            MinecraftServer server, RuntimeCapsule capsule) {
        Map<String, List<String>> lines = new LinkedHashMap<>();
        for (String category : CATEGORIES) lines.put(category, new ArrayList<>());
        lines.get(NATIVE_TICK).addAll(NativeTickState.capture(server).lines());
        for (ServerLevel world : server.getAllLevels()) {
            String dimension = world.dimension().identifier().toString();
            // Experimental trial-spawner logic has non-codec detector/force-activation state.
            // Do not claim exact runtime support merely because its ordinary data has a codec.
            var holders =
                    ((com.atsukimc.anticrash.mixin.ThreadedAnvilChunkStorageAccessorMixin)
                                    world.getChunkSource().chunkMap)
                            .anticrash$getCurrentChunkHolders();
            for (var holder : holders.values()) {
                LevelChunk loaded = VersionChunkView.worldChunk(holder);
                if (loaded == null) continue;
                for (BlockEntity entity : loaded.getBlockEntities().values()) {
                    if ((entity instanceof net.minecraft.world.level.block.entity.TestBlockEntity
                                    || entity
                                            instanceof
                                            net.minecraft.world.level.block.entity
                                                    .TestInstanceBlockEntity)
                            && !capsule.unsupportedRuntimeState.contains("GAMETEST_RUNTIME"))
                        capsule.unsupportedRuntimeState.add("GAMETEST_RUNTIME");
                    if (entity
                                    instanceof
                                    net.minecraft.world.level.block.entity.CreakingHeartBlockEntity
                            && !capsule.unsupportedRuntimeState.contains("CREAKING_HEART_RUNTIME"))
                        capsule.unsupportedRuntimeState.add("CREAKING_HEART_RUNTIME");
                    if (entity
                                    instanceof
                                    net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity
                            && !capsule.unsupportedRuntimeState.contains("TRIAL_SPAWNER_RUNTIME")) {
                        capsule.unsupportedRuntimeState.add("TRIAL_SPAWNER_RUNTIME");
                    }
                    if (entity
                                    instanceof
                                    net.minecraft.world.level.block.entity.vault.VaultBlockEntity
                            && !capsule.unsupportedRuntimeState.contains("VAULT_RUNTIME")) {
                        capsule.unsupportedRuntimeState.add("VAULT_RUNTIME");
                    }
                }
            }
            WorldAccessorMixin accessor = (WorldAccessorMixin) world;
            for (List<TickingBlockEntity> tickers :
                    List.of(
                            accessor.anticrash$getBlockEntityTickers(),
                            accessor.anticrash$getPendingBlockEntityTickers())) {
                for (TickingBlockEntity ticker : tickers) {
                    if (BlockEntityTickerSnapshot.removed(ticker)) continue;
                    BlockPos pos = ticker.getPos();
                    LevelChunk chunk =
                            world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
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
                                                            : BuiltInRegistries.ITEM.getKey(
                                                                    stand
                                                                            .anticrash$getItemBrewing())));
                        }
                    } else if (blockEntity instanceof TheEndGatewayBlockEntity) {
                        int cooldown =
                                ((EndGatewayBlockEntityAccessorMixin) blockEntity)
                                        .anticrash$getTeleportCooldown();
                        if (cooldown > 0) lines.get(END_GATEWAY).add(where + "|" + cooldown);
                    }
                    String sculk = SculkRuntimeFingerprint.capture(blockEntity);
                    if (sculk != null) lines.get(SCULK).add(where + "|" + sculk);
                }
            }
            ((ServerWorldAccessorMixin) world)
                    .anticrash$getEntityList()
                    .forEach(entity -> {
                        if (entity == null
                                || entity.isRemoved()
                                || entity instanceof net.minecraft.world.entity.player.Player)
                            return;
                        EntityRuntimeAccessorMixin runtime = (EntityRuntimeAccessorMixin) entity;
                        String id = dimension + "|" + entity.getStringUUID();
                        if (entity.portalProcess != null) {
                            lines.get(PORTAL)
                                    .add(id + "|" + PortalRuntime.canonical(entity.portalProcess));
                            if (PortalRuntime.type(entity.portalProcess) == null
                                    && !capsule.unsupportedRuntimeState.contains(
                                            "PORTAL_IMPLEMENTATION"))
                                capsule.unsupportedRuntimeState.add("PORTAL_IMPLEMENTATION");
                        }
                        StringBuilder line = new StringBuilder(id);
                        line.append("|fluidTrackers=")
                                .append(FluidInteractionSnapshot.capture(entity));
                        if (entity
                                        instanceof
                                        net.minecraft.world.entity.vehicle.minecart.MinecartTNT
                                && ((com.atsukimc.anticrash.mixin.TntMinecartRuntimeAccessorMixin)
                                                        entity)
                                                .anticrash$getDamageSource()
                                        != null
                                && !capsule.unsupportedRuntimeState.contains(
                                        "TNT_MINECART_DAMAGE_SOURCE"))
                            capsule.unsupportedRuntimeState.add("TNT_MINECART_DAMAGE_SOURCE");
                        if (entity
                                        instanceof
                                        net.minecraft.world.entity.vehicle.minecart.AbstractMinecart
                                                cart
                                && cart.getBehavior()
                                        instanceof
                                        net.minecraft.world.entity.vehicle.minecart
                                                .NewMinecartBehavior
                                && !capsule.unsupportedRuntimeState.contains(
                                        "EXPERIMENTAL_MINECART_RUNTIME"))
                            capsule.unsupportedRuntimeState.add("EXPERIMENTAL_MINECART_RUNTIME");
                        if (entity
                                        instanceof
                                        net.minecraft.world.entity.vehicle.minecart.MinecartHopper
                                && !capsule.unsupportedRuntimeState.contains(
                                        "HOPPER_MINECART_RUNTIME"))
                            capsule.unsupportedRuntimeState.add("HOPPER_MINECART_RUNTIME");
                        if (entity instanceof net.minecraft.world.entity.vehicle.boat.AbstractBoat
                                && !capsule.unsupportedRuntimeState.contains(
                                        "BOAT_TURN_UNDERWATER_RUNTIME"))
                            capsule.unsupportedRuntimeState.add("BOAT_TURN_UNDERWATER_RUNTIME");
                        int collisionChecks =
                                ((com.atsukimc.anticrash.mixin.EntityCollisionQueueAccessorMixin)
                                                entity)
                                        .anticrash$collisionChecks()
                                        .size();
                        if (collisionChecks > 0) {
                            line.append("|collisionChecks=").append(collisionChecks);
                            if (!capsule.unsupportedRuntimeState.contains("ENTITY_COLLISION_QUEUE"))
                                capsule.unsupportedRuntimeState.add("ENTITY_COLLISION_QUEUE");
                        }
                        if (entity instanceof net.minecraft.world.entity.item.PrimedTnt)
                            line.append("|teleportedTnt=")
                                    .append(
                                            ((com.atsukimc.anticrash.mixin.TntRuntimeAccessorMixin)
                                                            entity)
                                                    .anticrash$teleported());
                        if (entity
                                instanceof
                                net.minecraft.world.entity.item.FallingBlockEntity falling)
                            line.append("|shouldDupe=")
                                    .append(falling.forceTickAfterTeleportToDuplicate);
                        if (entity instanceof net.minecraft.world.entity.projectile.Projectile) {
                            line.append("|checkedOwner=")
                                    .append(
                                            ((com.atsukimc.anticrash.mixin
                                                                    .ProjectileRuntimeAccessorMixin)
                                                            entity)
                                                    .anticrash$checkedForLeftOwner());
                            var deflected =
                                    ((com.atsukimc.anticrash.mixin.ProjectileRuntimeAccessorMixin)
                                                    entity)
                                            .anticrash$lastDeflectedEntity();
                            if (deflected != null) {
                                line.append("|lastDeflected=").append(deflected.getUUID());
                                if (!capsule.unsupportedRuntimeState.contains(
                                        "PROJECTILE_DEFLECTION_RUNTIME"))
                                    capsule.unsupportedRuntimeState.add(
                                            "PROJECTILE_DEFLECTION_RUNTIME");
                            }
                        }
                        line.append("|groundCollision=")
                                .append(entity.verticalCollisionBelow)
                                .append("|motion=")
                                .append(EntityMotionFrame.capture(entity))
                                .append("|support=")
                                .append(entity.mainSupportingBlockPos)
                                .append("|forceSupport=")
                                .append(runtime.anticrash$forceSupportUpdate());
                        line.append("|ticked=").append(!runtime.anticrash$isFirstUpdate());
                        if (!runtime.anticrash$isFirstUpdate())
                            line.append("|water=").append(runtime.anticrash$isTouchingWater());
                        if (runtime.anticrash$getRidingCooldown() > 0)
                            line.append("|riding=").append(runtime.anticrash$getRidingCooldown());
                        if (entity.invulnerableTime > 0) {
                            line.append("|regen=").append(entity.invulnerableTime);
                            if (entity instanceof LivingEntity living) {
                                line.append("|lastDamage=")
                                        .append(
                                                ((LivingEntityAccessorMixin) living)
                                                        .anticrash$getLastDamageTaken());
                            }
                        }
                        if (entity instanceof LivingEntity)
                            line.append("|kineticTime=")
                                    .append(
                                            ((com.atsukimc.anticrash.mixin
                                                                    .LivingEntityAccessorMixin)
                                                            entity)
                                                    .anticrash$lastKineticAttackTime());
                        if (entity instanceof ItemEntity || entity instanceof ExperienceOrb)
                            line.append("|age=").append(entity.tickCount);
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
                                            + net.minecraft.world.level.ChunkPos.getX(packed)
                                            + ","
                                            + net.minecraft.world.level.ChunkPos.getZ(packed)
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
