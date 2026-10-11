package com.atsukimc.anticrash.runtime;

/** Stable category names written to diagnostics and used by operator tooling. */
public final class EquivalenceCategory {
    public static final String CHUNK_DOMAIN = "CHUNK_DOMAIN";
    public static final String BLOCK_EVENTS = "BLOCK_EVENTS";
    public static final String SCHEDULED_TICKS = "SCHEDULED_TICKS";
    public static final String MOVING_PISTONS = "MOVING_PISTONS";
    public static final String BLOCK_ENTITY_TICKERS = "BLOCK_ENTITY_TICKERS";
    public static final String ENTITIES = "ENTITIES";
    public static final String REDSTONE_RUNTIME = "REDSTONE_RUNTIME";
    public static final String WORLD_RUNTIME = "WORLD_RUNTIME";
    public static final String WORLD_RNG = "WORLD_RNG";
    public static final String TICKET_LEDGER = "TICKET_LEDGER";
    public static final String CARPET = "CARPET";
    public static final String FAKEPLAYERS = "FAKEPLAYERS";
    public static final String PLAYERS = "PLAYERS";
    public static final String CHECKPOINT_INTEGRITY = "CHECKPOINT_INTEGRITY";

    private EquivalenceCategory() {
    }
}
