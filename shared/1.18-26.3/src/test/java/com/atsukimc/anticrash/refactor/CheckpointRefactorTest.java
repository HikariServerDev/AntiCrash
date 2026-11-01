package com.atsukimc.anticrash.refactor;

import static org.junit.jupiter.api.Assertions.*;

import com.atsukimc.anticrash.checkpoint.CheckpointDiagnostics;
import com.atsukimc.anticrash.checkpoint.CheckpointEnginePolicy;
import com.atsukimc.anticrash.config.AntiCrashConfig;
import com.atsukimc.anticrash.integration.carpet.FakePlayerSnapshotOptions;
import com.atsukimc.anticrash.recovery.RecoveryChecks;
import com.atsukimc.anticrash.runtime.EquivalenceCategory;
import com.atsukimc.anticrash.runtime.RuntimeCapsule;
import com.atsukimc.anticrash.snapshot.CheckpointManifest;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/** Differential tests against the frozen pre-refactor implementations, without a live server. */
class CheckpointRefactorTest {
    @Test
    void strictChecksPreserveAcceptanceAndEveryFailureMessage() {
        compareStrict(null, null);
        CheckpointManifest manifest = manifest();
        compareStrict(manifest, null);
        RuntimeCapsule capsule = capsule();
        compareStrict(manifest, capsule);
        assertDoesNotThrow(() -> RecoveryChecks.validateStrictManifest(manifest, capsule));
        for (Consumer<CheckpointManifest> mutation :
                List.<Consumer<CheckpointManifest>>of(
                        m -> m.fidelity = null,
                        m -> m.fidelity = "OPTIMIZED",
                        m -> m.chunkDomainHash = null,
                        m -> m.chunkDomainHash = "wrong",
                        m -> m.fullChunkCount++,
                        m -> m.blockTickingChunkCount++,
                        m -> m.entityTickingChunkCount++,
                        m -> m.forcedChunkCount++,
                        m -> m.blockEntityTickerCount++,
                        m -> m.entityOrderCount++,
                        m -> m.torchBurnoutCount++,
                        m -> m.ticketCount++,
                        m -> m.checkpointPlayerCount++,
                        m -> m.checkpointPlayerHash = "wrong",
                        m -> m.carpetCaptureStatus = "INCOMPLETE",
                        m -> m.unsupportedRuntimeState.add("unknown"))) {
            CheckpointManifest changed = manifest();
            mutation.accept(changed);
            compareStrict(changed, capsule);
            assertThrows(
                    Exception.class, () -> RecoveryChecks.validateStrictManifest(changed, capsule));
        }
        capsule.expectedFingerprints.clear();
        compareStrict(manifest(), capsule);
    }

    @Test
    void unsupportedCategoriesKeepAllFlagCombinationsAndOrdering() {
        for (int mask = 0; mask < 32; mask++) {
            AntiCrashConfig config = new AntiCrashConfig();
            var strict = config.recovery.strict;
            strict.allowUnsupportedRuntimeState = (mask & 1) != 0;
            strict.requireEntityOrder = (mask & 2) != 0;
            strict.requireBlockEntityTickerOrder = (mask & 4) != 0;
            strict.requireTicketLedger = (mask & 8) != 0;
            strict.allowUnsupportedCarpetState = (mask & 16) != 0;
            assertEquals(
                    new ArrayList<>(
                            LegacyCheckpointLogic.strictAllowedUnsupportedCategories(config)),
                    new ArrayList<>(RecoveryChecks.allowedUnsupportedCategories(strict)));
        }
    }

    @Test
    void engineSelectionPreservesDefaultsAliasesAndUnsafeFlags() {
        String[] backends = {
            null, "AUTO", " auto ", "PORTABLE_GENERATION", "generation", "LEGACY_SAFE", "", "other"
        };
        String[] modes = {
            null, "LOGICAL_DELTA", " logical_delta ", "PORTABLE_GENERATION", "", "other"
        };
        for (String backend : backends)
            for (String mode : modes)
                for (int mask = 0; mask < 16; mask++) {
                    AntiCrashConfig config = new AntiCrashConfig();
                    config.snapshot.backend = backend;
                    config.checkpoint.engineMode = mode;
                    config.checkpoint.allowUnsupportedPersistentState = (mask & 1) != 0;
                    config.playerSnapshot.vanillaFiles.restorePlayerdata = (mask & 2) != 0;
                    config.playerSnapshot.vanillaFiles.restoreStats = (mask & 4) != 0;
                    config.playerSnapshot.vanillaFiles.restoreAdvancements = (mask & 8) != 0;
                    assertEquals(
                            LegacyCheckpointLogic.usePortableGeneration(config),
                            CheckpointEnginePolicy.usesPortableGeneration(config));
                    assertEquals(
                            LegacyCheckpointLogic.logicalDeltaRequested(config),
                            CheckpointEnginePolicy.logicalDeltaRequested(config));
                    assertEquals(
                            LegacyCheckpointLogic.hasUnsafeLogicalOptions(config),
                            CheckpointEnginePolicy.hasUnsafeLogicalOptions(config));
                }
    }

