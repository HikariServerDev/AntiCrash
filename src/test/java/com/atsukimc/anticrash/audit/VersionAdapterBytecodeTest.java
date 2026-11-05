//? if (>=1.19 && <=26.3) {
package com.atsukimc.anticrash.audit;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

import java.io.InputStream;

/** Offline evidence for version-dependent semantic assumptions, without starting a server. */
class VersionAdapterBytecodeTest {

    //? if 1.21.4 {
    /*@Test
    void enderPearlTicketHasChunkArgumentAndFortyTickExpiry()
*///?}
    //? if (>=1.21.9 && <=26.3) {
    @Test
    void modernSpawnPointPreservesBothAngles() {

        var spawn =
                net.minecraft.world.level.storage.LevelData.RespawnData.of(
                        net.minecraft.world.level.Level.OVERWORLD,
                        new net.minecraft.core.BlockPos(-330, 120, 122),
                        87.5f,
                        -22.25f);

        var respawn = new net.minecraft.server.level.ServerPlayer.RespawnConfig(spawn, true);

        var encoded =
                net.minecraft.server.level.ServerPlayer.RespawnConfig.CODEC
                        .encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, respawn)
                        .getOrThrow();

        var decoded =
                net.minecraft.server.level.ServerPlayer.RespawnConfig.CODEC
                        .parse(net.minecraft.nbt.NbtOps.INSTANCE, encoded)
                        .getOrThrow();

        assertEquals(respawn, decoded);

