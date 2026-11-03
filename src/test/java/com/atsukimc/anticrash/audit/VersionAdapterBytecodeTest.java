//? if (>=1.19 && <=26.3) {
/*package com.atsukimc.anticrash.audit;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

import java.io.InputStream;

/^* Offline evidence for version-dependent semantic assumptions, without starting a server. ^/
class VersionAdapterBytecodeTest {

//? if 1.21.4 {
/^@Test
    void enderPearlTicketHasChunkArgumentAndFortyTickExpiry()
^///?}
//? if (>=1.21.9 && <=26.3) {
/^@Test
    void modernSpawnPointPreservesBothAngles() {

^///?}
//? if (>=1.21.9 && <=1.21.11) {
/^var spawn =
                net.minecraft.world.WorldProperties.SpawnPoint.create(
                        net.minecraft.world.World.OVERWORLD,
                        new net.minecraft.util.math.BlockPos(-330, 120, 122),
                        87.5f,
                        -22.25f);
^///?}
//? if (>=26.1 && <=26.3) {
/^var spawn =
                net.minecraft.world.level.storage.LevelData.RespawnData.of(
                        net.minecraft.world.level.Level.OVERWORLD,
                        new net.minecraft.core.BlockPos(-330, 120, 122),
                        87.5f,
                        -22.25f);
^///?}


//? if (>=1.21.9 && <=1.21.11) {
/^var respawn = new net.minecraft.server.network.ServerPlayerEntity.Respawn(spawn, true);
^///?}
//? if (>=26.1 && <=26.3) {
/^var respawn = new net.minecraft.server.level.ServerPlayer.RespawnConfig(spawn, true);
^///?}


//? if (>=1.21.9 && <=1.21.11) {
/^var encoded =
                net.minecraft.server.network.ServerPlayerEntity.Respawn.CODEC
                        .encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, respawn)
                        .getOrThrow();
^///?}
//? if (>=26.1 && <=26.3) {
/^var encoded =
                net.minecraft.server.level.ServerPlayer.RespawnConfig.CODEC
                        .encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, respawn)
                        .getOrThrow();
^///?}


//? if (>=1.21.9 && <=1.21.11) {
/^var decoded =
                net.minecraft.server.network.ServerPlayerEntity.Respawn.CODEC
                        .parse(net.minecraft.nbt.NbtOps.INSTANCE, encoded)
                        .getOrThrow();
^///?}
//? if (>=26.1 && <=26.3) {
/^var decoded =
                net.minecraft.server.level.ServerPlayer.RespawnConfig.CODEC
                        .parse(net.minecraft.nbt.NbtOps.INSTANCE, encoded)
                        .getOrThrow();
^///?}


//? if (>=1.21.9 && <=26.3) {
/^assertEquals(respawn, decoded);
^///?}


//? if (>=1.21.9 && <=26.3) {
/^assertEquals(-22.25f, decoded.respawnData().pitch());
    }
^///?}



//? if (>=1.21.9 && <=26.3) {
/^@Test
    void borderIsPersistentStateAndRidingHasAnEventFreeRestorationPath() throws Exception {

^///?}
//? if (>=1.21.9 && <=1.21.11) {
/^assertTrue(
                net.minecraft.world.PersistentState.class.isAssignableFrom(
                        net.minecraft.world.border.WorldBorder.class));
^///?}
//? if (>=26.1 && <=26.3) {
/^assertTrue(
                net.minecraft.world.level.saveddata.SavedData.class.isAssignableFrom(
                        net.minecraft.world.level.border.WorldBorder.class));
^///?}


//? if (>=1.21.9 && <=1.21.11) {
/^var entity = read("net/minecraft/entity/Entity");
^///?}
//? if (>=26.1 && <=26.3) {
/^var entity = read("net/minecraft/world/entity/Entity");
^///?}


//? if (>=1.21.9 && <=1.21.11) {
/^assertTrue(
                entity.methods.stream()
                        .anyMatch(m ->
                                m.name.equals("startRiding")
                                        && m.desc.equals("(Lnet/minecraft/entity/Entity;ZZ)Z")));
^///?}
//? if (>=26.1 && <=26.3) {
/^assertTrue(
                entity.methods.stream()
                        .anyMatch(m ->
                                m.name.equals("startRiding")
                                        && m.desc.equals(
                                                "(Lnet/minecraft/world/entity/Entity;ZZ)Z")));
^///?}


//? if (>=1.21.9 && <=1.21.11) {
/^var projectile = read("net/minecraft/entity/projectile/ProjectileEntity");
^///?}
//? if (>=26.1 && <=26.3) {
/^var projectile = read("net/minecraft/world/entity/projectile/Projectile");
^///?}


//? if (>=1.21.9 && <=1.21.11) {
/^assertTrue(
                projectile.fields.stream()
                        .anyMatch(f -> f.name.equals("checkedForLeftOwner") && f.desc.equals("Z")));
^///?}
//? if (>=26.1 && <=26.3) {
/^assertTrue(
                projectile.fields.stream()
                        .anyMatch(f -> f.name.equals("leftOwnerChecked") && f.desc.equals("Z")));
^///?}
    //? if (>=1.21.9 && <=26.3) {

    /^}
    ^///?}



//? if (>=1.21.5 && <=26.3) {
/^@Test
    void ticketsUseCountdownAndSeparateLoadingAndSimulation() throws Exception
^///?}
//? if (>=1.21.4 && <=26.3) {
/^{

^///?}
//? if 1.21.4 {
/^assertEquals(40, net.minecraft.server.world.ChunkTicketType.ENDER_PEARL.getExpiryTicks());
^///?}
//? if (>=1.21.5 && <=1.21.11) {
/^var type = net.minecraft.server.world.ChunkTicketType.ENDER_PEARL;
^///?}
//? if (>=26.1 && <=26.3) {
/^var type = net.minecraft.server.level.TicketType.ENDER_PEARL;
^///?}


//? if 1.21.4 {
/^net.minecraft.server.world.ChunkTicketType<net.minecraft.util.math.ChunkPos> type =
                net.minecraft.server.world.ChunkTicketType.ENDER_PEARL;
^///?}
//? if (>=1.21.5 && <=1.21.11) {
/^assertEquals(40, type.expiryTicks());
^///?}
//? if (>=26.1 && <=26.3) {
/^assertEquals(40, type.timeout());
^///?}


//? if 1.21.4 {
/^assertEquals("ender_pearl", type.toString());
^///?}
//? if (>=1.21.5 && <=1.21.11) {
/^assertTrue(type.isForLoading());
^///?}
//? if (>=26.1 && <=26.3) {
/^assertTrue(type.doesLoad());
^///?}


//? if (>=1.21.5 && <=1.21.11) {
/^assertTrue(type.isForSimulation());
^///?}
//? if (>=26.1 && <=26.3) {
/^assertTrue(type.doesSimulate());
^///?}


//? if (>=1.21.5 && <=1.21.8) {
/^assertTrue(net.minecraft.server.world.ChunkTicketType.PORTAL.persist());
^///?}
//? if (>=1.21.9 && <=1.21.11) {
/^assertTrue(net.minecraft.server.world.ChunkTicketType.PORTAL.shouldSerialize());
^///?}
//? if (>=26.1 && <=26.3) {
/^assertTrue(net.minecraft.server.level.TicketType.PORTAL.persist());
^///?}


//? if (>=1.21.5 && <=1.21.11) {
/^assertEquals(
                "minecraft:ender_pearl",
                net.minecraft.registry.Registries.TICKET_TYPE.getId(type).toString());
^///?}
//? if (>=26.1 && <=26.3) {
/^assertEquals(
                "minecraft:ender_pearl",
                net.minecraft.core.registries.BuiltInRegistries.TICKET_TYPE
                        .getKey(type)
                        .toString());
^///?}


//? if (>=1.21.5 && <=1.21.11) {
/^var ticket = read("net/minecraft/server/world/ChunkTicket");
^///?}
//? if (>=26.1 && <=26.3) {
/^var ticket = read("net/minecraft/server/level/Ticket");
^///?}


//? if (>=1.21.5 && <=26.3) {
/^assertTrue(ticket.fields.stream().anyMatch(f -> f.name.equals("ticksLeft")));
^///?}


//? if (>=1.21.5 && <=26.3) {
/^assertFalse(
                ticket.fields.stream()
                        .anyMatch(f -> f.name.equals("argument") || f.name.equals("tickCreated")));
^///?}


//? if (>=1.21.5 && <=26.3) {
/^var recovery = com.atsukimc.anticrash.recovery.ChunkRecoveryManager.RECOVERY_TICKET;
^///?}


//? if (>=1.21.5 && <=1.21.8) || (>=26.1 && <=26.3) {
/^assertFalse(recovery.persist());
^///?}
//? if (>=1.21.9 && <=1.21.11) {
/^assertFalse(recovery.shouldSerialize());
^///?}


//? if (>=1.21.5 && <=1.21.8) {
/^assertNotEquals(net.minecraft.server.world.ChunkTicketType.START, recovery);
^///?}
//? if (>=1.21.9 && <=1.21.11) {
/^assertNotEquals(net.minecraft.server.world.ChunkTicketType.DRAGON, recovery);
^///?}
//? if (>=26.1 && <=26.3) {
/^assertNotEquals(net.minecraft.server.level.TicketType.DRAGON, recovery);
^///?}
    //? if (>=1.21.4 && <=26.3) {

    /^}
    ^///?}



//? if (>=1.21.4 && <=26.3) {
/^@Test
    void newPickPacketsAreRoutedThroughTheServerThread() throws Exception {

^///?}
//? if (>=1.21.4 && <=1.21.11) {
/^var handler = read("net/minecraft/server/network/ServerPlayNetworkHandler");
^///?}
//? if (>=26.1 && <=26.3) {
/^var handler = read("net/minecraft/server/network/ServerGamePacketListenerImpl");
^///?}


//? if (>=1.21.4 && <=1.21.11) {
/^for (String name : new String[] {"onPickItemFromBlock", "onPickItemFromEntity"})
            assertTrue(
                    invokes(
                            handler,
                            name,
                            "net/minecraft/network/NetworkThreadUtils",
                            "forceMainThread"));
^///?}
//? if (>=26.1 && <=26.3) {
/^for (String name : new String[] {"handlePickItemFromBlock", "handlePickItemFromEntity"})
            assertTrue(
                    invokes(
                            handler,
                            name,
                            "net/minecraft/network/protocol/PacketUtils",
                            "ensureRunningOnSameThread"));
^///?}


//? if (>=1.21.4 && <=26.3) {
/^assertFalse(handler.methods.stream().anyMatch(m -> m.name.equals("onPickFromInventory")));
    }
^///?}



//? if (>=1.21.4 && <=26.3) {
/^@Test
    void furnacePersistsAllFourRenamedTimers() throws Exception {

^///?}
//? if (>=1.21.4 && <=1.21.11) {
/^var furnace = read("net/minecraft/block/entity/AbstractFurnaceBlockEntity");
^///?}
//? if (>=26.1 && <=26.3) {
/^var furnace = read("net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity");
^///?}


//? if (>=1.21.4 && <=1.21.11) {
/^for (String field :
                new String[] {
                    "litTimeRemaining", "litTotalTime", "cookingTimeSpent", "cookingTotalTime"
                })
^///?}
//? if (>=26.1 && <=26.3) {
/^for (String field :
                new String[] {
                    "litTimeRemaining", "litTotalTime", "cookingTimer", "cookingTotalTime"
                })
^///?}
//? if (>=1.21.4 && <=26.3) {
/^{
            assertTrue(furnace.fields.stream().anyMatch(f -> f.name.equals(field)));
^///?}


//? if (>=1.21.4 && <=1.21.5) {
/^for (String method : new String[] {"readNbt", "writeNbt"})

^///?}
//? if (>=1.21.6 && <=1.21.11) {
/^for (String method : new String[] {"readData", "writeData"})

^///?}
//? if (>=26.1 && <=26.3) {
/^for (String method : new String[] {"loadAdditional", "saveAdditional"})

^///?}
//? if (>=1.21.4 && <=26.3) {
/^assertTrue(
                        furnace.methods.stream()
                                .filter(m -> m.name.equals(method))
                                .anyMatch(m -> {
                                    for (var insn : m.instructions)
                                        if (insn instanceof FieldInsnNode f
                                                && f.owner.equals(furnace.name)
                                                && f.name.equals(field)) return true;
^///?}


//? if (>=1.21.4 && <=26.3) {
/^return false;
                                }),
                        field + ": " + method);
        }
    }
^///?}


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
/^var sensor = read("net/minecraft/block/entity/SculkSensorBlockEntity");
^///?}
//? if (>=26.1 && <=26.3) {
/^var sensor = read("net/minecraft/world/level/block/entity/SculkSensorBlockEntity");
^///?}


//? if (>=1.19 && <=1.21.4) {
/^assertTrue(invokes(sensor, "writeNbt", "com/mojang/serialization/Codec", "encodeStart"));
^///?}
//? if 1.21.5 {
/^assertTrue(invokes(sensor, "writeNbt", "net/minecraft/nbt/NbtCompound", "put"));
^///?}
//? if (>=1.21.6 && <=1.21.11) {
/^assertTrue(invokes(sensor, "writeData", "net/minecraft/storage/WriteView", "put"));
^///?}
//? if (>=26.1 && <=26.3) {
/^assertTrue(
                invokes(
                        sensor,
                        "saveAdditional",
                        "net/minecraft/world/level/storage/ValueOutput",
                        "store"));
^///?}


//? if (>=1.19 && <=1.21.4) {
/^assertTrue(invokes(sensor, "readNbt", "com/mojang/serialization/Codec", "parse"));
^///?}
//? if 1.21.5 {
/^assertTrue(invokes(sensor, "readNbt", "net/minecraft/nbt/NbtCompound", "get"));
^///?}
//? if (>=1.21.6 && <=1.21.11) {
/^assertTrue(invokes(sensor, "readData", "net/minecraft/storage/ReadView", "read"));
^///?}
//? if (>=26.1 && <=26.3) {
/^assertTrue(
                invokes(
                        sensor,
                        "loadAdditional",
                        "net/minecraft/world/level/storage/ValueInput",
                        "read"));
^///?}


//? if (>=1.19 && <=1.19.4) {
/^var listener = read("net/minecraft/world/event/listener/VibrationListener");
^///?}
//? if (>=1.20 && <=1.21.11) {
/^var listener = read("net/minecraft/world/event/Vibrations$ListenerData");
^///?}
//? if (>=26.1 && <=26.3) {
/^var listener = read("net/minecraft/world/level/gameevent/vibrations/VibrationSystem$Data");
^///?}


//? if (>=1.19 && <=1.19.2) {
/^assertTrue(
                listener.fields.stream()
                        .anyMatch(f -> f.name.equals("distance") && f.desc.equals("F")));
^///?}
//? if (>=1.19.3 && <=1.19.4) {
/^assertTrue(
                listener.fields.stream()
                        .anyMatch(f ->
                                f.name.equals("selector")
                                        && f.desc.endsWith("/VibrationSelector;")));
^///?}
//? if (>=1.20 && <=1.21.11) {
/^assertTrue(
                listener.fields.stream()
                        .anyMatch(f ->
                                f.name.equals("vibrationSelector")
                                        && f.desc.endsWith("/VibrationSelector;")));
^///?}
//? if (>=26.1 && <=26.3) {
/^assertTrue(
                listener.fields.stream()
                        .anyMatch(f ->
                                f.name.equals("selectionStrategy")
                                        && f.desc.endsWith("/VibrationSelector;")));
^///?}


//? if (>=1.19 && <=1.21.11) {
/^assertTrue(listener.fields.stream().anyMatch(f -> f.name.equals("vibration")));
^///?}
//? if (>=26.1 && <=26.3) {
/^assertTrue(listener.fields.stream().anyMatch(f -> f.name.equals("currentVibration")));
^///?}

    }

    @Test
    void predictionAckIsCumulativeAndUsedByVanilla() throws Exception {

//? if (>=1.19 && <=1.21.11) {
/^var handler = read("net/minecraft/server/network/ServerPlayNetworkHandler");
^///?}
//? if (>=26.1 && <=26.3) {
/^var handler = read("net/minecraft/server/network/ServerGamePacketListenerImpl");
^///?}


//? if (>=1.19 && <=1.21.11) {
/^for (String method :
                new String[] {"onPlayerAction", "onPlayerInteractBlock", "onPlayerInteractItem"})
^///?}
//? if (>=26.1 && <=26.3) {
/^for (String method :
                new String[] {"handlePlayerAction", "handleUseItemOn", "handleUseItem"})
^///?}
{

//? if (>=1.19 && <=1.21.11) {
/^assertTrue(invokes(handler, method, handler.name, "updateSequence"), method);
^///?}
//? if (>=26.1 && <=26.3) {
/^assertTrue(invokes(handler, method, handler.name, "ackBlockChangesUpTo"), method);
^///?}

        }

//? if (>=1.19 && <=1.21.11) {
/^assertTrue(invokes(handler, "updateSequence", "java/lang/Math", "max"));
^///?}
//? if (>=26.1 && <=26.3) {
/^assertTrue(invokes(handler, "ackBlockChangesUpTo", "java/lang/Math", "max"));
^///?}

    }

    @Test
    void recipeMatchCacheIsSemanticStateNotJustAConstructorValue() throws Exception {

//? if (>=1.19 && <=1.21.1) {
/^var getter = read("net/minecraft/recipe/RecipeManager$1");
^///?}
//? if (>=1.21.2 && <=1.21.11) {
/^var getter = read("net/minecraft/recipe/ServerRecipeManager$1");
^///?}
//? if (>=26.1 && <=26.3) {
/^var getter = read("net/minecraft/world/item/crafting/RecipeManager$1");
^///?}


//? if (>=1.19 && <=1.21.1) {
/^assertTrue(
                getter.fields.stream()
                        .anyMatch(f -> f.name.equals("id") && f.desc.endsWith("/Identifier;")));
^///?}
//? if (>=1.21.2 && <=1.21.11) {
/^assertTrue(
                getter.fields.stream()
                        .anyMatch(f -> f.name.equals("id") && f.desc.endsWith("/RegistryKey;")));
^///?}
//? if (>=26.1 && <=26.3) {
/^assertTrue(
                getter.fields.stream()
                        .anyMatch(f ->
                                f.name.equals("lastRecipe") && f.desc.endsWith("/ResourceKey;")));
^///?}


//? if (>=1.19 && <=1.21.1) {
/^assertTrue(
                invokes(
                        getter,
                        "getFirstMatch",
                        "net/minecraft/recipe/RecipeManager",
                        "getFirstMatch"));
^///?}
//? if (>=1.21.2 && <=1.21.11) {
/^assertTrue(
                invokes(
                        getter,
                        "getFirstMatch",
                        "net/minecraft/recipe/ServerRecipeManager",
                        "getFirstMatch"));
^///?}
//? if (>=26.1 && <=26.3) {
/^assertTrue(
                invokes(
                        getter,
                        "getRecipeFor",
                        "net/minecraft/world/item/crafting/RecipeManager",
                        "getRecipeFor"));
^///?}

    }


//? if (>=1.21 && <=26.3) {
/^@Test
    void tickingGetterIsNotAnExactLoadedChunkSet() throws Exception {

^///?}
//? if (>=1.21 && <=1.21.11) {
/^var holder = read("net/minecraft/server/world/ChunkHolder");
^///?}
//? if (>=26.1 && <=26.3) {
/^var holder = read("net/minecraft/server/level/ChunkHolder");
^///?}


//? if (>=1.21 && <=1.21.11) {
/^assertTrue(invokes(holder, "getWorldChunk", holder.name, "getTickingFuture"));
^///?}
//? if (>=26.1 && <=26.3) {
/^assertTrue(invokes(holder, "getTickingChunk", holder.name, "getTickingChunkFuture"));
^///?}


//? if (>=1.21 && <=26.3) {
/^var adapter = read("com/atsukimc/anticrash/runtime/VersionChunkView");
^///?}


//? if (>=1.21 && <=1.21.11) {
/^assertTrue(invokes(adapter, "worldChunk", holder.name, "getLatest"));
^///?}
//? if (>=26.1 && <=26.3) {
/^assertTrue(invokes(adapter, "worldChunk", holder.name, "getLatestChunk"));
^///?}
    //? if (>=1.21 && <=26.3) {

    /^}
    ^///?}



//? if (>=1.21 && <=26.3) {
/^@Test
    void newPortalAndTntRuntimeFieldsExistAndAreNotVanillaNbtAuthority() throws Exception {

^///?}
//? if (>=1.21 && <=1.21.11) {
/^var portal = read("net/minecraft/world/dimension/PortalManager");
^///?}
//? if (>=26.1 && <=26.3) {
/^var portal = read("net/minecraft/world/entity/PortalProcessor");
^///?}


//? if (>=1.21 && <=1.21.11) {
/^for (String field : new String[] {"portal", "pos", "ticksInPortal", "inPortal"})

^///?}
//? if (>=26.1 && <=26.3) {
/^for (String field :
                new String[] {"portal", "entryPosition", "portalTime", "insidePortalThisTick"})

^///?}
//? if (>=1.21 && <=26.3) {
/^assertTrue(portal.fields.stream().anyMatch(f -> f.name.equals(field)), field);
^///?}


//? if (>=1.21 && <=1.21.11) {
/^var tnt = read("net/minecraft/entity/TntEntity");
^///?}
//? if (>=26.1 && <=26.3) {
/^var tnt = read("net/minecraft/world/entity/item/PrimedTnt");
^///?}


//? if (>=1.21 && <=1.21.11) {
/^assertTrue(
                tnt.fields.stream()
                        .anyMatch(f -> f.name.equals("teleported") && f.desc.equals("Z")));
^///?}
//? if (>=26.1 && <=26.3) {
/^assertTrue(
                tnt.fields.stream()
                        .anyMatch(f -> f.name.equals("usedPortal") && f.desc.equals("Z")));
^///?}


//? if (>=1.21 && <=26.3) {
/^for (var method : tnt.methods) {

^///?}
//? if (>=1.21 && <=1.21.5) {
/^if (!method.name.equals("writeCustomDataToNbt"))
^///?}
//? if (>=1.21.6 && <=1.21.11) {
/^if (!method.name.equals("writeCustomData"))
^///?}
//? if (>=26.1 && <=26.3) {
/^if (!method.name.equals("addAdditionalSaveData"))
^///?}
//? if (>=1.21 && <=26.3) {
/^continue;
^///?}


//? if (>=1.21 && <=26.3) {
/^for (var insn : method.instructions)
                if (insn instanceof FieldInsnNode f)

^///?}
//? if (>=1.21 && <=1.21.11) {
/^assertNotEquals(
                            "teleported",
                            f.name,
                            "If Vanilla gains this state, remove the duplicate extension after"
                                    + " reviewing it");
^///?}
//? if (>=26.1 && <=26.3) {
/^assertNotEquals(
                            "usedPortal",
                            f.name,
                            "If Vanilla gains this state, remove the duplicate extension after"
                                    + " reviewing it");
^///?}
        //? if (>=1.21 && <=26.3) {

        /^}
    }
        ^///?}

}
*///?}
