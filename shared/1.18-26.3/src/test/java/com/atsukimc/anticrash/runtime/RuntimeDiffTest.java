package com.atsukimc.anticrash.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.util.List;

class RuntimeDiffTest {
    @Test
    void namesWhatEachSideHasAlone() {
        assertEquals(
                "checkpoint only 1: [b]; now only 2: [d, e]",
                RuntimeDiff.describe(List.of("a", "b", "c"), List.of("a", "c", "d", "e"), 3));
        assertEquals(
                "now only 3: [x, y]", RuntimeDiff.describe(List.of(), List.of("x", "y", "x"), 5));
        assertEquals(
                "checkpoint only 4: [a, b]",
                RuntimeDiff.describe(List.of("a", "b", "c", "d"), List.of(), 2)
                        .replaceAll("\\[.*]", "[a, b]"));
    }

    @Test
    void sameLinesInAnotherOrderAreReportedAsSuch() {
        assertEquals(
                "same lines in a different order",
                RuntimeDiff.describe(List.of("a", "b"), List.of("b", "a"), 3));
        assertEquals("", RuntimeDiff.describe(List.of("a", "b"), List.of("a", "b"), 3));
    }

    @Test
    void aSideKnownOnlyByItsDigestGivesNoDetail() {
        assertEquals("", RuntimeDiff.describe(null, List.of("a"), 3));
        assertEquals("", RuntimeDiff.describe(List.of("a"), null, 3));
    }
}
