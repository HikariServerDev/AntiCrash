package com.atsukimc.anticrash.integration.carpet;

import com.atsukimc.anticrash.config.AntiCrashConfig.FakeSnapshot;

/** Names the repeated snapshot requirements without changing flags or serialization. */
public final class FakePlayerSnapshotOptions {
    private FakePlayerSnapshotOptions() {}

    public static boolean actionsRequired(FakeSnapshot options) {
        return options.actions
                || options.actionInternalTiming
                || options.itemUseCooldown
                || options.blockBreakProgress;
    }

    public static boolean inventoryRequired(FakeSnapshot options) {
        return options.inventory || options.selectedSlot;
    }
}