        assertEquals(-22.25f, decoded.respawnData().pitch());
    }

    @Test
    void borderIsPersistentStateAndRidingHasAnEventFreeRestorationPath() throws Exception {

        assertTrue(
                net.minecraft.world.level.saveddata.SavedData.class.isAssignableFrom(
                        net.minecraft.world.level.border.WorldBorder.class));

        var entity = read("net/minecraft/world/entity/Entity");

        assertTrue(
                entity.methods.stream()
                        .anyMatch(m ->
                                m.name.equals("startRiding")
                                        && m.desc.equals(
                                                "(Lnet/minecraft/world/entity/Entity;ZZ)Z")));

        var projectile = read("net/minecraft/world/entity/projectile/Projectile");

        assertTrue(
                projectile.fields.stream()
                        .anyMatch(f -> f.name.equals("leftOwnerChecked") && f.desc.equals("Z")));
    }

    //?}

    //? if (>=1.21.5 && <=26.3) {
    @Test
    void ticketsUseCountdownAndSeparateLoadingAndSimulation() throws Exception
                //?}
                //? if (>=1.21.4 && <=26.3) {
            {

        //?}
        //? if 1.21.4 {
        /*assertEquals(40, net.minecraft.server.level.TicketType.ENDER_PEARL.timeout());
*///?}
        //? if (>=1.21.5 && <=26.3) {
        var type = net.minecraft.server.level.TicketType.ENDER_PEARL;
        //?}

        //? if 1.21.4 {
        /*net.minecraft.server.level.TicketType<net.minecraft.world.level.ChunkPos> type =
                net.minecraft.server.level.TicketType.ENDER_PEARL;
*///?}
        //? if (>=1.21.5 && <=26.3) {
        assertEquals(40, type.timeout());
        //?}

        //? if 1.21.4 {
        /*assertEquals("ender_pearl", type.toString());
*///?}
        //? if (>=1.21.5 && <=26.3) {
        assertTrue(type.doesLoad());

        assertTrue(type.doesSimulate());

        assertTrue(net.minecraft.server.level.TicketType.PORTAL.persist());

        assertEquals(
                "minecraft:ender_pearl",
                net.minecraft.core.registries.BuiltInRegistries.TICKET_TYPE
                        .getKey(type)
                        .toString());

        var ticket = read("net/minecraft/server/level/Ticket");

        assertTrue(ticket.fields.stream().anyMatch(f -> f.name.equals("ticksLeft")));

        assertFalse(
                ticket.fields.stream()
                        .anyMatch(f -> f.name.equals("key") || f.name.equals("createdTick")));

        var recovery = com.atsukimc.anticrash.recovery.ChunkRecoveryManager.RECOVERY_TICKET;

        assertFalse(recovery.persist());
        //?}

        //? if (>=1.21.5 && <=1.21.8) {
        /*assertNotEquals(net.minecraft.server.level.TicketType.START, recovery);
*///?}
        //? if (>=1.21.9 && <=26.3) {
        assertNotEquals(net.minecraft.server.level.TicketType.DRAGON, recovery);
        //?}
        //? if (>=1.21.4 && <=26.3) {

    }

    @Test
    void newPickPacketsAreRoutedThroughTheServerThread() throws Exception {

        var handler = read("net/minecraft/server/network/ServerGamePacketListenerImpl");

        for (String name : new String[] {"handlePickItemFromBlock", "handlePickItemFromEntity"})
            assertTrue(
                    invokes(
                            handler,
                            name,
                            "net/minecraft/network/protocol/PacketUtils",
                            "ensureRunningOnSameThread"));

        assertFalse(handler.methods.stream().anyMatch(m -> m.name.equals("handlePickItem")));
    }

    @Test
    void furnacePersistsAllFourRenamedTimers() throws Exception {

        var furnace = read("net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity");

        for (String field :
                new String[] {
                    "litTimeRemaining", "litTotalTime", "cookingTimer", "cookingTotalTime"
                }) {
            assertTrue(furnace.fields.stream().anyMatch(f -> f.name.equals(field)));

            for (String method : new String[] {"loadAdditional", "saveAdditional"})
                assertTrue(
                        furnace.methods.stream()
                                .filter(m -> m.name.equals(method))
                                .anyMatch(m -> {
                                    for (var insn : m.instructions)
                                        if (insn instanceof FieldInsnNode f
                                                && f.owner.equals(furnace.name)
                                                && f.name.equals(field)) return true;

                                    return false;
                                }),
                        field + ": " + method);
        }
    }

    //?}

    private ClassNode read(String name) throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(in, name);
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, 0);
            return node;
        }
    }

    private boolean invokes(ClassNode node, String method, String owner, String called) {
        return node.methods.stream()
                .filter(m -> m.name.equals(method))
                .anyMatch(m -> {
                    for (var insn : m.instructions) {
                        if (insn instanceof MethodInsnNode i
                                && i.owner.equals(owner)
                                && i.name.equals(called)) return true;
                    }
                    return false;
                });
    }

    @Test
    void vanillaOwnsSensorSerializationSince119() throws Exception {

        var sensor = read("net/minecraft/world/level/block/entity/SculkSensorBlockEntity");

        //? if (>=1.19 && <=1.21.4) {
        /*assertTrue(invokes(sensor, "saveAdditional", "com/mojang/serialization/Codec", "encodeStart"));
*///?}
        //? if 1.21.5 {
        /*assertTrue(invokes(sensor, "saveAdditional", "net/minecraft/nbt/CompoundTag", "store"));
*///?}
        //? if (>=1.21.6 && <=26.3) {
        assertTrue(
                invokes(
                        sensor,
                        "saveAdditional",
                        "net/minecraft/world/level/storage/ValueOutput",
                        "store"));
        //?}

        //? if (>=1.19 && <=1.20.4) {
        /*assertTrue(invokes(sensor, "load", "com/mojang/serialization/Codec", "parse"));
*///?}
        //? if (>=1.20.5 && <=1.21.4) {
        /*assertTrue(invokes(sensor, "loadAdditional", "com/mojang/serialization/Codec", "parse"));
*///?}
        //? if 1.21.5 {
        /*assertTrue(invokes(sensor, "loadAdditional", "net/minecraft/nbt/CompoundTag", "read"));
*///?}
        //? if (>=1.21.6 && <=26.3) {
        assertTrue(
                invokes(
                        sensor,
                        "loadAdditional",
                        "net/minecraft/world/level/storage/ValueInput",
                        "read"));
        //?}

        //? if (>=1.19 && <=1.19.4) {
        /*var listener = read("net/minecraft/world/level/gameevent/vibrations/VibrationListener");
*///?}
        //? if (>=1.20 && <=26.3) {
        var listener = read("net/minecraft/world/level/gameevent/vibrations/VibrationSystem$Data");
        //?}

        //? if (>=1.19 && <=1.19.2) {
        /*assertTrue(
                listener.fields.stream()
                        .anyMatch(f -> f.name.equals("receivingDistance") && f.desc.equals("F")));
*///?}
        //? if (>=1.19.3 && <=26.3) {
        assertTrue(
                listener.fields.stream()
                        .anyMatch(f ->
                                f.name.equals("selectionStrategy")
                                        && f.desc.endsWith("/VibrationSelector;")));
        //?}

        //? if (>=1.19 && <=1.19.2) {
        /*assertTrue(listener.fields.stream().anyMatch(f -> f.name.equals("receivingEvent")));
*///?}
        //? if (>=1.19.3 && <=26.3) {
        assertTrue(listener.fields.stream().anyMatch(f -> f.name.equals("currentVibration")));
        //?}

    }

    @Test
    void predictionAckIsCumulativeAndUsedByVanilla() throws Exception {

        var handler = read("net/minecraft/server/network/ServerGamePacketListenerImpl");

        for (String method :
                new String[] {"handlePlayerAction", "handleUseItemOn", "handleUseItem"}) {

            assertTrue(invokes(handler, method, handler.name, "ackBlockChangesUpTo"), method);
        }

        assertTrue(invokes(handler, "ackBlockChangesUpTo", "java/lang/Math", "max"));
    }

    @Test
    void recipeMatchCacheIsSemanticStateNotJustAConstructorValue() throws Exception {

        var getter = read("net/minecraft/world/item/crafting/RecipeManager$1");

        //? if (>=1.19 && <=1.21.1) {
        /*assertTrue(
                getter.fields.stream()
                        .anyMatch(f -> f.name.equals("lastRecipe") && f.desc.endsWith("/ResourceLocation;")));
*///?}
        //? if (>=1.21.2 && <=26.3) {
        assertTrue(
                getter.fields.stream()
                        .anyMatch(f ->
                                f.name.equals("lastRecipe") && f.desc.endsWith("/ResourceKey;")));
        //?}

        assertTrue(
                invokes(
                        getter,
                        "getRecipeFor",
                        "net/minecraft/world/item/crafting/RecipeManager",
                        "getRecipeFor"));
    }

    //? if (>=1.21 && <=26.3) {
    @Test
    void tickingGetterIsNotAnExactLoadedChunkSet() throws Exception {

        var holder = read("net/minecraft/server/level/ChunkHolder");

        assertTrue(invokes(holder, "getTickingChunk", holder.name, "getTickingChunkFuture"));

        var adapter = read("com/atsukimc/anticrash/runtime/VersionChunkView");

        assertTrue(invokes(adapter, "worldChunk", holder.name, "getLatestChunk"));
    }

    @Test
    void newPortalAndTntRuntimeFieldsExistAndAreNotVanillaNbtAuthority() throws Exception {

        var portal = read("net/minecraft/world/entity/PortalProcessor");

        for (String field :
                new String[] {"portal", "entryPosition", "portalTime", "insidePortalThisTick"})
            assertTrue(portal.fields.stream().anyMatch(f -> f.name.equals(field)), field);

        var tnt = read("net/minecraft/world/entity/item/PrimedTnt");

        assertTrue(
                tnt.fields.stream()
                        .anyMatch(f -> f.name.equals("usedPortal") && f.desc.equals("Z")));

        for (var method : tnt.methods) {

            if (!method.name.equals("addAdditionalSaveData")) continue;

            for (var insn : method.instructions)
                if (insn instanceof FieldInsnNode f)
                    assertNotEquals(
                            "usedPortal",
                            f.name,
                            "If Vanilla gains this state, remove the duplicate extension after"
                                    + " reviewing it");
        }
    }
    //?}

}
//?}
