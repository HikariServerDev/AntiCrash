package com.atsukimc.anticrash.audit;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

import java.io.InputStream;

/** Offline evidence for version-dependent semantic assumptions, without starting a server. */
class VersionAdapterBytecodeTest {
    @Test
    void ticketsUseCountdownAndSeparateLoadingAndSimulation() throws Exception {
        var type = net.minecraft.server.world.ChunkTicketType.ENDER_PEARL;
        assertEquals(40, type.expiryTicks());
        assertTrue(type.isForLoading());
        assertTrue(type.isForSimulation());
        assertTrue(net.minecraft.server.world.ChunkTicketType.PORTAL.persist());
        assertEquals(
                "minecraft:ender_pearl",
                net.minecraft.registry.Registries.TICKET_TYPE.getId(type).toString());
        var ticket = read("net/minecraft/server/world/ChunkTicket");
        assertTrue(ticket.fields.stream().anyMatch(f -> f.name.equals("ticksLeft")));
        assertFalse(
                ticket.fields.stream()
                        .anyMatch(f -> f.name.equals("argument") || f.name.equals("tickCreated")));
        var recovery = com.atsukimc.anticrash.recovery.ChunkRecoveryManager.RECOVERY_TICKET;
        assertFalse(recovery.persist());
        assertNotEquals(net.minecraft.server.world.ChunkTicketType.START, recovery);
    }

    @Test
    void newPickPacketsAreRoutedThroughTheServerThread() throws Exception {
        var handler = read("net/minecraft/server/network/ServerPlayNetworkHandler");
        for (String name : new String[] {"onPickItemFromBlock", "onPickItemFromEntity"})
            assertTrue(
                    invokes(
                            handler,
                            name,
                            "net/minecraft/network/NetworkThreadUtils",
                            "forceMainThread"));
        assertFalse(handler.methods.stream().anyMatch(m -> m.name.equals("onPickFromInventory")));
    }

    @Test
    void furnacePersistsAllFourRenamedTimers() throws Exception {
        var furnace = read("net/minecraft/block/entity/AbstractFurnaceBlockEntity");
        for (String field :
                new String[] {
                    "litTimeRemaining", "litTotalTime", "cookingTimeSpent", "cookingTotalTime"
                }) {
            assertTrue(furnace.fields.stream().anyMatch(f -> f.name.equals(field)));
            for (String method : new String[] {"readNbt", "writeNbt"})
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
        var sensor = read("net/minecraft/block/entity/SculkSensorBlockEntity");
        assertTrue(invokes(sensor, "writeNbt", "net/minecraft/nbt/NbtCompound", "put"));
        assertTrue(invokes(sensor, "readNbt", "net/minecraft/nbt/NbtCompound", "get"));
        var listener = read("net/minecraft/world/event/Vibrations$ListenerData");
        assertTrue(
                listener.fields.stream()
                        .anyMatch(f ->
                                f.name.equals("vibrationSelector")
                                        && f.desc.endsWith("/VibrationSelector;")));
        assertTrue(listener.fields.stream().anyMatch(f -> f.name.equals("vibration")));
    }

    @Test
    void predictionAckIsCumulativeAndUsedByVanilla() throws Exception {
        var handler = read("net/minecraft/server/network/ServerPlayNetworkHandler");
        for (String method :
                new String[] {"onPlayerAction", "onPlayerInteractBlock", "onPlayerInteractItem"}) {
            assertTrue(invokes(handler, method, handler.name, "updateSequence"), method);
        }
        assertTrue(invokes(handler, "updateSequence", "java/lang/Math", "max"));
    }

    @Test
    void recipeMatchCacheIsSemanticStateNotJustAConstructorValue() throws Exception {
        var getter = read("net/minecraft/recipe/ServerRecipeManager$1");
        assertTrue(
                getter.fields.stream()
                        .anyMatch(f -> f.name.equals("id") && f.desc.endsWith("/RegistryKey;")));
        assertTrue(
                invokes(
                        getter,
                        "getFirstMatch",
                        "net/minecraft/recipe/ServerRecipeManager",
                        "getFirstMatch"));
    }

    @Test
    void tickingGetterIsNotAnExactLoadedChunkSet() throws Exception {
        var holder = read("net/minecraft/server/world/ChunkHolder");
        assertTrue(invokes(holder, "getWorldChunk", holder.name, "getTickingFuture"));
        var adapter = read("com/atsukimc/anticrash/runtime/VersionChunkView");
        assertTrue(invokes(adapter, "worldChunk", holder.name, "getLatest"));
    }

    @Test
    void newPortalAndTntRuntimeFieldsExistAndAreNotVanillaNbtAuthority() throws Exception {
        var portal = read("net/minecraft/world/dimension/PortalManager");
        for (String field : new String[] {"portal", "pos", "ticksInPortal", "inPortal"})
            assertTrue(portal.fields.stream().anyMatch(f -> f.name.equals(field)), field);
        var tnt = read("net/minecraft/entity/TntEntity");
        assertTrue(
                tnt.fields.stream()
                        .anyMatch(f -> f.name.equals("teleported") && f.desc.equals("Z")));
        for (var method : tnt.methods) {
            if (!method.name.equals("writeCustomDataToNbt")) continue;
            for (var insn : method.instructions)
                if (insn instanceof FieldInsnNode f)
                    assertNotEquals(
                            "teleported",
                            f.name,
                            "If Vanilla gains this state, remove the duplicate extension after"
                                    + " reviewing it");
        }
    }
}
