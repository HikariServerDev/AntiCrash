//? if (>=1.18 && <=1.21.11) {
package com.atsukimc.anticrash.probe;

import com.atsukimc.anticrash.AntiCrashController;
import com.atsukimc.anticrash.AntiCrashMod;
import com.atsukimc.anticrash.mixin.LivingEntityAccessorMixin;
import com.atsukimc.anticrash.mixin.ServerWorldAccessorMixin;
import com.atsukimc.anticrash.mixin.WorldAccessorMixin;
import com.atsukimc.anticrash.runtime.BlockEntityTickerSnapshot;
import com.atsukimc.anticrash.runtime.ChunkStateSnapshot;
import com.atsukimc.anticrash.runtime.EntityRuntimeSnapshot;
import com.atsukimc.anticrash.runtime.MovingPistonTickSnapshot;
import com.atsukimc.anticrash.runtime.RedstoneTorchBurnoutSnapshot;
import com.atsukimc.anticrash.runtime.RuntimeCapsule;
import com.atsukimc.anticrash.runtime.ScheduledTickSnapshot;
import com.atsukimc.anticrash.runtime.TicketLedgerSnapshot;
import com.google.gson.Gson;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.nbt.CompoundTag;
//? if (>=1.19.3 && <=1.21.11) {
/*import net.minecraft.core.registries.BuiltInRegistries;
*///?}

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
//? if (>=1.18 && <=1.19.2) {
import net.minecraft.core.Registry;
//?}

import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Test-only. Runs a script of commands at exact game ticks and writes the simulation state at the
 * end of every tick, so that an uninterrupted run (control) and a crash-and-recover run (recovery)
 * that start from the same checkpoint can be compared tick by tick.
 *
 * <p>Files, all under {@code <server>/probe}: {@code label.txt} (setup, control or recovery),
 * {@code boxes.txt} ({@code dimension x1 y1 z1 x2 y2 z2}), {@code script.txt} (lines {@code <ticks
 * after load> <command>}; turned into absolute game times once and kept as {@code script.abs.txt}
 * so the recovery run continues the same script). Directives: {@code @checkpoint}, {@code @halt}
 * (kill the JVM in a control run, stop cleanly in a recovery run) and {@code @stop}.
 */
public final class RuntimeStateProbe implements ModInitializer {
    private record Box(
            String dimension, int x1, int y1, int z1, int x2, int y2, int z2, boolean blocks) {
        boolean contains(BlockPos pos) {
            return pos.getX() >= x1
                    && pos.getX() <= x2
                    && pos.getY() >= y1
                    && pos.getY() <= y2
                    && pos.getZ() >= z1
                    && pos.getZ() <= z2;
        }

        boolean contains(double x, double y, double z) {
            return x >= x1 && x <= x2 + 1 && y >= y1 && y <= y2 + 1 && z >= z1 && z <= z2 + 1;
        }
    }

    private record Step(long time, String command) {}

    private static final Gson GSON = new Gson();
    private Path directory;
    private String label = "none";
    private final List<Box> boxes = new ArrayList<>();
    private List<Step> steps;
    private int cursor;
    private long lastTime = Long.MIN_VALUE;
    private String lastDump = "";
    private boolean audited;
    //? if (>=1.18 && <=1.21.10) {
    private static final net.minecraft.resources.ResourceLocation ECHO_CHANNEL =
            //?}
            //? if 1.21.11 {
            /*private static final net.minecraft.resources.Identifier ECHO_CHANNEL =
    *///?}
            identifier("anticrash_probe", "echo");
    private final java.util.concurrent.atomic.AtomicInteger echoReceived =
            new java.util.concurrent.atomic.AtomicInteger();
    private int payloadPhase;
    private int payloadWait;
    private String payloadFrozenResult;

    //? if (>=1.18 && <=1.21.10) {
    private static net.minecraft.resources.ResourceLocation identifier(
            String namespace, String path) {
        //?}
        //? if 1.21.11 {
        /*private static net.minecraft.resources.Identifier identifier(String namespace, String path) {
    *///?}
        //? if <=1.20.6 {
        return new net.minecraft.resources.ResourceLocation(namespace, path);
        //?}
        //? if (>=1.21 && <=1.21.10) {
        /*return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(namespace, path);
*///?}
        //? if 1.21.11 {
        /*return net.minecraft.resources.Identifier.fromNamespaceAndPath(namespace, path);
*///?}
    }

