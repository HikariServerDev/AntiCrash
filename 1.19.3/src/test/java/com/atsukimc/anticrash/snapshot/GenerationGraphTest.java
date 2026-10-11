package com.atsukimc.anticrash.snapshot;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GenerationGraphTest {
    @Test
    void retentionProtectsAllAncestorsOfKeptGeneration() {
        Set<String> protectedIds = GenerationGraph.protectedAncestors(
                List.of(new GenerationGraph.Node("cp-1", null),
                        new GenerationGraph.Node("cp-2", "cp-1"),
                        new GenerationGraph.Node("cp-3", "cp-2")),
                List.of("cp-3"));

        assertEquals(Set.of("cp-1", "cp-2", "cp-3"), protectedIds);
    }

    @Test
    void depthStopsOnCyclesInsteadOfLooping() {
        assertEquals(3, GenerationGraph.depth("cp-3",
                java.util.Map.of("cp-3", "cp-2", "cp-2", "cp-1", "cp-1", "cp-2")));
    }
}
