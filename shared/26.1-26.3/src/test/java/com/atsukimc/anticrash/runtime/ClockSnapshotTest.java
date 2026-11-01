package com.atsukimc.anticrash.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.atsukimc.anticrash.util.AtomicFiles;

import net.minecraft.nbt.NbtOps;
import net.minecraft.world.clock.ClockState;

import org.junit.jupiter.api.Test;

class ClockSnapshotTest {
    @Test
    void clockPhaseRateAndPauseSurviveBothCodecs() {
        var nativeState = new ClockState(-31L, 0.375f, 0.25f, true);
        var nativeCopy =
                ClockState.CODEC
                        .parse(
                                NbtOps.INSTANCE,
                                ClockState.CODEC
                                        .encodeStart(NbtOps.INSTANCE, nativeState)
                                        .getOrThrow())
                        .getOrThrow();
        assertEquals(nativeState, nativeCopy);
        var snapshot = ClockSnapshot.from("minecraft:overworld", nativeCopy);
        assertEquals(
                snapshot,
                AtomicFiles.GSON.fromJson(AtomicFiles.GSON.toJson(snapshot), ClockSnapshot.class));
    }

    @Test
    void fractionalPhaseCannotBeHiddenByMatchingWholeTicks() {
        var a = new RuntimeCapsule();
        a.clockStates =
                java.util.List.of(new ClockSnapshot("minecraft:overworld", 31, 0.25f, 1, false));
        var b = new RuntimeCapsule();
        b.clockStates =
                java.util.List.of(new ClockSnapshot("minecraft:overworld", 31, 0.5f, 1, false));
        assertNotEquals(
                RuntimeFingerprint.hashLines(RuntimeFingerprint.worldLines(a)),
                RuntimeFingerprint.hashLines(RuntimeFingerprint.worldLines(b)));
    }

    @Test
    void missingAndInvalidClockStateFailClosed() {
        var old = new RuntimeCapsule();
        old.formatVersion = 7;
        assertTrue(RuntimeCapsuleManager.strictMissingFields(old).contains("WORLD_CLOCKS_V8"));
        var current = new RuntimeCapsule();
        assertThrows(IllegalStateException.class, () -> RuntimeCapsuleManager.validate(current));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ClockSnapshot("minecraft:overworld", 31, 1, 1, false));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ClockSnapshot("minecraft:overworld", 31, 0, Float.NaN, false));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ClockSnapshot("minecraft:overworld", 31, 0, 0, false));
    }
}
