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
*///?} else if (>=1.21.9 && <=26.3) {
    @Test
    void modernSpawnPointPreservesBothAngles() {

        //?}
        //? if (>=1.21.9 && <=1.21.11) {
        /*var spawn =
                net.minecraft.world.level.storage.LevelData.RespawnData.of(
                        net.minecraft.world.level.Level.OVERWORLD,
                        new net.minecraft.core.BlockPos(-330, 120, 122),
                        87.5f,
                        -22.25f);
*///?} else if (>=26.1 && <=26.3) {
        var spawn =
                net.minecraft.world.level.storage.LevelData.RespawnData.of(
                        net.minecraft.world.level.Level.OVERWORLD,
                        new net.minecraft.core.BlockPos(-330, 120, 122),
                        87.5f,
                        -22.25f);
        //?}

        //? if (>=1.21.9 && <=1.21.11) {
        /*var respawn = new net.minecraft.server.level.ServerPlayer.RespawnConfig(spawn, true);
*///?} else if (>=26.1 && <=26.3) {
        var respawn = new net.minecraft.server.level.ServerPlayer.RespawnConfig(spawn, true);
        //?}

        //? if (>=1.21.9 && <=1.21.11) {
        /*var encoded =
                net.minecraft.server.level.ServerPlayer.RespawnConfig.CODEC
                        .encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, respawn)
                        .getOrThrow();
*///?} else if (>=26.1 && <=26.3) {
        var encoded =
                net.minecraft.server.level.ServerPlayer.RespawnConfig.CODEC
                        .encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, respawn)
                        .getOrThrow();
        //?}

        //? if (>=1.21.9 && <=1.21.11) {
        /*var decoded =
                net.minecraft.server.level.ServerPlayer.RespawnConfig.CODEC
                        .parse(net.minecraft.nbt.NbtOps.INSTANCE, encoded)
                        .getOrThrow();
*///?} else if (>=26.1 && <=26.3) {
        var decoded =
                net.minecraft.server.level.ServerPlayer.RespawnConfig.CODEC
                        .parse(net.minecraft.nbt.NbtOps.INSTANCE, encoded)
                        .getOrThrow();
        //?}

        //? if (>=1.21.9 && <=26.3) {
        assertEquals(respawn, decoded);
        //?}

        //? if (>=1.21.9 && <=26.3) {
        assertEquals(-22.25f, decoded.respawnData().pitch());
    }

    //?}

    //? if (>=1.21.9 && <=26.3) {
    @Test
    void borderIsPersistentStateAndRidingHasAnEventFreeRestorationPath() throws Exception {

        //?}
        //? if (>=1.21.9 && <=1.21.11) {
        /*assertTrue(
                net.minecraft.world.level.saveddata.SavedData.class.isAssignableFrom(
                        net.minecraft.world.level.border.WorldBorder.class));
*///?} else if (>=26.1 && <=26.3) {
        assertTrue(
                net.minecraft.world.level.saveddata.SavedData.class.isAssignableFrom(
                        net.minecraft.world.level.border.WorldBorder.class));
        //?}

        //? if (>=1.21.9 && <=1.21.11) {
        /*var entity = read("net/minecraft/world/entity/Entity");
*///?} else if (>=26.1 && <=26.3) {
        var entity = read("net/minecraft/world/entity/Entity");
        //?}

        //? if (>=1.21.9 && <=1.21.11) {
        /*assertTrue(
                entity.methods.stream()
                        .anyMatch(m ->
                                m.name.equals("startRiding")
                                        && m.desc.equals("(Lnet/minecraft/world/entity/Entity;ZZ)Z")));
*///?} else if (>=26.1 && <=26.3) {
        assertTrue(
                entity.methods.stream()
                        .anyMatch(m ->
                                m.name.equals("startRiding")
                                        && m.desc.equals(
                                                "(Lnet/minecraft/world/entity/Entity;ZZ)Z")));
        //?}

        //? if (>=1.21.9 && <=1.21.11) {
        /*var projectile = read("net/minecraft/world/entity/projectile/Projectile");
*///?} else if (>=26.1 && <=26.3) {
        var projectile = read("net/minecraft/world/entity/projectile/Projectile");
        //?}

        //? if (>=1.21.9 && <=1.21.11) {
        /*assertTrue(
                projectile.fields.stream()
                        .anyMatch(f -> f.name.equals("leftOwnerChecked") && f.desc.equals("Z")));
*///?} else if (>=26.1 && <=26.3) {
        assertTrue(
                projectile.fields.stream()
                        .anyMatch(f -> f.name.equals("leftOwnerChecked") && f.desc.equals("Z")));
        //?}
        //? if (>=1.21.9 && <=26.3) {

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
*///?} else if (>=1.21.5 && <=1.21.11) {
        /*var type = net.minecraft.server.level.TicketType.ENDER_PEARL;
*///?} else if (>=26.1 && <=26.3) {
        var type = net.minecraft.server.level.TicketType.ENDER_PEARL;
        //?}

        //? if 1.21.4 {
        /*net.minecraft.server.level.TicketType<net.minecraft.world.level.ChunkPos> type =
                net.minecraft.server.level.TicketType.ENDER_PEARL;
*///?} else if (>=1.21.5 && <=1.21.11) {
        /*assertEquals(40, type.timeout());
*///?} else if (>=26.1 && <=26.3) {
        assertEquals(40, type.timeout());
        //?}

        //? if 1.21.4 {
        /*assertEquals("ender_pearl", type.toString());
*///?} else if (>=1.21.5 && <=1.21.11) {
        /*assertTrue(type.doesLoad());
*///?} else if (>=26.1 && <=26.3) {
        assertTrue(type.doesLoad());
        //?}

        //? if (>=1.21.5 && <=1.21.11) {
        /*assertTrue(type.doesSimulate());
*///?} else if (>=26.1 && <=26.3) {
        assertTrue(type.doesSimulate());
        //?}

        //? if (>=1.21.5 && <=1.21.8) {
        /*assertTrue(net.minecraft.server.level.TicketType.PORTAL.persist());
*///?} else if (>=1.21.9 && <=1.21.11) {
        /*assertTrue(net.minecraft.server.level.TicketType.PORTAL.persist());
*///?} else if (>=26.1 && <=26.3) {
        assertTrue(net.minecraft.server.level.TicketType.PORTAL.persist());
        //?}

        //? if (>=1.21.5 && <=1.21.11) {
        /*assertEquals(
                "minecraft:ender_pearl",
                net.minecraft.core.registries.BuiltInRegistries.TICKET_TYPE.getKey(type).toString());
*///?} else if (>=26.1 && <=26.3) {
        assertEquals(
                "minecraft:ender_pearl",
                net.minecraft.core.registries.BuiltInRegistries.TICKET_TYPE
                        .getKey(type)
                        .toString());
        //?}

        //? if (>=1.21.5 && <=1.21.11) {
        /*var ticket = read("net/minecraft/server/level/Ticket");
*///?} else if (>=26.1 && <=26.3) {
        var ticket = read("net/minecraft/server/level/Ticket");
        //?}

        //? if (>=1.21.5 && <=26.3) {
        assertTrue(ticket.fields.stream().anyMatch(f -> f.name.equals("ticksLeft")));
        //?}

        //? if (>=1.21.5 && <=26.3) {
        assertFalse(
                ticket.fields.stream()
                        .anyMatch(f -> f.name.equals("key") || f.name.equals("createdTick")));
        //?}

        //? if (>=1.21.5 && <=26.3) {
        var recovery = com.atsukimc.anticrash.recovery.ChunkRecoveryManager.RECOVERY_TICKET;
        //?}

        //? if (>=1.21.5 && <=1.21.8) || (>=26.1 && <=26.3) {
        assertFalse(recovery.persist());
        //?}
        //? if (>=1.21.9 && <=1.21.11) {
        /*assertFalse(recovery.persist());
*///?} else if (>=1.21.5 && <=1.21.8) {
        /*assertNotEquals(net.minecraft.server.level.TicketType.START, recovery);
*///?}
        //? if (>=1.21.9 && <=1.21.11) {
        /*assertNotEquals(net.minecraft.server.level.TicketType.DRAGON, recovery);
*///?} else if (>=26.1 && <=26.3) {
        assertNotEquals(net.minecraft.server.level.TicketType.DRAGON, recovery);
        //?}
        //? if (>=1.21.4 && <=26.3) {

    }

    //?}

    //? if (>=1.21.4 && <=26.3) {
    @Test
    void newPickPacketsAreRoutedThroughTheServerThread() throws Exception {

        //?}
        //? if (>=1.21.4 && <=1.21.11) {
        /*var handler = read("net/minecraft/server/network/ServerGamePacketListenerImpl");
*///?} else if (>=26.1 && <=26.3) {
        var handler = read("net/minecraft/server/network/ServerGamePacketListenerImpl");
        //?}

        //? if (>=1.21.4 && <=1.21.11) {
        /*for (String name : new String[] {"handlePickItemFromBlock", "handlePickItemFromEntity"})
            assertTrue(
                    invokes(
                            handler,
                            name,
                            "net/minecraft/network/protocol/PacketUtils",
                            "ensureRunningOnSameThread"));
*///?} else if (>=26.1 && <=26.3) {
        for (String name : new String[] {"handlePickItemFromBlock", "handlePickItemFromEntity"})
            assertTrue(
                    invokes(
                            handler,
                            name,
                            "net/minecraft/network/protocol/PacketUtils",
                            "ensureRunningOnSameThread"));
        //?}

        //? if (>=1.21.4 && <=26.3) {
        assertFalse(handler.methods.stream().anyMatch(m -> m.name.equals("handlePickItem")));
    }

    //?}

    //? if (>=1.21.4 && <=26.3) {
    @Test
    void furnacePersistsAllFourRenamedTimers() throws Exception {

        //?}
        //? if (>=1.21.4 && <=1.21.11) {
        /*var furnace = read("net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity");
*///?} else if (>=26.1 && <=26.3) {
        var furnace = read("net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity");
        //?}

        //? if (>=1.21.4 && <=1.21.11) {
        /*for (String field :
                new String[] {
                    "litTimeRemaining", "litTotalTime", "cookingTimer", "cookingTotalTime"
                })
*///?} else if (>=26.1 && <=26.3) {
        for (String field :
                new String[] {
                    "litTimeRemaining", "litTotalTime", "cookingTimer", "cookingTotalTime"
                })
        //?}
        //? if (>=1.21.4 && <=26.3) {
        {
            assertTrue(furnace.fields.stream().anyMatch(f -> f.name.equals(field)));
            //?}

            //? if (>=1.21.4 && <=1.21.5) {
            /*for (String method : new String[] {"loadAdditional", "saveAdditional"})

*///?} else if (>=1.21.6 && <=1.21.11) {
            /*for (String method : new String[] {"loadAdditional", "saveAdditional"})

*///?} else if (>=26.1 && <=26.3) {
            for (String method : new String[] {"loadAdditional", "saveAdditional"})

                //?}
                //? if (>=1.21.4 && <=26.3) {
                assertTrue(
                        furnace.methods.stream()
                                .filter(m -> m.name.equals(method))
                                .anyMatch(m -> {
                                    for (var insn : m.instructions)
                                        if (insn instanceof FieldInsnNode f
                                                && f.owner.equals(furnace.name)
                                                && f.name.equals(field)) return true;
                                    //?}

                                    //? if (>=1.21.4 && <=26.3) {
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

        //? if (>=1.19 && <=1.21.11) {
        /*var sensor = read("net/minecraft/world/level/block/entity/SculkSensorBlockEntity");
*///?} else if (>=26.1 && <=26.3) {
        var sensor = read("net/minecraft/world/level/block/entity/SculkSensorBlockEntity");
        //?}

        //? if (>=1.19 && <=1.21.4) {
        /*assertTrue(invokes(sensor, "saveAdditional", "com/mojang/serialization/Codec", "encodeStart"));
*///?} else if 1.21.5 {
        /*assertTrue(invokes(sensor, "saveAdditional", "net/minecraft/nbt/CompoundTag", "store"));
*///?} else if (>=1.21.6 && <=1.21.11) {
        /*assertTrue(invokes(sensor, "saveAdditional", "net/minecraft/world/level/storage/ValueOutput", "store"));
*///?} else if (>=26.1 && <=26.3) {
        assertTrue(
                invokes(
                        sensor,
                        "saveAdditional",
                        "net/minecraft/world/level/storage/ValueOutput",
                        "store"));
        //?}

        //? if (>=1.19 && <=1.20.4) {
        /*assertTrue(invokes(sensor, "load", "com/mojang/serialization/Codec", "parse"));
*///?} else if (>=1.20.5 && <=1.21.4) {
        /*assertTrue(invokes(sensor, "loadAdditional", "com/mojang/serialization/Codec", "parse"));
*///?} else if 1.21.5 {
        /*assertTrue(invokes(sensor, "loadAdditional", "net/minecraft/nbt/CompoundTag", "read"));
*///?} else if (>=1.21.6 && <=1.21.11) {
        /*assertTrue(invokes(sensor, "loadAdditional", "net/minecraft/world/level/storage/ValueInput", "read"));
*///?} else if (>=26.1 && <=26.3) {
        assertTrue(
                invokes(
                        sensor,
                        "loadAdditional",
                        "net/minecraft/world/level/storage/ValueInput",
                        "read"));
        //?}

        //? if (>=1.19 && <=1.19.4) {
        /*var listener = read("net/minecraft/world/level/gameevent/vibrations/VibrationListener");
*///?} else if (>=1.20 && <=1.21.11) {
        /*var listener = read("net/minecraft/world/level/gameevent/vibrations/VibrationSystem$Data");
*///?} else if (>=26.1 && <=26.3) {
        var listener = read("net/minecraft/world/level/gameevent/vibrations/VibrationSystem$Data");
        //?}

        //? if (>=1.19 && <=1.19.2) {
        /*assertTrue(
                listener.fields.stream()
                        .anyMatch(f -> f.name.equals("receivingDistance") && f.desc.equals("F")));
*///?} else if (>=1.19.3 && <=1.19.4) {
        /*assertTrue(
                listener.fields.stream()
                        .anyMatch(f ->
                                f.name.equals("selectionStrategy")
                                        && f.desc.endsWith("/VibrationSelector;")));
*///?} else if (>=1.20 && <=1.21.11) {
        /*assertTrue(
                listener.fields.stream()
                        .anyMatch(f ->
                                f.name.equals("selectionStrategy")
                                        && f.desc.endsWith("/VibrationSelector;")));
*///?} else if (>=26.1 && <=26.3) {
        assertTrue(
                listener.fields.stream()
                        .anyMatch(f ->
                                f.name.equals("selectionStrategy")
                                        && f.desc.endsWith("/VibrationSelector;")));
        //?}

        //? if (>=1.19 && <=1.19.2) {
        /*assertTrue(listener.fields.stream().anyMatch(f -> f.name.equals("receivingEvent")));
*///?} else if (>=1.19.3 && <=26.3) {
        assertTrue(listener.fields.stream().anyMatch(f -> f.name.equals("currentVibration")));
        //?}

    }

    @Test
    void predictionAckIsCumulativeAndUsedByVanilla() throws Exception {

        //? if (>=1.19 && <=1.21.11) {
        /*var handler = read("net/minecraft/server/network/ServerGamePacketListenerImpl");
*///?} else if (>=26.1 && <=26.3) {
        var handler = read("net/minecraft/server/network/ServerGamePacketListenerImpl");
        //?}

        //? if (>=1.19 && <=1.21.11) {
        /*for (String method :
                new String[] {"handlePlayerAction", "handleUseItemOn", "handleUseItem"})
*///?} else if (>=26.1 && <=26.3) {
        for (String method :
                new String[] {"handlePlayerAction", "handleUseItemOn", "handleUseItem"})
        //?}
        {

            //? if (>=1.19 && <=1.21.11) {
            /*assertTrue(invokes(handler, method, handler.name, "ackBlockChangesUpTo"), method);
*///?} else if (>=26.1 && <=26.3) {
            assertTrue(invokes(handler, method, handler.name, "ackBlockChangesUpTo"), method);
            //?}

        }

        //? if (>=1.19 && <=1.21.11) {
        /*assertTrue(invokes(handler, "ackBlockChangesUpTo", "java/lang/Math", "max"));
*///?} else if (>=26.1 && <=26.3) {
        assertTrue(invokes(handler, "ackBlockChangesUpTo", "java/lang/Math", "max"));
        //?}

    }

    @Test
    void recipeMatchCacheIsSemanticStateNotJustAConstructorValue() throws Exception {

        //? if (>=1.19 && <=1.21.1) {
        /*var getter = read("net/minecraft/world/item/crafting/RecipeManager$1");
*///?} else if (>=1.21.2 && <=1.21.11) {
        /*var getter = read("net/minecraft/world/item/crafting/RecipeManager$1");
*///?} else if (>=26.1 && <=26.3) {
        var getter = read("net/minecraft/world/item/crafting/RecipeManager$1");
        //?}

        //? if (>=1.19 && <=1.21.1) {
        /*assertTrue(
                getter.fields.stream()
                        .anyMatch(f -> f.name.equals("lastRecipe") && f.desc.endsWith("/ResourceLocation;")));
*///?} else if (>=1.21.2 && <=1.21.11) {
        /*assertTrue(
                getter.fields.stream()
                        .anyMatch(f -> f.name.equals("lastRecipe") && f.desc.endsWith("/ResourceKey;")));
*///?} else if (>=26.1 && <=26.3) {
        assertTrue(
                getter.fields.stream()
                        .anyMatch(f ->
                                f.name.equals("lastRecipe") && f.desc.endsWith("/ResourceKey;")));
        //?}

        //? if (>=1.19 && <=1.21.1) {
        /*assertTrue(
                invokes(
                        getter,
                        "getRecipeFor",
                        "net/minecraft/world/item/crafting/RecipeManager",
                        "getRecipeFor"));
*///?} else if (>=1.21.2 && <=1.21.11) {
        /*assertTrue(
                invokes(
                        getter,
                        "getRecipeFor",
                        "net/minecraft/world/item/crafting/RecipeManager",
                        "getRecipeFor"));
*///?} else if (>=26.1 && <=26.3) {
        assertTrue(
                invokes(
                        getter,
                        "getRecipeFor",
                        "net/minecraft/world/item/crafting/RecipeManager",
                        "getRecipeFor"));
        //?}

    }

    //? if (>=1.21 && <=26.3) {
    @Test
    void tickingGetterIsNotAnExactLoadedChunkSet() throws Exception {

        //?}
        //? if (>=1.21 && <=1.21.11) {
        /*var holder = read("net/minecraft/server/level/ChunkHolder");
*///?} else if (>=26.1 && <=26.3) {
        var holder = read("net/minecraft/server/level/ChunkHolder");
        //?}

        //? if (>=1.21 && <=1.21.11) {
        /*assertTrue(invokes(holder, "getTickingChunk", holder.name, "getTickingChunkFuture"));
*///?} else if (>=26.1 && <=26.3) {
        assertTrue(invokes(holder, "getTickingChunk", holder.name, "getTickingChunkFuture"));
        //?}

        //? if (>=1.21 && <=26.3) {
        var adapter = read("com/atsukimc/anticrash/runtime/VersionChunkView");
        //?}

        //? if (>=1.21 && <=1.21.11) {
        /*assertTrue(invokes(adapter, "worldChunk", holder.name, "getLatestChunk"));
*///?} else if (>=26.1 && <=26.3) {
        assertTrue(invokes(adapter, "worldChunk", holder.name, "getLatestChunk"));
        //?}
        //? if (>=1.21 && <=26.3) {

    }

    //?}

    //? if (>=1.21 && <=26.3) {
    @Test
    void newPortalAndTntRuntimeFieldsExistAndAreNotVanillaNbtAuthority() throws Exception {

        //?}
        //? if (>=1.21 && <=1.21.11) {
        /*var portal = read("net/minecraft/world/entity/PortalProcessor");
*///?} else if (>=26.1 && <=26.3) {
        var portal = read("net/minecraft/world/entity/PortalProcessor");
        //?}

        //? if (>=1.21 && <=1.21.11) {
        /*for (String field : new String[] {"portal", "entryPosition", "portalTime", "insidePortalThisTick"})

*///?} else if (>=26.1 && <=26.3) {
        for (String field :
                new String[] {"portal", "entryPosition", "portalTime", "insidePortalThisTick"})

            //?}
            //? if (>=1.21 && <=26.3) {
            assertTrue(portal.fields.stream().anyMatch(f -> f.name.equals(field)), field);
        //?}

        //? if (>=1.21 && <=1.21.11) {
        /*var tnt = read("net/minecraft/world/entity/item/PrimedTnt");
*///?} else if (>=26.1 && <=26.3) {
        var tnt = read("net/minecraft/world/entity/item/PrimedTnt");
        //?}

        //? if (>=1.21 && <=1.21.11) {
        /*assertTrue(
                tnt.fields.stream()
                        .anyMatch(f -> f.name.equals("usedPortal") && f.desc.equals("Z")));
*///?} else if (>=26.1 && <=26.3) {
        assertTrue(
                tnt.fields.stream()
                        .anyMatch(f -> f.name.equals("usedPortal") && f.desc.equals("Z")));
        //?}

        //? if (>=1.21 && <=26.3) {
        for (var method : tnt.methods) {

            //?}
            //? if (>=1.21 && <=1.21.5) {
            /*if (!method.name.equals("addAdditionalSaveData"))
*///?} else if (>=1.21.6 && <=1.21.11) {
            /*if (!method.name.equals("addAdditionalSaveData"))
*///?} else if (>=26.1 && <=26.3) {
            if (!method.name.equals("addAdditionalSaveData"))
                //?}
                //? if (>=1.21 && <=26.3) {
                continue;
            //?}

            //? if (>=1.21 && <=26.3) {
            for (var insn : method.instructions)
                if (insn instanceof FieldInsnNode f)

                    //?}
                    //? if (>=1.21 && <=1.21.11) {
                    /*assertNotEquals(
                            "usedPortal",
                            f.name,
                            "If Vanilla gains this state, remove the duplicate extension after"
                                    + " reviewing it");
*///?} else if (>=26.1 && <=26.3) {
                    assertNotEquals(
                            "usedPortal",
                            f.name,
                            "If Vanilla gains this state, remove the duplicate extension after"
                                    + " reviewing it");
            //?}
            //? if (>=1.21 && <=26.3) {

        }
    }
    //?}

}
//?}
