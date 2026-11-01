package com.atsukimc.anticrash.runtime;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.nbt.CompoundTag;

import org.junit.jupiter.api.Test;

class SulfurRuntimeFrameTest {
    @Test
    void countdownAndPhaseRoundTripWithoutNormalization() {
        for (var frame :
                new SulfurRuntimeFrame[] {
                    new SulfurRuntimeFrame(-1, -1),
                    new SulfurRuntimeFrame(23, 1234567890123L),
                    new SulfurRuntimeFrame(0, -10)
                }) assertEquals(frame, SulfurRuntimeFrame.read(frame.write()));
    }

    @Test
    void absentOrMalformedRequiredStateIsRejected() {
        assertThrows(RuntimeException.class, () -> SulfurRuntimeFrame.read(new CompoundTag()));
        var broken = new SulfurRuntimeFrame(2, 40).write();
        broken.putString("Countdown", "2");
        assertThrows(RuntimeException.class, () -> SulfurRuntimeFrame.read(broken));
    }

    @Test
    void timingChangesAlterCanonicalFingerprint() {
        assertNotEquals(
                new SulfurRuntimeFrame(2, 40).canonical(),
                new SulfurRuntimeFrame(1, 40).canonical());
        assertNotEquals(
                new SulfurRuntimeFrame(2, 40).canonical(),
                new SulfurRuntimeFrame(2, 41).canonical());
    }
}
