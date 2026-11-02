package com.atsukimc.anticrash.runtime;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class ChunkDomainLevelTest {
    @Test
    void nativeNamesHaveTheSameStableCheckpointMeaning() {
        assertEquals("BORDER", ChunkDomainLevel.canonical("FULL"));
        assertEquals("TICKING", ChunkDomainLevel.canonical("BLOCK_TICKING"));
        for (String stable : new String[] {"INACCESSIBLE", "BORDER", "TICKING", "ENTITY_TICKING"})
            assertEquals(stable, ChunkDomainLevel.canonical(stable));
        assertThrows(IllegalArgumentException.class, () -> ChunkDomainLevel.canonical("FUTURE_STATE"));
        assertThrows(IllegalArgumentException.class, () -> ChunkDomainLevel.canonical(null));
    }

    @Test
    void realCapturePathNormalizesTheMappedGamesEnum() throws Exception {
        ClassNode row = new ClassNode();
        try (var input = ChunkStateSnapshot.ChunkReadiness.class.getResourceAsStream(
                "ChunkStateSnapshot$ChunkReadiness.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(row, 0);
        }
        assertTrue(row.methods.stream().filter(m -> m.name.equals("of")).anyMatch(m -> {
            for (var instruction : m.instructions)
                if (instruction instanceof MethodInsnNode call
                        && call.owner.equals("com/atsukimc/anticrash/runtime/ChunkDomainLevel")
                        && call.name.equals("canonical")) return true;
            return false;
        }), "The production capture must normalize native FULL/BLOCK_TICKING names before validation");
    }
}
