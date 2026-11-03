package com.atsukimc.anticrash.runtime;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.List;

class RecoveryRenderingCompatibilityTest {
    @Test
    void preservesCategoryIdsCountsHashesAndDiffOrderInBothLanguages() {
        var report = new RecoveryEquivalenceReport("cp-000001", "STRICT");
        report.add(
                RecoveryEquivalenceReport.compare(
                        EquivalenceCategory.CHUNK_DOMAIN, List.of("a"), List.of("b"), true));
        report.finalizeResult(false);
        String en = report.render(true, "en"), ja = report.render(true, "ja");
        assertTrue(
                en.startsWith(
                        "AntiCrash Recovery Equivalence\n"
                                + "Checkpoint: cp-000001\n"
                                + "Fidelity: STRICT\n"
                                + "CHUNK_DOMAIN: MISMATCH 1/1 expected="));
        assertTrue(
                ja.startsWith(
                        "AntiCrash 復旧等価性\n"
                                + "チェックポイント: cp-000001\n"
                                + "復旧モード: STRICT\n"
                                + "CHUNK_DOMAIN（Chunk実行領域）: MISMATCH 1/1 expected="));
        assertTrue(
                en.contains(
                        "\n"
                                + "  index 0 expected=a actual=b\n"
                                + "Result: NOT READY\n"
                                + "Blocking reason: CHUNK_DOMAIN MISMATCH"));
        assertTrue(
                ja.contains(
                        "\n  index 0 expected=a actual=b\n結果: 未準備\n停止理由: CHUNK_DOMAIN MISMATCH"));
        assertEquals(en.lines().count(), ja.lines().count());
        assertFalse(report.render(false, "en").contains("\n  index"));
    }
}
