package com.atsukimc.anticrash.logging;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class FailureDetailsTest {
    @Test
    void preservesCauseAndFramesWithoutForgedLogLines() {
        var cause = new IllegalArgumentException("bad\ninput");
        var error = new IllegalStateException("finalize", cause);
        String details = FailureDetails.describe(error);
        assertTrue(details.contains("IllegalStateException: finalize"));
        assertTrue(details.contains("caused by java.lang.IllegalArgumentException"));
        assertTrue(details.contains("bad\\ninput"));
        assertFalse(details.contains("\n"));
    }

    @Test
    void boundsMessagesFramesAndCauseChains() {
        Throwable failure = null;
        for (int i = 0; i < 20; i++) failure = new RuntimeException("\r\n".repeat(10000), failure);
        assertTrue(FailureDetails.describe(failure).length() <= 8192);
        assertEquals(
                "java.lang.OutOfMemoryError (details omitted)",
                FailureDetails.describe(new OutOfMemoryError()));
    }
}
