package com.atsukimc.anticrash.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Operator-facing result of comparing the checkpoint Runtime Capsule with the new JVM.
 *
 * <p>The report is intentionally independent of Minecraft objects. It can therefore be tested with
 * fake fingerprints and can be safely rendered while the world is frozen. A hash is only a quick
 * equality test; the optional difference list is retained for the few entries that explain a
 * blocked start.
 */
public final class RecoveryEquivalenceReport {
    public String checkpointId;
    public String fidelity;
    public boolean ready;
    public String blockingReason;
    public final List<EquivalenceCheck> checks = new ArrayList<>();

    public RecoveryEquivalenceReport() {}

    public RecoveryEquivalenceReport(String checkpointId, String fidelity) {
        this.checkpointId = checkpointId;
        this.fidelity = fidelity;
    }

    public void add(EquivalenceCheck check) {
        if (check != null) checks.add(check);
    }

    public EquivalenceCheck check(String category) {
        for (EquivalenceCheck check : checks) {
            if (category.equals(check.category)) return check;
        }
        return null;
    }

    /** Recomputes the final gate after all category comparisons have been added. */
    public void finalizeResult(boolean allowUnsupported) {
        finalizeResult(allowUnsupported ? null : Set.of());
    }

    /**
     * Recomputes the final gate while allowing only named unsupported categories.
     *
     * <p>Runtime and Carpet have separate configuration switches. A single global boolean would
     * accidentally make an unsupported RNG state acceptable when only Carpet state was allowed, so
     * the production path uses this category-scoped overload.
     */
    public void finalizeResult(Set<String> allowedUnsupportedCategories) {
        Set<String> allowed =
                allowedUnsupportedCategories == null
                        ? null
                        : new HashSet<>(allowedUnsupportedCategories);
        ready = true;
        blockingReason = null;
        for (EquivalenceCheck check : checks) {
            if (check == null || !check.blocking) continue;
            boolean blocks =
                    switch (check.status) {
                        case MATCH, NOT_APPLICABLE -> false;
                        case UNSUPPORTED -> allowed == null || !allowed.contains(check.category);
                        case MISMATCH, MISSING_FROM_OLD_FORMAT, INCOMPLETE -> true;
                    };
            if (!blocks) continue;
            ready = false;
            if (blockingReason == null) {
                blockingReason =
                        check.category
                                + " "
                                + check.status
                                + (check.differences.isEmpty()
                                        ? ""
                                        : ": " + check.differences.get(0));
            }
        }
    }

