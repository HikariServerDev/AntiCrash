package com.atsukimc.anticrash.integration.carpet;

/**
 * Carpet's global tick-speed state.
 *
 * <p>{@code carpet.helpers.TickSpeed} keeps the tick rate, the freeze flags and
 * {@code process_entities} in plain statics that Carpet never writes to disk, so every restart
 * silently returns to 20 TPS. A checkpoint is supposed to reproduce the simulation exactly, and a
 * device tuned at a non-default tick rate behaves differently at 20, so the value is captured with
 * the rest of the runtime state. Runtime Capsule v2 also records the scalar pause/warp fields. The
 * live player/source/callback references are deliberately not serialized; an active time warp is
 * therefore marked unsupported and blocks STRICT recovery by default.</p>
 */
public final class TickSpeedSnapshot {
    public int formatVersion = 2;
    public float tickrate = 20.0F;
    public float mspt = 50.0F;
    public boolean processEntities = true;
    public boolean paused;
    public boolean deepFrozen;
    public long timeBias;
    public long timeWarpStartTime;
    public long timeWarpScheduledTicks;
    public int playerActiveTimeout;
    public boolean superHot;
    public boolean timeWarpActive;
}
