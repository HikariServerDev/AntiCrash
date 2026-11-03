package com.atsukimc.anticrash.integration.carpet;

import com.atsukimc.anticrash.command.RecoveryCommandPolicy;
import com.atsukimc.anticrash.config.AntiCrashConfig;
import com.atsukimc.anticrash.logging.AntiCrashLogger;
import com.atsukimc.anticrash.player.InventorySnapshot;
import com.atsukimc.anticrash.player.NbtCodec;
import com.atsukimc.anticrash.util.ReflectionUtil;

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.entity.effect.StatusEffectInstance;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.nbt.Tag;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.nbt.NbtElement;
//?}

//? if (>=1.19.3 && <=1.21.11) {
/*import net.minecraft.registry.RegistryKey;
*///?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.resources.ResourceKey;
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

//? if (>=1.18 && <=1.19.2) {
import net.minecraft.util.registry.RegistryKey;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.effect.MobEffectInstance;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.GameMode;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.GameType;
*///?}

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Optional Carpet adapter. No Carpet class is loaded until the adapter is used. */
public final class CarpetIntegration {
    private static final String[] FAKE_CLASSES = {
        "carpet.patches.EntityPlayerMPFake", "carpet.fakePlayer.EntityPlayerMPFake"
    };
    private static boolean checked;
    private static boolean available;

    private CarpetIntegration() {}

    public static void initialize(AntiCrashLogger logger) {
        if (checked) return;
        checked = true;
        for (String className : FAKE_CLASSES)
            try {
                Class.forName(className, false, CarpetIntegration.class.getClassLoader());
                available = true;
                break;
            } catch (ClassNotFoundException | LinkageError ignored) {
            }
        if (!available) logger.normalKey("carpet.not_installed");
        else logger.normalKey("carpet.detected");
    }

    /**
     * Reads Carpet's global tick speed, or null when Carpet is absent.
     *
     * <p>Unlike the Minecraft classes reached elsewhere in this file, {@code TickSpeed} belongs to
     * Carpet itself, so its member names are not remapped and plain reflection resolves them.
     */
    public static TickSpeedSnapshot captureTickSpeed() {
        Class<?> tickSpeed = tickSpeedClass();
        if (tickSpeed == null) return null;
        try {
            TickSpeedSnapshot snapshot = new TickSpeedSnapshot();
            snapshot.tickrate = tickSpeed.getField("tickrate").getFloat(null);
            snapshot.mspt = tickSpeed.getField("mspt").getFloat(null);
            snapshot.processEntities = tickSpeed.getField("process_entities").getBoolean(null);
            snapshot.paused = (boolean) tickSpeed.getMethod("isPaused").invoke(null);
            snapshot.deepFrozen = (boolean) tickSpeed.getMethod("deeplyFrozen").invoke(null);
            snapshot.timeBias = tickSpeed.getField("time_bias").getLong(null);
            snapshot.timeWarpStartTime = tickSpeed.getField("time_warp_start_time").getLong(null);
            snapshot.timeWarpScheduledTicks =
                    tickSpeed.getField("time_warp_scheduled_ticks").getLong(null);
            snapshot.playerActiveTimeout = tickSpeed.getField("player_active_timeout").getInt(null);
            snapshot.superHot = tickSpeed.getField("is_superHot").getBoolean(null);
            Object advancer = tickSpeed.getField("time_advancerer").get(null);
            Object callback = tickSpeed.getField("tick_warp_callback").get(null);
            Object sender = tickSpeed.getField("tick_warp_sender").get(null);
            snapshot.timeWarpActive =
                    snapshot.timeBias > 0L
                            || snapshot.timeWarpScheduledTicks > 0L
                            || snapshot.timeWarpStartTime != 0L
                            || advancer != null
                            || callback != null
                            || sender != null;
            boolean valid =
                    Float.isFinite(snapshot.tickrate)
                            && snapshot.tickrate > 0.0F
                            && Float.isFinite(snapshot.mspt)
                            && snapshot.mspt > 0.0F
                            && snapshot.playerActiveTimeout >= 0;
            return valid ? snapshot : null;
        } catch (Exception | LinkageError ignored) {
            // A different Carpet build simply contributes no complete tick-speed state. STRICT
            // will reject the checkpoint instead of pretending that missing fields are equal.
            return null;
        }
    }

    /** Whether the optional Carpet runtime is present in this JVM. */
    public static boolean isInstalled() {
        return tickSpeedClass() != null || available;
    }

    /**
     * @return true when the snapshot was applied; false when Carpet or the state is unavailable
     */
    public static boolean restoreTickSpeed(TickSpeedSnapshot snapshot, AntiCrashLogger logger) {
        if (snapshot == null
                || (snapshot.formatVersion != 1 && snapshot.formatVersion != 2)
                || !Float.isFinite(snapshot.tickrate)
                || snapshot.tickrate <= 0.0F) {
            return false;
        }
        if (snapshot.formatVersion >= 2
                && (!Float.isFinite(snapshot.mspt)
                        || snapshot.mspt <= 0.0F
                        || snapshot.playerActiveTimeout < 0)) return false;
        if (snapshot.formatVersion >= 2 && snapshot.timeWarpActive) {
            // Carpet stores the player, command source and callback as live object references.
            // Rebinding those objects after a new ServerWorld is created is not a safe restore.
            logger.importantKey("carpet.time_warp_unsupported");
            return false;
        }
        Class<?> tickSpeed = tickSpeedClass();
        if (tickSpeed == null) return false;
        try {
            // tickrate(float) also recomputes mspt and notifies Carpet's own listeners.
            tickSpeed.getMethod("tickrate", float.class).invoke(null, snapshot.tickrate);
            tickSpeed.getField("process_entities").setBoolean(null, snapshot.processEntities);
            tickSpeed
                    .getMethod("setFrozenState", boolean.class, boolean.class)
                    .invoke(null, snapshot.paused, snapshot.deepFrozen);
            if (snapshot.formatVersion >= 2) {
                tickSpeed.getField("mspt").setFloat(null, snapshot.mspt);
                tickSpeed.getField("time_bias").setLong(null, snapshot.timeBias);
                tickSpeed
                        .getField("time_warp_start_time")
                        .setLong(null, snapshot.timeWarpStartTime);
                tickSpeed
                        .getField("time_warp_scheduled_ticks")
                        .setLong(null, snapshot.timeWarpScheduledTicks);
                tickSpeed
                        .getField("player_active_timeout")
                        .setInt(null, snapshot.playerActiveTimeout);
                tickSpeed.getField("is_superHot").setBoolean(null, snapshot.superHot);
                // An inactive snapshot must not retain callback/source/player objects from the
                // newly booted JVM. These are intentionally cleared, never copied from disk.
                tickSpeed.getField("time_advancerer").set(null, null);
                tickSpeed.getField("tick_warp_callback").set(null, null);
                tickSpeed.getField("tick_warp_sender").set(null, null);
            }
            logger.normalKey("carpet.tickrate_restored", snapshot.tickrate);
            return true;
        } catch (Exception | LinkageError exception) {
            logger.importantKey("carpet.tickrate_restore_failed", String.valueOf(exception));
            return false;
        }
    }