    // Use the actual wire decoder once payload packets become typed records.
    // This preserves channel data and lets installed mods decode their own payloads.
    //? if <=1.20.1 {
    private static net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket payload(
            //?} else {
            /*private static net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket payload(
*///?}
            //? if (>=1.18 && <=1.21.10) {
            net.minecraft.resources.ResourceLocation channel,
            net.minecraft.network.FriendlyByteBuf data) {
        //?}
        //? if 1.21.11 {
        /*net.minecraft.resources.Identifier channel, net.minecraft.network.FriendlyByteBuf data) {
            *///?}
        //? if <=1.20.1 {
        return new net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket(
                channel, data);
        //?} else {
        /*var wire = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            //? if (>=1.20.2 && <=1.21.10) {
            /^wire.writeResourceLocation(channel);
            ^///?}
            //? if 1.21.11 {
            /^wire.writeIdentifier(channel);
            ^///?}
            wire.writeBytes(data, data.readerIndex(), data.readableBytes());
            //? if <=1.20.4 {
            return new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(wire);
            //?} else {
            /^return net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket.STREAM_CODEC.decode(wire);
            ^///?}
        } finally {
            wire.release();
            data.release();
        }
*///?}
    }

    private static CompoundTag blockEntityNbt(BlockEntity entity, ServerLevel world) {
        //? if <=1.20.4 {
        return entity.saveWithId();
        //?} else if <=1.21.5 {
        /*return entity.saveWithId(world.registryAccess());
*///?} else {
        /*var view = com.atsukimc.anticrash.runtime.VersionStorage.writer(world.registryAccess());
        entity.saveWithId(view);
        return view.buildResult();
*///?}
    }

    private static CompoundTag entityNbt(Entity entity) {
        //? if <=1.21.5 {
        return entity.saveWithoutId(new CompoundTag());
        //?} else {
        /*var view = com.atsukimc.anticrash.runtime.VersionStorage.writer(entity.registryAccess());
        entity.saveWithoutId(view);
        return view.buildResult();
*///?}
    }

    private static String carpetRules() throws ReflectiveOperationException {
        Class<?> settings = Class.forName("carpet.CarpetSettings");
        return " creativeNoClip="
                + settings.getField("creativeNoClip").get(null)
                + " flippinCactus="
                + settings.getField("flippinCactus").get(null);
    }