    public List<String> unsupportedCategories() {
        List<String> result = new ArrayList<>();
        for (EquivalenceCheck check : checks) {
            if (check.status == EquivalenceStatus.UNSUPPORTED
                    || check.status == EquivalenceStatus.MISSING_FROM_OLD_FORMAT) {
                result.add(check.category);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** Keeps the historical English rendering for callers outside the command layer. */
    public String render(boolean verbose) {
        return render(verbose, "en");
    }

    /** Renders the report using the AntiCrash language setting. */
    public String render(boolean verbose, String language) {
        boolean japanese = "ja".equalsIgnoreCase(language);
        StringBuilder output = new StringBuilder();
        output.append(japanese ? "AntiCrash 復旧等価性" : "AntiCrash Recovery Equivalence")
                .append('\n')
                .append(japanese ? "チェックポイント: " : "Checkpoint: ")
                .append(checkpointId == null ? "-" : checkpointId)
                .append('\n')
                .append(japanese ? "復旧モード: " : "Fidelity: ")
                .append(fidelity == null ? "-" : fidelity)
                .append('\n');
        for (EquivalenceCheck check : checks) {
            output.append(categoryLabel(check.category, japanese))
                    .append(": ")
                    .append(check.status)
                    .append(' ')
                    .append(check.expectedCount)
                    .append('/')
                    .append(check.actualCount);
            if (check.expectedFingerprint != null || check.actualFingerprint != null) {
                output.append(" expected=")
                        .append(shortHash(check.expectedFingerprint))
                        .append(" actual=")
                        .append(shortHash(check.actualFingerprint));
            }
            output.append('\n');
            if (verbose) {
                for (String difference : check.differences) {
                    output.append("  ").append(difference).append('\n');
                }
            }
        }
        output.append(japanese ? "結果: " : "Result: ")
                .append(ready ? (japanese ? "準備完了" : "READY") : (japanese ? "未準備" : "NOT READY"))
                .append('\n');
        if (blockingReason != null) {
            output.append(japanese ? "停止理由: " : "Blocking reason: ")
                    .append(blockingReason)
                    .append('\n');
        }
        return output.toString().stripTrailing();
    }

    private static String categoryLabel(String category, boolean japanese) {
        if (!japanese || category == null) return category;
        return switch (category) {
            case EquivalenceCategory.CHUNK_DOMAIN -> "CHUNK_DOMAIN（Chunk実行領域）";
            case EquivalenceCategory.BLOCK_EVENTS -> "BLOCK_EVENTS（Block Event）";
            case EquivalenceCategory.SCHEDULED_TICKS -> "SCHEDULED_TICKS（Scheduled Tick）";
            case EquivalenceCategory.MOVING_PISTONS -> "MOVING_PISTONS（Moving Piston）";
            case EquivalenceCategory.BLOCK_ENTITY_TICKERS -> "BLOCK_ENTITY_TICKERS（BlockEntity順序）";
            case EquivalenceCategory.ENTITIES -> "ENTITIES（Entity順序）";
            case EquivalenceCategory.REDSTONE_RUNTIME -> "REDSTONE_RUNTIME（Torch Burnout）";
            case EquivalenceCategory.WORLD_RUNTIME -> "WORLD_RUNTIME（World Runtime）";
            case EquivalenceCategory.WORLD_RNG -> "WORLD_RNG（World RNG）";
            case EquivalenceCategory.TICKET_LEDGER -> "TICKET_LEDGER（Chunk Ticket）";
            case EquivalenceCategory.CARPET -> "CARPET";
            case EquivalenceCategory.FAKEPLAYERS -> "FAKEPLAYERS（FakePlayer）";
            case EquivalenceCategory.PLAYERS -> "PLAYERS（Player）";
            case EquivalenceCategory.CHECKPOINT_INTEGRITY -> "CHECKPOINT_INTEGRITY（Checkpoint整合性）";
            default -> category;
        };
    }

    private static String shortHash(String value) {
        if (value == null) return "-";
        return value.length() <= 16 ? value : value.substring(0, 16);
    }

    /** Builds a deterministic comparison without Minecraft dependencies. */
    public static EquivalenceCheck compare(
            String category, List<String> expected, List<String> actual, boolean blocking) {
        List<String> left = expected == null ? List.of() : expected;
        List<String> right = actual == null ? List.of() : actual;
        String expectedFingerprint = RuntimeFingerprint.hashLines(left);
        String actualFingerprint = RuntimeFingerprint.hashLines(right);
        List<String> differences = new ArrayList<>();
        int shared = Math.min(left.size(), right.size());
        for (int index = 0; index < shared && differences.size() < 20; index++) {
            if (!String.valueOf(left.get(index)).equals(String.valueOf(right.get(index)))) {
                differences.add(
                        "index "
                                + index
                                + " expected="
                                + left.get(index)
                                + " actual="
                                + right.get(index));
            }
        }
        if (left.size() != right.size() && differences.size() < 20) {
            differences.add("count expected=" + left.size() + " actual=" + right.size());
        }
        EquivalenceStatus status =
                left.equals(right) ? EquivalenceStatus.MATCH : EquivalenceStatus.MISMATCH;
        return new EquivalenceCheck(
                category,
                status,
                expectedFingerprint,
                actualFingerprint,
                left.size(),
                right.size(),
                blocking,
                differences);
    }

    public static EquivalenceCheck missing(String category, boolean blocking, String detail) {
        List<String> differences = detail == null ? List.of() : List.of(detail);
        return new EquivalenceCheck(
                category,
                EquivalenceStatus.MISSING_FROM_OLD_FORMAT,
                null,
                null,
                0,
                0,
                blocking,
                differences);
    }

    public static EquivalenceCheck unsupported(String category, boolean blocking, String detail) {
        List<String> differences = detail == null ? List.of() : List.of(detail);
        return new EquivalenceCheck(
                category, EquivalenceStatus.UNSUPPORTED, null, null, 0, 0, blocking, differences);
    }

    /**
     * Keeps a comparison's fingerprints and bounded diff while explicitly declaring that the state
     * cannot be restored safely. This is used for observable-only ledgers: identical values are not
     * silently promoted to MATCH when the implementation intentionally did not restore the
     * underlying Vanilla object graph.
     */
    public static EquivalenceCheck unsupportedComparison(
            String category,
            List<String> expected,
            List<String> actual,
            boolean blocking,
            String detail) {
        EquivalenceCheck check = compare(category, expected, actual, blocking);
        check.status = EquivalenceStatus.UNSUPPORTED;
        if (detail != null && !detail.isBlank()) check.differences.add(0, detail);
        return check;
    }

    public static EquivalenceCheck notApplicable(String category) {
        return new EquivalenceCheck(
                category, EquivalenceStatus.NOT_APPLICABLE, null, null, 0, 0, false, List.of());
    }

    /** Verifies that the diagnostic fingerprints embedded in a v6 capsule are self-consistent. */
    public static EquivalenceCheck checkpointIntegrity(RuntimeCapsule capsule) {
        if (capsule == null || capsule.expectedFingerprints == null) {
            return missing(
                    EquivalenceCategory.CHECKPOINT_INTEGRITY,
                    true,
                    "Expected fingerprint map is missing");
        }
        List<String> categories =
                new ArrayList<>(
                        List.of(
                                EquivalenceCategory.CHUNK_DOMAIN,
                                EquivalenceCategory.BLOCK_EVENTS,
                                EquivalenceCategory.SCHEDULED_TICKS,
                                EquivalenceCategory.MOVING_PISTONS,
                                EquivalenceCategory.BLOCK_ENTITY_TICKERS,
                                EquivalenceCategory.ENTITIES,
                                EquivalenceCategory.REDSTONE_RUNTIME,
                                EquivalenceCategory.WORLD_RUNTIME,
                                EquivalenceCategory.WORLD_RNG,
                                EquivalenceCategory.CARPET,
                                EquivalenceCategory.TICKET_LEDGER));
        if (capsule.checkpointPlayersCaptured) categories.add(EquivalenceCategory.PLAYERS);
        List<String> differences = new ArrayList<>();
        for (String category : categories) {
            String recorded = capsule.expectedFingerprints.get(category);
            if (recorded == null || recorded.isBlank()) {
                differences.add("missing fingerprint " + category);
                continue;
            }
            String computed =
                    switch (category) {
                        case EquivalenceCategory.CHUNK_DOMAIN ->
                                RuntimeFingerprint.hashLines(
                                        RuntimeFingerprint.chunkDomainLines(capsule.chunks));
                        case EquivalenceCategory.BLOCK_EVENTS ->
                                RuntimeFingerprint.hashLines(
                                        RuntimeFingerprint.blockEventLines(capsule.blockEvents));
                        case EquivalenceCategory.SCHEDULED_TICKS ->
                                RuntimeFingerprint.hashLines(
                                        RuntimeFingerprint.scheduledTickLines(
                                                capsule.scheduledTicks));
                        case EquivalenceCategory.MOVING_PISTONS ->
                                RuntimeFingerprint.hashLines(
                                        RuntimeFingerprint.movingPistonLines(
                                                capsule.movingPistonTickOrder));
                        case EquivalenceCategory.BLOCK_ENTITY_TICKERS ->
                                RuntimeFingerprint.hashLines(
                                        RuntimeFingerprint.tickerLines(
                                                capsule.blockEntityTickerOrder));
                        case EquivalenceCategory.ENTITIES ->
                                RuntimeFingerprint.hashLines(
                                        RuntimeFingerprint.entityLines(capsule.entityOrder));
                        case EquivalenceCategory.REDSTONE_RUNTIME ->
                                RuntimeFingerprint.hashLines(
                                        RuntimeFingerprint.burnoutLines(
                                                capsule.redstoneTorchBurnout));
                        case EquivalenceCategory.WORLD_RUNTIME ->
                                RuntimeFingerprint.hashLines(
                                        RuntimeFingerprint.worldLines(capsule.worlds));
                        case EquivalenceCategory.WORLD_RNG ->
                                RuntimeFingerprint.hashLines(RuntimeFingerprint.worldRngLines());
                        case EquivalenceCategory.CARPET ->
                                RuntimeFingerprint.hashLines(
                                        RuntimeFingerprint.carpetLines(capsule.carpetTickSpeed));
                        case EquivalenceCategory.TICKET_LEDGER ->
                                RuntimeFingerprint.hashLines(
                                        RuntimeFingerprint.ticketLines(capsule.ticketLedger));
                        case EquivalenceCategory.PLAYERS ->
                                RuntimeFingerprint.hashLines(
                                        RuntimeFingerprint.checkpointPlayerLines(
                                                capsule.checkpointPlayers));
                        default -> null;
                    };
            if (!recorded.equals(computed)) {
                differences.add(
                        "fingerprint "
                                + category
                                + " recorded="
                                + shortHash(recorded)
                                + " computed="
                                + shortHash(computed));
            }
        }
        return new EquivalenceCheck(
                EquivalenceCategory.CHECKPOINT_INTEGRITY,
                differences.isEmpty() ? EquivalenceStatus.MATCH : EquivalenceStatus.MISMATCH,
                null,
                null,
                categories.size(),
                categories.size(),
                true,
                differences);
    }
}
