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

        //? if (>=1.18 && <=1.21.11) {
        /*var method =
                CarpetIntegration.class.getDeclaredMethod(
                        "arguments",
                        Class[].class,
                        net.minecraft.server.MinecraftServer.class,
                        net.minecraft.server.level.ServerLevel.class,
                        FakePlayerSnapshot.class,
                        net.minecraft.world.level.GameType.class);
        *///?} else if (>=26.1 && <=26.3) {
        var method =
                CarpetIntegration.class.getDeclaredMethod(
                        "arguments",
                        Class[].class,
                        net.minecraft.server.MinecraftServer.class,
                        net.minecraft.server.level.ServerLevel.class,
                        FakePlayerSnapshot.class,
                        net.minecraft.world.level.GameType.class);
        //?}

        method.setAccessible(true);

        //? if (>=1.18 && <=1.20.1) || (>=1.20.3 && <=1.21.11) {
        /*Object[] args =
                (Object[])
                        method.invoke(
                                null,
                                new Class<?>[] {
                                    String.class,
                                    net.minecraft.server.MinecraftServer.class,
                                    net.minecraft.world.phys.Vec3.class,
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
        *///?}
        //? if 1.20.2 {
        /*Object[] args =
                        (Object[])
                                method.invoke(
                                        null,
                                        new Class<?>[] {
                                            String.class,
                                            net.minecraft.server.MinecraftServer.class,
                                            net.minecraft.world.phys.Vec3.class,
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
*///?} else if (>=26.1 && <=26.3) {
        Object[] args =
                (Object[])
                        method.invoke(
                                null,
                                new Class<?>[] {
                                    String.class,
                                    net.minecraft.server.MinecraftServer.class,
                                    net.minecraft.world.phys.Vec3.class,
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

        //? if (>=1.18 && <=1.21.11) {
        /*assertEquals(new net.minecraft.world.phys.Vec3(120, 90, -450), args[2]);
        *///?} else if (>=26.1 && <=26.3) {
        assertEquals(new net.minecraft.world.phys.Vec3(120, 90, -450), args[2]);
        //?}

        assertEquals(73.0D, args[3]);
        assertEquals(-21.0D, args[4]);
        assertEquals(true, args[7]);

        //? if 1.20.2 {
        /*assertThrows(IllegalStateException.class, ((Runnable) args[8])::run);
*///?}

    }

    private static Class<?> dimensionKeyType() throws Exception {

        //? if (>=1.18 && <=1.21.11) {
        /*return net.minecraft.server.level.ServerLevel.class.getMethod("dimension").getReturnType();
        *///?} else if (>=26.1 && <=26.3) {
        return net.minecraft.server.level.ServerLevel.class.getMethod("dimension").getReturnType();
        //?}

    }

    private static Class<?> modeType() {

        //? if (>=1.18 && <=1.21.11) {
        /*return net.minecraft.world.level.GameType.class;
        *///?} else if (>=26.1 && <=26.3) {
        return net.minecraft.world.level.GameType.class;
        //?}

    }
}
