package com.atsukimc.anticrash.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoveryEquivalenceReportTest {
    @Test
    void allMatchingCategoriesBecomeReady() {
        RecoveryEquivalenceReport report = new RecoveryEquivalenceReport("cp-000512", "STRICT");
        report.add(RecoveryEquivalenceReport.compare(
                EquivalenceCategory.CHUNK_DOMAIN, List.of("overworld|1,2"),
                List.of("overworld|1,2"), true));
        report.add(RecoveryEquivalenceReport.notApplicable(EquivalenceCategory.PLAYERS));

        report.finalizeResult(Set.of());

        assertTrue(report.ready);
        assertEquals("STRICT", report.fidelity);
        assertEquals("READY", report.render(false).substring(report.render(false).lastIndexOf("Result: ") + 8));
    }

    @Test
    void orderDifferenceIsABlockingMismatchWithActionableDetails() {
        RecoveryEquivalenceReport report = new RecoveryEquivalenceReport("cp-000512", "STRICT");
        report.add(RecoveryEquivalenceReport.compare(
                EquivalenceCategory.BLOCK_ENTITY_TICKERS,
                List.of("CURRENT|0,0,0|minecraft:chest|a", "CURRENT|1,0,0|minecraft:chest|b"),
                List.of("CURRENT|1,0,0|minecraft:chest|b", "CURRENT|0,0,0|minecraft:chest|a"),
                true));

        report.finalizeResult(Set.of());

        assertFalse(report.ready);
        assertTrue(report.blockingReason.contains(EquivalenceCategory.BLOCK_ENTITY_TICKERS));
        assertTrue(report.render(true).contains("index 0"));
    }

    @Test
    void unsupportedStateIsAllowedOnlyForTheNamedCategory() {
        RecoveryEquivalenceReport report = new RecoveryEquivalenceReport("cp-000512", "STRICT");
        report.add(RecoveryEquivalenceReport.unsupported(EquivalenceCategory.WORLD_RNG, true,
                "Random implementation is opaque"));
        report.add(RecoveryEquivalenceReport.unsupported(EquivalenceCategory.CARPET, true,
                "Carpet adapter unavailable"));

        report.finalizeResult(Set.of(EquivalenceCategory.WORLD_RNG));

        assertFalse(report.ready);
        assertEquals(EquivalenceCategory.CARPET, report.blockingReason.split(" ")[0]);
        assertTrue(report.unsupportedCategories().contains(EquivalenceCategory.WORLD_RNG));
    }

    @Test
    void oldFormatIsExplicitlyMissingAndNeverMatch() {
        RecoveryEquivalenceReport report = new RecoveryEquivalenceReport("cp-000100", "STRICT");
        report.add(RecoveryEquivalenceReport.missing(EquivalenceCategory.CHUNK_DOMAIN, true,
                "Runtime Capsule v5 has no exact domain"));

        report.finalizeResult(Set.of());

        assertFalse(report.ready);
        assertEquals(EquivalenceStatus.MISSING_FROM_OLD_FORMAT,
                report.check(EquivalenceCategory.CHUNK_DOMAIN).status);
    }

    @Test
    void japaneseRenderingUsesConfiguredLanguageWithoutChangingTheGate() {
        RecoveryEquivalenceReport report = new RecoveryEquivalenceReport("cp-000512", "STRICT");
        report.add(RecoveryEquivalenceReport.compare(
                EquivalenceCategory.CHUNK_DOMAIN, List.of("overworld|1,2"),
                List.of("overworld|1,2"), true));
        report.finalizeResult(Set.of());

        String rendered = report.render(false, "ja");
        assertTrue(rendered.startsWith("AntiCrash 復旧等価性"));
        assertTrue(rendered.contains("CHUNK_DOMAIN（Chunk実行領域）: MATCH"));
        assertTrue(rendered.contains("結果: 準備完了"));
        assertTrue(report.ready);
    }
}
