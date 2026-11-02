package com.atsukimc.anticrash.recovery;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class VersionChunkLifecycleTest {
    @Test
    void scopeIsNestedAndClearedAfterFailure() {
        assertFalse(VersionChunkLifecycle.simulationDisabled());
        assertThrows(
                IllegalStateException.class,
                () ->
                        VersionChunkLifecycle.withoutSimulation(() -> {
                            assertTrue(VersionChunkLifecycle.simulationDisabled());
                            VersionChunkLifecycle.withoutSimulation(
                                    () -> assertTrue(VersionChunkLifecycle.simulationDisabled()));
                            assertTrue(VersionChunkLifecycle.simulationDisabled());
                            throw new IllegalStateException("injected");
                        }));
        assertFalse(VersionChunkLifecycle.simulationDisabled());
    }

    @Test
    void scopeIsThreadLocal() throws InterruptedException {
        VersionChunkLifecycle.withoutSimulation(() -> {
            java.util.concurrent.atomic.AtomicBoolean observed =
                    new java.util.concurrent.atomic.AtomicBoolean(true);
            Thread other =
                    new Thread(() -> observed.set(VersionChunkLifecycle.simulationDisabled()));
            other.start();
            try {
                other.join();
            } catch (InterruptedException failure) {
                throw new RuntimeException(failure);
            }
            assertFalse(observed.get());
            assertTrue(VersionChunkLifecycle.simulationDisabled());
        });
        assertFalse(VersionChunkLifecycle.simulationDisabled());
    }
}
