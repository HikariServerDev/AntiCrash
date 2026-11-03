package com.atsukimc.anticrash.integration.carpet;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class FakePlayerFactoryContractTest {
    static class Server {}

    static class Vector {}

    static class Dimension {}

    static class Mode {}

    private FakePlayerFactoryContract resolve(Class<?>... signature) {
        return FakePlayerFactoryContract.resolve(
                signature, Server.class, Vector.class, Dimension.class, Mode.class);
    }

    @Test
    void acceptsOnlyVerifiedCompleteLayouts() {
        assertEquals(
                FakePlayerFactoryContract.SCALAR,
                resolve(
                        String.class,
                        Server.class,
                        double.class,
                        double.class,
                        double.class,
                        double.class,
                        double.class,
                        Dimension.class,
                        Mode.class,
                        boolean.class));
        assertEquals(
                FakePlayerFactoryContract.VECTOR,
                resolve(
                        String.class,
                        Server.class,
                        Vector.class,
                        double.class,
                        double.class,
                        Dimension.class,
                        Mode.class,
                        boolean.class));
    }

    @Test
    void rejectsAmbiguousForkArgumentsInsteadOfGuessing() {
        assertNull(resolve(Vector.class, double.class, double.class, boolean.class));
        assertNull(
                resolve(
                        String.class,
                        Server.class,
                        Vector.class,
                        float.class,
                        float.class,
                        Dimension.class,
                        Mode.class,
                        boolean.class));
        assertNull(
                resolve(
                        String.class,
                        Server.class,
                        Vector.class,
                        double.class,
                        double.class,
                        Dimension.class,
                        Mode.class,
                        boolean.class,
                        boolean.class));
    }
}
