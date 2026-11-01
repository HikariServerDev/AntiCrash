package com.atsukimc.anticrash.checkpoint;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RollingBudgetControllerTest {
    @Test
    void adaptiveBudgetNeverLeavesConfiguredBounds() {
        RollingBudgetController controller = new RollingBudgetController(1.0, 3.0, true);
        for (int i = 0; i < 20; i++) {
            controller.observe(10_000_000L);
        }
        assertTrue(controller.currentMs() >= 1.0 && controller.currentMs() <= 3.0);
        for (int i = 0; i < 20; i++) {
            controller.observe(0L);
        }
        assertTrue(controller.currentMs() >= 1.0 && controller.currentMs() <= 3.0);
    }
}
