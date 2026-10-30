package com.atsukimc.anticrash.integration.carpet;

import com.atsukimc.anticrash.config.AntiCrashConfig;
import com.atsukimc.anticrash.command.RecoveryCommandPolicy;
import com.atsukimc.anticrash.logging.AntiCrashLogger;
import com.atsukimc.anticrash.player.InventorySnapshot;
import com.atsukimc.anticrash.player.NbtCodec;
import com.atsukimc.anticrash.util.ReflectionUtil;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.GameMode;
import com.mojang.authlib.GameProfile;

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
    private static final String[] FAKE_CLASSES = {"carpet.patches.EntityPlayerMPFake", "carpet.fakePlayer.EntityPlayerMPFake"};
    private static boolean checked;
    private static boolean available;

    private CarpetIntegration() {
    }

    public static void initialize(AntiCrashLogger logger) {
        if (checked) return;
        checked=true;
        for (String className:FAKE_CLASSES) try {
            Class.forName(className,false,CarpetIntegration.class.getClassLoader());
            available=true;
            break;
        } catch (ClassNotFoundException | LinkageError ignored) { }
        if (!available) logger.normalKey("carpet.not_installed");
        else logger.normalKey("carpet.detected");
    }

    /**
     * Reads Carpet's global tick speed, or null when Carpet is absent.
     *
     * <p>Unlike the Minecraft classes reached elsewhere in this file, {@code TickSpeed} belongs to
     * Carpet itself, so its member names are not remapped and plain reflection resolves them.</p>
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
            snapshot.timeWarpScheduledTicks = tickSpeed.getField("time_warp_scheduled_ticks").getLong(null);
            snapshot.playerActiveTimeout = tickSpeed.getField("player_active_timeout").getInt(null);
            snapshot.superHot = tickSpeed.getField("is_superHot").getBoolean(null);
            Object advancer = tickSpeed.getField("time_advancerer").get(null);
            Object callback = tickSpeed.getField("tick_warp_callback").get(null);
            Object sender = tickSpeed.getField("tick_warp_sender").get(null);
            snapshot.timeWarpActive = snapshot.timeBias > 0L
                    || snapshot.timeWarpScheduledTicks > 0L
                    || snapshot.timeWarpStartTime != 0L
                    || advancer != null || callback != null || sender != null;
            boolean valid = Float.isFinite(snapshot.tickrate) && snapshot.tickrate > 0.0F
                    && Float.isFinite(snapshot.mspt) && snapshot.mspt > 0.0F
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

    /** @return true when the snapshot was applied; false when Carpet or the state is unavailable */
    public static boolean restoreTickSpeed(TickSpeedSnapshot snapshot, AntiCrashLogger logger) {
        if (snapshot == null || (snapshot.formatVersion != 1 && snapshot.formatVersion != 2)
                || !Float.isFinite(snapshot.tickrate) || snapshot.tickrate <= 0.0F) {
            return false;
        }
        if (snapshot.formatVersion >= 2
                && (!Float.isFinite(snapshot.mspt) || snapshot.mspt <= 0.0F
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
            tickSpeed.getMethod("setFrozenState", boolean.class, boolean.class)
                    .invoke(null, snapshot.paused, snapshot.deepFrozen);
            if (snapshot.formatVersion >= 2) {
                tickSpeed.getField("mspt").setFloat(null, snapshot.mspt);
                tickSpeed.getField("time_bias").setLong(null, snapshot.timeBias);
                tickSpeed.getField("time_warp_start_time").setLong(null, snapshot.timeWarpStartTime);
                tickSpeed.getField("time_warp_scheduled_ticks")
                        .setLong(null, snapshot.timeWarpScheduledTicks);
                tickSpeed.getField("player_active_timeout")
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
            return Class.forName("carpet.helpers.TickSpeed", true,
                    CarpetIntegration.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError ignored) {
            return null;
        }
    }

    public static String version() {
        try { return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("carpet").map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse(null); }
        catch(Exception ignored){ return null; }
    }

    public static boolean isFakePlayer(ServerPlayerEntity player) {
        String name=player.getClass().getName().toLowerCase(Locale.ROOT);
        return name.contains("entityplayermpfake") || name.contains("fakeplayer") || name.startsWith("carpet.");
    }

    public static List<FakePlayerSnapshot> capture(MinecraftServer server, AntiCrashConfig config, AntiCrashLogger logger) {
        try { return capture(server, config, logger, false); }
        catch (IOException impossible) { return List.of(); }
    }

    public static List<FakePlayerSnapshot> captureForCheckpoint(MinecraftServer server,
                                                                 AntiCrashConfig config,
                                                                 AntiCrashLogger logger) throws IOException {
        // OPTIMIZED retains the historical best-effort behavior. Only STRICT
        // must fail closed when the optional adapter cannot capture live state.
        boolean strict = "STRICT".equalsIgnoreCase(config.recovery.fidelity);
        return capture(server, config, logger, strict);
    }

    /** Strict, throwing capture used by the post-restore equivalence gate. */
    public static List<FakePlayerSnapshot> captureForVerification(MinecraftServer server,
                                                                    AntiCrashConfig config,
                                                                    AntiCrashLogger logger)
            throws IOException {
        return capture(server, config, logger, true);
    }

    private static List<FakePlayerSnapshot> capture(MinecraftServer server, AntiCrashConfig config,
                                                     AntiCrashLogger logger, boolean strict) throws IOException {
        List<FakePlayerSnapshot> snapshots=new ArrayList<>();
        if (!config.fakePlayer.enabled) {
            // A disabled adapter must not turn live FakePlayers into an apparently complete
            // empty checkpoint.  Their ActionPack/entity state is part of the execution domain;
            // STRICT therefore refuses to publish a checkpoint until the operator enables the
            // adapter (OPTIMIZED keeps the historical opt-out behavior).
            boolean fakePlayerPresent = server.getPlayerManager().getPlayerList().stream()
                    .anyMatch(CarpetIntegration::isFakePlayer);
            if (strict && fakePlayerPresent) {
                throw new IOException("STRICT FakePlayer capture is disabled while FakePlayers are online");
            }
            return snapshots;
        }
        if (!available) {
            // TickSpeed may be present even when this build uses an unknown FakePlayer class name.
            // Do not silently turn live fake players into an empty checkpoint just because the
            // optional adapter could not identify its creation API.
            boolean fakePlayerPresent = server.getPlayerManager().getPlayerList().stream()
                    .anyMatch(CarpetIntegration::isFakePlayer);
            if (strict && fakePlayerPresent) {
                throw new IOException("Carpet FakePlayer adapter is unavailable while FakePlayers are online");
            }
            return snapshots;
        }
        for (ServerPlayerEntity player:server.getPlayerManager().getPlayerList()) if (isFakePlayer(player)) try { snapshots.add(captureOne(player,config)); } catch(Exception exception){ logger.importantKey("fakeplayer.snapshot_failed",player.getName().getString(),exception.getMessage());if(strict){if(exception instanceof IOException ioException)throw ioException;throw new IOException("Could not capture FakePlayer "+player.getName().getString(),exception);}}
        return snapshots;
    }

    private static FakePlayerSnapshot captureOne(ServerPlayerEntity player, AntiCrashConfig config) throws Exception {
        FakePlayerSnapshot data=new FakePlayerSnapshot(); data.uuid=player.getUuidAsString(); data.name=player.getGameProfile().getName();
        if(config.fakePlayer.snapshot.dimension) data.dimension=player.getWorld().getRegistryKey().getValue().toString();
        if(config.fakePlayer.snapshot.position){data.x=player.getX();data.y=player.getY();data.z=player.getZ();}
        if(config.fakePlayer.snapshot.rotation){data.yaw=player.getYaw(0);data.pitch=player.getPitch(0);}
        if(config.fakePlayer.snapshot.gamemode) data.gamemode=player.interactionManager.getGameMode().getName();
        if(config.fakePlayer.snapshot.flying) data.flying=player.getAbilities().flying;
        if(config.fakePlayer.snapshot.sneaking) data.sneaking=player.isSneaking();
        if(config.fakePlayer.snapshot.sprinting) data.sprinting=player.isSprinting();
        if(config.fakePlayer.snapshot.movement){data.forward=player.forwardSpeed;data.strafing=player.sidewaysSpeed;}
        if(config.fakePlayer.snapshot.inventory||config.fakePlayer.snapshot.selectedSlot) data.inventory=InventorySnapshot.capture(player,config.fakePlayer.snapshot.inventory,config.fakePlayer.snapshot.inventory,config.fakePlayer.snapshot.inventory,config.fakePlayer.snapshot.selectedSlot);
        if(config.fakePlayer.snapshot.health) data.health=player.getHealth();
        if(config.fakePlayer.snapshot.hunger){data.hunger=player.getHungerManager().getFoodLevel();data.saturation=player.getHungerManager().getSaturationLevel();data.exhaustion=player.getHungerManager().getExhaustion();}
        if(config.fakePlayer.snapshot.effects) for(StatusEffectInstance effect:player.getStatusEffects()) data.effectsNbt.add(NbtCodec.encode(effect.writeNbt()));
        if(player.hasVehicle()){
            data.vehicleUuid=player.getVehicle().getUuidAsString();
            data.rootVehicleNbt=captureRootVehicle(player);
        }
        captureUseState(player,data);
        Object actionPack=findActionPack(player);
        if(actionPack==null&&(config.fakePlayer.snapshot.actions||config.fakePlayer.snapshot.actionInternalTiming
                ||config.fakePlayer.snapshot.itemUseCooldown||config.fakePlayer.snapshot.blockBreakProgress)){
            throw new IOException("Carpet ActionPack is unavailable for "+data.name);
        }
        if(actionPack!=null&&(config.fakePlayer.snapshot.actions||config.fakePlayer.snapshot.actionInternalTiming
                ||config.fakePlayer.snapshot.itemUseCooldown||config.fakePlayer.snapshot.blockBreakProgress)){
            data.actionPackState=ActionPackStateCodec.capture(actionPack,config.fakePlayer.snapshot.actions,
                    config.fakePlayer.snapshot.actionInternalTiming,config.fakePlayer.snapshot.itemUseCooldown,
                    config.fakePlayer.snapshot.blockBreakProgress);
            ActionPackStateCodec.validateSnapshot(data.actionPackState,config.fakePlayer.snapshot.actions,
                    config.fakePlayer.snapshot.actionInternalTiming,config.fakePlayer.snapshot.itemUseCooldown,
                    config.fakePlayer.snapshot.blockBreakProgress);
            if(config.fakePlayer.snapshot.itemUseCooldown){Number cooldown=ActionPackStateCodec.findNumber(actionPack,"itemUseCooldown");data.itemUseCooldown=cooldown==null?null:cooldown.intValue();}
            if(config.fakePlayer.snapshot.blockBreakProgress){Number breaking=ActionPackStateCodec.findNumber(actionPack,"blockBreakingProgress");data.blockBreakingProgress=breaking==null?null:breaking.intValue();}
        }
        return data;
    }

    public static RestoreSummary restore(MinecraftServer server,List<FakePlayerSnapshot> snapshots,AntiCrashConfig config,AntiCrashLogger logger){
        RestoreSummary summary=new RestoreSummary(); if(!config.fakePlayer.enabled||snapshots==null||snapshots.isEmpty()) return summary;
        summary.expected=snapshots.size();
        if(!available){logger.importantKey("fakeplayer.snapshots_without_carpet");summary.failed=snapshots.size();return summary;}
        Set<String> uuids=new HashSet<>();Set<String> names=new HashSet<>();boolean invalid=false;
        for(FakePlayerSnapshot data:snapshots)try{validate(data,config);if(!uuids.add(data.uuid.toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Duplicate FakePlayer UUID");if(!names.add(data.name.toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Duplicate FakePlayer name");}catch(Exception exception){invalid=true;logger.importantKey("fakeplayer.restore_failed",data==null?"<null>":data.name,exception.getMessage());}
        if(invalid){summary.failed=summary.expected;return summary;}
        for (FakePlayerSnapshot data : snapshots) {
            try {
                ServerPlayerEntity player = find(server, data);
                if (player == null && config.fakePlayer.autoRejoin) player = create(server, data);
                if (player == null || !isFakePlayer(player)) {
                    throw new IllegalStateException("Carpet did not create the FakePlayer");
                }
                if (!player.getUuidAsString().equalsIgnoreCase(data.uuid)) {
                    throw new IllegalStateException("FakePlayer UUID does not match the checkpoint: expected "
                            + data.uuid + ", actual " + player.getUuidAsString());
                }
                if (!data.name.equals(player.getGameProfile().getName())) {
                    throw new IllegalStateException("FakePlayer name does not match the checkpoint");
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
    public static void validateSnapshots(List<FakePlayerSnapshot> snapshots,
                                         AntiCrashConfig config) throws IOException {
        if(snapshots==null)throw new IOException("FakePlayer snapshot list is missing");
        Set<String> uuids=new HashSet<>();
        Set<String> names=new HashSet<>();
        for(FakePlayerSnapshot data:snapshots){
            try{
                validate(data,config);
                if(!uuids.add(data.uuid.toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Duplicate FakePlayer UUID");
                if(!names.add(data.name.toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Duplicate FakePlayer name");
            }catch(Exception exception){
                if(exception instanceof IOException ioException)throw ioException;
                throw new IOException("Invalid FakePlayer snapshot: "+exception.getMessage(),exception);
            }
        }
    }

    private static ServerPlayerEntity find(MinecraftServer server,FakePlayerSnapshot data){
        try{UUID uuid=UUID.fromString(data.uuid);ServerPlayerEntity byUuid=server.getPlayerManager().getPlayer(uuid);if(byUuid!=null&&isFakePlayer(byUuid))return byUuid;}catch(Exception ignored){}
        for(ServerPlayerEntity player:server.getPlayerManager().getPlayerList())if(isFakePlayer(player)&&player.getGameProfile().getName().equalsIgnoreCase(data.name))return player;return null;
    }

    private static ServerPlayerEntity create(MinecraftServer server,FakePlayerSnapshot data) throws IOException {
        if (!server.isOnThread()) throw new IOException("FakePlayer restore must run on the server thread");
        UUID expectedUuid = UUID.fromString(data.uuid);
        // PlayerManager.onPlayerConnect may disconnect an existing UUID. Reject collisions before
        // calling Carpet so restoring a bot cannot evict or overwrite a real player's session.
        if (server.getPlayerManager().getPlayer(expectedUuid) != null
                || server.getPlayerManager().getPlayer(data.name) != null) {
            throw new IOException("FakePlayer checkpoint identity is already in use: " + data.name);
        }
        try (FakePlayerProfileScope ignored = FakePlayerProfileScope.open(
                server.getUserCache(), server, expectedUuid, data.name)) {
            return createWithCheckpointProfile(server, data);
        }
    }

    private static ServerPlayerEntity createWithCheckpointProfile(MinecraftServer server,FakePlayerSnapshot data) throws IOException {
        ServerWorld world=findWorld(server,data.dimension); if(world==null)world=server.getOverworld(); GameMode mode=data.gamemode==null?GameMode.SURVIVAL:GameMode.byName(data.gamemode,GameMode.SURVIVAL);
        for(String className:FAKE_CLASSES) try{Class<?> type=Class.forName(className);for(Method method:ReflectionUtil.allMethods(type))if(Modifier.isStatic(method.getModifiers())&&(method.getName().equals("createFake")||method.getName().equals("createFakePlayer"))){Object[] args=arguments(method.getParameterTypes(),server,world,data,mode);if(args==null)continue;Object result=method.invoke(null,args);if(result instanceof ServerPlayerEntity player)return player;ServerPlayerEntity found=find(server,data);return found;}}catch(java.lang.reflect.InvocationTargetException failure){throw new IOException("Carpet FakePlayer factory failed",failure.getCause());}catch(Exception|LinkageError ignored){}
        // Command fallback: only characters a Brigadier word accepts, so the name cannot alter it.
        if(data.name!=null&&data.name.matches("[A-Za-z0-9_.+-]{1,16}")){try{RecoveryCommandPolicy.runInternal(()->{server.getCommandManager().executeWithPrefix(server.getCommandSource(),"player "+data.name+" spawn");return null;});}catch(Exception|LinkageError ignored){}}
        return find(server,data);
    }

    private static Object[] arguments(Class<?>[] types,MinecraftServer server,ServerWorld world,FakePlayerSnapshot data,GameMode mode){
        Object[] args=new Object[types.length];int doubles=0,floats=0,booleans=0;
        double[] doubleValues={data.x,data.y,data.z,data.yaw,data.pitch};float[] floatValues={data.yaw,data.pitch};
        for(int i=0;i<types.length;i++){Class<?> t=types[i];if(t==net.minecraft.util.math.Vec3d.class){args[i]=new net.minecraft.util.math.Vec3d(data.x,data.y,data.z);doubles=3;}else if(t==String.class)args[i]=data.name;else if(t==MinecraftServer.class||server!=null&&t.isAssignableFrom(server.getClass()))args[i]=server;else if(t==ServerWorld.class||world!=null&&t.isAssignableFrom(world.getClass()))args[i]=world;else if(t==RegistryKey.class)args[i]=world.getRegistryKey();else if(t==GameProfile.class)try{args[i]=new GameProfile(UUID.fromString(data.uuid),data.name);}catch(Exception e){return null;}else if(t==GameMode.class)args[i]=mode;else if(t==double.class||t==Double.class){if(doubles>=doubleValues.length)return null;args[i]=doubleValues[doubles++];}else if(t==float.class||t==Float.class){if(floats>=floatValues.length)return null;args[i]=floatValues[floats++];}else if(t==boolean.class||t==Boolean.class){args[i]=booleans++==0?data.flying:false;}else if(t==UUID.class)try{args[i]=UUID.fromString(data.uuid);}catch(Exception e){return null;}else return null;}return args;
    }

    private static void apply(MinecraftServer server,ServerPlayerEntity player,FakePlayerSnapshot data,AntiCrashConfig config) throws IOException {
        ServerWorld world=(ServerWorld) player.getWorld();
        if(config.fakePlayer.snapshot.dimension){world=findWorld(server,data.dimension);if(world==null)throw new IllegalArgumentException("FakePlayer dimension is unavailable: "+data.dimension);}
        boolean inventoryEnabled=config.fakePlayer.snapshot.inventory||config.fakePlayer.snapshot.selectedSlot;if(inventoryEnabled){if(data.inventory==null)throw new IOException("Enabled FakePlayer inventory snapshot is missing");data.inventory.validate(player,config.fakePlayer.snapshot.inventory,config.fakePlayer.snapshot.inventory,config.fakePlayer.snapshot.inventory,config.fakePlayer.snapshot.selectedSlot);}
        List<StatusEffectInstance> effects=null;if(config.fakePlayer.snapshot.effects){if(data.effectsNbt==null)throw new IOException("Enabled FakePlayer effect snapshot is missing");effects=new ArrayList<>(data.effectsNbt.size());for(String encoded:data.effectsNbt){NbtElement element=NbtCodec.decode(encoded);if(!(element instanceof net.minecraft.nbt.NbtCompound compound))throw new IOException("FakePlayer effect snapshot is not an NBT compound");StatusEffectInstance effect=StatusEffectInstance.fromNbt(compound);if(effect==null)throw new IOException("FakePlayer effect snapshot is invalid");effects.add(effect);}}
        boolean actionRequested=config.fakePlayer.snapshot.actions||config.fakePlayer.snapshot.actionInternalTiming||config.fakePlayer.snapshot.itemUseCooldown||config.fakePlayer.snapshot.blockBreakProgress;Object actionPack=findActionPack(player);if(actionRequested&&(data.actionPackState==null||actionPack==null))throw new IOException("Enabled FakePlayer ActionPack snapshot is missing");
        if(actionRequested)ActionPackStateCodec.validateSnapshot(data.actionPackState,
                config.fakePlayer.snapshot.actions,config.fakePlayer.snapshot.actionInternalTiming,
                config.fakePlayer.snapshot.itemUseCooldown,config.fakePlayer.snapshot.blockBreakProgress);
        net.minecraft.entity.Entity vehicle=null;
        if(data.vehicleUuid!=null){
            UUID vehicleId;try{vehicleId=UUID.fromString(data.vehicleUuid);}catch(Exception exception){throw new IOException("Invalid FakePlayer vehicle UUID",exception);}
            if(data.rootVehicleNbt!=null){
                vehicle=restoreRootVehicle(server,world,player,data.rootVehicleNbt,vehicleId);
            }else{
                // A snapshot from before the vehicle was captured: whatever the player data held.
                for(ServerWorld candidate:server.getWorlds()){vehicle=candidate.getEntity(vehicleId);if(vehicle!=null)break;}
            }
            if(vehicle==null)throw new IOException("FakePlayer vehicle is unavailable: "+data.vehicleUuid);
        }else if(player.hasVehicle()){
            // Not riding at the checkpoint: the vehicle the player data put it on is from another time.
            discardStaleVehicle(player);
        }
        if(config.fakePlayer.snapshot.position||config.fakePlayer.snapshot.rotation||config.fakePlayer.snapshot.dimension){double x=config.fakePlayer.snapshot.position?data.x:player.getX(),y=config.fakePlayer.snapshot.position?data.y:player.getY(),z=config.fakePlayer.snapshot.position?data.z:player.getZ();float yaw=config.fakePlayer.snapshot.rotation?data.yaw:player.getYaw(0),pitch=config.fakePlayer.snapshot.rotation?data.pitch:player.getPitch(0);if(config.fakePlayer.snapshot.dimension&&player.getWorld()!=world)player.teleport(world,x,y,z,yaw,pitch);else{player.updatePositionAndAngles(x,y,z,yaw,pitch);
            // A FakePlayer's player ticket follows its tracked chunk position. Move it now so a
            // FakePlayer that keeps chunks loaded does so at its checkpoint position immediately.
            ((ServerWorld)player.getWorld()).getChunkManager().updatePosition(player);}}
        if(config.fakePlayer.snapshot.gamemode&&data.gamemode!=null)player.changeGameMode(GameMode.byName(data.gamemode,player.interactionManager.getGameMode()));if(inventoryEnabled)data.inventory.restore(player,config.fakePlayer.snapshot.inventory,config.fakePlayer.snapshot.inventory,config.fakePlayer.snapshot.inventory,config.fakePlayer.snapshot.selectedSlot);if(config.fakePlayer.snapshot.flying){player.getAbilities().flying=data.flying;player.sendAbilitiesUpdate();}if(config.fakePlayer.snapshot.sneaking){player.setSneaking(data.sneaking);}if(config.fakePlayer.snapshot.sprinting){player.setSprinting(data.sprinting);}if(config.fakePlayer.snapshot.movement)player.updateInput(data.strafing,data.forward,false,data.sneaking);if(config.fakePlayer.snapshot.health)player.setHealth(data.health);if(config.fakePlayer.snapshot.hunger){player.getHungerManager().setFoodLevel(data.hunger);player.getHungerManager().setSaturationLevel(data.saturation);player.getHungerManager().setExhaustion(data.exhaustion);}if(effects!=null){player.clearStatusEffects();for(StatusEffectInstance effect:effects)player.addStatusEffect(effect);}
        restoreUseState(player,data);
        if(data.actionPackState!=null)ActionPackStateCodec.restore(actionPack,data.actionPackState,
                config.fakePlayer.snapshot.actions,config.fakePlayer.snapshot.actionInternalTiming,
                config.fakePlayer.snapshot.itemUseCooldown,config.fakePlayer.snapshot.blockBreakProgress);
        if(config.fakePlayer.snapshot.itemUseCooldown)ActionPackStateCodec.setNumber(actionPack,"itemUseCooldown",data.itemUseCooldown);
        if(config.fakePlayer.snapshot.blockBreakProgress)ActionPackStateCodec.setNumber(actionPack,"blockBreakingProgress",data.blockBreakingProgress);
        if(data.actionPackState!=null&&!ActionPackStateCodec.matches(data.actionPackState,actionPack,
                config.fakePlayer.snapshot.actions,config.fakePlayer.snapshot.actionInternalTiming,
                config.fakePlayer.snapshot.itemUseCooldown,config.fakePlayer.snapshot.blockBreakProgress))throw new IOException("FakePlayer ActionPack verification failed: "+ActionPackStateCodec.describeMismatch(data.actionPackState,actionPack,config.fakePlayer.snapshot.actions,config.fakePlayer.snapshot.actionInternalTiming,config.fakePlayer.snapshot.itemUseCooldown,config.fakePlayer.snapshot.blockBreakProgress));
        if(vehicle!=null){if(vehicle.getWorld()!=player.getWorld()||!player.startRiding(vehicle,true))throw new IOException("FakePlayer riding state could not be restored");}
    }

    /**
     * The root vehicle with every non-player passenger, written the way an entity chunk save
     * writes it, so the state AntiCrash adds to entity data is included.
     */
    private static String captureRootVehicle(ServerPlayerEntity player) throws IOException {
        net.minecraft.entity.Entity root=player.getRootVehicle();
        if(root==null||root==player)return null;
        net.minecraft.nbt.NbtCompound nbt=new net.minecraft.nbt.NbtCompound();
        boolean saved;
        com.atsukimc.anticrash.runtime.RuntimeExtensionNbt.entitySave(true);
        try{saved=root.saveNbt(nbt);}finally{com.atsukimc.anticrash.runtime.RuntimeExtensionNbt.entitySave(false);}
        return saved?NbtCodec.encode(nbt):null;
    }

    /** Removes the vehicle (and its non-player passengers) the player currently rides. */
    private static void discardStaleVehicle(ServerPlayerEntity player){
        net.minecraft.entity.Entity root=player.getRootVehicle();
        player.stopRiding();
        if(root==null||root==player)return;
        for(net.minecraft.entity.Entity entity:root.streamPassengersAndSelf().toList()){
            if(!(entity instanceof net.minecraft.entity.player.PlayerEntity))entity.discard();
        }
    }

    private static void collectUuids(net.minecraft.nbt.NbtCompound nbt,Set<UUID> uuids){
        if(nbt.containsUuid("UUID"))uuids.add(nbt.getUuid("UUID"));
        net.minecraft.nbt.NbtList passengers=nbt.getList("Passengers",10);
        for(int i=0;i<passengers.size();i++)collectUuids(passengers.getCompound(i),uuids);
    }

    /**
     * Puts the checkpoint's vehicle into the world in place of the one Vanilla loaded from the
     * player data file when the FakePlayer joined.
     */
    private static net.minecraft.entity.Entity restoreRootVehicle(MinecraftServer server,ServerWorld world,
            ServerPlayerEntity player,String encoded,UUID vehicleId) throws IOException {
        net.minecraft.nbt.NbtCompound nbt=NbtCodec.decodeCompound(encoded);
        Set<UUID> uuids=new HashSet<>();
        collectUuids(nbt,uuids);
        if(!uuids.contains(vehicleId))throw new IOException("FakePlayer vehicle snapshot does not contain the vehicle");
        if(player.hasVehicle())discardStaleVehicle(player);
        for(UUID uuid:uuids)for(ServerWorld candidate:server.getWorlds()){
            net.minecraft.entity.Entity existing=candidate.getEntity(uuid);
            if(existing!=null&&!(existing instanceof net.minecraft.entity.player.PlayerEntity))existing.discard();
        }
        net.minecraft.entity.Entity root=net.minecraft.entity.EntityType.loadEntityWithPassengers(nbt,world,
                entity->world.tryLoadEntity(entity)?entity:null);
        if(root==null)throw new IOException("FakePlayer vehicle could not be loaded");
        for(net.minecraft.entity.Entity entity:root.streamPassengersAndSelf().toList()){
            if(vehicleId.equals(entity.getUuid()))return entity;
        }
        throw new IOException("FakePlayer vehicle was not created from its snapshot");
    }

    private static void captureUseState(ServerPlayerEntity player,FakePlayerSnapshot data){
        com.atsukimc.anticrash.mixin.LivingEntityAccessorMixin living=(com.atsukimc.anticrash.mixin.LivingEntityAccessorMixin)player;
        data.lastAttackedTicks=living.anticrash$getLastAttackedTicks();
        if(player.isUsingItem()){data.activeHand=player.getActiveHand().name();data.itemUseTimeLeft=living.anticrash$getItemUseTimeLeft();}
        com.atsukimc.anticrash.mixin.ItemCooldownManagerAccessorMixin manager=(com.atsukimc.anticrash.mixin.ItemCooldownManagerAccessorMixin)player.getItemCooldownManager();
        List<FakePlayerSnapshot.ItemCooldown> cooldowns=new ArrayList<>();
        for(java.util.Map.Entry<net.minecraft.item.Item,Object> entry:manager.anticrash$getEntries().entrySet()){
            int remaining=((com.atsukimc.anticrash.mixin.ItemCooldownEntryAccessorMixin)entry.getValue()).anticrash$getEndTick()-manager.anticrash$getTick();
            if(remaining<=0)continue;
            FakePlayerSnapshot.ItemCooldown cooldown=new FakePlayerSnapshot.ItemCooldown();
            cooldown.item=net.minecraft.registry.Registries.ITEM.getId(entry.getKey()).toString();cooldown.remainingTicks=remaining;cooldowns.add(cooldown);
        }
        cooldowns.sort(java.util.Comparator.comparing(cooldown->cooldown.item));
        if(!cooldowns.isEmpty())data.itemCooldowns=cooldowns;
    }

    /** Runs after the inventory is back, so the hand holds the item that was being used. */
    private static void restoreUseState(ServerPlayerEntity player,FakePlayerSnapshot data)throws IOException{
        com.atsukimc.anticrash.mixin.LivingEntityAccessorMixin living=(com.atsukimc.anticrash.mixin.LivingEntityAccessorMixin)player;
        if(data.lastAttackedTicks!=null){
            living.anticrash$setLastAttackedTicks(Math.max(0,data.lastAttackedTicks));
            // PlayerEntity#tick resets the attack cooldown when the main-hand item differs from the
            // one it saw last tick. A newly created player has seen nothing, so its first tick would
            // throw the restored value away. At a tick boundary that remembered stack is the main hand.
            ((com.atsukimc.anticrash.mixin.PlayerEntityAccessorMixin)player).anticrash$setSelectedItem(player.getMainHandStack().copy());
        }
        if(data.activeHand!=null&&data.itemUseTimeLeft!=null){
            net.minecraft.util.Hand hand;
            try{hand=net.minecraft.util.Hand.valueOf(data.activeHand);}catch(IllegalArgumentException invalid){throw new IOException("Invalid FakePlayer active hand: "+data.activeHand);}
            if(player.getStackInHand(hand).isEmpty())throw new IOException("FakePlayer was using an item but its hand is empty");
            player.clearActiveItem();
            player.setCurrentHand(hand);
            living.anticrash$setItemUseTimeLeft(Math.max(0,data.itemUseTimeLeft));
        }
        if(data.itemCooldowns!=null)for(FakePlayerSnapshot.ItemCooldown cooldown:data.itemCooldowns){
            if(cooldown==null||cooldown.item==null||cooldown.remainingTicks<=0)continue;
            net.minecraft.util.Identifier id=net.minecraft.util.Identifier.tryParse(cooldown.item);
            if(id==null||!net.minecraft.registry.Registries.ITEM.containsId(id))throw new IOException("Unknown FakePlayer cooldown item: "+cooldown.item);
            // The same remaining time and the same expiry tick; only the progress bar's start differs.
            player.getItemCooldownManager().set(net.minecraft.registry.Registries.ITEM.get(id),cooldown.remainingTicks);
        }
    }

    private static Object findActionPack(ServerPlayerEntity player){for(Method method:ReflectionUtil.allMethods(player.getClass())){String name=method.getName().toLowerCase(Locale.ROOT);if(method.getParameterCount()==0&&(name.contains("actionpack")||name.contains("action_pack"))&&!method.getReturnType().isPrimitive())try{return method.invoke(player);}catch(Exception ignored){}}for(java.lang.reflect.Field field:ReflectionUtil.instanceFields(player.getClass())){String name=field.getName().toLowerCase(Locale.ROOT);if(name.contains("actionpack")||name.contains("action_pack"))try{return field.get(player);}catch(Exception ignored){}}return null;}
    private static ServerWorld findWorld(MinecraftServer server,String id){if(id!=null)for(ServerWorld world:server.getWorlds())if(world.getRegistryKey().getValue().toString().equals(id))return world;return null;}
    private static void validate(FakePlayerSnapshot data,AntiCrashConfig config) throws IOException {
        if(data==null||data.formatVersion!=1)throw new IllegalArgumentException("Unsupported or empty FakePlayer snapshot");
        // Carpet accepts any name Vanilla's login would (e.g. "bot.bed"): printable ASCII without
        // spaces, at most 16 characters. The old [A-Za-z0-9_] rule refused a whole restore.
        if(data.name==null||!data.name.matches("[\\x21-\\x7E]{1,16}"))throw new IllegalArgumentException("Invalid FakePlayer name: "+data.name);
        try{UUID parsed=UUID.fromString(data.uuid);if(!parsed.toString().equalsIgnoreCase(data.uuid))throw new IllegalArgumentException("non-canonical UUID");}catch(Exception exception){throw new IllegalArgumentException("Invalid FakePlayer UUID",exception);}
        if(config.fakePlayer.snapshot.dimension&&data.dimension==null)throw new IllegalArgumentException("FakePlayer dimension is missing");
        if(config.fakePlayer.snapshot.position&&!(Double.isFinite(data.x)&&Double.isFinite(data.y)&&Double.isFinite(data.z)))throw new IllegalArgumentException("FakePlayer position is not finite");
        if(config.fakePlayer.snapshot.rotation&&!(Float.isFinite(data.yaw)&&Float.isFinite(data.pitch)))throw new IllegalArgumentException("FakePlayer rotation is not finite");
        if(config.fakePlayer.snapshot.movement&&!(Float.isFinite(data.forward)&&Float.isFinite(data.strafing)))throw new IllegalArgumentException("FakePlayer movement is not finite");
        if(config.fakePlayer.snapshot.health&&!Float.isFinite(data.health))throw new IllegalArgumentException("FakePlayer health is not finite");
        if(config.fakePlayer.snapshot.hunger&&!(Float.isFinite(data.saturation)&&Float.isFinite(data.exhaustion)))throw new IllegalArgumentException("FakePlayer hunger state is not finite");
        if(config.fakePlayer.snapshot.gamemode&&(data.gamemode==null||!Set.of("survival","creative","adventure","spectator").contains(data.gamemode)))throw new IllegalArgumentException("Invalid FakePlayer gamemode");
        if(data.vehicleUuid!=null)try{UUID parsed=UUID.fromString(data.vehicleUuid);if(!parsed.toString().equalsIgnoreCase(data.vehicleUuid))throw new IllegalArgumentException("non-canonical UUID");}catch(Exception exception){throw new IllegalArgumentException("Invalid FakePlayer vehicle UUID",exception);}
        if(data.effectsNbt==null||data.effectsNbt.size()>1024)throw new IllegalArgumentException("Invalid FakePlayer effect list");
        if(config.fakePlayer.snapshot.effects)for(String encoded:data.effectsNbt){
            NbtElement element=NbtCodec.decode(encoded);
            if(!(element instanceof net.minecraft.nbt.NbtCompound compound)
                    ||StatusEffectInstance.fromNbt(compound)==null)throw new IOException("FakePlayer effect snapshot is invalid");
        }
        boolean inventoryEnabled=config.fakePlayer.snapshot.inventory||config.fakePlayer.snapshot.selectedSlot;
        if(inventoryEnabled){
            if(data.inventory==null)throw new IOException("Enabled FakePlayer inventory snapshot is missing");
            data.inventory.validatePortable(config.fakePlayer.snapshot.inventory,
                    config.fakePlayer.snapshot.inventory,config.fakePlayer.snapshot.inventory,
                    config.fakePlayer.snapshot.selectedSlot);
        }
        boolean actionRequested=config.fakePlayer.snapshot.actions
                ||config.fakePlayer.snapshot.actionInternalTiming
                ||config.fakePlayer.snapshot.itemUseCooldown
                ||config.fakePlayer.snapshot.blockBreakProgress;
        if(actionRequested)ActionPackStateCodec.validateSnapshot(data.actionPackState,
                config.fakePlayer.snapshot.actions,config.fakePlayer.snapshot.actionInternalTiming,
                config.fakePlayer.snapshot.itemUseCooldown,config.fakePlayer.snapshot.blockBreakProgress);
    }
    public static final class RestoreSummary{
        public int expected,ready,failed;
        public boolean allReady(){return expected==ready&&failed==0;}
        public static RestoreSummary pending(int expected){RestoreSummary summary=new RestoreSummary();summary.expected=Math.max(0,expected);return summary;}
    }
}
