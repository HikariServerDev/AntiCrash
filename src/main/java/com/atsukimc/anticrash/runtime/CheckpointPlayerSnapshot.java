package com.atsukimc.anticrash.runtime;

/**
 * The small, non-selective Real Player identity captured for the STRICT player-readiness gate.
 *
 * <p>This is intentionally separate from {@code players.json}. The latter is an optional selective
 * restore payload and may be disabled by the operator; STRICT still needs to know which real
 * players were present at the checkpoint when {@code
 * recovery.strict.players.require_checkpoint_players=true}.
 */
public final class CheckpointPlayerSnapshot {
    public int formatVersion = 1;
    public String uuid;
    public String name;
    public String dimension;

    public String canonical() {
        return String.valueOf(uuid) + "|" + String.valueOf(name) + "|" + String.valueOf(dimension);
    }
}
