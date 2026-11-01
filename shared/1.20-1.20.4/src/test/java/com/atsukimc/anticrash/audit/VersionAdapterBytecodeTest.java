package com.atsukimc.anticrash.audit;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

import java.io.InputStream;

/** Offline evidence for version-dependent semantic assumptions, without starting a server. */
class VersionAdapterBytecodeTest {
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
        assertTrue(invokes(sensor, "writeNbt", "com/mojang/serialization/Codec", "encodeStart"));
        assertTrue(invokes(sensor, "readNbt", "com/mojang/serialization/Codec", "parse"));
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
        var getter = read("net/minecraft/recipe/RecipeManager$1");
        assertTrue(
                getter.fields.stream()
                        .anyMatch(f -> f.name.equals("id") && f.desc.endsWith("/Identifier;")));
        assertTrue(
                invokes(
                        getter,
                        "getFirstMatch",
                        "net/minecraft/recipe/RecipeManager",
                        "getFirstMatch"));
    }
}
