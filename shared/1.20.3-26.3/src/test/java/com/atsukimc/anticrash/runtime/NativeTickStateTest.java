package com.atsukimc.anticrash.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import org.junit.jupiter.api.Test;

class NativeTickStateTest {
    @Test
    void preservesRateFreezeAndStepThroughSerialization() {
        NativeTickState state = new NativeTickState();
        state.tickRate = 500;
        state.stepTicks = 2;
        state.frozen = true;
        state.shouldTick = true;
        Gson gson = new Gson();
        NativeTickState restored = gson.fromJson(gson.toJson(state), NativeTickState.class);
        assertEquals(state.lines(), restored.lines());
        assertEquals(2, restored.stepTicks);
        assertEquals(500, restored.tickRate);
    }

    @Test
    void rejectsInvalidSnapshotBeforeApplyingIt() {
        NativeTickState state = new NativeTickState();
        state.tickRate = Float.NaN;
        assertThrows(IllegalArgumentException.class, state::validate);
        state.tickRate = 20;
        state.stepTicks = -1;
        assertThrows(IllegalArgumentException.class, state::validate);
    }

    @Test
    void oldCapsuleDoesNotInventVanillaTickState() {
        RuntimeCapsule old = new Gson().fromJson("{\"formatVersion\":7}", RuntimeCapsule.class);
        assertNull(old.nativeTickState);
    }
}
