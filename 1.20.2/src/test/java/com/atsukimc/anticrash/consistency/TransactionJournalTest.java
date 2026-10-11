package com.atsukimc.anticrash.consistency;

import com.atsukimc.anticrash.core.InternalStateManager;
import com.atsukimc.anticrash.util.AtomicFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionJournalTest {
    @TempDir
    Path temp;

    @Test
    void appendsJsonlRecordsAndListsRunFiles() throws Exception {
        InternalStateManager internal = new InternalStateManager(temp.resolve("game"), message -> { });
        internal.initializeDirectories();
        internal.beginRun(null);
        TransactionJournal journal = new TransactionJournal(internal);
        TransactionRecord record = new TransactionRecord();
        record.transactionId = "tx-1";
        record.transactionType = "BLOCK_PLACE";
        record.reconciliationStatus = "UNRESOLVED";

        journal.append(record);
        journal.close();

        assertEquals(1, Files.readAllLines(journal.file()).size());
        TransactionRecord decoded = AtomicFiles.GSON.fromJson(Files.readString(journal.file()), TransactionRecord.class);
        assertEquals("tx-1", decoded.transactionId);
        assertEquals(1, TransactionJournal.list(internal.journalDir()).size());
        assertTrue(journal.file().getFileName().toString().startsWith("transactions-"));
    }

    @Test
    void scansBothCommitCandidateAndFallbackAcrossRunFiles() throws Exception {
        InternalStateManager internal = new InternalStateManager(temp.resolve("game"), message -> { });
        internal.initializeDirectories();
        internal.beginRun(null);
        TransactionJournal journal = new TransactionJournal(internal);
        TransactionRecord record = new TransactionRecord();
        record.transactionId = "tx-window";
        record.checkpointId = "cp-000002";
        record.fallbackCheckpointId = "cp-000001";
        record.reconciliationStatus = "UNRESOLVED";
        journal.append(record);
        journal.close();
        Files.writeString(internal.journalDir().resolve("transactions-corrupt.jsonl"),
                "{not-json\n");

        TransactionJournal.ScanResult forCandidate =
                TransactionJournal.scanUnresolved(internal.journalDir(), "cp-000002");
        TransactionJournal.ScanResult forFallback =
                TransactionJournal.scanUnresolved(internal.journalDir(), "cp-000001");

        assertEquals(1, forCandidate.unresolved());
        assertEquals(1, forFallback.unresolved());
        assertEquals(1, forCandidate.malformed());
        assertEquals(1, forFallback.malformed());
    }

    @Test
    void wildcardRecordsRemainConservativeUntilTheBaseCheckpointIsVerified() throws Exception {
        InternalStateManager internal = new InternalStateManager(temp.resolve("game"), message -> { });
        internal.initializeDirectories();
        internal.beginRun(null);
        TransactionJournal journal = new TransactionJournal(internal);
        TransactionRecord record = new TransactionRecord();
        record.transactionId = "tx-unverified-base";
        record.checkpointId = "*";
        journal.append(record);
        journal.close();

        assertEquals(1, TransactionJournal.scanUnresolved(
                internal.journalDir(), "cp-999999").unresolved());
    }

    @Test
    void oversizedSingleLineIsDiscardedWithoutLosingFollowingRecords() throws Exception {
        InternalStateManager internal = new InternalStateManager(temp.resolve("game"), message -> { });
        internal.initializeDirectories();
        Path file = internal.journalDir().resolve("transactions-test.jsonl");
        TransactionRecord valid = new TransactionRecord();
        valid.transactionId = "after-large-line";
        valid.checkpointId = "cp-000001";
        Files.writeString(file, "x".repeat(1024 * 1024 + 1) + "\n"
                + AtomicFiles.GSON.toJson(valid).replace("\n", "") + "\n");

        TransactionJournal.ScanResult result =
                TransactionJournal.scanUnresolved(internal.journalDir(), "cp-000001");

        assertEquals(1, result.malformed());
        assertEquals(1, result.unresolved());
    }

    @Test
    void utf8ByteLimitIsEnforcedEvenWhenCharacterCountIsSmaller() throws Exception {
        InternalStateManager internal = new InternalStateManager(temp.resolve("game"), message -> { });
        internal.initializeDirectories();
        Path file = internal.journalDir().resolve("transactions-unicode.jsonl");
        String oversizedId = "あ".repeat(400_000);
        Files.writeString(file, "{\"formatVersion\":1,\"transactionId\":\""
                + oversizedId + "\",\"checkpointId\":\"cp-000001\","
                + "\"reconciliationStatus\":\"UNRESOLVED\"}\n");

        TransactionJournal.ScanResult result =
                TransactionJournal.scanUnresolved(internal.journalDir(), "cp-000001");

        assertEquals(1, result.malformed());
        assertEquals(0, result.unresolved());
    }

    @Test
    void oversizedNbtIsOmittedButTheTransactionRemainsScannable() throws Exception {
        InternalStateManager internal = new InternalStateManager(temp.resolve("game"), message -> { });
        internal.initializeDirectories();
        internal.beginRun(null);
        TransactionJournal journal = new TransactionJournal(internal);
        TransactionRecord record = new TransactionRecord();
        record.transactionId = "large-nbt";
        record.checkpointId = "cp-000001";
        record.nbt = "x".repeat(1024 * 1024);
        record.before = record.nbt;
        record.after = record.nbt;

        journal.append(record);
        journal.close();

        String line = Files.readString(journal.file()).trim();
        assertTrue(line.length() < 1024 * 1024);
        assertTrue(line.contains("\"detailsOmitted\":true"));
        assertEquals("x".repeat(1024 * 1024), record.nbt);
        TransactionJournal.ScanResult scan =
                TransactionJournal.scanUnresolved(internal.journalDir(), "cp-000001");
        assertEquals(1, scan.unresolved());
        assertEquals(0, scan.malformed());
    }

    @Test
    void warnSummaryPreservesRecoveryBoundaryWithoutSensitiveDetails() {
        TransactionRecord detailed = new TransactionRecord();
        detailed.transactionId = "tx-warning";
        detailed.checkpointId = "cp-000002";
        detailed.fallbackCheckpointId = "cp-000001";
        detailed.serverTick = 42;
        detailed.wallClockEpochMs = 1234;
        detailed.transactionType = "ITEM_PICKUP";
        detailed.playerUuid = "private-player";
        detailed.nbt = "private-nbt";

        TransactionRecord summary = TransactionRecord.warningSummary(detailed);

        assertEquals("tx-warning", summary.transactionId);
        assertEquals("cp-000002", summary.checkpointId);
        assertEquals("cp-000001", summary.fallbackCheckpointId);
        assertEquals("ITEM_PICKUP", summary.transactionType);
        assertNull(summary.playerUuid);
        assertNull(summary.nbt);
    }
}
