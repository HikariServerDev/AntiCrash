package com.atsukimc.anticrash.runtime;

import com.atsukimc.anticrash.i18n.MessageCatalog;

/** Preserves stable diagnostic identifiers and field order in both languages. */
public final class RecoveryReportFormatter {
    private RecoveryReportFormatter() {}

    public static String render(
            RecoveryEquivalenceReport report, boolean verbose, String language) {

        StringBuilder output = new StringBuilder();
        output.append(
                        com.atsukimc.anticrash.i18n.MessageCatalog.text(
                                language, "diagnostics.equivalence.anticrash_recovery_equivalence"))
                .append('\n')
                .append(
                        com.atsukimc.anticrash.i18n.MessageCatalog.text(
                                language, "diagnostics.equivalence.checkpoint"))
                .append(report.checkpointId == null ? "-" : report.checkpointId)
                .append('\n')
                .append(
                        com.atsukimc.anticrash.i18n.MessageCatalog.text(
                                language, "diagnostics.equivalence.fidelity"))
                .append(report.fidelity == null ? "-" : report.fidelity)
                .append('\n');
        for (EquivalenceCheck check : report.checks) {
            output.append(categoryLabel(check.category, language))
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
        output.append(
                        com.atsukimc.anticrash.i18n.MessageCatalog.text(
                                language, "diagnostics.equivalence.result"))
                .append(
                        report.ready
                                ? com.atsukimc.anticrash.i18n.MessageCatalog.text(
                                        language, "diagnostics.equivalence.ready")
                                : com.atsukimc.anticrash.i18n.MessageCatalog.text(
                                        language, "diagnostics.equivalence.not_ready"))
                .append('\n');
        if (report.blockingReason != null) {
            output.append(
                            com.atsukimc.anticrash.i18n.MessageCatalog.text(
                                    language, "diagnostics.equivalence.blocking_reason"))
                    .append(report.blockingReason)
                    .append('\n');
        }
        return output.toString().stripTrailing();
    }

    private static String categoryLabel(String category, String language) {
        if (category == null) return null;
        return switch (category) {
            case EquivalenceCategory.CHUNK_DOMAIN ->
                    MessageCatalog.text(language, "diagnostics.category.CHUNK_DOMAIN");
            case EquivalenceCategory.BLOCK_EVENTS ->
                    MessageCatalog.text(language, "diagnostics.category.BLOCK_EVENTS");
            case EquivalenceCategory.SCHEDULED_TICKS ->
                    MessageCatalog.text(language, "diagnostics.category.SCHEDULED_TICKS");
            case EquivalenceCategory.MOVING_PISTONS ->
                    MessageCatalog.text(language, "diagnostics.category.MOVING_PISTONS");
            case EquivalenceCategory.BLOCK_ENTITY_TICKERS ->
                    MessageCatalog.text(language, "diagnostics.category.BLOCK_ENTITY_TICKERS");
            case EquivalenceCategory.ENTITIES ->
                    MessageCatalog.text(language, "diagnostics.category.ENTITIES");
            case EquivalenceCategory.REDSTONE_RUNTIME ->
                    MessageCatalog.text(language, "diagnostics.category.REDSTONE_RUNTIME");
            case EquivalenceCategory.WORLD_RUNTIME ->
                    MessageCatalog.text(language, "diagnostics.category.WORLD_RUNTIME");
            case EquivalenceCategory.WORLD_RNG ->
                    MessageCatalog.text(language, "diagnostics.category.WORLD_RNG");
            case EquivalenceCategory.TICKET_LEDGER ->
                    MessageCatalog.text(language, "diagnostics.category.TICKET_LEDGER");
            case EquivalenceCategory.CARPET ->
                    MessageCatalog.text(language, "diagnostics.category.CARPET");
            case EquivalenceCategory.FAKEPLAYERS ->
                    MessageCatalog.text(language, "diagnostics.category.FAKEPLAYERS");
            case EquivalenceCategory.PLAYERS ->
                    MessageCatalog.text(language, "diagnostics.category.PLAYERS");
            case EquivalenceCategory.CHECKPOINT_INTEGRITY ->
                    MessageCatalog.text(language, "diagnostics.category.CHECKPOINT_INTEGRITY");
            default -> category;
        };
    }

    private static String shortHash(String value) {
        if (value == null) return "-";
        return value.length() <= 16 ? value : value.substring(0, 16);
    }
}
