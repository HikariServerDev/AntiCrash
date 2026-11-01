package com.atsukimc.anticrash.consistency;

/** Optional SPI; AntiCrash never assumes a MySQL schema or mutates an external database. */
public interface ExternalPlayerDataBridge {
    default void onCheckpoint(String checkpointId) { }
    default void onTransaction(TransactionRecord transaction) { }
    default void onRecovery(String checkpointId) { }
}
