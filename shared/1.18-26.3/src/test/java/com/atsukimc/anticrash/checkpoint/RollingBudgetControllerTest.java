package com.atsukimc.anticrash.checkpoint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
