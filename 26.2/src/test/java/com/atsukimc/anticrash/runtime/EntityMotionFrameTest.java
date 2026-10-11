package com.atsukimc.anticrash.runtime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.world.phys.Vec3;
import net.minecraft.nbt.CompoundTag;
class EntityMotionFrameTest {
    @Test void fullAndNullableHistoryRoundTrip() {
        for(var frame:new EntityMotionFrame[]{new EntityMotionFrame(new Vec3(0.25,-0.5,2),new Vec3(-330,120,122),true),
                new EntityMotionFrame(Vec3.ZERO,null,false)})
            assertEquals(frame,EntityMotionFrame.read(frame.write()));
    }
    @Test void invalidHistoryCannotBeRestored() {
        assertThrows(IllegalArgumentException.class,()->new EntityMotionFrame(new Vec3(Double.NaN,0,0),null,false));
        assertThrows(IllegalArgumentException.class,()->new EntityMotionFrame(Vec3.ZERO,new Vec3(0,Double.POSITIVE_INFINITY,0),true));
        assertThrows(RuntimeException.class,()->EntityMotionFrame.read(new CompoundTag()));
    }
}