    @Test
    void diagnosticTextKeepsEveryLabelValueAndOrdering() {
        Random random = new Random(0xAC101);
        String[] categories = {
            EquivalenceCategory.CHUNK_DOMAIN,
            EquivalenceCategory.BLOCK_ENTITY_TICKERS,
            EquivalenceCategory.ENTITIES,
            EquivalenceCategory.BLOCK_EVENTS,
            EquivalenceCategory.SCHEDULED_TICKS,
            EquivalenceCategory.MOVING_PISTONS,
            EquivalenceCategory.REDSTONE_RUNTIME,
            EquivalenceCategory.WORLD_RUNTIME,
            EquivalenceCategory.WORLD_RNG,
            EquivalenceCategory.TICKET_LEDGER,
            EquivalenceCategory.CARPET,
            EquivalenceCategory.FAKEPLAYERS
        };
        for (int sample = 0; sample < 100; sample++) {
            RuntimeCapsule capsule = capsule();
            CheckpointManifest manifest = manifest();
            manifest.fidelity = sample % 2 == 0 ? "STRICT" : null;
            manifest.loadedChunkCount = random.nextInt();
            manifest.fullChunkCount = random.nextInt();
            manifest.blockTickingChunkCount = random.nextInt();
            manifest.entityTickingChunkCount = random.nextInt();
            manifest.forcedChunkCount = random.nextInt();
            manifest.blockEntityTickerCount = random.nextInt();
            manifest.entityOrderCount = random.nextInt();
            manifest.blockEventCount = random.nextInt();
            manifest.torchBurnoutCount = random.nextInt();
            manifest.ticketCount = random.nextInt();
            manifest.fakePlayerCount = random.nextInt();
            manifest.carpetCaptureStatus = sample % 3 == 0 ? null : "CAPTURED";
            for (String category : categories)
                if (random.nextBoolean()) {
                    capsule.expectedFingerprints.put(
                            category, random.nextBoolean() ? null : "hash-" + random.nextInt());
                }
            if (sample % 2 == 0) capsule.unsupportedRuntimeState.add(EquivalenceCategory.WORLD_RNG);
            capsule.scheduledTicks.add(null);
            capsule.movingPistonTickOrder.add(null);
            capsule.worlds.add(null);
            for (String dimension :
                    List.of("minecraft:the_nether", "minecraft:overworld", "custom:日本語")) {
                var world = new RuntimeCapsule.WorldRuntime();
                world.dimension = dimension;
                world.loadedChunkCount = random.nextInt();
                world.fullChunkCount = random.nextInt();
                world.blockTickingChunkCount = random.nextInt();
                world.entityTickingChunkCount = random.nextInt();
                world.forcedChunkCount = random.nextInt();
                capsule.worlds.add(world);
            }
            long bytes = random.nextLong(), used = random.nextLong(), max = random.nextLong();
            assertEquals(
                    LegacyCheckpointLogic.checkpointDiagnostic(capsule, manifest, bytes, used, max),
                    CheckpointDiagnostics.describe(capsule, manifest, bytes, used, max));
        }
    }

    @Test
    void fakePlayerRequirementsPreserveEveryFlagCombination() {
        for (int mask = 0; mask < 64; mask++) {
            var options = new AntiCrashConfig.FakeSnapshot();
            options.actions = (mask & 1) != 0;
            options.actionInternalTiming = (mask & 2) != 0;
            options.itemUseCooldown = (mask & 4) != 0;
            options.blockBreakProgress = (mask & 8) != 0;
            options.inventory = (mask & 16) != 0;
            options.selectedSlot = (mask & 32) != 0;
            assertEquals((mask & 15) != 0, FakePlayerSnapshotOptions.actionsRequired(options));
            assertEquals((mask & 48) != 0, FakePlayerSnapshotOptions.inventoryRequired(options));
        }
    }

    private static RuntimeCapsule capsule() {
        RuntimeCapsule capsule = new RuntimeCapsule();
        capsule.expectedFingerprints.put(EquivalenceCategory.CHUNK_DOMAIN, "domain");
        return capsule;
    }

    private static CheckpointManifest manifest() {
        CheckpointManifest manifest = new CheckpointManifest();
        manifest.fidelity = "STRICT";
        manifest.chunkDomainHash = "domain";
        manifest.carpetCaptureStatus = "NOT_APPLICABLE";
        return manifest;
    }

    private static void compareStrict(CheckpointManifest manifest, RuntimeCapsule capsule) {
        assertEquals(
                outcome(() -> LegacyCheckpointLogic.validateStrictManifest(manifest, capsule)),
                outcome(() -> RecoveryChecks.validateStrictManifest(manifest, capsule)));
    }

    private static String outcome(org.junit.jupiter.api.function.Executable operation) {
        try {
            operation.execute();
            return "accepted";
        } catch (Throwable error) {
            return error.getClass().getName() + ":" + error.getMessage();
        }
    }
}
