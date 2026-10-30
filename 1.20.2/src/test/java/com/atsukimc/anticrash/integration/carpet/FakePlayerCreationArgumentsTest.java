package com.atsukimc.anticrash.integration.carpet;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FakePlayerCreationArgumentsTest {
    @Test
    void vectorFactoryConsumesPositionBeforeYawPitch() throws Exception {
        FakePlayerSnapshot data = new FakePlayerSnapshot();
        data.x = 120; data.y = 90; data.z = -450;
        data.yaw = 73; data.pitch = -21; data.flying = true;
        var method = CarpetIntegration.class.getDeclaredMethod("arguments", Class[].class,
                net.minecraft.server.MinecraftServer.class, net.minecraft.server.world.ServerWorld.class,
                FakePlayerSnapshot.class, net.minecraft.world.GameMode.class);
        method.setAccessible(true);
        Object[] args = (Object[]) method.invoke(null, new Class<?>[]{
                net.minecraft.util.math.Vec3d.class, double.class, double.class, boolean.class, Runnable.class
        }, null, null, data, null);
        assertNotNull(args);
        assertEquals(new net.minecraft.util.math.Vec3d(120, 90, -450), args[0]);
        assertEquals(73.0D, args[1]);
        assertEquals(-21.0D, args[2]);
        assertEquals(true, args[3]);
        assertThrows(IllegalStateException.class, ((Runnable)args[4])::run);
    }
}
