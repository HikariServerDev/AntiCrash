package com.atsukimc.anticrash.audit;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import java.io.InputStream;
import static org.junit.jupiter.api.Assertions.*;

/** Offline evidence for version-dependent semantic assumptions, without starting a server. */
class VersionAdapterBytecodeTest {
    @Test void modernSpawnPointPreservesBothAngles() {
        var spawn=net.minecraft.world.level.storage.LevelData.RespawnData.of(net.minecraft.world.level.Level.OVERWORLD,
                new net.minecraft.core.BlockPos(-330,120,122),87.5f,-22.25f);
        var respawn=new net.minecraft.server.level.ServerPlayer.RespawnConfig(spawn,true);
        var encoded=net.minecraft.server.level.ServerPlayer.RespawnConfig.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,respawn).getOrThrow();
        var decoded=net.minecraft.server.level.ServerPlayer.RespawnConfig.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE,encoded).getOrThrow();
        assertEquals(respawn,decoded);
        assertEquals(-22.25f,decoded.respawnData().pitch());
    }
    @Test void borderIsPersistentStateAndRidingHasAnEventFreeRestorationPath() throws Exception {
        assertTrue(net.minecraft.world.level.saveddata.SavedData.class.isAssignableFrom(net.minecraft.world.level.border.WorldBorder.class));
        var entity=read("net/minecraft/world/entity/Entity");
        assertTrue(entity.methods.stream().anyMatch(m->m.name.equals("startRiding") && m.desc.equals("(Lnet/minecraft/world/entity/Entity;ZZ)Z")));
        var projectile=read("net/minecraft/world/entity/projectile/Projectile");
        assertTrue(projectile.fields.stream().anyMatch(f->f.name.equals("leftOwnerChecked") && f.desc.equals("Z")));
    }
    @Test void ticketsUseCountdownAndSeparateLoadingAndSimulation() throws Exception {
        var type = net.minecraft.server.level.TicketType.ENDER_PEARL;
        assertEquals(40, type.timeout());
        assertTrue(type.doesLoad()); assertTrue(type.doesSimulate());
        assertTrue(net.minecraft.server.level.TicketType.PORTAL.persist());
        assertEquals("minecraft:ender_pearl", net.minecraft.core.registries.BuiltInRegistries.TICKET_TYPE.getKey(type).toString());
        var ticket = read("net/minecraft/server/level/Ticket");
        assertTrue(ticket.fields.stream().anyMatch(f -> f.name.equals("ticksLeft")));
        assertFalse(ticket.fields.stream().anyMatch(f -> f.name.equals("argument") || f.name.equals("tickCreated")));
        var recovery = com.atsukimc.anticrash.recovery.ChunkRecoveryManager.RECOVERY_TICKET;
        assertFalse(recovery.persist());
        assertNotEquals(net.minecraft.server.level.TicketType.DRAGON, recovery);
    }
    @Test void newPickPacketsAreRoutedThroughTheServerThread() throws Exception {
        var handler = read("net/minecraft/server/network/ServerGamePacketListenerImpl");
        for (String name : new String[]{"handlePickItemFromBlock", "handlePickItemFromEntity"})
            assertTrue(invokes(handler, name, "net/minecraft/network/protocol/PacketUtils", "ensureRunningOnSameThread"));
        assertFalse(handler.methods.stream().anyMatch(m -> m.name.equals("onPickFromInventory")));
    }
    @Test void furnacePersistsAllFourRenamedTimers() throws Exception {
        var furnace = read("net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity");
        for (String field : new String[]{"litTimeRemaining", "litTotalTime", "cookingTimer", "cookingTotalTime"}) {
            assertTrue(furnace.fields.stream().anyMatch(f -> f.name.equals(field)));
            for (String method : new String[]{"loadAdditional", "saveAdditional"})
                assertTrue(furnace.methods.stream().filter(m -> m.name.equals(method)).anyMatch(m -> {
                    for (var insn : m.instructions)
                        if (insn instanceof FieldInsnNode f && f.owner.equals(furnace.name) && f.name.equals(field)) return true;
                    return false;
                }), field + ": " + method);
        }
    }
    private ClassNode read(String name) throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(in, name);
            ClassNode node = new ClassNode(); new ClassReader(in).accept(node, 0); return node;
        }
    }
    private boolean invokes(ClassNode node, String method, String owner, String called) {
        return node.methods.stream().filter(m -> m.name.equals(method)).anyMatch(m -> {
            for (var insn : m.instructions) {
                if (insn instanceof MethodInsnNode i && i.owner.equals(owner) && i.name.equals(called)) return true;
            }
            return false;
        });
    }
    @Test void vanillaOwnsSensorSerializationSince119() throws Exception {
        var sensor = read("net/minecraft/world/level/block/entity/SculkSensorBlockEntity");
        assertTrue(invokes(sensor, "saveAdditional", "net/minecraft/world/level/storage/ValueOutput", "store"));
        assertTrue(invokes(sensor, "loadAdditional", "net/minecraft/world/level/storage/ValueInput", "read"));
        var listener = read("net/minecraft/world/level/gameevent/vibrations/VibrationSystem$Data");
        assertTrue(listener.fields.stream().anyMatch(f -> f.name.equals("selectionStrategy") && f.desc.endsWith("/VibrationSelector;")));
        assertTrue(listener.fields.stream().anyMatch(f -> f.name.equals("currentVibration")));
    }
    @Test void predictionAckIsCumulativeAndUsedByVanilla() throws Exception {
        var handler = read("net/minecraft/server/network/ServerGamePacketListenerImpl");
        for (String method : new String[]{"handlePlayerAction", "handleUseItemOn", "handleUseItem"}) {
            assertTrue(invokes(handler, method, handler.name, "ackBlockChangesUpTo"), method);
        }
        assertTrue(invokes(handler, "ackBlockChangesUpTo", "java/lang/Math", "max"));
    }
    @Test void recipeMatchCacheIsSemanticStateNotJustAConstructorValue() throws Exception {
        var getter = read("net/minecraft/world/item/crafting/RecipeManager$1");
        assertTrue(getter.fields.stream().anyMatch(f -> f.name.equals("lastRecipe") && f.desc.endsWith("/ResourceKey;")));
        assertTrue(invokes(getter, "getRecipeFor", "net/minecraft/world/item/crafting/RecipeManager", "getRecipeFor"));
    }
    @Test void tickingGetterIsNotAnExactLoadedChunkSet() throws Exception {
        var holder = read("net/minecraft/server/level/ChunkHolder");
        assertTrue(invokes(holder, "getTickingChunk", holder.name, "getTickingChunkFuture"));
        var adapter = read("com/atsukimc/anticrash/runtime/VersionChunkView");
        assertTrue(invokes(adapter, "worldChunk", holder.name, "getLatestChunk"));
    }
    @Test void newPortalAndTntRuntimeFieldsExistAndAreNotVanillaNbtAuthority() throws Exception {
        var portal = read("net/minecraft/world/entity/PortalProcessor");
        for (String field : new String[]{"portal", "entryPosition", "portalTime", "insidePortalThisTick"})
            assertTrue(portal.fields.stream().anyMatch(f -> f.name.equals(field)), field);
        var tnt = read("net/minecraft/world/entity/item/PrimedTnt");
        assertTrue(tnt.fields.stream().anyMatch(f -> f.name.equals("usedPortal") && f.desc.equals("Z")));
        for (var method : tnt.methods) {
            if (!method.name.equals("addAdditionalSaveData")) continue;
            for (var insn : method.instructions)
                if (insn instanceof FieldInsnNode f) assertNotEquals("usedPortal", f.name,
                        "If Vanilla gains this state, remove the duplicate extension after reviewing it");
        }
    }
}
