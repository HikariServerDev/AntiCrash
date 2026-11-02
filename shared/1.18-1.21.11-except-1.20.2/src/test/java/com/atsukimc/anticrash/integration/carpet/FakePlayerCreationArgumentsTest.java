package com.atsukimc.anticrash.integration.carpet;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class FakePlayerCreationArgumentsTest {
    @Test
    void vectorFactoryConsumesPositionBeforeYawPitch() throws Exception {
        FakePlayerSnapshot data = new FakePlayerSnapshot();
        data.x = 120;
        data.y = 90;
        data.z = -450;
        data.yaw = 73;
        data.pitch = -21;
        data.flying = true;
        var method =
                CarpetIntegration.class.getDeclaredMethod(
                        "arguments",
                        Class[].class,
                        net.minecraft.server.MinecraftServer.class,
                        net.minecraft.server.world.ServerWorld.class,
                        FakePlayerSnapshot.class,
                        net.minecraft.world.GameMode.class);
        method.setAccessible(true);
        Object[] args =
                (Object[])
                        method.invoke(
                                null,
                                new Class<?>[] {
                                    String.class,
                                    net.minecraft.server.MinecraftServer.class,
                                    net.minecraft.util.math.Vec3d.class,
                                    double.class,
                                    double.class,
                                    dimensionKeyType(),
                                    modeType(),
                                    boolean.class
                                },
                                null,
                                null,
                                data,
                                null);
        assertNotNull(args);
        assertEquals(new net.minecraft.util.math.Vec3d(120, 90, -450), args[2]);
        assertEquals(73.0D, args[3]);
        assertEquals(-21.0D, args[4]);
        assertEquals(true, args[7]);
    }

    private static Class<?> dimensionKeyType() throws Exception {
        return net.minecraft.server.world.ServerWorld.class
                .getMethod("getRegistryKey")
                .getReturnType();
    }

    private static Class<?> modeType() {
        return net.minecraft.world.GameMode.class;
    }
}
