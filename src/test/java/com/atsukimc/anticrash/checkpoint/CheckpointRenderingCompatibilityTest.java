package com.atsukimc.anticrash.checkpoint;

import static org.junit.jupiter.api.Assertions.*;

import com.atsukimc.anticrash.config.AntiCrashConfig;
import com.atsukimc.anticrash.core.AntiCrashState;
import com.atsukimc.anticrash.snapshot.CheckpointManifest;

import org.junit.jupiter.api.Test;

class CheckpointRenderingCompatibilityTest {
    @Test
    void matchesFrozenPreRefactorOutputForBothLanguagesAndVerbosity() {
        for (String lang : new String[] {"en", "ja"})
            for (boolean verbose : new boolean[] {false, true})
                for (boolean hasManifest : new boolean[] {false, true}) {
                    CheckpointManifest manifest = hasManifest ? new CheckpointManifest() : null;
                    if (manifest != null) {
                        manifest.checkpointId = "cp-123456";
                        manifest.snapshotBackend = "PORTABLE_GENERATION";
                    }
                    var view =
                            new CheckpointDiagnostics.View(
                                    AntiCrashState.RECOVERY_FROZEN,
                                    true,
                                    "incident",
                                    new AntiCrashConfig().checkpoint,
                                    manifest,
                                    null,
                                    "candidates",
                                    "mirror",
                                    "failure",
                                    "compaction");
                    assertEquals(
                            legacy(view, lang, verbose),
                            CheckpointDiagnostics.render(view, lang, verbose));
                }
    }

    // Frozen 05c8cd5 display contract. Deliberate test-only duplication catches order/spacing
    // drift.
    private static String legacy(
            CheckpointDiagnostics.View view, String language, boolean verbose) {

        boolean japanese = "ja".equalsIgnoreCase(language);
        CheckpointManifest manifest = view.manifest();
        CheckpointTiming timing = view.timing();
        StringBuilder result = new StringBuilder();
        result.append(
                        japanese
                                ? "AntiCrash Checkpoint Performance"
                                : "AntiCrash Checkpoint Performance")
                .append('\n')
                .append(japanese ? "状態: " : "State: ")
                .append(view.state())
                .append('\n')
                .append(japanese ? "進行中: " : "In flight: ")
                .append(view.capturing())
                .append('\n')
                .append(view.incident())
                .append('\n')
                .append(
                        japanese
                                ? "閾値 target/warn/critical ms: "
                                : "Threshold target/warn/critical ms: ")
                .append(view.settings().targetBarrierMs)
                .append('/')
                .append(view.settings().warnBarrierMs)
                .append('/')
                .append(view.settings().criticalBarrierMs)
                .append('\n');
        if (manifest == null) {
            result.append(japanese ? "まだ確定済みcheckpointはありません。" : "No committed checkpoint yet.");
            if (view.finalizationError() != null) {
                result.append('\n')
                        .append(japanese ? "直近エラー: " : "Last error: ")
                        .append(view.finalizationError());
            }
            return result.toString();
        }
        result.append(japanese ? "Checkpoint: " : "Checkpoint: ")
                .append(manifest.checkpointId)
                .append('\n')
                .append(japanese ? "Backend: " : "Backend: ")
                .append(manifest.snapshotBackend)
                .append('\n')
                .append(japanese ? "Engine: " : "Engine: ")
                .append(manifest.checkpointEngine)
                .append('\n')
                .append(japanese ? "Parent: " : "Parent: ")
                .append(manifest.parentCheckpointId)
                .append('\n')
                .append(japanese ? "論理カット: " : "Logical cut: ")
                .append(manifest.logicalCutServerTick)
                .append('\n')
                .append(japanese ? "Barrier ms: " : "Barrier ms: ")
                .append(manifest.barrierDurationMs)
                .append('\n')
                .append(japanese ? "Finalization ms: " : "Finalization ms: ")
                .append(manifest.finalizationDurationMs)
                .append('\n')
                .append(
                        japanese
                                ? "Generation files present/absent/inherited/sealed/tombstone: "
                                : "Generation files present/absent/inherited/sealed/tombstone: ")
                .append(manifest.generationPresentFiles)
                .append('/')
                .append(manifest.generationAbsentFiles)
                .append('/')
                .append(manifest.generationInheritedFiles)
                .append('/')
                .append(manifest.generationSealedFiles)
                .append('/')
                .append(manifest.generationTombstoneFiles)
                .append('\n')
                .append(japanese ? "Heap before/after/max: " : "Heap before/after/max: ")
                .append(manifest.heapUsedBeforeBytes)
                .append('/')
                .append(manifest.heapUsedAfterBytes)
                .append('/')
                .append(manifest.heapMaxBytes)
                .append('\n')
                .append(
                        japanese
                                ? "Phase ms（barrier等の親phaseはinclusive）: "
                                : "Phase ms (parent phases such as barrier are inclusive): ")
                .append(timing == null ? manifest.phaseDurationsMs : timing.durationsMs())
                .append('\n')
                .append(japanese ? "Logical delta: " : "Logical delta: ")
                .append(manifest.logicalDeltaChunkCount)
                .append(" chunks / ")
                .append(manifest.logicalDeltaEntityChunkCount)
                .append(" entity chunks / ")
                .append(manifest.logicalDeltaTombstoneCount)
                .append(" tombstones / ")
                .append(manifest.logicalDeltaPayloadBytes)
                .append(" bytes")
                .append('\n')
                .append(japanese ? "Candidate cache: " : "Candidate cache: ")
                .append(view.candidates())
                .append('\n')
                .append(japanese ? "Mirror: " : "Mirror: ")
                .append(view.mirror());
        if (view.compactionError() != null) {
            result.append('\n')
                    .append(japanese ? "直近compactionエラー: " : "Last compaction error: ")
                    .append(view.compactionError());
        }
        if (verbose) {
            result.append('\n')
                    .append(
                            japanese
                                    ? "詳細phase（親phaseはinclusive）: "
                                    : "Detailed phases (parent phases are inclusive): ")
                    .append(timing == null ? manifest.phaseDurationsMs : timing.durationsMs())
                    .append('\n')
                    .append(japanese ? "未対応状態: " : "Unsupported state: ")
                    .append(manifest.logicalDeltaComponentStatus)
                    .append('\n')
                    .append(japanese ? "Runtime unsupported: " : "Runtime unsupported: ")
                    .append(manifest.unsupportedRuntimeState);
        }
        if (view.finalizationError() != null) {
            result.append('\n')
                    .append(japanese ? "直近エラー: " : "Last error: ")
                    .append(view.finalizationError());
        }
        return result.toString();
    }
}
