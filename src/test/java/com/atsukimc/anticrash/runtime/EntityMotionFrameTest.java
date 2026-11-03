//? if (>=1.21.11 && <=26.3) {
/*package com.atsukimc.anticrash.runtime;

import static org.junit.jupiter.api.Assertions.*;

//? if 1.21.11 {
/^import net.minecraft.nbt.NbtCompound;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.nbt.CompoundTag;
^///?}

//? if 1.21.11 {
/^import net.minecraft.util.math.Vec3d;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.phys.Vec3;
^///?}


import org.junit.jupiter.api.Test;

class EntityMotionFrameTest {
    @Test
    void fullAndNullableHistoryRoundTrip() {

//? if 1.21.11 {
/^for (var frame :
                new EntityMotionFrame[] {
                    new EntityMotionFrame(
                            new Vec3d(0.25, -0.5, 2), new Vec3d(-330, 120, 122), true),
                    new EntityMotionFrame(Vec3d.ZERO, null, false)
                })
^///?}
//? if (>=26.1 && <=26.3) {
/^for (var frame :
                new EntityMotionFrame[] {
                    new EntityMotionFrame(new Vec3(0.25, -0.5, 2), new Vec3(-330, 120, 122), true),
                    new EntityMotionFrame(Vec3.ZERO, null, false)
                })
^///?}
assertEquals(frame, EntityMotionFrame.read(frame.write()));
    }

    @Test
    void invalidHistoryCannotBeRestored() {

//? if 1.21.11 {
/^assertThrows(
                IllegalArgumentException.class,
                () -> new EntityMotionFrame(new Vec3d(Double.NaN, 0, 0), null, false));
^///?}
//? if (>=26.1 && <=26.3) {
/^assertThrows(
                IllegalArgumentException.class,
                () -> new EntityMotionFrame(new Vec3(Double.NaN, 0, 0), null, false));
^///?}


//? if 1.21.11 {
/^assertThrows(
                IllegalArgumentException.class,
                () ->
                        new EntityMotionFrame(
                                Vec3d.ZERO, new Vec3d(0, Double.POSITIVE_INFINITY, 0), true));
^///?}
//? if (>=26.1 && <=26.3) {
/^assertThrows(
                IllegalArgumentException.class,
                () ->
                        new EntityMotionFrame(
                                Vec3.ZERO, new Vec3(0, Double.POSITIVE_INFINITY, 0), true));
^///?}


//? if 1.21.11 {
/^assertThrows(RuntimeException.class, () -> EntityMotionFrame.read(new NbtCompound()));
^///?}
//? if (>=26.1 && <=26.3) {
/^assertThrows(RuntimeException.class, () -> EntityMotionFrame.read(new CompoundTag()));
^///?}

    }
}
*///?}
