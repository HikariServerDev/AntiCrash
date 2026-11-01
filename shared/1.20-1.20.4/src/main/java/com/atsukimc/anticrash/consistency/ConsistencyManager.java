package com.atsukimc.anticrash.consistency;

import com.atsukimc.anticrash.config.AntiCrashConfig;
import com.atsukimc.anticrash.core.InternalStateManager;
import com.atsukimc.anticrash.logging.AntiCrashLogger;
import com.atsukimc.anticrash.player.NbtCodec;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public final class ConsistencyManager implements AutoCloseable {
    private final AntiCrashConfig config;
    private final AntiCrashLogger logger;
    private final TransactionJournal journal;
    private final CopyOnWriteArrayList<ExternalPlayerDataBridge> bridges =
            new CopyOnWriteArrayList<>();
    private volatile String committedCheckpointId = "none";
    private volatile String transactionCheckpointId = "none";
    private volatile String transactionFallbackCheckpointId;
    private final AtomicLong warningCount = new AtomicLong();

    public ConsistencyManager(
            AntiCrashConfig config, InternalStateManager internal, AntiCrashLogger logger)
            throws java.io.IOException {
        this.config = config;
        this.logger = logger;
        this.journal = new TransactionJournal(internal);
    }

    public void registerBridge(ExternalPlayerDataBridge bridge) {
        if (bridge != null) bridges.addIfAbsent(bridge);
    }

    public void setCheckpoint(String id) {
        String next = id == null ? "none" : id;
        if (next.equals(committedCheckpointId)) return;
        committedCheckpointId = next;
        for (ExternalPlayerDataBridge bridge : bridges)
            safe(() -> bridge.onCheckpoint(committedCheckpointId));
    }

    /**
     * Selects the point-in-time snapshot that transactions now follow. Until that snapshot is
     * atomically committed, the previous valid checkpoint remains a conservative fallback.
     */
    public void setTransactionCheckpoint(String primary, String fallback) {
        transactionCheckpointId = primary == null ? committedCheckpointId : primary;
        transactionFallbackCheckpointId = fallback;
    }

    public long warningCount() {
        return warningCount.get();
    }

    public long journalCount() {
        return journal.count();
    }

    public void record(
            String type,
            ServerPlayerEntity player,
            String source,
            String destination,
            BlockPos pos,
            ItemStack stack,
            MinecraftServer server) {
        String mode = config.consistency.mode.toUpperCase(Locale.ROOT);
        if (mode.equals("IGNORE")) return;
        warningCount.incrementAndGet();
        TransactionRecord transaction = new TransactionRecord();
        transaction.transactionId = UUID.randomUUID().toString();
        transaction.checkpointId = transactionCheckpointId;
        transaction.fallbackCheckpointId = transactionFallbackCheckpointId;
        transaction.serverTick = server == null ? 0 : server.getTicks();
        transaction.wallClockEpochMs = System.currentTimeMillis();
        transaction.playerUuid = player == null ? null : player.getUuidAsString();
        transaction.transactionType = type;
        transaction.source = source;
        transaction.destination = destination;
        transaction.dimension =
                player == null ? null : player.getWorld().getRegistryKey().getValue().toString();
        if (pos != null) {
            transaction.x = pos.getX();
            transaction.y = pos.getY();
            transaction.z = pos.getZ();
        }
        if (stack != null) {
            transaction.itemStackId = Registries.ITEM.getId(stack.getItem()).toString();
            transaction.count = stack.getCount();
            try {
                transaction.nbt =
                        NbtCodec.encode(stack.writeNbt(new net.minecraft.nbt.NbtCompound()));
            } catch (Exception ignored) {
                transaction.nbt = "UNAVAILABLE";
            }
        }
        transaction.before = transaction.nbt == null ? "UNAVAILABLE" : transaction.nbt;
        transaction.after = "UNAVAILABLE";
        if (mode.equals("WARN"))
            logger.verboseKey(
                    "consistency.transaction_warning",
                    type,
                    player == null ? "unknown" : player.getName().getString());
        if (mode.equals("WARN") || mode.equals("JOURNAL"))
            try {
                journal.append(
                        mode.equals("WARN")
                                ? TransactionRecord.warningSummary(transaction)
                                : transaction);
            } catch (Exception exception) {
                logger.importantKey("consistency.journal_append_failed", exception.getMessage());
            }
        for (ExternalPlayerDataBridge bridge : bridges)
            safe(() -> bridge.onTransaction(transaction));
    }

    public void onRecovery(String id) {
        if (!config.consistency.mode.equalsIgnoreCase("IGNORE"))
            try {
                TransactionJournal.ScanResult scan =
                        TransactionJournal.scanUnresolved(journal.file().getParent(), id);
                long unresolved = scan.unresolved() + scan.malformed();
                warningCount.set(unresolved);
                if (unresolved > 0)
                    logger.importantKey("consistency.recovery_unresolved", unresolved, id);
                if (scan.malformed() > 0)
                    logger.importantKey("consistency.journal_malformed", scan.malformed());
            } catch (Exception exception) {
                logger.importantKey("consistency.journal_scan_failed", exception.getMessage());
            }
        for (ExternalPlayerDataBridge bridge : bridges) safe(() -> bridge.onRecovery(id));
    }

    public List<java.nio.file.Path> listJournalFiles() throws java.io.IOException {
        return TransactionJournal.list(journal.file().getParent());
    }

    private void safe(Runnable action) {
        try {
            action.run();
        } catch (Exception exception) {
            logger.importantKey("consistency.bridge_failed", exception.getMessage());
        }
    }

    @Override
    public void close() {
        try {
            journal.close();
        } catch (Exception exception) {
            logger.importantKey("consistency.journal_close_failed", exception.getMessage());
        }
    }
}
