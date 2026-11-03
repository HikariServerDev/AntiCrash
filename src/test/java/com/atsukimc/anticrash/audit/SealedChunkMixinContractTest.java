package com.atsukimc.anticrash.audit;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.List;

/** Checks the actual mapped overloads, not just that a method with this name exists. */
class SealedChunkMixinContractTest {
    private ClassNode read(String internalName) throws Exception {
        try (var input = getClass().getClassLoader().getResourceAsStream(internalName + ".class")) {
            assertNotNull(input, internalName);
            ClassNode result = new ClassNode();
            new ClassReader(input).accept(result, 0);
            return result;
        }
    }

    private Object value(AnnotationNode annotation, String key) {
        for (int i = 0; annotation.values != null && i < annotation.values.size(); i += 2)
            if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        return null;
    }

    @Test
    void sealedChunkCopySelectsThePayloadArgumentEvenWithAnExtraContextTag() throws Exception {
        var mixin = read("com/atsukimc/anticrash/mixin/VersionedChunkStorageMixin");
        var owner =
                mixin.invisibleAnnotations.stream()
                        .filter(a -> a.desc.endsWith("/Mixin;"))
                        .findFirst()
                        .orElseThrow();
        Type target = (Type) ((List<?>) value(owner, "value")).get(0);
        var game = read(target.getInternalName());
        var handler =
                mixin.methods.stream()
                        .filter(m -> m.name.equals("anticrash$copySealedChunk"))
                        .findFirst()
                        .orElseThrow();
        var injection =
                handler.visibleAnnotations.stream()
                        .filter(a -> a.desc.endsWith("/ModifyVariable;"))
                        .findFirst()
                        .orElseThrow();
        assertEquals(
                0,
                value(injection, "ordinal"),
                "Implicit selection breaks when a context NBT is present");
        assertEquals(true, value(injection, "argsOnly"));
        var nbt = Type.getArgumentTypes(handler.desc)[0];
        String selector = (String) ((List<?>) value(injection, "method")).get(0);
        long overloads =
                game.methods.stream()
                        .filter(m -> m.name.equals(selector))
                        .filter(m ->
                                java.util.Arrays.asList(Type.getArgumentTypes(m.desc))
                                        .contains(nbt))
                        .count();
        assertTrue(overloads > 0, target + ": " + selector);
        // Every overload with an NBT payload must still return that NBT type. The selected
        // ordinal is the first NBT argument, never a later data-fixer context argument.
        for (var method : game.methods)
            if (method.name.equals(selector)
                    && java.util.Arrays.asList(Type.getArgumentTypes(method.desc)).contains(nbt))
                assertEquals(nbt, Type.getReturnType(method.desc), method.desc);
    }
}