    private static Class<?> tickSpeedClass() {
        try {
            return Class.forName(
                    "carpet.helpers.TickSpeed", true, CarpetIntegration.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError ignored) {
            return null;
        }
    }

    public static String version() {
        try {
            return net.fabricmc.loader.api.FabricLoader.getInstance()
                    .getModContainer("carpet")
                    .map(container -> container.getMetadata().getVersion().getFriendlyString())
                    .orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    //? if (>=1.18 && <=1.21.11) {
    public static boolean isFakePlayer(ServerPlayerEntity player)
                //?}
                //? if (>=26.1 && <=26.3) {
                /*public static boolean isFakePlayer(ServerPlayer player)
*///?}
            {
        String name = player.getClass().getName().toLowerCase(Locale.ROOT);
        return name.contains("entityplayermpfake")
                || name.contains("fakeplayer")
                || name.startsWith("carpet.");
    }

    public static List<FakePlayerSnapshot> capture(
            MinecraftServer server, AntiCrashConfig config, AntiCrashLogger logger) {
        try {
            return capture(server, config, logger, false);
        } catch (IOException impossible) {
            return List.of();
        }
    }

    public static List<FakePlayerSnapshot> captureForCheckpoint(
            MinecraftServer server, AntiCrashConfig config, AntiCrashLogger logger)
            throws IOException {
        // OPTIMIZED retains the historical best-effort behavior. Only STRICT
        // must fail closed when the optional adapter cannot capture live state.
        boolean strict = "STRICT".equalsIgnoreCase(config.recovery.fidelity);
        return capture(server, config, logger, strict);
    }

    /** Strict, throwing capture used by the post-restore equivalence gate. */
    public static List<FakePlayerSnapshot> captureForVerification(
            MinecraftServer server, AntiCrashConfig config, AntiCrashLogger logger)
            throws IOException {
        return capture(server, config, logger, true);
    }

    private static List<FakePlayerSnapshot> capture(
            MinecraftServer server, AntiCrashConfig config, AntiCrashLogger logger, boolean strict)
            throws IOException {
        List<FakePlayerSnapshot> snapshots = new ArrayList<>();
        if (!config.fakePlayer.enabled) {
            // A disabled adapter must not turn live FakePlayers into an apparently complete
            // empty checkpoint.  Their ActionPack/entity state is part of the execution domain;
            // STRICT therefore refuses to publish a checkpoint until the operator enables the
            // adapter (OPTIMIZED keeps the historical opt-out behavior).

            //? if (>=1.18 && <=1.21.11) {
            boolean fakePlayerPresent =
                    server.getPlayerManager().getPlayerList().stream()
                            .anyMatch(CarpetIntegration::isFakePlayer);
            //?}
            //? if (>=26.1 && <=26.3) {
            /*boolean fakePlayerPresent =
                                server.getPlayerList().getPlayers().stream()
                                        .anyMatch(CarpetIntegration::isFakePlayer);
*///?}

            if (strict && fakePlayerPresent) {
                throw new IOException(
                        "STRICT FakePlayer capture is disabled while FakePlayers are online");
            }
            return snapshots;
        }
        if (!available) {
            // TickSpeed may be present even when this build uses an unknown FakePlayer class name.
            // Do not silently turn live fake players into an empty checkpoint just because the
            // optional adapter could not identify its creation API.

            //? if (>=1.18 && <=1.21.11) {
            boolean fakePlayerPresent =
                    server.getPlayerManager().getPlayerList().stream()
                            .anyMatch(CarpetIntegration::isFakePlayer);
            //?}
            //? if (>=26.1 && <=26.3) {
            /*boolean fakePlayerPresent =
                                server.getPlayerList().getPlayers().stream()
                                        .anyMatch(CarpetIntegration::isFakePlayer);
*///?}

            if (strict && fakePlayerPresent) {
                throw new IOException(
                        "Carpet FakePlayer adapter is unavailable while FakePlayers are online");
            }
            return snapshots;
        }

        //? if (>=1.18 && <=1.21.11) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList())

            //?}
            //? if (>=26.1 && <=26.3) {
            /*for (ServerPlayer player : server.getPlayerList().getPlayers())
*///?}
            if (isFakePlayer(player))
                try {
                    snapshots.add(captureOne(player, config));
                } catch (Exception exception) {
                    logger.importantKey(
                            "fakeplayer.snapshot_failed",
                            player.getName().getString(),
                            exception.getMessage());
                    if (strict) {
                        if (exception instanceof IOException ioException) throw ioException;
                        throw new IOException(
                                "Could not capture FakePlayer " + player.getName().getString(),
                                exception);
                    }
                }
        return snapshots;
    }

    //? if (>=1.18 && <=1.21.11) {
    private static FakePlayerSnapshot captureOne(ServerPlayerEntity player, AntiCrashConfig config)
            throws Exception
                //?}
                //? if (>=26.1 && <=26.3) {
                /*private static FakePlayerSnapshot captureOne(ServerPlayer player, AntiCrashConfig config)
                            throws Exception
*///?}
            {
        FakePlayerSnapshot data = new FakePlayerSnapshot();

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

        if (config.fakePlayer.snapshot.dimension)

            //? if (>=1.18 && <=1.21.8) {
            data.dimension = player.getWorld().getRegistryKey().getValue().toString();
        //?}
        //? if (>=1.21.9 && <=1.21.11) {
        /*data.dimension = player.getEntityWorld().getRegistryKey().getValue().toString();
*///?}
        //? if (>=26.1 && <=26.3) {
        /*data.dimension = player.level().dimension().identifier().toString();
*///?}

        if (config.fakePlayer.snapshot.position) {
            data.x = player.getX();
            data.y = player.getY();
            data.z = player.getZ();
        }
        if (config.fakePlayer.snapshot.rotation) {

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
        if (config.fakePlayer.snapshot.gamemode)

            //? if (>=1.18 && <=1.21.4) {
            data.gamemode = player.interactionManager.getGameMode().getName();
        //?}
        //? if (>=1.21.5 && <=1.21.11) {
        /*data.gamemode = player.interactionManager.getGameMode().asString();
*///?}
        //? if (>=26.1 && <=26.3) {
        /*data.gamemode = player.gameMode.getGameModeForPlayer().getSerializedName();
*///?}

        if (config.fakePlayer.snapshot.flying) data.flying = player.getAbilities().flying;
        if (config.fakePlayer.snapshot.sneaking)
            //? if (>=1.18 && <=1.21.11) {
            data.sneaking = player.isSneaking();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*data.sneaking = player.isShiftKeyDown();
*///?}

        if (config.fakePlayer.snapshot.sprinting) data.sprinting = player.isSprinting();
        if (config.fakePlayer.snapshot.movement) {

            //? if (>=1.18 && <=1.21.11) {
            data.forward = player.forwardSpeed;
            //?}
            //? if (>=26.1 && <=26.3) {
            /*data.forward = player.zza;
*///?}

            //? if (>=1.18 && <=1.21.11) {
            data.strafing = player.sidewaysSpeed;
            //?}
            //? if (>=26.1 && <=26.3) {
            /*data.strafing = player.xxa;
*///?}

            //? if (>=1.21.2 && <=1.21.11) {
            /*data.playerInput = PlayerInputCodec.capture(player.getPlayerInput());
*///?}
            //? if (>=26.1 && <=26.3) {
            /*data.playerInput = PlayerInputCodec.capture(player.getLastClientInput());
*///?}

        }
        if (FakePlayerSnapshotOptions.inventoryRequired(config.fakePlayer.snapshot))
            data.inventory =
                    InventorySnapshot.capture(
                            player,
                            config.fakePlayer.snapshot.inventory,
                            config.fakePlayer.snapshot.inventory,
                            config.fakePlayer.snapshot.inventory,
                            config.fakePlayer.snapshot.selectedSlot);
        if (config.fakePlayer.snapshot.health) data.health = player.getHealth();
        if (config.fakePlayer.snapshot.hunger) {

            //? if (>=1.18 && <=1.21.11) {
            data.hunger = player.getHungerManager().getFoodLevel();
            //?}
            //? if (>=26.1 && <=26.3) {
            /*data.hunger = player.getFoodData().getFoodLevel();
*///?}

            //? if (>=1.18 && <=1.21.11) {
            data.saturation = player.getHungerManager().getSaturationLevel();
            //?}
            //? if (>=26.1 && <=26.3) {
            /*data.saturation = player.getFoodData().getSaturationLevel();
*///?}

            //? if (>=1.18 && <=1.21.1) {
            data.exhaustion = player.getHungerManager().getExhaustion();
            //?}
            //? if (>=1.21.2 && <=1.21.11) {
            /*data.exhaustion =
                                ((com.atsukimc.anticrash.mixin.HungerManagerAccessorMixin)
                                                player.getHungerManager())
                                        .anticrash$getExhaustion();
*///?}
            //? if (>=26.1 && <=26.3) {
            /*data.exhaustion =
                                ((com.atsukimc.anticrash.mixin.HungerManagerAccessorMixin) player.getFoodData())
                                        .anticrash$getExhaustion();
*///?}

        }
        if (config.fakePlayer.snapshot.effects)

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

        //? if (>=1.18 && <=1.21.11) {
        if (player.hasVehicle())
        //?}
        //? if (>=26.1 && <=26.3) {
        /*if (player.isPassenger())
*///?}
        {

            //? if (>=1.18 && <=1.21.11) {
            data.vehicleUuid = player.getVehicle().getUuidAsString();
            //?}
            //? if (>=26.1 && <=26.3) {
            /*data.vehicleUuid = player.getVehicle().getStringUUID();
*///?}

            data.rootVehicleNbt = captureRootVehicle(player);
        }
        captureUseState(player, data);
        Object actionPack = findActionPack(player);
        if (actionPack == null
                && (FakePlayerSnapshotOptions.actionsRequired(config.fakePlayer.snapshot))) {
            throw new IOException("Carpet ActionPack is unavailable for " + data.name);
        }
        if (actionPack != null
                && (FakePlayerSnapshotOptions.actionsRequired(config.fakePlayer.snapshot))) {
            data.actionPackState =
                    ActionPackStateCodec.capture(
                            actionPack,
                            config.fakePlayer.snapshot.actions,
                            config.fakePlayer.snapshot.actionInternalTiming,
                            config.fakePlayer.snapshot.itemUseCooldown,
                            config.fakePlayer.snapshot.blockBreakProgress);
            ActionPackStateCodec.validateSnapshot(
                    data.actionPackState,
                    config.fakePlayer.snapshot.actions,
                    config.fakePlayer.snapshot.actionInternalTiming,
                    config.fakePlayer.snapshot.itemUseCooldown,
                    config.fakePlayer.snapshot.blockBreakProgress);
            if (config.fakePlayer.snapshot.itemUseCooldown) {
                Number cooldown = ActionPackStateCodec.findNumber(actionPack, "itemUseCooldown");
                data.itemUseCooldown = cooldown == null ? null : cooldown.intValue();
            }
            if (config.fakePlayer.snapshot.blockBreakProgress) {
                Number breaking =
                        ActionPackStateCodec.findNumber(actionPack, "blockBreakingProgress");
                data.blockBreakingProgress = breaking == null ? null : breaking.intValue();
            }
        }
        return data;
    }

    public static RestoreSummary restore(
            MinecraftServer server,
            List<FakePlayerSnapshot> snapshots,
            AntiCrashConfig config,
            AntiCrashLogger logger) {
        RestoreSummary summary = new RestoreSummary();
        if (!config.fakePlayer.enabled || snapshots == null || snapshots.isEmpty()) return summary;
        summary.expected = snapshots.size();
        if (!available) {
            logger.importantKey("fakeplayer.snapshots_without_carpet");
            summary.failed = snapshots.size();
            return summary;
        }
        Set<String> uuids = new HashSet<>();
        Set<String> names = new HashSet<>();
        boolean invalid = false;
        for (FakePlayerSnapshot data : snapshots)
            try {
                validate(data, config);
                if (!uuids.add(data.uuid.toLowerCase(Locale.ROOT)))
                    throw new IllegalArgumentException("Duplicate FakePlayer UUID");
                if (!names.add(data.name.toLowerCase(Locale.ROOT)))
                    throw new IllegalArgumentException("Duplicate FakePlayer name");
            } catch (Exception exception) {
                invalid = true;
                logger.importantKey(
                        "fakeplayer.restore_failed",
                        data == null ? "<null>" : data.name,
                        exception.getMessage());
            }
        if (invalid) {
            summary.failed = summary.expected;
            return summary;
        }
        for (FakePlayerSnapshot data : snapshots) {
            try {

                //? if (>=1.18 && <=1.21.11) {
                ServerPlayerEntity player = find(server, data);
                //?}
                //? if (>=26.1 && <=26.3) {
                /*ServerPlayer player = find(server, data);
*///?}

                if (player == null && config.fakePlayer.autoRejoin) player = create(server, data);
                if (player == null || !isFakePlayer(player)) {
                    throw new IllegalStateException("Carpet did not create the FakePlayer");
                }

                //? if (>=1.18 && <=1.21.11) {
                if (!player.getUuidAsString().equalsIgnoreCase(data.uuid))
                //?}
                //? if (>=26.1 && <=26.3) {
                /*if (!player.getStringUUID().equalsIgnoreCase(data.uuid))
*///?}
                {

                    //? if (>=1.18 && <=1.21.11) {
                    throw new IllegalStateException(
                            "FakePlayer UUID does not match the checkpoint: expected "
                                    + data.uuid
                                    + ", actual "
                                    + player.getUuidAsString());
                    //?}
                    //? if (>=26.1 && <=26.3) {
                    /*throw new IllegalStateException(
                                                "FakePlayer UUID does not match the checkpoint: expected "
                                                        + data.uuid
                                                        + ", actual "
                                                        + player.getStringUUID());
*///?}

                }

                //? if (>=1.18 && <=1.21.8) {
                if (!data.name.equals(player.getGameProfile().getName()))
                //?}
                //? if (>=1.21.9 && <=26.3) {
                /*if (!data.name.equals(player.getGameProfile().name()))
*///?}
                {
                    throw new IllegalStateException(
                            "FakePlayer name does not match the checkpoint");
                }
                apply(server, player, data, config);
                summary.ready++;
                logger.normalKey("fakeplayer.restored", data.name);
            } catch (Exception exception) {
                summary.failed++;
                logger.importantKey("fakeplayer.restore_failed", data.name, exception.getMessage());
            }
        }
        return summary;
    }

    /** Performs all portable FakePlayer checks before Block Events or live entities are changed. */
    public static void validateSnapshots(List<FakePlayerSnapshot> snapshots, AntiCrashConfig config)
            throws IOException {
        if (snapshots == null) throw new IOException("FakePlayer snapshot list is missing");
        Set<String> uuids = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (FakePlayerSnapshot data : snapshots) {
            try {
                validate(data, config);
                if (!uuids.add(data.uuid.toLowerCase(Locale.ROOT)))
                    throw new IllegalArgumentException("Duplicate FakePlayer UUID");
                if (!names.add(data.name.toLowerCase(Locale.ROOT)))
                    throw new IllegalArgumentException("Duplicate FakePlayer name");
            } catch (Exception exception) {
                if (exception instanceof IOException ioException) throw ioException;
                throw new IOException(
                        "Invalid FakePlayer snapshot: " + exception.getMessage(), exception);
            }
        }
    }

    //? if (>=1.18 && <=1.21.11) {
    private static ServerPlayerEntity find(MinecraftServer server, FakePlayerSnapshot data)
                //?}
                //? if (>=26.1 && <=26.3) {
                /*private static ServerPlayer find(MinecraftServer server, FakePlayerSnapshot data)
*///?}
            {
        try {
            UUID uuid = UUID.fromString(data.uuid);

            //? if (>=1.18 && <=1.21.11) {
            ServerPlayerEntity byUuid = server.getPlayerManager().getPlayer(uuid);
            //?}
            //? if (>=26.1 && <=26.3) {
            /*ServerPlayer byUuid = server.getPlayerList().getPlayer(uuid);
*///?}

            if (byUuid != null && isFakePlayer(byUuid)) return byUuid;
        } catch (Exception ignored) {
        }

        //? if (>=1.18 && <=1.21.11) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList())

            //?}
            //? if (>=1.18 && <=1.21.8) {
            if (isFakePlayer(player)
                    && player.getGameProfile().getName().equalsIgnoreCase(data.name))
                //?}
                //? if (>=26.1 && <=26.3) {
                /*for (ServerPlayer player : server.getPlayerList().getPlayers())
*///?}
                //? if (>=1.21.9 && <=26.3) {
                /*if (isFakePlayer(player) && player.getGameProfile().name().equalsIgnoreCase(data.name))
*///?}
                return player;
        return null;
    }

    //? if (>=1.18 && <=1.21.11) {
    private static ServerPlayerEntity create(MinecraftServer server, FakePlayerSnapshot data)
            throws IOException
                //?}
                //? if (>=26.1 && <=26.3) {
                /*private static ServerPlayer create(MinecraftServer server, FakePlayerSnapshot data)
                            throws IOException
*///?}
            {

        //? if (>=1.18 && <=1.21.11) {
        if (!server.isOnThread())

            //?}
            //? if (>=26.1 && <=26.3) {
            /*if (!server.isSameThread())
*///?}
            throw new IOException("FakePlayer restore must run on the server thread");
        UUID expectedUuid = UUID.fromString(data.uuid);
        // PlayerManager.onPlayerConnect may disconnect an existing UUID. Reject collisions before
        // calling Carpet so restoring a bot cannot evict or overwrite a real player's session.

        //? if (>=1.18 && <=1.21.11) {
        if (server.getPlayerManager().getPlayer(expectedUuid) != null
                || server.getPlayerManager().getPlayer(data.name) != null)
        //?}
        //? if (>=26.1 && <=26.3) {
        /*if (server.getPlayerList().getPlayer(expectedUuid) != null
                        || server.getPlayerList().getPlayerByName(data.name) != null)
*///?}
        {
            throw new IOException("FakePlayer checkpoint identity is already in use: " + data.name);
        }

        //? if (>=1.18 && <=1.21.8) {
        try (FakePlayerProfileScope ignored =
                FakePlayerProfileScope.open(
                        server.getUserCache(), server, expectedUuid, data.name))
        //?}
        //? if (>=1.21.9 && <=1.21.11) {
        /*try (FakePlayerProfileScope ignored =
                        FakePlayerProfileScope.open(
                                server.getApiServices().nameToIdCache(), server, expectedUuid, data.name))
*///?}
        //? if (>=26.1 && <=26.3) {
        /*try (FakePlayerProfileScope ignored =
                        FakePlayerProfileScope.open(
                                server.services().nameToIdCache(), server, expectedUuid, data.name))
*///?}
        {
            return createWithCheckpointProfile(server, data);
        }
    }

    //? if (>=1.18 && <=1.21.11) {
    private static ServerPlayerEntity createWithCheckpointProfile(
            MinecraftServer server, FakePlayerSnapshot data) throws IOException
                //?}
                //? if (>=26.1 && <=26.3) {
                /*private static ServerPlayer createWithCheckpointProfile(
                            MinecraftServer server, FakePlayerSnapshot data) throws IOException
*///?}
            {

        //? if (>=1.18 && <=1.21.11) {
        ServerWorld world = findWorld(server, data.dimension);
        //?}
        //? if (>=26.1 && <=26.3) {
        /*ServerLevel world = findWorld(server, data.dimension);
*///?}

        if (world == null)
            //? if (>=1.18 && <=1.21.11) {
            world = server.getOverworld();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*world = server.overworld();
*///?}

        //? if (>=1.18 && <=1.21.4) {
        GameMode mode =
                data.gamemode == null
                        ? GameMode.SURVIVAL
                        : GameMode.byName(data.gamemode, GameMode.SURVIVAL);
        //?}
        //? if (>=1.21.5 && <=1.21.11) {
        /*GameMode mode =
                        data.gamemode == null
                                ? GameMode.SURVIVAL
                                : GameMode.byId(data.gamemode, GameMode.SURVIVAL);
*///?}
        //? if (>=26.1 && <=26.3) {
        /*GameType mode =
                        data.gamemode == null
                                ? GameType.SURVIVAL
                                : GameType.byName(data.gamemode, GameType.SURVIVAL);
*///?}

        String unsupportedFactory = null;
        for (String className : FAKE_CLASSES)
            try {
                Class<?> type = Class.forName(className);
                for (Method method : ReflectionUtil.allMethods(type))
                    if (Modifier.isStatic(method.getModifiers())
                            && (method.getName().equals("createFake")
                                    || method.getName().equals("createFakePlayer"))) {
                        Object[] args =
                                arguments(method.getParameterTypes(), server, world, data, mode);
                        if (args == null) {
                            unsupportedFactory = method.toGenericString();
                            continue;
                        }
                        Object result = method.invoke(null, args);

                        //? if (>=1.18 && <=1.21.11) {
                        if (result instanceof ServerPlayerEntity player)
                            //?}
                            //? if (>=26.1 && <=26.3) {
                            /*if (result instanceof ServerPlayer player)
*///?}
                            return player;

                        //? if (>=1.18 && <=1.21.11) {
                        ServerPlayerEntity found = find(server, data);
                        //?}
                        //? if (>=26.1 && <=26.3) {
                        /*ServerPlayer found = find(server, data);
*///?}

                        return found;
                    }
            } catch (java.lang.reflect.InvocationTargetException failure) {
                throw new IOException("Carpet FakePlayer factory failed", failure.getCause());
            } catch (Exception | LinkageError ignored) {
            }
        if (unsupportedFactory != null)
            throw new IOException(
                    "Unsupported Carpet FakePlayer factory signature: " + unsupportedFactory);
        // Command fallback: only characters a Brigadier word accepts, so the name cannot alter it.
        if (data.name != null && data.name.matches("[A-Za-z0-9_.+-]{1,16}")) {
            try {

                //? if (>=1.18 && <=1.19) {
                RecoveryCommandPolicy.runInternal(() ->
                        server.getCommandManager()
                                .execute(
                                        server.getCommandSource(),
                                        "player " + data.name + " spawn"));
                //?}
                //? if (>=1.19.1 && <=1.20.2) {
                /*RecoveryCommandPolicy.runInternal(() ->
                                        server.getCommandManager()
                                                .executeWithPrefix(
                                                        server.getCommandSource(),
                                                        "player " + data.name + " spawn"));
*///?}
                //? if (>=1.20.3 && <=26.3) {
                /*RecoveryCommandPolicy.runInternal(() -> {
*///?}
                //? if (>=1.20.3 && <=1.21.9) {
                /*server.getCommandManager()
                                            .executeWithPrefix(
                                                    server.getCommandSource(), "player " + data.name + " spawn");
*///?}
                //? if (>=1.21.10 && <=1.21.11) {
                /*server.getCommandManager()
                                            .parseAndExecute(
                                                    server.getCommandSource(), "player " + data.name + " spawn");
*///?}
                //? if (>=26.1 && <=26.3) {
                /*server.getCommands()
                                            .performPrefixedCommand(
                                                    server.createCommandSourceStack(),
                                                    "player " + data.name + " spawn");
*///?}

                //? if (>=1.20.3 && <=26.3) {
                /*return null;
                                });
*///?}

            } catch (Exception | LinkageError ignored) {
            }
        }
        return find(server, data);
    }

    //? if (>=1.18 && <=1.21.11) {
    private static Object[] arguments(
            Class<?>[] types,
            MinecraftServer server,
            ServerWorld world,
            FakePlayerSnapshot data,
            GameMode mode)
                //?}
                //? if (>=26.1 && <=26.3) {
                /*private static Object[] arguments(
                            Class<?>[] types,
                            MinecraftServer server,
                            ServerLevel world,
                            FakePlayerSnapshot data,
                            GameType mode)
*///?}
            {

        //? if (>=1.18 && <=1.21.11) {
        FakePlayerFactoryContract contract =
                FakePlayerFactoryContract.resolve(
                        types,
                        MinecraftServer.class,
                        net.minecraft.util.math.Vec3d.class,
                        RegistryKey.class,
                        GameMode.class);
        //?}
        //? if (>=26.1 && <=26.3) {
        /*FakePlayerFactoryContract contract =
                        FakePlayerFactoryContract.resolve(
                                types,
                                MinecraftServer.class,
                                net.minecraft.world.phys.Vec3.class,
                                ResourceKey.class,
                                GameType.class);
*///?}

        if (contract == null) return null;

        //? if (>=1.18 && <=1.21.11) {
        Object dimension = world == null ? null : world.getRegistryKey();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*Object dimension = world == null ? null : world.dimension();
*///?}

        Object[] arguments =
                switch (contract) {
                    case SCALAR ->
                            new Object[] {
                                data.name,
                                server,
                                data.x,
                                data.y,
                                data.z,
                                (double) data.yaw,
                                (double) data.pitch,
                                dimension,
                                mode,
                                data.flying
                            };

                    //? if (>=1.18 && <=1.21.11) {
                    case VECTOR, VECTOR_WITH_FAILURE_HANDLER ->
                            new Object[] {
                                data.name,
                                server,
                                new net.minecraft.util.math.Vec3d(data.x, data.y, data.z),
                                (double) data.yaw,
                                (double) data.pitch,
                                dimension,
                                mode,
                                data.flying
                            };
                        //?}
                        //? if (>=26.1 && <=26.3) {
                        /*case VECTOR, VECTOR_WITH_FAILURE_HANDLER ->
                                                    new Object[] {
                                                        data.name,
                                                        server,
                                                        new net.minecraft.world.phys.Vec3(data.x, data.y, data.z),
                                                        (double) data.yaw,
                                                        (double) data.pitch,
                                                        dimension,
                                                        mode,
                                                        data.flying
                                                    };
*///?}

                };
        return contract.withFailureHandler(arguments);
    }

    //? if (>=1.18 && <=1.21.11) {
    private static void apply(
            MinecraftServer server,
            ServerPlayerEntity player,
            FakePlayerSnapshot data,
            AntiCrashConfig config)
            throws IOException
                //?}
                //? if (>=1.18 && <=1.21.1) {
            {

        //?}
        //? if (>=26.1 && <=26.3) {
        /*private static void apply(
                    MinecraftServer server,
                    ServerPlayer player,
                    FakePlayerSnapshot data,
                    AntiCrashConfig config)
                    throws IOException
*///?}
        //? if (>=1.21.2 && <=26.3) {
        /*{
                // Validate new input state before teleport, vehicle changes, or inventory mutation.
                if (config.fakePlayer.snapshot.movement) PlayerInputCodec.restore(data.playerInput);
*///?}

        //? if (>=1.18 && <=1.21.8) {
        ServerWorld world = (ServerWorld) player.getWorld();
        //?}
        //? if (>=1.21.9 && <=1.21.11) {
        /*ServerWorld world = (ServerWorld) player.getEntityWorld();
*///?}
        //? if (>=26.1 && <=26.3) {
        /*ServerLevel world = (ServerLevel) player.level();
*///?}

        if (config.fakePlayer.snapshot.dimension) {
            world = findWorld(server, data.dimension);
            if (world == null)
                throw new IllegalArgumentException(
                        "FakePlayer dimension is unavailable: " + data.dimension);
        }
        boolean inventoryEnabled =
                FakePlayerSnapshotOptions.inventoryRequired(config.fakePlayer.snapshot);
        if (inventoryEnabled) {
            if (data.inventory == null)
                throw new IOException("Enabled FakePlayer inventory snapshot is missing");
            data.inventory.validate(
                    player,
                    config.fakePlayer.snapshot.inventory,
                    config.fakePlayer.snapshot.inventory,
                    config.fakePlayer.snapshot.inventory,
                    config.fakePlayer.snapshot.selectedSlot);
        }

        //? if (>=1.18 && <=1.21.11) {
        List<StatusEffectInstance> effects = null;
        //?}
        //? if (>=26.1 && <=26.3) {
        /*List<MobEffectInstance> effects = null;
*///?}

        if (config.fakePlayer.snapshot.effects) {
            if (data.effectsNbt == null)
                throw new IOException("Enabled FakePlayer effect snapshot is missing");
            effects = new ArrayList<>(data.effectsNbt.size());
            for (String encoded : data.effectsNbt) {

                //? if (>=1.18 && <=1.21.11) {
                NbtElement element = NbtCodec.decode(encoded);
                //?}
                //? if (>=26.1 && <=26.3) {
                /*Tag element = NbtCodec.decode(encoded);
*///?}

                //? if (>=1.18 && <=1.21.11) {
                if (!(element instanceof net.minecraft.nbt.NbtCompound compound))

                    //?}
                    //? if (>=26.1 && <=26.3) {
                    /*if (!(element instanceof net.minecraft.nbt.CompoundTag compound))
*///?}
                    throw new IOException("FakePlayer effect snapshot is not an NBT compound");

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

                if (effect == null) throw new IOException("FakePlayer effect snapshot is invalid");
                effects.add(effect);
            }
        }
        boolean actionRequested =
                FakePlayerSnapshotOptions.actionsRequired(config.fakePlayer.snapshot);
        Object actionPack = findActionPack(player);
        if (actionRequested && (data.actionPackState == null || actionPack == null))
            throw new IOException("Enabled FakePlayer ActionPack snapshot is missing");
        if (actionRequested)
            ActionPackStateCodec.validateSnapshot(
                    data.actionPackState,
                    config.fakePlayer.snapshot.actions,
                    config.fakePlayer.snapshot.actionInternalTiming,
                    config.fakePlayer.snapshot.itemUseCooldown,
                    config.fakePlayer.snapshot.blockBreakProgress);

        //? if (>=1.18 && <=1.21.11) {
        net.minecraft.entity.Entity vehicle = null;
        //?}
        //? if (>=26.1 && <=26.3) {
        /*net.minecraft.world.entity.Entity vehicle = null;
*///?}

        if (data.vehicleUuid != null) {
            UUID vehicleId;
            try {
                vehicleId = UUID.fromString(data.vehicleUuid);
            } catch (Exception exception) {
                throw new IOException("Invalid FakePlayer vehicle UUID", exception);
            }
            if (data.rootVehicleNbt != null) {
                vehicle = restoreRootVehicle(server, world, player, data.rootVehicleNbt, vehicleId);
            } else {
                // A snapshot from before the vehicle was captured: whatever the player data held.

                //? if (>=1.18 && <=1.21.11) {
                for (ServerWorld candidate : server.getWorlds())
                //?}
                //? if (>=26.1 && <=26.3) {
                /*for (ServerLevel candidate : server.getAllLevels())
*///?}
                {
                    vehicle = candidate.getEntity(vehicleId);
                    if (vehicle != null) break;
                }
            }
            if (vehicle == null)
                throw new IOException("FakePlayer vehicle is unavailable: " + data.vehicleUuid);
        } else
        //? if (>=1.18 && <=1.21.11) {
        if (player.hasVehicle())
        //?}
        //? if (>=26.1 && <=26.3) {
        /*if (player.isPassenger())
*///?}
        {
            // Not riding at the checkpoint: the vehicle the player data put it on is from another
            // time.
            discardStaleVehicle(player);
        }
        if (config.fakePlayer.snapshot.position
                || config.fakePlayer.snapshot.rotation
                || config.fakePlayer.snapshot.dimension) {
            double x = config.fakePlayer.snapshot.position ? data.x : player.getX(),
                    y = config.fakePlayer.snapshot.position ? data.y : player.getY(),
                    z = config.fakePlayer.snapshot.position ? data.z : player.getZ();

            //? if (>=1.18 && <=1.21.11) {
            float yaw = config.fakePlayer.snapshot.rotation ? data.yaw : player.getYaw(0),
                    pitch = config.fakePlayer.snapshot.rotation ? data.pitch : player.getPitch(0);
            //?}
            //? if (>=26.1 && <=26.3) {
            /*float yaw = config.fakePlayer.snapshot.rotation ? data.yaw : player.getViewYRot(0),
                                pitch =
                                        config.fakePlayer.snapshot.rotation
                                                ? data.pitch
                                                : player.getViewXRot(0);
*///?}

            //? if (>=1.18 && <=1.21.8) {
            if (config.fakePlayer.snapshot.dimension && player.getWorld() != world)

                //?}
                //? if (>=1.18 && <=1.21.1) {
                player.teleport(world, x, y, z, yaw, pitch);
            //?}
            //? if (>=1.21.9 && <=1.21.11) {
            /*if (config.fakePlayer.snapshot.dimension && player.getEntityWorld() != world)
*///?}
            //? if (>=1.21.2 && <=1.21.11) {
            /*player.teleport(world, x, y, z, java.util.Set.of(), yaw, pitch, false);
*///?}
            //? if (>=26.1 && <=26.3) {
            /*if (config.fakePlayer.snapshot.dimension && player.level() != world)
                            player.teleportTo(world, x, y, z, java.util.Set.of(), yaw, pitch, false);
*///?}

            else {

                //? if (>=1.18 && <=1.21.11) {
                player.updatePositionAndAngles(x, y, z, yaw, pitch);
                //?}
                //? if (>=26.1 && <=26.3) {
                /*player.absSnapTo(x, y, z, yaw, pitch);
*///?}

                // A FakePlayer's player ticket follows its tracked chunk position. Move it now so a
                // FakePlayer that keeps chunks loaded does so at its checkpoint position
                // immediately.

                //? if (>=1.18 && <=1.21.8) {
                ((ServerWorld) player.getWorld()).getChunkManager().updatePosition(player);
                //?}
                //? if (>=1.21.9 && <=1.21.11) {
                /*((ServerWorld) player.getEntityWorld()).getChunkManager().updatePosition(player);
*///?}
                //? if (>=26.1 && <=26.3) {
                /*((ServerLevel) player.level()).getChunkSource().move(player);
*///?}

            }
        }
        if (config.fakePlayer.snapshot.gamemode && data.gamemode != null)

            //? if (>=1.18 && <=1.21.4) {
            player.changeGameMode(
                    GameMode.byName(data.gamemode, player.interactionManager.getGameMode()));
        //?}
        //? if (>=1.21.5 && <=1.21.11) {
        /*player.changeGameMode(
                            GameMode.byId(data.gamemode, player.interactionManager.getGameMode()));
*///?}
        //? if (>=26.1 && <=26.3) {
        /*player.setGameMode(
                            GameType.byName(data.gamemode, player.gameMode.getGameModeForPlayer()));
*///?}

        if (inventoryEnabled)
            data.inventory.restore(
                    player,
                    config.fakePlayer.snapshot.inventory,
                    config.fakePlayer.snapshot.inventory,
                    config.fakePlayer.snapshot.inventory,
                    config.fakePlayer.snapshot.selectedSlot);
        if (config.fakePlayer.snapshot.flying) {
            player.getAbilities().flying = data.flying;

            //? if (>=1.18 && <=1.21.11) {
            player.sendAbilitiesUpdate();
            //?}
            //? if (>=26.1 && <=26.3) {
            /*player.onUpdateAbilities();
*///?}

        }
        if (config.fakePlayer.snapshot.sneaking) {

            //? if (>=1.18 && <=1.21.11) {
            player.setSneaking(data.sneaking);
            //?}
            //? if (>=26.1 && <=26.3) {
            /*player.setShiftKeyDown(data.sneaking);
*///?}

        }
        if (config.fakePlayer.snapshot.sprinting) {
            player.setSprinting(data.sprinting);
        }

        //? if (>=1.18 && <=1.21.1) {
        if (config.fakePlayer.snapshot.movement)
            player.updateInput(data.strafing, data.forward, false, data.sneaking);
        //?}
        //? if (>=1.21.2 && <=26.3) {
        /*if (config.fakePlayer.snapshot.movement) {
*///?}
        //? if (>=1.21.2 && <=1.21.11) {
        /*player.setPlayerInput(PlayerInputCodec.restore(data.playerInput));
*///?}
        //? if (>=26.1 && <=26.3) {
        /*player.setLastClientInput(PlayerInputCodec.restore(data.playerInput));
*///?}

        //? if (>=1.21.2 && <=1.21.11) {
        /*player.sidewaysSpeed = data.strafing;
*///?}
        //? if (>=26.1 && <=26.3) {
        /*player.xxa = data.strafing;
*///?}

        //? if (>=1.21.2 && <=1.21.11) {
        /*player.forwardSpeed = data.forward;
*///?}
        //? if (>=26.1 && <=26.3) {
        /*player.zza = data.forward;
*///?}
        //? if (>=1.21.2 && <=26.3) {

        /*}
*///?}

        if (config.fakePlayer.snapshot.health) player.setHealth(data.health);
        if (config.fakePlayer.snapshot.hunger) {

            //? if (>=1.18 && <=1.21.11) {
            player.getHungerManager().setFoodLevel(data.hunger);
            //?}
            //? if (>=26.1 && <=26.3) {
            /*player.getFoodData().setFoodLevel(data.hunger);
*///?}

            //? if (>=1.18 && <=1.21.11) {
            player.getHungerManager().setSaturationLevel(data.saturation);
            //?}
            //? if (>=26.1 && <=26.3) {
            /*player.getFoodData().setSaturation(data.saturation);
*///?}

            //? if (>=1.18 && <=1.21.1) {
            player.getHungerManager().setExhaustion(data.exhaustion);
            //?}
            //? if (>=1.21.2 && <=1.21.11) {
            /*((com.atsukimc.anticrash.mixin.HungerManagerAccessorMixin) player.getHungerManager())
                                .anticrash$setExhaustion(data.exhaustion);
*///?}
            //? if (>=26.1 && <=26.3) {
            /*((com.atsukimc.anticrash.mixin.HungerManagerAccessorMixin) player.getFoodData())
                                .anticrash$setExhaustion(data.exhaustion);
*///?}

        }
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
        restoreUseState(player, data);
        if (data.actionPackState != null)
            ActionPackStateCodec.restore(
                    actionPack,
                    data.actionPackState,
                    config.fakePlayer.snapshot.actions,
                    config.fakePlayer.snapshot.actionInternalTiming,
                    config.fakePlayer.snapshot.itemUseCooldown,
                    config.fakePlayer.snapshot.blockBreakProgress);
        if (config.fakePlayer.snapshot.itemUseCooldown)
            ActionPackStateCodec.setNumber(actionPack, "itemUseCooldown", data.itemUseCooldown);
        if (config.fakePlayer.snapshot.blockBreakProgress)
            ActionPackStateCodec.setNumber(
                    actionPack, "blockBreakingProgress", data.blockBreakingProgress);
        if (data.actionPackState != null
                && !ActionPackStateCodec.matches(
                        data.actionPackState,
                        actionPack,
                        config.fakePlayer.snapshot.actions,
                        config.fakePlayer.snapshot.actionInternalTiming,
                        config.fakePlayer.snapshot.itemUseCooldown,
                        config.fakePlayer.snapshot.blockBreakProgress))
            throw new IOException(
                    "FakePlayer ActionPack verification failed: "
                            + ActionPackStateCodec.describeMismatch(
                                    data.actionPackState,
                                    actionPack,
                                    config.fakePlayer.snapshot.actions,
                                    config.fakePlayer.snapshot.actionInternalTiming,
                                    config.fakePlayer.snapshot.itemUseCooldown,
                                    config.fakePlayer.snapshot.blockBreakProgress));
        if (vehicle != null) {

            //? if (>=1.18 && <=1.21.8) {
            if (vehicle.getWorld() != player.getWorld() || !player.startRiding(vehicle, true))

                //?}
                //? if (>=1.21.9 && <=1.21.11) {
                /*if (vehicle.getEntityWorld() != player.getEntityWorld()
                                    || !player.startRiding(vehicle, true, false))
*///?}
                //? if (>=26.1 && <=26.3) {
                /*if (vehicle.level() != player.level() || !player.startRiding(vehicle, true, false))
*///?}
                throw new IOException("FakePlayer riding state could not be restored");
        }
    }

    /**
     * The root vehicle with every non-player passenger, written the way an entity chunk save writes
     * it, so the state AntiCrash adds to entity data is included.
     */

    //? if (>=1.18 && <=1.21.11) {
    private static String captureRootVehicle(ServerPlayerEntity player) throws IOException
                //?}
                //? if (>=26.1 && <=26.3) {
                /*private static String captureRootVehicle(ServerPlayer player) throws IOException
*///?}
            {

        //? if (>=1.18 && <=1.21.11) {
        net.minecraft.entity.Entity root = player.getRootVehicle();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*net.minecraft.world.entity.Entity root = player.getRootVehicle();
*///?}

        if (root == null || root == player) return null;

        //? if (>=1.18 && <=1.21.5) {
        net.minecraft.nbt.NbtCompound nbt = new net.minecraft.nbt.NbtCompound();
        //?}

        //? if (>=1.18 && <=1.21.5) {
        boolean saved;
        //?}
        //? if (>=1.21.6 && <=1.21.11) {
        /*net.minecraft.nbt.NbtCompound nbt;
*///?}
        //? if (>=26.1 && <=26.3) {
        /*net.minecraft.nbt.CompoundTag nbt;
*///?}

        com.atsukimc.anticrash.runtime.RuntimeExtensionNbt.entitySave(true);
        try {

            //? if (>=1.18 && <=1.21.5) {
            saved = root.saveNbt(nbt);
            //?}
            //? if (>=1.21.6 && <=26.3) {
            /*nbt = com.atsukimc.anticrash.runtime.VersionStorage.entity(root);
*///?}

        } finally {
            com.atsukimc.anticrash.runtime.RuntimeExtensionNbt.entitySave(false);
        }

        //? if (>=1.18 && <=1.21.5) {
        return saved ? NbtCodec.encode(nbt) : null;
        //?}
        //? if (>=1.21.6 && <=26.3) {
        /*return nbt != null ? NbtCodec.encode(nbt) : null;
*///?}

    }

    /** Removes the vehicle (and its non-player passengers) the player currently rides. */

    //? if (>=1.18 && <=1.21.11) {
    private static void discardStaleVehicle(ServerPlayerEntity player)
                //?}
                //? if (>=26.1 && <=26.3) {
                /*private static void discardStaleVehicle(ServerPlayer player)
*///?}
            {

        //? if (>=1.18 && <=1.21.11) {
        net.minecraft.entity.Entity root = player.getRootVehicle();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*net.minecraft.world.entity.Entity root = player.getRootVehicle();
*///?}

        player.stopRiding();
        if (root == null || root == player) return;

        //? if (>=1.18 && <=1.21.11) {
        for (net.minecraft.entity.Entity entity : root.streamPassengersAndSelf().toList())
        //?}
        //? if (>=26.1 && <=26.3) {
        /*for (net.minecraft.world.entity.Entity entity : root.getPassengersAndSelf().toList())
*///?}
        {

            //? if (>=1.18 && <=1.21.11) {
            if (!(entity instanceof net.minecraft.entity.player.PlayerEntity))
                //?}
                //? if (>=26.1 && <=26.3) {
                /*if (!(entity instanceof net.minecraft.world.entity.player.Player))
*///?}
                entity.discard();
        }
    }

    //? if (>=1.18 && <=1.21.11) {
    private static void collectUuids(net.minecraft.nbt.NbtCompound nbt, Set<UUID> uuids)
                //?}
                //? if (>=26.1 && <=26.3) {
                /*private static void collectUuids(net.minecraft.nbt.CompoundTag nbt, Set<UUID> uuids)
*///?}
            {

        //? if (>=1.18 && <=1.21.4) {
        if (nbt.containsUuid("UUID")) uuids.add(nbt.getUuid("UUID"));
        //?}
        //? if (>=1.21.5 && <=1.21.11) {
        /*nbt.get("UUID", net.minecraft.util.Uuids.INT_STREAM_CODEC).ifPresent(uuids::add);
*///?}
        //? if (>=26.1 && <=26.3) {
        /*nbt.read("UUID", net.minecraft.core.UUIDUtil.CODEC).ifPresent(uuids::add);
*///?}

        //? if (>=1.18 && <=1.21.4) {
        net.minecraft.nbt.NbtList passengers = nbt.getList("Passengers", 10);
        //?}
        //? if (>=1.21.5 && <=1.21.11) {
        /*net.minecraft.nbt.NbtList passengers =
                        com.atsukimc.anticrash.runtime.VersionNbt.list(nbt, "Passengers", 10);
*///?}
        //? if (>=26.1 && <=26.3) {
        /*net.minecraft.nbt.ListTag passengers =
                        com.atsukimc.anticrash.runtime.VersionNbt.list(nbt, "Passengers", 10);
*///?}

        for (int i = 0; i < passengers.size(); i++
        //? if (>=1.18 && <=1.21.4) {
        ) collectUuids(passengers.getCompound(i), uuids);
        //?}
        //? if (>=1.21.5 && <=26.3) {
        /*)
                    collectUuids(passengers.getCompound(i).orElseThrow(), uuids);
*///?}

    }

    /**
     * Puts the checkpoint's vehicle into the world in place of the one Vanilla loaded from the
     * player data file when the FakePlayer joined.
     */

    //? if (>=1.18 && <=1.21.11) {
    private static net.minecraft.entity.Entity restoreRootVehicle(
            MinecraftServer server,
            ServerWorld world,
            ServerPlayerEntity player,
            String encoded,
            UUID vehicleId)
            throws IOException
                //?}
                //? if (>=26.1 && <=26.3) {
                /*private static net.minecraft.world.entity.Entity restoreRootVehicle(
                            MinecraftServer server,
                            ServerLevel world,
                            ServerPlayer player,
                            String encoded,
                            UUID vehicleId)
                            throws IOException
*///?}
            {

        //? if (>=1.18 && <=1.21.11) {
        net.minecraft.nbt.NbtCompound nbt = NbtCodec.decodeCompound(encoded);
        //?}
        //? if (>=26.1 && <=26.3) {
        /*net.minecraft.nbt.CompoundTag nbt = NbtCodec.decodeCompound(encoded);
*///?}

        Set<UUID> uuids = new HashSet<>();
        collectUuids(nbt, uuids);
        if (!uuids.contains(vehicleId))
            throw new IOException("FakePlayer vehicle snapshot does not contain the vehicle");

        //? if (>=1.18 && <=1.21.11) {
        if (player.hasVehicle())
            //?}
            //? if (>=26.1 && <=26.3) {
            /*if (player.isPassenger())
*///?}
            discardStaleVehicle(player);
        for (UUID uuid : uuids)

            //? if (>=1.18 && <=1.21.11) {
            for (ServerWorld candidate : server.getWorlds())
            //?}
            //? if (>=26.1 && <=26.3) {
            /*for (ServerLevel candidate : server.getAllLevels())
*///?}
            {

                //? if (>=1.18 && <=1.21.11) {
                net.minecraft.entity.Entity existing = candidate.getEntity(uuid);
                //?}
                //? if (>=26.1 && <=26.3) {
                /*net.minecraft.world.entity.Entity existing = candidate.getEntity(uuid);
*///?}

                //? if (>=1.18 && <=1.21.11) {
                if (existing != null
                        && !(existing instanceof net.minecraft.entity.player.PlayerEntity))

                    //?}
                    //? if (>=26.1 && <=26.3) {
                    /*if (existing != null
                                            && !(existing instanceof net.minecraft.world.entity.player.Player))
*///?}
                    existing.discard();
            }

        //? if (>=1.18 && <=1.21.1) {
        net.minecraft.entity.Entity root =
                net.minecraft.entity.EntityType.loadEntityWithPassengers(
                        nbt, world, entity -> world.tryLoadEntity(entity) ? entity : null);
        //?}
        //? if (>=1.21.2 && <=1.21.11) {
        /*net.minecraft.entity.Entity root =
                        net.minecraft.entity.EntityType.loadEntityWithPassengers(
                                nbt,
                                world,
                                net.minecraft.entity.SpawnReason.LOAD,
                                entity -> world.tryLoadEntity(entity) ? entity : null);
*///?}
        //? if 26.1 {
        /*net.minecraft.world.entity.Entity root =
                        net.minecraft.world.entity.EntityType.loadEntityRecursive(
                                nbt,
                                world,
                                net.minecraft.world.entity.EntitySpawnReason.LOAD,
                                entity -> world.addWithUUID(entity) ? entity : null);
*///?}
        //? if (>=26.2 && <=26.3) {
        /*net.minecraft.world.entity.Entity root =
                        net.minecraft.world.entity.EntityType.loadEntityRecursive(
                                nbt,
                                world,
                                new net.minecraft.world.entity.EntitySpawnRequest(
                                        net.minecraft.world.entity.EntitySpawnReason.LOAD, false),
                                entity -> world.addWithUUID(entity) ? entity : null);
*///?}

        if (root == null) throw new IOException("FakePlayer vehicle could not be loaded");

        //? if (>=1.18 && <=1.21.11) {
        for (net.minecraft.entity.Entity entity : root.streamPassengersAndSelf().toList())
        //?}
        //? if (>=26.1 && <=26.3) {
        /*for (net.minecraft.world.entity.Entity entity : root.getPassengersAndSelf().toList())
*///?}
        {

            //? if (>=1.18 && <=1.21.11) {
            if (vehicleId.equals(entity.getUuid()))
                //?}
                //? if (>=26.1 && <=26.3) {
                /*if (vehicleId.equals(entity.getUUID()))
*///?}
                return entity;
        }
        throw new IOException("FakePlayer vehicle was not created from its snapshot");
    }

    //? if (>=1.18 && <=1.21.10) {
    private static void captureUseState(ServerPlayerEntity player, FakePlayerSnapshot data)
                //?}
                //? if 1.21.11 {
                /*private static void captureUseState(ServerPlayerEntity player, FakePlayerSnapshot data)
                            throws IOException
*///?}
                //? if (>=26.1 && <=26.3) {
                /*private static void captureUseState(ServerPlayer player, FakePlayerSnapshot data)
                            throws IOException
*///?}
            {

        //? if (>=26.1 && <=26.3) {
        /*var fluids = new net.minecraft.nbt.CompoundTag();
*///?}

        //? if (>=26.1 && <=26.3) {
        /*fluids.put(
                        "Trackers",
                        com.atsukimc.anticrash.runtime.FluidInteractionSnapshot.capture(player));
*///?}

        //? if (>=26.1 && <=26.3) {
        /*data.fluidTrackersNbt = NbtCodec.encode(fluids);
*///?}

        //? if (>=1.21.11 && <=26.3) {
        /*data.motionFrameNbt =
                        NbtCodec.encode(
                                com.atsukimc.anticrash.runtime.EntityMotionFrame.capture(player).write());
*///?}

        com.atsukimc.anticrash.mixin.LivingEntityAccessorMixin living =
                (com.atsukimc.anticrash.mixin.LivingEntityAccessorMixin) player;
        data.lastAttackedTicks = living.anticrash$getLastAttackedTicks();

        //? if (>=1.21.11 && <=26.3) {
        /*data.lastKineticAttackTime = living.anticrash$lastKineticAttackTime();
*///?}

        if (player.isUsingItem()) {

            //? if (>=1.18 && <=1.21.11) {
            data.activeHand = player.getActiveHand().name();
            //?}
            //? if (>=26.1 && <=26.3) {
            /*data.activeHand = player.getUsedItemHand().name();
*///?}

            data.itemUseTimeLeft = living.anticrash$getItemUseTimeLeft();
        }

        //? if (>=1.18 && <=1.21.11) {
        com.atsukimc.anticrash.mixin.ItemCooldownManagerAccessorMixin manager =
                (com.atsukimc.anticrash.mixin.ItemCooldownManagerAccessorMixin)
                        player.getItemCooldownManager();
        //?}
        //? if (>=26.1 && <=26.3) {
        /*com.atsukimc.anticrash.mixin.ItemCooldownManagerAccessorMixin manager =
                        (com.atsukimc.anticrash.mixin.ItemCooldownManagerAccessorMixin)
                                player.getCooldowns();
*///?}

        List<FakePlayerSnapshot.ItemCooldown> cooldowns = new ArrayList<>();

        //? if (>=1.18 && <=1.21.1) {
        for (java.util.Map.Entry<net.minecraft.item.Item, Object> entry :
                manager.anticrash$getEntries().entrySet())
        //?}
        //? if (>=1.21.2 && <=1.21.11) {
        /*for (java.util.Map.Entry<net.minecraft.util.Identifier, Object> entry :
                        manager.anticrash$getEntries().entrySet())
*///?}
        //? if (>=26.1 && <=26.3) {
        /*for (java.util.Map.Entry<net.minecraft.resources.Identifier, Object> entry :
                        manager.anticrash$getEntries().entrySet())
*///?}
        {
            int remaining =
                    ((com.atsukimc.anticrash.mixin.ItemCooldownEntryAccessorMixin) entry.getValue())
                                    .anticrash$getEndTick()
                            - manager.anticrash$getTick();
            if (remaining <= 0) continue;
            FakePlayerSnapshot.ItemCooldown cooldown = new FakePlayerSnapshot.ItemCooldown();

            //? if (>=1.18 && <=1.19.2) {
            cooldown.item =
                    net.minecraft.util.registry.Registry.ITEM.getId(entry.getKey()).toString();
            //?}
            //? if (>=1.19.3 && <=1.21.1) {
            /*cooldown.item = net.minecraft.registry.Registries.ITEM.getId(entry.getKey()).toString();
*///?}
            //? if (>=1.21.2 && <=26.3) {
            /*cooldown.item = entry.getKey().toString();
*///?}

            cooldown.remainingTicks = remaining;
            cooldowns.add(cooldown);
        }
        cooldowns.sort(java.util.Comparator.comparing(cooldown -> cooldown.item));
        if (!cooldowns.isEmpty()) data.itemCooldowns = cooldowns;
    }

    /** Runs after the inventory is back, so the hand holds the item that was being used. */

    //? if (>=1.18 && <=1.21.11) {
    private static void restoreUseState(ServerPlayerEntity player, FakePlayerSnapshot data)
            throws IOException
                //?}
                //? if (>=26.1 && <=26.3) {
                /*private static void restoreUseState(ServerPlayer player, FakePlayerSnapshot data)
                            throws IOException
*///?}
            {

        //? if (>=26.1 && <=26.3) {
        /*if (data.fluidTrackersNbt != null)
                    com.atsukimc.anticrash.runtime.FluidInteractionSnapshot.restore(
                            player,
                            com.atsukimc.anticrash.runtime.VersionNbt.list(
                                    NbtCodec.decodeCompound(data.fluidTrackersNbt), "Trackers", 10));
*///?}

        //? if (>=1.21.11 && <=26.3) {
        /*if (data.motionFrameNbt != null)
                    com.atsukimc.anticrash.runtime.EntityMotionFrame.read(
                                    NbtCodec.decodeCompound(data.motionFrameNbt))
                            .apply(player);
*///?}

        com.atsukimc.anticrash.mixin.LivingEntityAccessorMixin living =
                (com.atsukimc.anticrash.mixin.LivingEntityAccessorMixin) player;

        //? if (>=1.21.11 && <=26.3) {
        /*if (data.lastKineticAttackTime != null)
                    living.anticrash$setLastKineticAttackTime(data.lastKineticAttackTime);
*///?}

        if (data.lastAttackedTicks != null) {
            living.anticrash$setLastAttackedTicks(Math.max(0, data.lastAttackedTicks));
            // PlayerEntity#tick resets the attack cooldown when the main-hand item differs from the
            // one it saw last tick. A newly created player has seen nothing, so its first tick
            // would
            // throw the restored value away. At a tick boundary that remembered stack is the main
            // hand.

            //? if (>=1.18 && <=1.21.11) {
            ((com.atsukimc.anticrash.mixin.PlayerEntityAccessorMixin) player)
                    .anticrash$setSelectedItem(player.getMainHandStack().copy());
            //?}
            //? if (>=26.1 && <=26.3) {
            /*((com.atsukimc.anticrash.mixin.PlayerEntityAccessorMixin) player)
                                .anticrash$setSelectedItem(player.getMainHandItem().copy());
*///?}

        }
        if (data.activeHand != null && data.itemUseTimeLeft != null) {

            //? if (>=1.18 && <=1.21.11) {
            net.minecraft.util.Hand hand;
            //?}
            //? if (>=26.1 && <=26.3) {
            /*net.minecraft.world.InteractionHand hand;
*///?}

            try {

                //? if (>=1.18 && <=1.21.11) {
                hand = net.minecraft.util.Hand.valueOf(data.activeHand);
                //?}
                //? if (>=26.1 && <=26.3) {
                /*hand = net.minecraft.world.InteractionHand.valueOf(data.activeHand);
*///?}

            } catch (IllegalArgumentException invalid) {
                throw new IOException("Invalid FakePlayer active hand: " + data.activeHand);
            }

            //? if (>=1.18 && <=1.21.11) {
            if (player.getStackInHand(hand).isEmpty())

                //?}
                //? if (>=26.1 && <=26.3) {
                /*if (player.getItemInHand(hand).isEmpty())
*///?}
                throw new IOException("FakePlayer was using an item but its hand is empty");

            //? if (>=1.18 && <=1.21.11) {
            player.clearActiveItem();
            //?}
            //? if (>=26.1 && <=26.3) {
            /*player.stopUsingItem();
*///?}

            //? if (>=1.18 && <=1.21.11) {
            player.setCurrentHand(hand);
            //?}
            //? if (>=26.1 && <=26.3) {
            /*player.startUsingItem(hand);
*///?}

            living.anticrash$setItemUseTimeLeft(Math.max(0, data.itemUseTimeLeft));
        }
        if (data.itemCooldowns != null)
            for (FakePlayerSnapshot.ItemCooldown cooldown : data.itemCooldowns) {
                if (cooldown == null || cooldown.item == null || cooldown.remainingTicks <= 0)
                    continue;

                //? if (>=1.18 && <=1.21.11) {
                net.minecraft.util.Identifier id =
                        net.minecraft.util.Identifier.tryParse(cooldown.item);
                //?}
                //? if (>=26.1 && <=26.3) {
                /*net.minecraft.resources.Identifier id =
                                        net.minecraft.resources.Identifier.tryParse(cooldown.item);
*///?}

                //? if (>=1.18 && <=1.19.2) {
                if (id == null || !net.minecraft.util.registry.Registry.ITEM.containsId(id))

                    //?}
                    //? if (>=1.19.3 && <=1.21.1) {
                    /*if (id == null || !net.minecraft.registry.Registries.ITEM.containsId(id))
*///?}
                    //? if (>=1.18 && <=1.21.1) {
                    throw new IOException("Unknown FakePlayer cooldown item: " + cooldown.item);
                //?}
                //? if (>=1.21.2 && <=26.3) {
                /*if (id == null)
                                    throw new IOException("Invalid FakePlayer cooldown group: " + cooldown.item);
*///?}

                // The same remaining time and the same expiry tick; only the progress bar's start
                // differs.

                //? if (>=1.18 && <=1.19.2) {
                player.getItemCooldownManager()
                        .set(
                                net.minecraft.util.registry.Registry.ITEM.get(id),
                                cooldown.remainingTicks);
                //?}
                //? if (>=1.19.3 && <=1.21.1) {
                /*player.getItemCooldownManager()
                                        .set(
                                                net.minecraft.registry.Registries.ITEM.get(id),
                                                cooldown.remainingTicks);
*///?}
                //? if (>=1.21.2 && <=1.21.11) {
                /*player.getItemCooldownManager().set(id, cooldown.remainingTicks);
*///?}
                //? if (>=26.1 && <=26.3) {
                /*player.getCooldowns().addCooldown(id, cooldown.remainingTicks);
*///?}

            }
    }

    //? if (>=1.18 && <=1.21.11) {
    private static Object findActionPack(ServerPlayerEntity player)
                //?}
                //? if (>=26.1 && <=26.3) {
                /*private static Object findActionPack(ServerPlayer player)
*///?}
            {
        for (Method method : ReflectionUtil.allMethods(player.getClass())) {
            String name = method.getName().toLowerCase(Locale.ROOT);
            if (method.getParameterCount() == 0
                    && (name.contains("actionpack") || name.contains("action_pack"))
                    && !method.getReturnType().isPrimitive())
                try {
                    return method.invoke(player);
                } catch (Exception ignored) {
                }
        }
        for (java.lang.reflect.Field field : ReflectionUtil.instanceFields(player.getClass())) {
            String name = field.getName().toLowerCase(Locale.ROOT);
            if (name.contains("actionpack") || name.contains("action_pack"))
                try {
                    return field.get(player);
                } catch (Exception ignored) {
                }
        }
        return null;
    }

    //? if (>=1.18 && <=1.21.11) {
    private static ServerWorld findWorld(MinecraftServer server, String id)
                //?}
                //? if (>=26.1 && <=26.3) {
                /*private static ServerLevel findWorld(MinecraftServer server, String id)
*///?}
            {
        if (id != null)

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

    private static void validate(FakePlayerSnapshot data, AntiCrashConfig config)
            throws IOException {
        if (data == null || data.formatVersion != 1)
            throw new IllegalArgumentException("Unsupported or empty FakePlayer snapshot");

        //? if (>=1.21.11 && <=26.3) {
        /*if (data.motionFrameNbt != null)
                    com.atsukimc.anticrash.runtime.EntityMotionFrame.read(
                            NbtCodec.decodeCompound(data.motionFrameNbt));
*///?}

        // LivingEntity initializes lastKineticAttackTime to Integer.MIN_VALUE (as long).
        // Preserve that never-hit sentinel, while still rejecting other invalid negative times.

        //? if (>=26.1 && <=26.3) {
        /*if (data.fluidTrackersNbt != null)
                    com.atsukimc.anticrash.runtime.FluidInteractionSnapshot.decode(
                            com.atsukimc.anticrash.runtime.VersionNbt.list(
                                    NbtCodec.decodeCompound(data.fluidTrackersNbt), "Trackers", 10));
*///?}

        // LivingEntity initializes lastKineticHitFeedbackTime to Integer.MIN_VALUE (as long).
        // Preserve that never-hit sentinel, while still rejecting other invalid negative times.

        //? if (>=1.21.11 && <=26.3) {
        /*if (data.lastKineticAttackTime != null
                        && data.lastKineticAttackTime < -1
                        && data.lastKineticAttackTime != Integer.MIN_VALUE)
                    throw new IllegalArgumentException("Invalid kinetic attack time");
*///?}

        // Carpet accepts any name Vanilla's login would (e.g. "bot.bed"): printable ASCII without
        // spaces, at most 16 characters. The old [A-Za-z0-9_] rule refused a whole restore.
        if (data.name == null || !data.name.matches("[\\x21-\\x7E]{1,16}"))
            throw new IllegalArgumentException("Invalid FakePlayer name: " + data.name);
        try {
            UUID parsed = UUID.fromString(data.uuid);
            if (!parsed.toString().equalsIgnoreCase(data.uuid))
                throw new IllegalArgumentException("non-canonical UUID");
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid FakePlayer UUID", exception);
        }
        if (config.fakePlayer.snapshot.dimension && data.dimension == null)
            throw new IllegalArgumentException("FakePlayer dimension is missing");
        if (config.fakePlayer.snapshot.position
                && !(Double.isFinite(data.x) && Double.isFinite(data.y) && Double.isFinite(data.z)))
            throw new IllegalArgumentException("FakePlayer position is not finite");
        if (config.fakePlayer.snapshot.rotation
                && !(Float.isFinite(data.yaw) && Float.isFinite(data.pitch)))
            throw new IllegalArgumentException("FakePlayer rotation is not finite");
        if (config.fakePlayer.snapshot.movement
                && !(Float.isFinite(data.forward) && Float.isFinite(data.strafing)))
            throw new IllegalArgumentException("FakePlayer movement is not finite");
        if (config.fakePlayer.snapshot.health && !Float.isFinite(data.health))
            throw new IllegalArgumentException("FakePlayer health is not finite");
        if (config.fakePlayer.snapshot.hunger
                && !(Float.isFinite(data.saturation) && Float.isFinite(data.exhaustion)))
            throw new IllegalArgumentException("FakePlayer hunger state is not finite");
        if (config.fakePlayer.snapshot.gamemode
                && (data.gamemode == null
                        || !Set.of("survival", "creative", "adventure", "spectator")
                                .contains(data.gamemode)))
            throw new IllegalArgumentException("Invalid FakePlayer gamemode");
        if (data.vehicleUuid != null)
            try {
                UUID parsed = UUID.fromString(data.vehicleUuid);
                if (!parsed.toString().equalsIgnoreCase(data.vehicleUuid))
                    throw new IllegalArgumentException("non-canonical UUID");
            } catch (Exception exception) {
                throw new IllegalArgumentException("Invalid FakePlayer vehicle UUID", exception);
            }
        if (data.effectsNbt == null || data.effectsNbt.size() > 1024)
            throw new IllegalArgumentException("Invalid FakePlayer effect list");
        if (config.fakePlayer.snapshot.effects)
            for (String encoded : data.effectsNbt) {

                //? if (>=1.18 && <=1.21.11) {
                NbtElement element = NbtCodec.decode(encoded);
                //?}
                //? if (>=26.1 && <=26.3) {
                /*Tag element = NbtCodec.decode(encoded);
*///?}

                //? if (>=1.18 && <=1.21.4) {
                if (!(element instanceof net.minecraft.nbt.NbtCompound compound)
                        || StatusEffectInstance.fromNbt(compound) == null)

                    //?}
                    //? if (>=1.21.5 && <=1.21.11) {
                    /*if (!(element instanceof net.minecraft.nbt.NbtCompound compound)
                                            || com.atsukimc.anticrash.runtime.VersionNbt.effect(compound) == null)
*///?}
                    //? if (>=26.1 && <=26.3) {
                    /*if (!(element instanceof net.minecraft.nbt.CompoundTag compound)
                                            || com.atsukimc.anticrash.runtime.VersionNbt.effect(compound) == null)
*///?}
                    throw new IOException("FakePlayer effect snapshot is invalid");
            }
        boolean inventoryEnabled =
                FakePlayerSnapshotOptions.inventoryRequired(config.fakePlayer.snapshot);
        if (inventoryEnabled) {
            if (data.inventory == null)
                throw new IOException("Enabled FakePlayer inventory snapshot is missing");
            data.inventory.validatePortable(
                    config.fakePlayer.snapshot.inventory,
                    config.fakePlayer.snapshot.inventory,
                    config.fakePlayer.snapshot.inventory,
                    config.fakePlayer.snapshot.selectedSlot);
        }
        boolean actionRequested =
                FakePlayerSnapshotOptions.actionsRequired(config.fakePlayer.snapshot);
        if (actionRequested)
            ActionPackStateCodec.validateSnapshot(
                    data.actionPackState,
                    config.fakePlayer.snapshot.actions,
                    config.fakePlayer.snapshot.actionInternalTiming,
                    config.fakePlayer.snapshot.itemUseCooldown,
                    config.fakePlayer.snapshot.blockBreakProgress);
    }

    public static final class RestoreSummary {
        public int expected, ready, failed;

        public boolean allReady() {
            return expected == ready && failed == 0;
        }

        public static RestoreSummary pending(int expected) {
            RestoreSummary summary = new RestoreSummary();
            summary.expected = Math.max(0, expected);
            return summary;
        }
    }
}