    // Fabric API is compiled against intermediary names here, so its networking API is reached
    // reflectively.
    private void registerEchoReceiver() {
        try {
            Class<?> networking =
                    Class.forName("net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking");
            Class<?> handlerType =
                    Class.forName(
                            "net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking$PlayChannelHandler");
            Object handler =
                    java.lang.reflect.Proxy.newProxyInstance(
                            handlerType.getClassLoader(),
                            new Class<?>[] {handlerType},
                            (proxy, method, arguments) -> {
                                if (method.getName().equals("receive"))
                                    echoReceived.incrementAndGet();
                                return method.getReturnType() == boolean.class
                                        ? Boolean.FALSE
                                        : null;
                            });
            for (java.lang.reflect.Method method : networking.getMethods()) {
                if (method.getName().equals("registerGlobalReceiver")) {
                    method.invoke(null, ECHO_CHANNEL, handler);
                    return;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            // Without Fabric networking the payload test reports the channel as unregistered.
        }
    }

    private static boolean canSend(net.minecraft.server.level.ServerPlayer player) {
        try {
            for (java.lang.reflect.Method method :
                    Class.forName("net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking")
                            .getMethods()) {
                if (method.getName().equals("canSend")
                        && method.getParameterCount() == 2
                        && method.getParameterTypes()[0].isInstance(player)) {
                    return (Boolean) method.invoke(null, player, ECHO_CHANNEL);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            // reported as false
        }
        return false;
    }

    /**
     * What a client that joins during the recovery freeze sends: its channel registration, the
     * Carpet handshake and an ordinary mod payload. Records what the server made of each while
     * frozen and again after the world was started.
     */
    private void payloadTest(MinecraftServer server) throws Exception {
        AntiCrashController controller = AntiCrashMod.controller();
        if (controller == null || server.getPlayerList().getPlayers().isEmpty()) return;
        net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        Class<?> carpet = Class.forName("carpet.network.ServerNetworkHandler");
        java.lang.reflect.Method valid =
                carpet.getMethod(
                        "isValidCarpetPlayer", net.minecraft.server.level.ServerPlayer.class);
        if (payloadPhase == 0) {
            if (!controller.isRecoveryFrozen()
                    || !controller.statusSnapshot().state().equals("READY_TO_START")) return;
            net.minecraft.network.FriendlyByteBuf register =
                    new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            register.writeBytes(
                    ECHO_CHANNEL.toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            Class<?> client = Class.forName("carpet.network.CarpetClient");
            net.minecraft.network.FriendlyByteBuf hello =
                    new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            hello.writeVarInt(client.getField("HELLO").getInt(null));
            hello.writeUtf(
                    String.valueOf(
                            Class.forName("carpet.CarpetSettings")
                                    .getField("carpetVersion")
                                    .get(null)));

            var packets =
                    List.of(
                            payload(identifier("minecraft", "register"), register),
                            payload(
                                    //? if (>=1.18 && <=1.21.10) {
                                    (net.minecraft.resources.ResourceLocation)
                                            //?}
                                            //? if 1.21.11 {
                                            /*(net.minecraft.resources.Identifier)
                                    *///?}
                                            client.getField("CARPET_CHANNEL").get(null),
                                    hello),
                            payload(
                                    ECHO_CHANNEL,
                                    new net.minecraft.network.FriendlyByteBuf(
                                            io.netty.buffer.Unpooled.buffer())),
                            // No Fabric receiver: exercises a mod's own packet-handler hook.
                            payload(
                                    identifier("anticrash_probe", "own_hook"),
                                    new net.minecraft.network.FriendlyByteBuf(
                                            io.netty.buffer.Unpooled.buffer())));

            // As a real client's packets arrive: on a network thread, not on the server thread.
            Thread network =
                    new Thread(
                            () -> {
                                for (var packet : packets) {
                                    try {
                                        player.connection.handleCustomPayload(packet);
                                    } catch (RuntimeException offThread) {
                                        /* rescheduled to the server thread */
                                    }
                                }
                            },
                            "probe-network");
            network.start();
            payloadPhase = 1;
        } else if (payloadPhase == 1 && ++payloadWait >= 10) {
            payloadFrozenResult =
                    "frozen carpetClient="
                            + valid.invoke(null, player)
                            + " channelRegistered="
                            + canSend(player)
                            + " modPayloadHandled="
                            + echoReceived.get()
                            + carpetRules();
            payloadPhase = 2;
            payloadWait = 0;
            run(server, "ac start");
        } else if (payloadPhase == 2 && !controller.isRecoveryFrozen() && ++payloadWait >= 10) {
            Files.writeString(
                    directory.resolve("payload-result.txt"),
                    payloadFrozenResult
                            + "\nstarted carpetClient="
                            + valid.invoke(null, player)
                            + " channelRegistered="
                            + canSend(player)
                            + " modPayloadHandled="
                            + echoReceived.get()
                            + carpetRules()
                            + "\n");
            Files.delete(directory.resolve("payload-test"));
            Runtime.getRuntime().halt(7);
        }
    }

    private boolean stopWhenQuiet;
    private int quietWait;
    private int incidentTarget;
    private int incidentCommitted;
    private long incidentNextTick;
    private String incidentLastId = "none";
    private boolean incidentInitialized;

    @Override
    public void onInitialize() {
        directory = FabricLoader.getInstance().getGameDir().resolve("probe");
        if (!Files.isDirectory(directory)) return;
        ServerTickEvents.END_SERVER_TICK.register(this::endTick);
        registerEchoReceiver();
        // Simulates a start that fails after every mod initialised but before the world is loaded
        // (another mod refusing to start, the port being taken).
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.register(
                server -> {
                    Path flag =
                            net.fabricmc.loader.api.FabricLoader.getInstance()
                                    .getGameDir()
                                    .resolve("probe")
                                    .resolve("halt-before-world");
                    if (Files.exists(flag)) {
                        try {
                            Files.delete(flag);
                        } catch (java.io.IOException ignored) {
                        }
                        Runtime.getRuntime().halt(3);
                    }
                });
    }

    private void endTick(MinecraftServer server) {
        try {
            if (Files.exists(directory.resolve("incident-count.txt"))) {
                incidentTick(server);
                return;
            }
            if (Files.exists(directory.resolve("payload-test"))) payloadTest(server);
            Path verifyBeforeStart = directory.resolve("verify-before-start");
            if (Files.exists(verifyBeforeStart)) {
                AntiCrashController controller = AntiCrashMod.controller();
                if (controller != null
                        && controller.statusSnapshot().state().equals("READY_TO_START")) {
                    run(server, "ac recovery verify --verbose");
                    run(server, "ac debug equivalence --verbose");
                    run(server, "ac start");
                    Files.move(
                            verifyBeforeStart,
                            directory.resolve("verify-before-start.done"),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
            long time = server.overworld().getGameTime();
            if (steps == null) load(time);
            if (!audited && Files.exists(directory.resolve("audit"))) {
                audited = true;
                // Applies every mixin of every loaded configuration now and throws on any failure.
                org.spongepowered.asm.mixin.MixinEnvironment.getCurrentEnvironment().audit();
                Files.writeString(directory.resolve("audit-done.txt"), "ok");
            }
            if (steps == null) return;
            String dump = dump(server, time);
            if (time != lastTime || !dump.equals(lastDump)) {
                Path out = directory.resolve("out").resolve(label);
                Files.createDirectories(out);
                Files.writeString(out.resolve(time + ".txt"), dump);
                lastTime = time;
                lastDump = dump;
            }
            if (stopWhenQuiet) {
                // A normal stop drops the block events queued for the next tick (Vanilla does not
                // save them), which breaks a flying machine caught at that moment. The harness
                // stops between runs only on a tick with nothing in flight.
                boolean quiet =
                        dump.contains("[block-events] []") && !dump.contains("[piston-order]");
                if (quiet || ++quietWait > 200) {
                    stopWhenQuiet = false;
                    server.halt(false);
                    return;
                }
            }
            while (cursor < steps.size() && steps.get(cursor).time <= time) {
                Step step = steps.get(cursor++);
                // A step at or before the checkpoint tick already ran before the crash.
                if (step.time == time) run(server, step.command);
            }
        } catch (Throwable failure) {
            try {
                Files.writeString(
                        directory.resolve("probe-error.txt"),
                        String.valueOf(failure)
                                + "\n"
                                + java.util.Arrays.toString(failure.getStackTrace()));
            } catch (IOException ignored) {
                // nothing else to do
            }
        }
    }

    /**
     * Incident-only workload; never packaged in the distributable mod. Manual cuts every 5 s stress
     * allocation/retention without changing the configured 30 s automatic interval.
     */
    private void incidentTick(MinecraftServer server) throws IOException {
        if (!incidentInitialized) {
            incidentInitialized = true;
            incidentTarget =
                    Integer.parseInt(
                            Files.readString(directory.resolve("incident-count.txt")).trim());
            for (String command :
                    List.of(
                            "gamerule doMobSpawning false",
                            "gamerule randomTickSpeed 0",
                            "gamerule doWeatherCycle false",
                            "gamerule doDaylightCycle false",
                            "forceload add 0 0 255 255",
                            "forceload add 256 0 511 255",
                            "forceload add 0 256 255 511",
                            "forceload add 256 256 511 383",
                            "execute in minecraft:the_nether run forceload add 0 0 15 15",
                            "execute in minecraft:the_nether run summon minecraft:cow 8 64 8"
                                    + " {NoAI:1b,PersistenceRequired:1b}",
                            "setblock 8 5 8 minecraft:bell",
                            "setblock 9 5 8 minecraft:lectern",
                            "player IncidentBot spawn at 8.5 6 8.5 facing 0 0 in"
                                    + " minecraft:overworld in creative",
                            "player IncidentBot use interval 4")) run(server, command);
            incidentNextTick = server.getTickCount() + 300;
        }
        AntiCrashController controller = AntiCrashMod.controller();
        if (controller == null) return;
        var status = controller.statusSnapshot();
        if (!status.activeCheckpoint().equals("none")
                && !status.activeCheckpoint().equals(incidentLastId)) {
            incidentLastId = status.activeCheckpoint();
            incidentCommitted++;
            Files.writeString(
                    directory.resolve("incident-progress.txt"),
                    incidentCommitted + " " + incidentLastId);
            incidentNextTick = server.getTickCount() + 100;
            if (incidentCommitted >= incidentTarget) {
                server.halt(false);
                return;
            }
        }
        if (server.getTickCount() % 20 == 0) {
            Runtime jvm = Runtime.getRuntime();
            Files.writeString(
                    directory.resolve("memory.csv"),
                    server.getTickCount()
                            + ","
                            + (jvm.totalMemory() - jvm.freeMemory())
                            + ","
                            + jvm.maxMemory()
                            + "\n",
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        }
        if (server.getTickCount() < incidentNextTick || !status.state().equals("RUNNING")) return;
        for (int n = 0; n < 110; n++) {
            // 1.18.2 MovingPistonBlock#createBlockEntity returns null: /setblock cannot
            // synthesize moving-piston BEs. Use its verified public semantic constructor instead.
            BlockPos pos = new BlockPos(n * 2, 8, 16);
            BlockState state =
                    net.minecraft.world.level.block.Blocks.MOVING_PISTON
                            .defaultBlockState()
                            .setValue(
                                    net.minecraft.world.level.block.piston.MovingPistonBlock.FACING,
                                    net.minecraft.core.Direction.EAST);
            server.overworld()
                    .setBlock(
                            pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
            server.overworld().setBlock(pos, state, 2);
            server.overworld()
                    .setBlockEntity(
                            new PistonMovingBlockEntity(
                                    pos,
                                    state,
                                    net.minecraft.world.level.block.Blocks.STONE
                                            .defaultBlockState(),
                                    net.minecraft.core.Direction.EAST,
                                    true,
                                    false));
        }
        run(server, "setblock 4 8 4 minecraft:water");
        run(server, "setblock 5 8 4 minecraft:repeater[delay=4,facing=east,powered=false]");
        run(server, "setblock 6 8 4 minecraft:redstone_block");
        run(
                server,
                "setblock 8 5 8 "
                        + (incidentCommitted % 2 == 0 ? "minecraft:bell" : "minecraft:lectern"));
        run(server, "scoreboard objectives add incident dummy");
        run(server, "scoreboard players add fixture incident 1");
        if (incidentCommitted == 0) controller.requestBaseCheckpoint();
        else controller.requestCheckpoint();
        incidentNextTick = server.getTickCount() + 600;
    }

    private void load(long time) throws IOException {
        Path labelFile = directory.resolve("label.txt");
        if (Files.exists(labelFile)) label = Files.readString(labelFile).trim();
        Path boxFile = directory.resolve("boxes.txt");
        boxes.clear();
        if (Files.exists(boxFile)) {
            for (String line : Files.readAllLines(boxFile)) {
                String[] p = line.trim().split("\\s+");
                if (p.length < 7) continue;
                boxes.add(
                        new Box(
                                p[0],
                                Math.min(i(p[1]), i(p[4])),
                                Math.min(i(p[2]), i(p[5])),
                                Math.min(i(p[3]), i(p[6])),
                                Math.max(i(p[1]), i(p[4])),
                                Math.max(i(p[2]), i(p[5])),
                                Math.max(i(p[3]), i(p[6])),
                                p.length < 8 || !p[7].equals("entities")));
            }
        }
        Path relative = directory.resolve("script.txt");
        Path absolute = directory.resolve("script.abs.txt");
        if (Files.exists(relative)) {
            List<String> lines = new ArrayList<>();
            for (String line : Files.readAllLines(relative)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int space = line.indexOf(' ');
                lines.add(
                        (time + 3 + Long.parseLong(line.substring(0, space)))
                                + line.substring(space));
            }
            Files.write(absolute, lines);
            Files.delete(relative);
        }
        if (!Files.exists(absolute)) return;
        List<Step> loaded = new ArrayList<>();
        for (String line : Files.readAllLines(absolute)) {
            int space = line.indexOf(' ');
            loaded.add(
                    new Step(Long.parseLong(line.substring(0, space)), line.substring(space + 1)));
        }
        loaded.sort(Comparator.comparingLong(Step::time));
        steps = loaded;
    }

    private static int i(String value) {
        return Integer.parseInt(value);
    }

    private void run(MinecraftServer server, String command) {
        switch (command) {
            case "@checkpoint" -> {
                AntiCrashController controller = AntiCrashMod.controller();
                if (controller != null) controller.requestCheckpoint();
            }
            // Both runs end as a crash. A normal stop would drop the block events queued for the
            // next tick (Vanilla does not save them) and break the flying machines for every later
            // run; after a crash the next start recovers the last checkpoint with them intact.
            case "@halt" -> Runtime.getRuntime().halt(1);
            case "@stop" -> server.halt(false);
            case "@stop-quiet" -> stopWhenQuiet = true;
            // Minecraft 1.19.1 renamed the command-source entry point; the
            // ParseResults overload named execute is not equivalent.
            //? if <=1.19 {
            default ->
                    server.getCommands().performCommand(server.createCommandSourceStack(), command);
                //?} else if <=1.21.9 {
                /*default -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
*///?} else {
                /*default -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
*///?}
        }
    }

    private String dump(MinecraftServer server, long time) {
        StringBuilder out = new StringBuilder(1 << 14);
        out.append("time ").append(time).append('\n');
        Map<String, ServerLevel> worlds = new TreeMap<>();
        for (ServerLevel world : server.getAllLevels())
            //? if (>=1.18 && <=1.21.10) {
            worlds.put(world.dimension().location().toString(), world);
        //?}
        //? if 1.21.11 {
        /*worlds.put(world.dimension().identifier().toString(), world);
            *///?}
        for (Map.Entry<String, ServerLevel> entry : worlds.entrySet())
            world(out, entry.getKey(), entry.getValue());
        capsule(out, server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            out.append("[player] ")
                    //? if <=1.21.8 {
                    .append(player.getGameProfile().getName())
                    //?} else {
                    /*.append(player.getGameProfile().name())
*///?}
                    .append(
                            String.format(
                                    Locale.ROOT,
                                    " %.5f %.5f %.5f",
                                    player.getX(),
                                    player.getY(),
                                    player.getZ()))
                    .append(" using=")
                    .append(player.isUsingItem())
                    .append(" useLeft=")
                    .append(player.getUseItemRemainingTicks())
                    .append(" lastAttack=")
                    .append(((LivingEntityAccessorMixin) player).anticrash$getLastAttackedTicks())
                    .append(" cooldown=")
                    .append(
                            player.getCooldowns()
                                    //? if <=1.21.1 {
                                    .isOnCooldown(player.getMainHandItem().getItem())
                            //?} else {
                            /*.isOnCooldown(player.getMainHandItem())
*///?}
                            )
                    .append(" health=")
                    .append(player.getHealth())
                    .append(" screen=")
                    .append(player.containerMenu != player.inventoryMenu)
                    .append('\n');
        }
        return out.toString();
    }

    private void world(StringBuilder out, String dimension, ServerLevel world) {
        WorldAccessorMixin accessor = (WorldAccessorMixin) world;
        out.append("[world] ")
                .append(dimension)
                .append(" timeOfDay=")
                .append(world.getDayTime())
                .append(" idle=")
                .append(((ServerWorldAccessorMixin) world).anticrash$getIdleTimeout())
                .append(" rain=")
                .append(accessor.anticrash$getRainGradient())
                .append(" thunder=")
                .append(accessor.anticrash$getThunderGradient())
                .append(" tickOrder=")
                .append(accessor.anticrash$getNextTickOrder())
                .append('\n');
        List<String> blockEntities = new ArrayList<>();
        java.util.Set<Long> chunks = new java.util.TreeSet<>();
        for (Box box : boxes) {
            if (!box.dimension.equals(dimension) || !box.blocks) continue;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int cx = box.x1 >> 4; cx <= box.x2 >> 4; cx++) {
                for (int cz = box.z1 >> 4; cz <= box.z2 >> 4; cz++) {
                    LevelChunk chunk = world.getChunkSource().getChunkNow(cx, cz);
                    if (chunk == null) {
                        out.append("[unloaded] ")
                                .append(dimension)
                                .append(' ')
                                .append(cx)
                                .append(',')
                                .append(cz)
                                .append('\n');
                        continue;
                    }
                    if (chunks.add(net.minecraft.world.level.ChunkPos.asLong(cx, cz))) {
                        out.append("[inhabited] ")
                                .append(dimension)
                                .append(' ')
                                .append(cx)
                                .append(',')
                                .append(cz)
                                .append(' ')
                                .append(chunk.getInhabitedTime())
                                .append('\n');
                    }
                    for (int x = Math.max(box.x1, cx << 4);
                            x <= Math.min(box.x2, (cx << 4) + 15);
                            x++) {
                        for (int z = Math.max(box.z1, cz << 4);
                                z <= Math.min(box.z2, (cz << 4) + 15);
                                z++) {
                            for (int y = box.y1; y <= box.y2; y++) {
                                BlockState state = chunk.getBlockState(pos.set(x, y, z));
                                if (state.isAir()) continue;
                                out.append("[block] ")
                                        .append(dimension)
                                        .append(' ')
                                        .append(x)
                                        .append(',')
                                        .append(y)
                                        .append(',')
                                        .append(z)
                                        .append(' ')
                                        .append(state)
                                        .append('\n');
                            }
                        }
                    }
                    for (Map.Entry<BlockPos, BlockEntity> blockEntity :
                            chunk.getBlockEntities().entrySet()) {
                        if (!box.contains(blockEntity.getKey())) continue;
                        BlockEntity value = blockEntity.getValue();
                        StringBuilder line = new StringBuilder();
                        BlockPos p = blockEntity.getKey();
                        line.append(
                                        String.format(
                                                Locale.ROOT,
                                                "%08d,%04d,%08d ",
                                                p.getX() + 10000000,
                                                p.getY() + 1000,
                                                p.getZ() + 10000000))
                                .append(p.getX())
                                .append(',')
                                .append(p.getY())
                                .append(',')
                                .append(p.getZ())
                                .append(' ')
                                .append(blockEntityNbt(value, world));
                        if (value instanceof PistonMovingBlockEntity piston) {
                            line.append(" progress=")
                                    .append(piston.getProgress(1.0f))
                                    .append(" lastProgress=")
                                    .append(piston.getProgress(0.0f));
                        }
                        if (value instanceof TheEndGatewayBlockEntity gateway) {
                            line.append(" cooldown=").append(gateway.isCoolingDown());
                        }
                        blockEntities.add(line.toString());
                    }
                }
            }
        }
        java.util.Collections.sort(blockEntities);
        for (String line : blockEntities)
            out.append("[be] ")
                    .append(dimension)
                    .append(' ')
                    .append(line.substring(line.indexOf(' ') + 1))
                    .append('\n');

        // Tick order is state: these two lists are written in iteration order, not sorted.
        for (List<TickingBlockEntity> list :
                List.of(
                        accessor.anticrash$getBlockEntityTickers(),
                        accessor.anticrash$getPendingBlockEntityTickers())) {
            for (TickingBlockEntity ticker : list) {
                if (BlockEntityTickerSnapshot.removed(ticker)
                        || !inBoxes(dimension, ticker.getPos())) continue;
                BlockPos p = ticker.getPos();
                out.append("[be-order] ")
                        .append(dimension)
                        .append(' ')
                        .append(p.getX())
                        .append(',')
                        .append(p.getY())
                        .append(',')
                        .append(p.getZ())
                        .append(' ')
                        .append(ticker.getType())
                        .append('\n');
            }
        }
        List<String> entities = new ArrayList<>();
        List<String> ages = new ArrayList<>();
        ((ServerWorldAccessorMixin) world)
                .anticrash$getEntityList()
                .forEach(entity -> {
                    if (entity instanceof ServerPlayer || !inBoxes(dimension, entity)) return;

                    //? if (>=1.18 && <=1.19.2) {
                    String id = Registry.ENTITY_TYPE.getKey(entity.getType()).toString();
                    //?}
                    //? if (>=1.19.3 && <=1.21.11) {
                    /*String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
*///?}

                    String where =
                            String.format(
                                    Locale.ROOT,
                                    "%.6f,%.6f,%.6f",
                                    entity.getX(),
                                    entity.getY(),
                                    entity.getZ());
                    out.append("[entity-order] ")
                            .append(dimension)
                            .append(' ')
                            .append(id)
                            .append(' ')
                            .append(where)
                            .append('\n');
                    CompoundTag nbt = entityNbt(entity);
                    nbt.remove("UUID");
                    entities.add(
                            id + ' ' + where + " regen=" + entity.invulnerableTime + ' ' + nbt);
                    // Entity#age is restored only for items and experience orbs (see the audit);
                    // kept on
                    // its own line so that known difference cannot hide any other.
                    ages.add(id + ' ' + where + " age=" + entity.tickCount);
                });
        java.util.Collections.sort(entities);
        for (String line : entities)
            out.append("[entity] ").append(dimension).append(' ').append(line).append('\n');
        java.util.Collections.sort(ages);
        for (String line : ages)
            out.append("[age] ").append(dimension).append(' ').append(line).append('\n');
    }

    private boolean inBoxes(String dimension, BlockPos pos) {
        for (Box box : boxes) if (box.dimension.equals(dimension) && box.contains(pos)) return true;
        return false;
    }

    private boolean inBoxes(String dimension, Entity entity) {
        for (Box box : boxes) {
            if (box.dimension.equals(dimension)
                    && box.contains(entity.getX(), entity.getY(), entity.getZ())) return true;
        }
        return false;
    }

    /** The runtime state AntiCrash itself captures, in a form that does not depend on the JVM. */
    private void capsule(StringBuilder out, MinecraftServer server) {
        AntiCrashController controller = AntiCrashMod.controller();
        if (controller == null) return;
        RuntimeCapsule capsule = controller.runtimeCapsules().captureCurrentForEquivalence(server);
        for (ScheduledTickSnapshot tick : capsule.scheduledTicks) {
            out.append("[tick] ")
                    .append(tick.dimension)
                    .append(' ')
                    .append(tick.kind)
                    .append(' ')
                    .append(tick.type)
                    .append(' ')
                    .append(tick.x)
                    .append(',')
                    .append(tick.y)
                    .append(',')
                    .append(tick.z)
                    .append(" at=")
                    .append(tick.triggerTick)
                    .append(" priority=")
                    .append(tick.priority)
                    .append(" order=")
                    .append(tick.subTickOrder)
                    .append('\n');
        }
        out.append("[block-events] ").append(GSON.toJson(capsule.blockEvents.entries)).append('\n');
        for (MovingPistonTickSnapshot piston : capsule.movingPistonTickOrder) {
            out.append("[piston-order] ")
                    .append(piston.dimension)
                    .append(' ')
                    .append(piston.x)
                    .append(',')
                    .append(piston.y)
                    .append(',')
                    .append(piston.z)
                    .append(" progress=")
                    .append(piston.progress)
                    .append(" lastProgress=")
                    .append(piston.lastProgress)
                    .append('\n');
        }
        for (BlockEntityTickerSnapshot ticker : capsule.blockEntityTickerOrder) {
            out.append("[ticker] ").append(ticker.canonical()).append('\n');
        }
        for (EntityRuntimeSnapshot entity : capsule.entityOrder) {
            if (entity.type.equals("minecraft:player")) continue;
            out.append("[entity-list] ")
                    .append(entity.dimension)
                    .append(' ')
                    .append(entity.type)
                    .append(' ')
                    .append(entity.chunkX)
                    .append(',')
                    .append(entity.chunkZ)
                    .append('\n');
        }
        List<String> tickets = new ArrayList<>();
        for (TicketLedgerSnapshot.Entry ticket : capsule.ticketLedger) {
            String argument = ticket.type.equals("post_teleport") ? "-" : ticket.argument;
            tickets.add(
                    ticket.dimension
                            + ' '
                            + ticket.x
                            + ','
                            + ticket.z
                            + ' '
                            + ticket.type
                            + " level="
                            + ticket.level
                            + " arg="
                            + argument
                            + " remaining="
                            + ticket.remainingTicks);
        }
        java.util.Collections.sort(tickets);
        for (String ticket : tickets) out.append("[ticket] ").append(ticket).append('\n');
        for (ChunkStateSnapshot chunks : capsule.chunks) {
            out.append("[domain] ")
                    .append(chunks.dimension)
                    .append(" loaded=")
                    .append(chunks.loadedChunks.size())
                    .append(" full=")
                    .append(chunks.fullChunks.size())
                    .append(" block=")
                    .append(chunks.blockTickingChunks.size())
                    .append(" entity=")
                    .append(chunks.entityTickingChunks.size())
                    .append(" forced=")
                    .append(chunks.forcedChunks.size())
                    .append('\n');
            List<String> rows = new ArrayList<>();
            for (ChunkStateSnapshot.ChunkReadiness row : chunks.domain) rows.add(row.canonical());
            java.util.Collections.sort(rows);
            for (String row : rows)
                out.append("[chunk] ")
                        .append(chunks.dimension)
                        .append(' ')
                        .append(row)
                        .append('\n');
        }
        for (RedstoneTorchBurnoutSnapshot torch : capsule.redstoneTorchBurnout) {
            for (String line : torch.canonicalLines())
                out.append("[torch] ").append(line).append('\n');
        }
    }
}
//?}
