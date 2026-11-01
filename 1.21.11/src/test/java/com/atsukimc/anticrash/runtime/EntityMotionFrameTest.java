package com.atsukimc.anticrash.runtime;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Vec3d;

import org.junit.jupiter.api.Test;

class EntityMotionFrameTest {
    @Test
    void fullAndNullableHistoryRoundTrip() {
        for (var frame :
                new EntityMotionFrame[] {
                    new EntityMotionFrame(
                            new Vec3d(0.25, -0.5, 2), new Vec3d(-330, 120, 122), true),
                    new EntityMotionFrame(Vec3d.ZERO, null, false)
                }) assertEquals(frame, EntityMotionFrame.read(frame.write()));
    }

    @Test
    void invalidHistoryCannotBeRestored() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EntityMotionFrame(new Vec3d(Double.NaN, 0, 0), null, false));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new EntityMotionFrame(
                                Vec3d.ZERO, new Vec3d(0, Double.POSITIVE_INFINITY, 0), true));
        assertThrows(RuntimeException.class, () -> EntityMotionFrame.read(new NbtCompound()));
    }
}
