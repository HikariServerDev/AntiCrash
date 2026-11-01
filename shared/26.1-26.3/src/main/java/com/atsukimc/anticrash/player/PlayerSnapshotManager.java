package com.atsukimc.anticrash.player;

import com.atsukimc.anticrash.config.AntiCrashConfig;
import com.atsukimc.anticrash.integration.carpet.CarpetIntegration;
import com.atsukimc.anticrash.logging.AntiCrashLogger;
import com.atsukimc.anticrash.runtime.CheckpointPlayerSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.Vec3;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PlayerSnapshotManager {
    private final AntiCrashConfig config;
    private final AntiCrashLogger logger;
    private final Map<UUID, PlayerSnapshotData> snapshots = new HashMap<>();
    private final Set<UUID> restored = new HashSet<>();

    public PlayerSnapshotManager(AntiCrashConfig config, AntiCrashLogger logger) {
        this.config = config;
        this.logger = logger;
    }

    public List<PlayerSnapshotData> capture(MinecraftServer server) throws IOException {
        List<PlayerSnapshotData> result = new ArrayList<>();
        if (!config.playerSnapshot.enabled) return result;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (CarpetIntegration.isFakePlayer(player)) continue;
            try { result.add(captureOne(player)); }
            catch (Exception exception) {
                logger.importantKey("player.snapshot_failed", player.getName().getString(), exception.getMessage());
                if (exception instanceof IOException ioException) throw ioException;
                throw new IOException("Could not capture player " + player.getName().getString(), exception);
            }
        }
        return result;
    }

    /**
     * Captures only the stable identity needed by STRICT's optional checkpoint-player gate.
     * This deliberately does not depend on {@code player_snapshot.enabled}; selective Player
     * restore and readiness validation are separate policies.
     */
    public List<CheckpointPlayerSnapshot> captureCheckpointPlayers(MinecraftServer server)
            throws IOException {
        List<CheckpointPlayerSnapshot> result = new ArrayList<>();
        try {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (CarpetIntegration.isFakePlayer(player)) continue;
                CheckpointPlayerSnapshot snapshot = new CheckpointPlayerSnapshot();
                snapshot.uuid = player.getStringUUID();
                snapshot.name = player.getGameProfile().name();
                snapshot.dimension = player.level().dimension().identifier().toString();
                result.add(snapshot);
            }
        } catch (RuntimeException exception) {
            throw new IOException("Could not capture STRICT checkpoint player identities", exception);
        }
        result.sort(java.util.Comparator.comparing(CheckpointPlayerSnapshot::canonical));
        return List.copyOf(result);
    }

    private PlayerSnapshotData captureOne(ServerPlayer player) throws IOException {
        PlayerSnapshotData data = new PlayerSnapshotData();
        data.uuid = player.getStringUUID();
        data.name = player.getGameProfile().name();
        if (config.playerSnapshot.dimension) data.dimension = player.level().dimension().identifier().toString();
        if (config.playerSnapshot.position) { data.x=player.getX(); data.y=player.getY(); data.z=player.getZ(); }
        if (config.playerSnapshot.rotation) { data.yaw=player.getViewYRot(0); data.pitch=player.getViewXRot(0); }
        if (config.playerSnapshot.gamemode) data.gamemode = player.gameMode.getGameModeForPlayer().getSerializedName();
        if (config.playerSnapshot.abilities) {
            net.minecraft.nbt.CompoundTag abilities = com.atsukimc.anticrash.runtime.VersionStorage.abilities(player.getAbilities());
            data.abilitiesNbt = NbtCodec.encode(abilities);
        }
        data.inventory = InventorySnapshot.capture(player, config.playerSnapshot.inventoryMain, config.playerSnapshot.inventoryArmor, config.playerSnapshot.inventoryOffhand, config.playerSnapshot.selectedHotbarSlot);
        if (config.playerSnapshot.experienceLevel) data.experienceLevel=player.experienceLevel;
        if (config.playerSnapshot.experienceProgress) data.experienceProgress=player.experienceProgress;
        if (config.playerSnapshot.experienceTotal) data.experienceTotal=player.totalExperience;
        if (config.playerSnapshot.enderChest) data.enderChestNbt=NbtCodec.encode(com.atsukimc.anticrash.runtime.VersionStorage.inventory(player.getEnderChestInventory(), player.registryAccess()));
        if (config.playerSnapshot.health) data.health=player.getHealth();
        if (config.playerSnapshot.absorption) data.absorption=player.getAbsorptionAmount();
        if (config.playerSnapshot.foodLevel) data.foodLevel=player.getFoodData().getFoodLevel();
        if (config.playerSnapshot.foodSaturation) data.foodSaturation=player.getFoodData().getSaturationLevel();
        if (config.playerSnapshot.foodExhaustion) data.foodExhaustion=((com.atsukimc.anticrash.mixin.HungerManagerAccessorMixin)player.getFoodData()).anticrash$getExhaustion();
        if (config.playerSnapshot.potionEffects) for (MobEffectInstance effect : player.getActiveEffects()) data.effectsNbt.add(NbtCodec.encode(com.atsukimc.anticrash.runtime.VersionNbt.effect(effect)));
        if (config.playerSnapshot.velocity) { Vec3 velocity=player.getDeltaMovement(); data.velocityX=velocity.x; data.velocityY=velocity.y; data.velocityZ=velocity.z; }
        if (config.playerSnapshot.fallDistance) data.fallDistance=player.fallDistance;
        if (config.playerSnapshot.fireTicks) data.fireTicks=player.getRemainingFireTicks();
        if (config.playerSnapshot.air) data.air=player.getAirSupply();
        if (config.playerSnapshot.spawnPoint && player.getRespawnConfig() != null) {
            var respawn = player.getRespawnConfig();
            var spawn=respawn.respawnData();
            data.hasSpawnPoint=true; BlockPos pos=spawn.pos(); data.spawnX=pos.getX(); data.spawnY=pos.getY(); data.spawnZ=pos.getZ(); data.spawnAngle=spawn.yaw(); data.spawnPitch=spawn.pitch(); data.spawnForced=respawn.forced();
            data.spawnDimension=spawn.dimension().identifier().toString();
        }
        return data;
    }

    public void load(List<PlayerSnapshotData> saved) throws IOException {
        Map<UUID, PlayerSnapshotData> validated = new HashMap<>();
        if (saved == null) saved = List.of();
        for (PlayerSnapshotData data : saved) {
            if (data == null || data.formatVersion != 1) throw new IOException("Unsupported or empty player snapshot");
            UUID uuid;
            try { uuid = UUID.fromString(data.uuid); }
            catch (Exception exception) { throw new IOException("Invalid player snapshot UUID", exception); }
            validatePayload(data);
            if (validated.putIfAbsent(uuid, data) != null) throw new IOException("Duplicate player snapshot UUID: " + uuid);
        }
        // Publish only after every entry passes so malformed late entries cannot leave a partial
        // restore map behind.
        snapshots.clear();
        snapshots.putAll(validated);
        restored.clear();
    }

    private void validatePayload(PlayerSnapshotData data) throws IOException {
        validateFiniteFields(data);
        if (config.playerSnapshot.dimension
                && (data.dimension == null || data.dimension.isBlank() || data.dimension.length() > 512)) {
            throw new IOException("Player snapshot dimension is missing or invalid");
        }
        if (config.playerSnapshot.gamemode && data.gamemode != null
                && !Set.of("survival", "creative", "adventure", "spectator").contains(data.gamemode)) {
            throw new IOException("Invalid player gamemode: " + data.gamemode);
        }
        if (config.playerSnapshot.abilities) {
            if (data.abilitiesNbt == null) throw new IOException("Enabled abilities snapshot is missing");
            NbtCodec.decodeCompound(data.abilitiesNbt);
        }
        boolean inventoryEnabled = config.playerSnapshot.inventoryMain || config.playerSnapshot.inventoryArmor
                || config.playerSnapshot.inventoryOffhand || config.playerSnapshot.selectedHotbarSlot;
        if (inventoryEnabled) {
            if (data.inventory == null) throw new IOException("Enabled inventory snapshot is missing");
            data.inventory.validatePortable(config.playerSnapshot.inventoryMain,
                    config.playerSnapshot.inventoryArmor, config.playerSnapshot.inventoryOffhand,
                    config.playerSnapshot.selectedHotbarSlot);
        }
        if (config.playerSnapshot.enderChest) {
            if (data.enderChestNbt == null) throw new IOException("Enabled Ender Chest snapshot is missing");
            if (!(NbtCodec.decode(data.enderChestNbt) instanceof ListTag)) {
                throw new IOException("Ender Chest snapshot is not an NBT list");
            }
        }
        if (config.playerSnapshot.potionEffects) {
            if (data.effectsNbt == null || data.effectsNbt.size() > 1024) {
                throw new IOException("Enabled effect snapshot is missing or too large");
            }
            for (String encoded : data.effectsNbt) {
                Tag decoded = NbtCodec.decode(encoded);
                if (!(decoded instanceof net.minecraft.nbt.CompoundTag compound)
                        || com.atsukimc.anticrash.runtime.VersionNbt.effect(compound) == null) {
                    throw new IOException("Potion effect snapshot is invalid");
                }
            }
        }
        if (config.playerSnapshot.spawnPoint && data.hasSpawnPoint
                && (data.spawnDimension == null || data.spawnDimension.isBlank()
                || data.spawnDimension.length() > 512)) {
            throw new IOException("Spawn dimension is missing or invalid");
        }
    }

    public boolean restoreOnJoin(ServerPlayer player) {
        if (!config.playerSnapshot.enabled || restored.contains(player.getUUID())) return true;
        PlayerSnapshotData data = snapshots.get(player.getUUID());
        if (data == null) return true;
        try {
            MinecraftServer server = player.level().getServer();
            ServerLevel target = (ServerLevel) player.level();
            if (config.playerSnapshot.dimension) {
                target = findWorld(server, data.dimension);
                if (target == null) throw new IllegalStateException("snapshot dimension is unavailable: " + data.dimension);
            }
            validateFiniteFields(data);

            // Decode and resolve every enabled optional payload before changing position,
            // inventory, effects, or any other live state. A malformed late field must not
            // leave a partially restored player.
            net.minecraft.world.entity.player.Abilities.Packed abilities = null;
            if (config.playerSnapshot.abilities) {
                if (data.abilitiesNbt == null) throw new IOException("Enabled abilities snapshot is missing");
                abilities = com.atsukimc.anticrash.runtime.VersionStorage.abilities(NbtCodec.decodeCompound(data.abilitiesNbt));
            }
            boolean inventoryEnabled = config.playerSnapshot.inventoryMain || config.playerSnapshot.inventoryArmor
                    || config.playerSnapshot.inventoryOffhand || config.playerSnapshot.selectedHotbarSlot;
            if (inventoryEnabled) {
                if (data.inventory == null) throw new IOException("Enabled inventory snapshot is missing");
                data.inventory.validate(player, config.playerSnapshot.inventoryMain,
                        config.playerSnapshot.inventoryArmor, config.playerSnapshot.inventoryOffhand,
                        config.playerSnapshot.selectedHotbarSlot);
            }
            List<net.minecraft.world.item.ItemStack> enderChest = null;
            if (config.playerSnapshot.enderChest) {
                if (data.enderChestNbt == null) throw new IOException("Enabled Ender Chest snapshot is missing");
                Tag decoded = NbtCodec.decode(data.enderChestNbt);
                if (!(decoded instanceof ListTag list)) throw new IOException("Ender Chest snapshot is not an NBT list");
                enderChest = com.atsukimc.anticrash.runtime.VersionStorage.inventory(list, player.getEnderChestInventory().getContainerSize(), player.registryAccess());
            }
            List<MobEffectInstance> effects = null;
            if (config.playerSnapshot.potionEffects) {
                if (data.effectsNbt == null) throw new IOException("Enabled effect snapshot is missing");
                effects = new ArrayList<>(data.effectsNbt.size());
                for (String encoded : data.effectsNbt) {
                    Tag decoded = NbtCodec.decode(encoded);
                    if (!(decoded instanceof net.minecraft.nbt.CompoundTag compound)) {
                        throw new IOException("Potion effect snapshot is not an NBT compound");
                    }
                    MobEffectInstance effect = com.atsukimc.anticrash.runtime.VersionNbt.effect(compound);
                    if (effect == null) throw new IOException("Potion effect snapshot is invalid");
                    effects.add(effect);
                }
            }
            ServerLevel spawnWorld = null;
            if (config.playerSnapshot.spawnPoint && data.hasSpawnPoint) {
                spawnWorld = findWorld(server, data.spawnDimension);
                if (spawnWorld == null) throw new IOException("Spawn dimension is unavailable: " + data.spawnDimension);
            }
            if (config.playerSnapshot.gamemode && data.gamemode != null
                    && !Set.of("survival", "creative", "adventure", "spectator").contains(data.gamemode)) {
                throw new IOException("Invalid player gamemode: " + data.gamemode);
            }

            if (config.playerSnapshot.dimension || config.playerSnapshot.position || config.playerSnapshot.rotation) {
                double x=config.playerSnapshot.position?data.x:player.getX(), y=config.playerSnapshot.position?data.y:player.getY(), z=config.playerSnapshot.position?data.z:player.getZ();
                float yaw=config.playerSnapshot.rotation?data.yaw:player.getViewYRot(0), pitch=config.playerSnapshot.rotation?data.pitch:player.getViewXRot(0);
                // teleport() also handles the same-world case through requestTeleport, which tells the
                // client its new position; updatePositionAndAngles alone left the client elsewhere.
                player.teleportTo(target,x,y,z,java.util.Set.of(),yaw,pitch,false);
            }
            if (config.playerSnapshot.gamemode && data.gamemode != null) player.setGameMode(net.minecraft.world.level.GameType.byName(data.gamemode, player.gameMode.getGameModeForPlayer()));
            if (abilities != null) { player.getAbilities().apply(abilities); player.onUpdateAbilities(); }
            boolean inventoryChanged = data.inventory != null && data.inventory.restore(player, config.playerSnapshot.inventoryMain, config.playerSnapshot.inventoryArmor, config.playerSnapshot.inventoryOffhand, config.playerSnapshot.selectedHotbarSlot);
            if (config.playerSnapshot.experienceLevel) player.setExperienceLevels(data.experienceLevel);
            if (config.playerSnapshot.experienceProgress) player.experienceProgress=data.experienceProgress;
            if (config.playerSnapshot.experienceTotal) player.totalExperience=data.experienceTotal;
            if (enderChest != null) com.atsukimc.anticrash.runtime.VersionStorage.applyInventory(player.getEnderChestInventory(), enderChest);
            if (config.playerSnapshot.health) player.setHealth(data.health);
            if (config.playerSnapshot.absorption) player.setAbsorptionAmount(data.absorption);
            if (config.playerSnapshot.foodLevel) player.getFoodData().setFoodLevel(data.foodLevel);
            if (config.playerSnapshot.foodSaturation) player.getFoodData().setSaturation(data.foodSaturation);
            if (config.playerSnapshot.foodExhaustion) ((com.atsukimc.anticrash.mixin.HungerManagerAccessorMixin)player.getFoodData()).anticrash$setExhaustion(data.foodExhaustion);
            if (effects != null) { player.removeAllEffects(); for (MobEffectInstance effect : effects) player.addEffect(effect); }
            if (config.playerSnapshot.velocity) player.setDeltaMovement(data.velocityX,data.velocityY,data.velocityZ);
            if (config.playerSnapshot.fallDistance) player.fallDistance=data.fallDistance;
            if (config.playerSnapshot.fireTicks) player.setRemainingFireTicks(data.fireTicks);
            if (config.playerSnapshot.air) player.setAirSupply(data.air);
            if (spawnWorld != null) player.setRespawnPosition(new ServerPlayer.RespawnConfig(net.minecraft.world.level.storage.LevelData.RespawnData.of(spawnWorld.dimension(),new BlockPos(data.spawnX,data.spawnY,data.spawnZ),data.spawnAngle,data.spawnPitch),data.spawnForced),false);
            if (inventoryChanged) player.inventoryMenu.sendAllDataToRemote();
            restored.add(player.getUUID());
            logger.normalKey("player.restored", data.name);
            return true;
        } catch (Exception exception) {
            logger.importantKey("player.restore_failed", data.name, exception.getMessage());
            return false;
        }
    }

    private static ServerLevel findWorld(MinecraftServer server, String id) {
        if (id == null) return null;
        for (ServerLevel world : server.getAllLevels()) if (world.dimension().identifier().toString().equals(id)) return world;
        return null;
    }

    private void validateFiniteFields(PlayerSnapshotData data) throws IOException {
        if (config.playerSnapshot.position && !(Double.isFinite(data.x) && Double.isFinite(data.y) && Double.isFinite(data.z))) {
            throw new IOException("Player snapshot position is not finite");
        }
        if (config.playerSnapshot.rotation && !(Float.isFinite(data.yaw) && Float.isFinite(data.pitch))) {
            throw new IOException("Player snapshot rotation is not finite");
        }
        if (config.playerSnapshot.experienceProgress && !Float.isFinite(data.experienceProgress)) throw new IOException("Experience progress is not finite");
        if (config.playerSnapshot.health && !Float.isFinite(data.health)) throw new IOException("Health is not finite");
        if (config.playerSnapshot.absorption && !Float.isFinite(data.absorption)) throw new IOException("Absorption is not finite");
        if (config.playerSnapshot.foodSaturation && !Float.isFinite(data.foodSaturation)) throw new IOException("Food saturation is not finite");
        if (config.playerSnapshot.foodExhaustion && !Float.isFinite(data.foodExhaustion)) throw new IOException("Food exhaustion is not finite");
        if (config.playerSnapshot.velocity && !(Double.isFinite(data.velocityX)
                && Double.isFinite(data.velocityY) && Double.isFinite(data.velocityZ))) throw new IOException("Velocity is not finite");
        if (config.playerSnapshot.fallDistance && !Double.isFinite(data.fallDistance)) throw new IOException("Fall distance is not finite");
        if (config.playerSnapshot.spawnPoint && data.hasSpawnPoint && (!Float.isFinite(data.spawnAngle)||!Float.isFinite(data.spawnPitch))) throw new IOException("Spawn angle is not finite");
        if (config.playerSnapshot.experienceProgress && (data.experienceProgress < 0.0F || data.experienceProgress > 1.0F)) throw new IOException("Experience progress is outside 0..1");
        if (config.playerSnapshot.experienceLevel && data.experienceLevel < 0) throw new IOException("Experience level is negative");
        if (config.playerSnapshot.experienceTotal && data.experienceTotal < 0) throw new IOException("Total experience is negative");
    }
}
