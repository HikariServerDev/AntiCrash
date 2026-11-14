package com.atsukimc.anticrash.integration.carpet;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

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
                        MinecraftServer.class,
                        ServerLevel.class,
                        FakePlayerSnapshot.class,
                        GameType.class);

        method.setAccessible(true);

        //? if <=1.20.1 || (>=1.20.3 && <=1.21.11) {
        /*Object[] args =
                (Object[])
                        method.invoke(
                                null,
                                new Class<?>[] {
                                    String.class,
                                    MinecraftServer.class,
                                    Vec3.class,
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
        *///?} else if <=1.20.2 {
        /*Object[] args =
                (Object[])
                        method.invoke(
                                null,
                                new Class<?>[] {
                                    String.class,
                                    MinecraftServer.class,
                                    Vec3.class,
                                    double.class,
                                    double.class,
                                    dimensionKeyType(),
                                    modeType(),
                                    boolean.class,
                                    Runnable.class
                                },
                                null,
                                null,
                                data,
                                null);
        *///?} else {
        Object[] args =
                (Object[])
                        method.invoke(
                                null,
                                new Class<?>[] {
                                    String.class,
                                    MinecraftServer.class,
                                    Vec3.class,
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
        //?}

        assertNotNull(args);

        assertEquals(new Vec3(120, 90, -450), args[2]);

        assertEquals(73.0D, args[3]);
        assertEquals(-21.0D, args[4]);
        assertEquals(true, args[7]);

        //? if 1.20.2 {
        /*assertThrows(IllegalStateException.class, ((Runnable) args[8])::run);
        *///?}

    }

    private static Class<?> dimensionKeyType() throws Exception {

        return ServerLevel.class.getMethod("dimension").getReturnType();
    }

    private static Class<?> modeType() {

        return GameType.class;
    }
}
