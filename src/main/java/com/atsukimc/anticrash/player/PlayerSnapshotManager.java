package com.atsukimc.anticrash.player;

import com.atsukimc.anticrash.config.AntiCrashConfig;
import com.atsukimc.anticrash.integration.carpet.CarpetIntegration;
import com.atsukimc.anticrash.logging.AntiCrashLogger;
import com.atsukimc.anticrash.runtime.CheckpointPlayerSnapshot;

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.entity.effect.StatusEffectInstance;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.core.BlockPos;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.nbt.NbtElement;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.nbt.ListTag;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.nbt.NbtList;
//?}

//? if (>=1.19.3 && <=1.21.4) {
/*import net.minecraft.registry.RegistryKey;
*///?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.nbt.Tag;
*///?}

import net.minecraft.server.MinecraftServer;
//? if (>=1.18 && <=1.21.11) {
import net.minecraft.server.network.ServerPlayerEntity;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.server.level.ServerLevel;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.server.world.ServerWorld;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.server.level.ServerPlayer;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.util.math.BlockPos;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.effect.MobEffectInstance;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.util.math.Vec3d;
//?}

//? if (>=1.18 && <=1.19.2) {
import net.minecraft.util.registry.RegistryKey;
//?}

//? if (>=1.18 && <=1.21.4) {
import net.minecraft.world.World;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.phys.Vec3;
*///?}

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

        //? if (>=1.18 && <=1.21.11) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList())
        //?}
        //? if (>=26.1 && <=26.3) {
        /*for (ServerPlayer player : server.getPlayerList().getPlayers())
*///?}
        {
            if (CarpetIntegration.isFakePlayer(player)) continue;
            try {
                result.add(captureOne(player));
            } catch (Exception exception) {
                logger.importantKey(
                        "player.snapshot_failed",
                        player.getName().getString(),
                        exception.getMessage());
                if (exception instanceof IOException ioException) throw ioException;
                throw new IOException(
                        "Could not capture player " + player.getName().getString(), exception);
            }
        }
        return result;
    }

    /**
     * Captures only the stable identity needed by STRICT's optional checkpoint-player gate. This
     * deliberately does not depend on {@code player_snapshot.enabled}; selective Player restore and
     * readiness validation are separate policies.
     */
    public List<CheckpointPlayerSnapshot> captureCheckpointPlayers(MinecraftServer server)
            throws IOException {
        List<CheckpointPlayerSnapshot> result = new ArrayList<>();
        try {

            //? if (>=1.18 && <=1.21.11) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList())
            //?}
            //? if (>=26.1 && <=26.3) {
            /*for (ServerPlayer player : server.getPlayerList().getPlayers())
*///?}
            {
                if (CarpetIntegration.isFakePlayer(player)) continue;
                CheckpointPlayerSnapshot snapshot = new CheckpointPlayerSnapshot();

                //? if (>=1.18 && <=1.21.11) {
                snapshot.uuid = player.getUuidAsString();
                //?}
                //? if (>=26.1 && <=26.3) {
                /*snapshot.uuid = player.getStringUUID();
*///?}

                //? if (>=1.18 && <=1.21.8) {
                snapshot.name = player.getGameProfile().getName();
                //?}
                //? if (>=1.21.9 && <=26.3) {
                /*snapshot.name = player.getGameProfile().name();
*///?}

                //? if (>=1.18 && <=1.21.8) {
                snapshot.dimension = player.getWorld().getRegistryKey().getValue().toString();
                //?}
                //? if (>=1.21.9 && <=1.21.11) {
                /*snapshot.dimension = player.getEntityWorld().getRegistryKey().getValue().toString();
*///?}
                //? if (>=26.1 && <=26.3) {
                /*snapshot.dimension = player.level().dimension().identifier().toString();
*///?}

                result.add(snapshot);
            }
        } catch (RuntimeException exception) {
            throw new IOException(
                    "Could not capture STRICT checkpoint player identities", exception);
        }
        result.sort(java.util.Comparator.comparing(CheckpointPlayerSnapshot::canonical));
        return List.copyOf(result);
    }

    //? if (>=1.18 && <=1.21.11) {
    private PlayerSnapshotData captureOne(ServerPlayerEntity player) throws IOException
                //?}
                //? if (>=26.1 && <=26.3) {
                /*private PlayerSnapshotData captureOne(ServerPlayer player) throws IOException
*///?}
            {
        PlayerSnapshotData data = new PlayerSnapshotData();

        //? if (>=1.18 && <=1.21.11) {
        data.uuid = player.getUuidAsString();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*data.uuid = player.getStringUUID();
*///?}

        //? if (>=1.18 && <=1.21.8) {
        data.name = player.getGameProfile().getName();
        //?}
        //? if (>=1.21.9 && <=26.3) {
        /*data.name = player.getGameProfile().name();
*///?}

        if (config.playerSnapshot.dimension)

            //? if (>=1.18 && <=1.21.8) {
            data.dimension = player.getWorld().getRegistryKey().getValue().toString();
        //?}
        //? if (>=1.21.9 && <=1.21.11) {
        /*data.dimension = player.getEntityWorld().getRegistryKey().getValue().toString();
*///?}
        //? if (>=26.1 && <=26.3) {
        /*data.dimension = player.level().dimension().identifier().toString();
*///?}

        if (config.playerSnapshot.position) {
            data.x = player.getX();
            data.y = player.getY();
            data.z = player.getZ();
        }
        if (config.playerSnapshot.rotation) {

            //? if (>=1.18 && <=1.21.11) {
            data.yaw = player.getYaw(0);
            //?}
            //? if (>=26.1 && <=26.3) {
            /*data.yaw = player.getViewYRot(0);
*///?}

            //? if (>=1.18 && <=1.21.11) {
            data.pitch = player.getPitch(0);
            //?}
            //? if (>=26.1 && <=26.3) {
            /*data.pitch = player.getViewXRot(0);
*///?}

        }
        if (config.playerSnapshot.gamemode)

            //? if (>=1.18 && <=1.21.4) {
            data.gamemode = player.interactionManager.getGameMode().getName();
        //?}
        //? if (>=1.21.5 && <=1.21.11) {
        /*data.gamemode = player.interactionManager.getGameMode().asString();
*///?}
        //? if (>=26.1 && <=26.3) {
        /*data.gamemode = player.gameMode.getGameModeForPlayer().getSerializedName();
*///?}

        if (config.playerSnapshot.abilities) {

            //? if (>=1.18 && <=1.21.5) {
            net.minecraft.nbt.NbtCompound abilities = new net.minecraft.nbt.NbtCompound();
            //?}

            //? if (>=1.18 && <=1.21.5) {
            player.getAbilities().writeNbt(abilities);
            //?}
            //? if (>=1.21.6 && <=1.21.11) {
            /*net.minecraft.nbt.NbtCompound abilities =
                                com.atsukimc.anticrash.runtime.VersionStorage.abilities(player.getAbilities());
*///?}
            //? if (>=26.1 && <=26.3) {
            /*net.minecraft.nbt.CompoundTag abilities =
                                com.atsukimc.anticrash.runtime.VersionStorage.abilities(player.getAbilities());
*///?}

            data.abilitiesNbt = NbtCodec.encode(abilities);
        }
        data.inventory =
                InventorySnapshot.capture(
                        player,
                        config.playerSnapshot.inventoryMain,
                        config.playerSnapshot.inventoryArmor,
                        config.playerSnapshot.inventoryOffhand,
                        config.playerSnapshot.selectedHotbarSlot);
        if (config.playerSnapshot.experienceLevel) data.experienceLevel = player.experienceLevel;
        if (config.playerSnapshot.experienceProgress)
            data.experienceProgress = player.experienceProgress;
        if (config.playerSnapshot.experienceTotal) data.experienceTotal = player.totalExperience;
        if (config.playerSnapshot.enderChest)

            //? if (>=1.18 && <=1.20.4) {
            data.enderChestNbt = NbtCodec.encode(player.getEnderChestInventory().toNbtList());
        //?}
        //? if (>=1.20.5 && <=1.21.5) {
        /*data.enderChestNbt =
                            NbtCodec.encode(
                                    player.getEnderChestInventory().toNbtList(player.getRegistryManager()));
*///?}
        //? if (>=1.21.6 && <=1.21.11) {
        /*data.enderChestNbt =
                            NbtCodec.encode(
                                    com.atsukimc.anticrash.runtime.VersionStorage.inventory(
                                            player.getEnderChestInventory(), player.getRegistryManager()));
*///?}
        //? if (>=26.1 && <=26.3) {
        /*data.enderChestNbt =
                            NbtCodec.encode(
                                    com.atsukimc.anticrash.runtime.VersionStorage.inventory(
                                            player.getEnderChestInventory(), player.registryAccess()));
*///?}

        if (config.playerSnapshot.health) data.health = player.getHealth();
        if (config.playerSnapshot.absorption) data.absorption = player.getAbsorptionAmount();

        //? if (>=1.18 && <=1.21.11) {
        if (config.playerSnapshot.foodLevel)
            data.foodLevel = player.getHungerManager().getFoodLevel();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*if (config.playerSnapshot.foodLevel) data.foodLevel = player.getFoodData().getFoodLevel();
*///?}

        if (config.playerSnapshot.foodSaturation)

            //? if (>=1.18 && <=1.21.11) {
            data.foodSaturation = player.getHungerManager().getSaturationLevel();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*data.foodSaturation = player.getFoodData().getSaturationLevel();
*///?}

        if (config.playerSnapshot.foodExhaustion)

            //? if (>=1.18 && <=1.21.1) {
            data.foodExhaustion = player.getHungerManager().getExhaustion();
        //?}
        //? if (>=1.21.2 && <=1.21.11) {
        /*data.foodExhaustion =
                            ((com.atsukimc.anticrash.mixin.HungerManagerAccessorMixin)
                                            player.getHungerManager())
                                    .anticrash$getExhaustion();
*///?}
        //? if (>=26.1 && <=26.3) {
        /*data.foodExhaustion =
                            ((com.atsukimc.anticrash.mixin.HungerManagerAccessorMixin) player.getFoodData())
                                    .anticrash$getExhaustion();
*///?}

        if (config.playerSnapshot.potionEffects)

            //? if (>=1.18 && <=1.21.11) {
            for (StatusEffectInstance effect : player.getStatusEffects())

                //?}
                //? if (>=1.18 && <=1.20.4) {
                data.effectsNbt.add(
                        NbtCodec.encode(effect.writeNbt(new net.minecraft.nbt.NbtCompound())));
        //?}
        //? if (>=1.20.5 && <=1.21.4) {
        /*data.effectsNbt.add(NbtCodec.encode(effect.writeNbt()));
*///?}
        //? if (>=26.1 && <=26.3) {
        /*for (MobEffectInstance effect : player.getActiveEffects())
*///?}
        //? if (>=1.21.5 && <=26.3) {
        /*data.effectsNbt.add(
                                NbtCodec.encode(com.atsukimc.anticrash.runtime.VersionNbt.effect(effect)));
*///?}

        if (config.playerSnapshot.velocity) {

            //? if (>=1.18 && <=1.21.11) {
            Vec3d velocity = player.getVelocity();
            //?}
            //? if (>=26.1 && <=26.3) {
            /*Vec3 velocity = player.getDeltaMovement();
*///?}

            data.velocityX = velocity.x;
            data.velocityY = velocity.y;
            data.velocityZ = velocity.z;
        }
        if (config.playerSnapshot.fallDistance) data.fallDistance = player.fallDistance;
        if (config.playerSnapshot.fireTicks)
            //? if (>=1.18 && <=1.21.11) {
            data.fireTicks = player.getFireTicks();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*data.fireTicks = player.getRemainingFireTicks();
*///?}

        if (config.playerSnapshot.air)
            //? if (>=1.18 && <=1.21.11) {
            data.air = player.getAir();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*data.air = player.getAirSupply();
*///?}

        //? if (>=1.18 && <=1.21.4) {
        if (config.playerSnapshot.spawnPoint && player.getSpawnPointPosition() != null)
        //?}
        //? if (>=1.21.5 && <=1.21.11) {
        /*if (config.playerSnapshot.spawnPoint && player.getRespawn() != null)
*///?}
        //? if (>=26.1 && <=26.3) {
        /*if (config.playerSnapshot.spawnPoint && player.getRespawnConfig() != null)
*///?}
        {

            //? if (>=1.21.5 && <=1.21.11) {
            /*var respawn = player.getRespawn();
*///?}
            //? if (>=26.1 && <=26.3) {
            /*var respawn = player.getRespawnConfig();
*///?}

            //? if (>=1.21.9 && <=26.3) {
            /*var spawn = respawn.respawnData();
*///?}

            data.hasSpawnPoint = true;

            //? if (>=1.18 && <=1.21.4) {
            BlockPos pos = player.getSpawnPointPosition();
            //?}
            //? if (>=1.21.5 && <=1.21.8) {
            /*BlockPos pos = respawn.pos();
*///?}
            //? if (>=1.21.9 && <=1.21.11) {
            /*BlockPos pos = spawn.getPos();
*///?}
            //? if (>=26.1 && <=26.3) {
            /*BlockPos pos = spawn.pos();
*///?}

            data.spawnX = pos.getX();
            data.spawnY = pos.getY();
            data.spawnZ = pos.getZ();

            //? if (>=1.18 && <=1.21.4) {
            data.spawnAngle = player.getSpawnAngle();
            //?}
            //? if (>=1.21.5 && <=1.21.8) {
            /*data.spawnAngle = respawn.angle();
*///?}
            //? if (>=1.21.9 && <=26.3) {
            /*data.spawnAngle = spawn.yaw();
*///?}

            //? if (>=1.18 && <=1.21.4) {
            data.spawnForced = player.isSpawnForced();
            //?}
            //? if (>=1.21.9 && <=26.3) {
            /*data.spawnPitch = spawn.pitch();
*///?}

            //? if (>=1.21.5 && <=26.3) {
            /*data.spawnForced = respawn.forced();
*///?}

            //? if (>=1.18 && <=1.21.4) {
            RegistryKey<World> key = player.getSpawnPointDimension();
            //?}

            //? if (>=1.18 && <=1.21.4) {
            data.spawnDimension =
                    key == null
                            ? player.getWorld().getRegistryKey().getValue().toString()
                            : key.getValue().toString();
            //?}
            //? if (>=1.21.5 && <=1.21.8) {
            /*data.spawnDimension = respawn.dimension().getValue().toString();
*///?}
            //? if (>=1.21.9 && <=1.21.11) {
            /*data.spawnDimension = spawn.getDimension().getValue().toString();
*///?}
            //? if (>=26.1 && <=26.3) {
            /*data.spawnDimension = spawn.dimension().identifier().toString();
*///?}

        }
        return data;
    }

    public void load(List<PlayerSnapshotData> saved) throws IOException {
        Map<UUID, PlayerSnapshotData> validated = new HashMap<>();
        if (saved == null) saved = List.of();
        for (PlayerSnapshotData data : saved) {
            if (data == null || data.formatVersion != 1)
                throw new IOException("Unsupported or empty player snapshot");
            UUID uuid;
            try {
                uuid = UUID.fromString(data.uuid);
            } catch (Exception exception) {
                throw new IOException("Invalid player snapshot UUID", exception);
            }
            validatePayload(data);
            if (validated.putIfAbsent(uuid, data) != null)
                throw new IOException("Duplicate player snapshot UUID: " + uuid);
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
                && (data.dimension == null
                        || data.dimension.isBlank()
                        || data.dimension.length() > 512)) {
            throw new IOException("Player snapshot dimension is missing or invalid");
        }
        if (config.playerSnapshot.gamemode
                && data.gamemode != null
                && !Set.of("survival", "creative", "adventure", "spectator")
                        .contains(data.gamemode)) {
            throw new IOException("Invalid player gamemode: " + data.gamemode);
        }
        if (config.playerSnapshot.abilities) {
            if (data.abilitiesNbt == null)
                throw new IOException("Enabled abilities snapshot is missing");
            NbtCodec.decodeCompound(data.abilitiesNbt);
        }
        boolean inventoryEnabled =
                config.playerSnapshot.inventoryMain
                        || config.playerSnapshot.inventoryArmor
                        || config.playerSnapshot.inventoryOffhand
                        || config.playerSnapshot.selectedHotbarSlot;
        if (inventoryEnabled) {
            if (data.inventory == null)
                throw new IOException("Enabled inventory snapshot is missing");
            data.inventory.validatePortable(
                    config.playerSnapshot.inventoryMain,
                    config.playerSnapshot.inventoryArmor,
                    config.playerSnapshot.inventoryOffhand,
                    config.playerSnapshot.selectedHotbarSlot);
        }
        if (config.playerSnapshot.enderChest) {
            if (data.enderChestNbt == null)
                throw new IOException("Enabled Ender Chest snapshot is missing");

            //? if (>=1.18 && <=1.21.11) {
            if (!(NbtCodec.decode(data.enderChestNbt) instanceof NbtList))
            //?}
            //? if (>=26.1 && <=26.3) {
            /*if (!(NbtCodec.decode(data.enderChestNbt) instanceof ListTag))
*///?}
            {
                throw new IOException("Ender Chest snapshot is not an NBT list");
            }
        }
        if (config.playerSnapshot.potionEffects) {
            if (data.effectsNbt == null || data.effectsNbt.size() > 1024) {
                throw new IOException("Enabled effect snapshot is missing or too large");
            }
            for (String encoded : data.effectsNbt) {

                //? if (>=1.18 && <=1.21.11) {
                NbtElement decoded = NbtCodec.decode(encoded);
                //?}
                //? if (>=26.1 && <=26.3) {
                /*Tag decoded = NbtCodec.decode(encoded);
*///?}

                //? if (>=1.18 && <=1.21.4) {
                if (!(decoded instanceof net.minecraft.nbt.NbtCompound compound)
                        || StatusEffectInstance.fromNbt(compound) == null)
                //?}
                //? if (>=1.21.5 && <=1.21.11) {
                /*if (!(decoded instanceof net.minecraft.nbt.NbtCompound compound)
                                        || com.atsukimc.anticrash.runtime.VersionNbt.effect(compound) == null)
*///?}
                //? if (>=26.1 && <=26.3) {
                /*if (!(decoded instanceof net.minecraft.nbt.CompoundTag compound)
                                        || com.atsukimc.anticrash.runtime.VersionNbt.effect(compound) == null)
*///?}
                {
                    throw new IOException("Potion effect snapshot is invalid");
                }
            }
        }
        if (config.playerSnapshot.spawnPoint
                && data.hasSpawnPoint
                && (data.spawnDimension == null
                        || data.spawnDimension.isBlank()
                        || data.spawnDimension.length() > 512)) {
            throw new IOException("Spawn dimension is missing or invalid");
        }
    }

    //? if (>=1.18 && <=1.21.11) {
    public boolean restoreOnJoin(ServerPlayerEntity player)
                //?}
                //? if (>=26.1 && <=26.3) {
                /*public boolean restoreOnJoin(ServerPlayer player)
*///?}
            {

        //? if (>=1.18 && <=1.21.11) {
        if (!config.playerSnapshot.enabled || restored.contains(player.getUuid()))
            //?}
            //? if (>=26.1 && <=26.3) {
            /*if (!config.playerSnapshot.enabled || restored.contains(player.getUUID()))
*///?}
            return true;

        //? if (>=1.18 && <=1.21.11) {
        PlayerSnapshotData data = snapshots.get(player.getUuid());
        //?}
        //? if (>=26.1 && <=26.3) {
        /*PlayerSnapshotData data = snapshots.get(player.getUUID());
*///?}

        if (data == null) return true;
        try {

            //? if (>=1.18 && <=1.21.5) {
            MinecraftServer server = player.server;
            //?}
            //? if (>=1.21.6 && <=1.21.8) {
            /*MinecraftServer server = player.getServer();
*///?}
            //? if (>=1.21.9 && <=1.21.11) {
            /*MinecraftServer server = player.getEntityWorld().getServer();
*///?}
            //? if (>=26.1 && <=26.3) {
            /*MinecraftServer server = player.level().getServer();
*///?}

            //? if (>=1.18 && <=1.21.8) {
            ServerWorld target = (ServerWorld) player.getWorld();
            //?}
            //? if (>=1.21.9 && <=1.21.11) {
            /*ServerWorld target = (ServerWorld) player.getEntityWorld();
*///?}
            //? if (>=26.1 && <=26.3) {
            /*ServerLevel target = (ServerLevel) player.level();
*///?}

            if (config.playerSnapshot.dimension) {
                target = findWorld(server, data.dimension);
                if (target == null)
                    throw new IllegalStateException(
                            "snapshot dimension is unavailable: " + data.dimension);
            }
            validateFiniteFields(data);

            // Decode and resolve every enabled optional payload before changing position,
            // inventory, effects, or any other live state. A malformed late field must not
            // leave a partially restored player.

            //? if (>=1.18 && <=1.21.5) {
            net.minecraft.nbt.NbtCompound abilities = null;
            //?}
            //? if (>=1.21.6 && <=1.21.11) {
            /*net.minecraft.entity.player.PlayerAbilities.Packed abilities = null;
*///?}
            //? if (>=26.1 && <=26.3) {
            /*net.minecraft.world.entity.player.Abilities.Packed abilities = null;
*///?}

            if (config.playerSnapshot.abilities) {
                if (data.abilitiesNbt == null)
                    throw new IOException("Enabled abilities snapshot is missing");

                //? if (>=1.18 && <=1.21.5) {
                abilities = NbtCodec.decodeCompound(data.abilitiesNbt);
                //?}
                //? if (>=1.21.6 && <=26.3) {
                /*abilities =
                                        com.atsukimc.anticrash.runtime.VersionStorage.abilities(
                                                NbtCodec.decodeCompound(data.abilitiesNbt));
*///?}

            }
            boolean inventoryEnabled =
                    config.playerSnapshot.inventoryMain
                            || config.playerSnapshot.inventoryArmor
                            || config.playerSnapshot.inventoryOffhand
                            || config.playerSnapshot.selectedHotbarSlot;
            if (inventoryEnabled) {
                if (data.inventory == null)
                    throw new IOException("Enabled inventory snapshot is missing");
                data.inventory.validate(
                        player,
                        config.playerSnapshot.inventoryMain,
                        config.playerSnapshot.inventoryArmor,
                        config.playerSnapshot.inventoryOffhand,
                        config.playerSnapshot.selectedHotbarSlot);
            }

            //? if (>=1.18 && <=1.21.5) {
            NbtList enderChest = null;
            //?}
            //? if (>=1.21.6 && <=1.21.11) {
            /*List<net.minecraft.item.ItemStack> enderChest = null;
*///?}
            //? if (>=26.1 && <=26.3) {
            /*List<net.minecraft.world.item.ItemStack> enderChest = null;
*///?}

            if (config.playerSnapshot.enderChest) {
                if (data.enderChestNbt == null)
                    throw new IOException("Enabled Ender Chest snapshot is missing");

                //? if (>=1.18 && <=1.21.11) {
                NbtElement decoded = NbtCodec.decode(data.enderChestNbt);
                //?}
                //? if (>=26.1 && <=26.3) {
                /*Tag decoded = NbtCodec.decode(data.enderChestNbt);
*///?}

                //? if (>=1.18 && <=1.21.11) {
                if (!(decoded instanceof NbtList list))

                    //?}
                    //? if (>=26.1 && <=26.3) {
                    /*if (!(decoded instanceof ListTag list))
*///?}
                    throw new IOException("Ender Chest snapshot is not an NBT list");

                //? if (>=1.18 && <=1.21.5) {
                enderChest = list;
                //?}
                //? if (>=1.21.6 && <=1.21.11) {
                /*enderChest =
                                        com.atsukimc.anticrash.runtime.VersionStorage.inventory(
                                                list,
                                                player.getEnderChestInventory().size(),
                                                player.getRegistryManager());
*///?}
                //? if (>=26.1 && <=26.3) {
                /*enderChest =
                                        com.atsukimc.anticrash.runtime.VersionStorage.inventory(
                                                list,
                                                player.getEnderChestInventory().getContainerSize(),
                                                player.registryAccess());
*///?}

            }

            //? if (>=1.18 && <=1.21.11) {
            List<StatusEffectInstance> effects = null;
            //?}
            //? if (>=26.1 && <=26.3) {
            /*List<MobEffectInstance> effects = null;
*///?}

            if (config.playerSnapshot.potionEffects) {
                if (data.effectsNbt == null)
                    throw new IOException("Enabled effect snapshot is missing");
                effects = new ArrayList<>(data.effectsNbt.size());
                for (String encoded : data.effectsNbt) {

                    //? if (>=1.18 && <=1.21.11) {
                    NbtElement decoded = NbtCodec.decode(encoded);
                    //?}
                    //? if (>=26.1 && <=26.3) {
                    /*Tag decoded = NbtCodec.decode(encoded);
*///?}

                    //? if (>=1.18 && <=1.21.11) {
                    if (!(decoded instanceof net.minecraft.nbt.NbtCompound compound))
                    //?}
                    //? if (>=26.1 && <=26.3) {
                    /*if (!(decoded instanceof net.minecraft.nbt.CompoundTag compound))
*///?}
                    {
                        throw new IOException("Potion effect snapshot is not an NBT compound");
                    }

                    //? if (>=1.18 && <=1.21.4) {
                    StatusEffectInstance effect = StatusEffectInstance.fromNbt(compound);
                    //?}
                    //? if (>=1.21.5 && <=1.21.11) {
                    /*StatusEffectInstance effect =
                                                com.atsukimc.anticrash.runtime.VersionNbt.effect(compound);
*///?}
                    //? if (>=26.1 && <=26.3) {
                    /*MobEffectInstance effect =
                                                com.atsukimc.anticrash.runtime.VersionNbt.effect(compound);
*///?}

                    if (effect == null) throw new IOException("Potion effect snapshot is invalid");
                    effects.add(effect);
                }
            }

            //? if (>=1.18 && <=1.21.11) {
            ServerWorld spawnWorld = null;
            //?}
            //? if (>=26.1 && <=26.3) {
            /*ServerLevel spawnWorld = null;
*///?}

            if (config.playerSnapshot.spawnPoint && data.hasSpawnPoint) {
                spawnWorld = findWorld(server, data.spawnDimension);
                if (spawnWorld == null)
                    throw new IOException("Spawn dimension is unavailable: " + data.spawnDimension);
            }
            if (config.playerSnapshot.gamemode
                    && data.gamemode != null
                    && !Set.of("survival", "creative", "adventure", "spectator")
                            .contains(data.gamemode)) {
                throw new IOException("Invalid player gamemode: " + data.gamemode);
            }

            if (config.playerSnapshot.dimension
                    || config.playerSnapshot.position
                    || config.playerSnapshot.rotation) {
                double x = config.playerSnapshot.position ? data.x : player.getX(),
                        y = config.playerSnapshot.position ? data.y : player.getY(),
                        z = config.playerSnapshot.position ? data.z : player.getZ();

                //? if (>=1.18 && <=1.21.11) {
                float yaw = config.playerSnapshot.rotation ? data.yaw : player.getYaw(0),
                        pitch = config.playerSnapshot.rotation ? data.pitch : player.getPitch(0);
                //?}
                //? if (>=26.1 && <=26.3) {
                /*float yaw = config.playerSnapshot.rotation ? data.yaw : player.getViewYRot(0),
                                        pitch = config.playerSnapshot.rotation ? data.pitch : player.getViewXRot(0);
*///?}

                // teleport() also handles the same-world case through requestTeleport, which tells
                // the
                // client its new position; updatePositionAndAngles alone left the client elsewhere.

                //? if (>=1.18 && <=1.21.1) {
                player.teleport(target, x, y, z, yaw, pitch);
                //?}
                //? if (>=1.21.2 && <=1.21.11) {
                /*player.teleport(target, x, y, z, java.util.Set.of(), yaw, pitch, false);
*///?}
                //? if (>=26.1 && <=26.3) {
                /*player.teleportTo(target, x, y, z, java.util.Set.of(), yaw, pitch, false);
*///?}

            }
            if (config.playerSnapshot.gamemode && data.gamemode != null)

                //? if (>=1.18 && <=1.21.4) {
                player.changeGameMode(
                        net.minecraft.world.GameMode.byName(
                                data.gamemode, player.interactionManager.getGameMode()));
            //?}
            //? if (>=1.21.5 && <=1.21.11) {
            /*player.changeGameMode(
                                    net.minecraft.world.GameMode.byId(
                                            data.gamemode, player.interactionManager.getGameMode()));
*///?}
            //? if (>=26.1 && <=26.3) {
            /*player.setGameMode(
                                    net.minecraft.world.level.GameType.byName(
                                            data.gamemode, player.gameMode.getGameModeForPlayer()));
*///?}

            if (abilities != null) {

                //? if (>=1.18 && <=1.21.5) {
                player.getAbilities().readNbt(abilities);
                //?}
                //? if (>=1.21.6 && <=1.21.11) {
                /*player.getAbilities().unpack(abilities);
*///?}
                //? if (>=26.1 && <=26.3) {
                /*player.getAbilities().apply(abilities);
*///?}

                //? if (>=1.18 && <=1.21.11) {
                player.sendAbilitiesUpdate();
                //?}
                //? if (>=26.1 && <=26.3) {
                /*player.onUpdateAbilities();
*///?}

            }
            boolean inventoryChanged =
                    data.inventory != null
                            && data.inventory.restore(
                                    player,
                                    config.playerSnapshot.inventoryMain,
                                    config.playerSnapshot.inventoryArmor,
                                    config.playerSnapshot.inventoryOffhand,
                                    config.playerSnapshot.selectedHotbarSlot);
            if (config.playerSnapshot.experienceLevel)

                //? if (>=1.18 && <=1.21.11) {
                player.setExperienceLevel(data.experienceLevel);
            //?}
            //? if (>=26.1 && <=26.3) {
            /*player.setExperienceLevels(data.experienceLevel);
*///?}

            if (config.playerSnapshot.experienceProgress)
                player.experienceProgress = data.experienceProgress;
            if (config.playerSnapshot.experienceTotal)
                player.totalExperience = data.experienceTotal;

            //? if (>=1.18 && <=1.20.4) {
            if (enderChest != null) player.getEnderChestInventory().readNbtList(enderChest);
            //?}
            //? if (>=1.20.5 && <=26.3) {
            /*if (enderChest != null)
*///?}
            //? if (>=1.20.5 && <=1.21.5) {
            /*player.getEnderChestInventory()
                                    .readNbtList(enderChest, player.getRegistryManager());
*///?}
            //? if (>=1.21.6 && <=26.3) {
            /*com.atsukimc.anticrash.runtime.VersionStorage.applyInventory(
                                    player.getEnderChestInventory(), enderChest);
*///?}

            if (config.playerSnapshot.health) player.setHealth(data.health);
            if (config.playerSnapshot.absorption) player.setAbsorptionAmount(data.absorption);

            //? if (>=1.18 && <=1.21.11) {
            if (config.playerSnapshot.foodLevel)
                player.getHungerManager().setFoodLevel(data.foodLevel);
            //?}
            //? if (>=26.1 && <=26.3) {
            /*if (config.playerSnapshot.foodLevel) player.getFoodData().setFoodLevel(data.foodLevel);
*///?}

            if (config.playerSnapshot.foodSaturation)

                //? if (>=1.18 && <=1.21.11) {
                player.getHungerManager().setSaturationLevel(data.foodSaturation);
            //?}
            //? if (>=26.1 && <=26.3) {
            /*player.getFoodData().setSaturation(data.foodSaturation);
*///?}

            if (config.playerSnapshot.foodExhaustion)

                //? if (>=1.18 && <=1.21.1) {
                player.getHungerManager().setExhaustion(data.foodExhaustion);
            //?}
            //? if (>=1.21.2 && <=1.21.11) {
            /*((com.atsukimc.anticrash.mixin.HungerManagerAccessorMixin)
                                            player.getHungerManager())
                                    .anticrash$setExhaustion(data.foodExhaustion);
*///?}
            //? if (>=26.1 && <=26.3) {
            /*((com.atsukimc.anticrash.mixin.HungerManagerAccessorMixin) player.getFoodData())
                                    .anticrash$setExhaustion(data.foodExhaustion);
*///?}

            if (effects != null) {

                //? if (>=1.18 && <=1.21.11) {
                player.clearStatusEffects();
                //?}
                //? if (>=26.1 && <=26.3) {
                /*player.removeAllEffects();
*///?}

                //? if (>=1.18 && <=1.21.11) {
                for (StatusEffectInstance effect : effects) player.addStatusEffect(effect);
                //?}
                //? if (>=26.1 && <=26.3) {
                /*for (MobEffectInstance effect : effects) player.addEffect(effect);
*///?}

            }
            if (config.playerSnapshot.velocity)

                //? if (>=1.18 && <=1.21.11) {
                player.setVelocity(data.velocityX, data.velocityY, data.velocityZ);
            //?}
            //? if (>=26.1 && <=26.3) {
            /*player.setDeltaMovement(data.velocityX, data.velocityY, data.velocityZ);
*///?}

            if (config.playerSnapshot.fallDistance) player.fallDistance = data.fallDistance;
            if (config.playerSnapshot.fireTicks)
                //? if (>=1.18 && <=1.21.11) {
                player.setFireTicks(data.fireTicks);
            //?}
            //? if (>=26.1 && <=26.3) {
            /*player.setRemainingFireTicks(data.fireTicks);
*///?}

            if (config.playerSnapshot.air)
                //? if (>=1.18 && <=1.21.11) {
                player.setAir(data.air);
            //?}
            //? if (>=26.1 && <=26.3) {
            /*player.setAirSupply(data.air);
*///?}

            if (spawnWorld != null)

                //? if (>=1.18 && <=1.21.4) {
                player.setSpawnPoint(
                        spawnWorld.getRegistryKey(),
                        new BlockPos(data.spawnX, data.spawnY, data.spawnZ),
                        data.spawnAngle,
                        data.spawnForced,
                        false);
            //?}
            //? if (>=1.21.5 && <=1.21.8) {
            /*player.setSpawnPoint(
                                    new ServerPlayerEntity.Respawn(
                                            spawnWorld.getRegistryKey(),
                                            new BlockPos(data.spawnX, data.spawnY, data.spawnZ),
                                            data.spawnAngle,
                                            data.spawnForced),
                                    false);
*///?}
            //? if (>=1.21.9 && <=1.21.11) {
            /*player.setSpawnPoint(
                                    new ServerPlayerEntity.Respawn(
                                            net.minecraft.world.WorldProperties.SpawnPoint.create(
                                                    spawnWorld.getRegistryKey(),
                                                    new BlockPos(data.spawnX, data.spawnY, data.spawnZ),
                                                    data.spawnAngle,
                                                    data.spawnPitch),
                                            data.spawnForced),
                                    false);
*///?}
            //? if (>=26.1 && <=26.3) {
            /*player.setRespawnPosition(
                                    new ServerPlayer.RespawnConfig(
                                            net.minecraft.world.level.storage.LevelData.RespawnData.of(
                                                    spawnWorld.dimension(),
                                                    new BlockPos(data.spawnX, data.spawnY, data.spawnZ),
                                                    data.spawnAngle,
                                                    data.spawnPitch),
                                            data.spawnForced),
                                    false);
*///?}

            if (inventoryChanged)
                //? if (>=1.18 && <=1.21.11) {
                player.playerScreenHandler.syncState();
            //?}
            //? if (>=26.1 && <=26.3) {
            /*player.inventoryMenu.sendAllDataToRemote();
*///?}

            //? if (>=1.18 && <=1.21.11) {
            restored.add(player.getUuid());
            //?}
            //? if (>=26.1 && <=26.3) {
            /*restored.add(player.getUUID());
*///?}

            logger.normalKey("player.restored", data.name);
            return true;
        } catch (Exception exception) {
            logger.importantKey("player.restore_failed", data.name, exception.getMessage());
            return false;
        }
    }

    //? if (>=1.18 && <=1.21.11) {
    private static ServerWorld findWorld(MinecraftServer server, String id)
                //?}
                //? if (>=26.1 && <=26.3) {
                /*private static ServerLevel findWorld(MinecraftServer server, String id)
*///?}
            {
        if (id == null) return null;

        //? if (>=1.18 && <=1.21.11) {
        for (ServerWorld world : server.getWorlds())
            if (world.getRegistryKey().getValue().toString().equals(id))
                //?}
                //? if (>=26.1 && <=26.3) {
                /*for (ServerLevel world : server.getAllLevels())
                            if (world.dimension().identifier().toString().equals(id))
*///?}
                return world;
        return null;
    }

    private void validateFiniteFields(PlayerSnapshotData data) throws IOException {
        if (config.playerSnapshot.position
                && !(Double.isFinite(data.x)
                        && Double.isFinite(data.y)
                        && Double.isFinite(data.z))) {
            throw new IOException("Player snapshot position is not finite");
        }
        if (config.playerSnapshot.rotation
                && !(Float.isFinite(data.yaw) && Float.isFinite(data.pitch))) {
            throw new IOException("Player snapshot rotation is not finite");
        }
        if (config.playerSnapshot.experienceProgress && !Float.isFinite(data.experienceProgress))
            throw new IOException("Experience progress is not finite");
        if (config.playerSnapshot.health && !Float.isFinite(data.health))
            throw new IOException("Health is not finite");
        if (config.playerSnapshot.absorption && !Float.isFinite(data.absorption))
            throw new IOException("Absorption is not finite");
        if (config.playerSnapshot.foodSaturation && !Float.isFinite(data.foodSaturation))
            throw new IOException("Food saturation is not finite");
        if (config.playerSnapshot.foodExhaustion && !Float.isFinite(data.foodExhaustion))
            throw new IOException("Food exhaustion is not finite");
        if (config.playerSnapshot.velocity
                && !(Double.isFinite(data.velocityX)
                        && Double.isFinite(data.velocityY)
                        && Double.isFinite(data.velocityZ)))
            throw new IOException("Velocity is not finite");

        //? if (>=1.18 && <=1.21.4) {
        if (config.playerSnapshot.fallDistance && !Float.isFinite(data.fallDistance))

            //?}
            //? if (>=1.21.5 && <=26.3) {
            /*if (config.playerSnapshot.fallDistance && !Double.isFinite(data.fallDistance))
*///?}
            throw new IOException("Fall distance is not finite");

        //? if (>=1.18 && <=1.21.8) {
        if (config.playerSnapshot.spawnPoint
                && data.hasSpawnPoint
                && !Float.isFinite(data.spawnAngle))

            //?}
            //? if (>=1.21.9 && <=26.3) {
            /*if (config.playerSnapshot.spawnPoint
                            && data.hasSpawnPoint
                            && (!Float.isFinite(data.spawnAngle) || !Float.isFinite(data.spawnPitch)))
*///?}
            throw new IOException("Spawn angle is not finite");
        if (config.playerSnapshot.experienceProgress
                && (data.experienceProgress < 0.0F || data.experienceProgress > 1.0F))
            throw new IOException("Experience progress is outside 0..1");
        if (config.playerSnapshot.experienceLevel && data.experienceLevel < 0)
            throw new IOException("Experience level is negative");
        if (config.playerSnapshot.experienceTotal && data.experienceTotal < 0)
            throw new IOException("Total experience is negative");
    }
}
