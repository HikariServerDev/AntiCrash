package com.atsukimc.anticrash.recovery;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PreWorldDecisionInputTest {
    @Test
    void readsOneAnswerWithoutWrappingOrClosingInput() {
        ByteArrayInputStream input = new ByteArrayInputStream("Y\nstop\n".getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        PreWorldDecisionInput.Answer answer = PreWorldDecisionInput.await(
                input, new PrintStream(output), "prompt", 1_000,
                PreWorldDecisionInput.Answer.TIMEOUT, millis -> { });

        assertEquals(PreWorldDecisionInput.Answer.RESTORE, answer);
        // The following line remains in the source stream.  There is no BufferedReader prefetch.
        assertEquals('s', input.read());
        assertEquals("prompt\n", output.toString(StandardCharsets.UTF_8));
    }

    @Test
    void timeoutReturnsTheConfiguredPolicyWithoutLeavingAReaderThread() {
        ByteArrayInputStream input = new ByteArrayInputStream(new byte[0]);
        PreWorldDecisionInput.Answer answer = PreWorldDecisionInput.await(
                input, new PrintStream(new ByteArrayOutputStream()), null, 1,
                PreWorldDecisionInput.Answer.RESTORE, millis -> { });

        assertEquals(PreWorldDecisionInput.Answer.RESTORE, answer);
    }

    @Test
    void parserAcceptsOnlyAStandaloneYOrN() {
        assertEquals(PreWorldDecisionInput.Answer.RESTORE, PreWorldDecisionInput.parse(" y "));
        assertEquals(PreWorldDecisionInput.Answer.CONTINUE, PreWorldDecisionInput.parse("N"));
        assertEquals(null, PreWorldDecisionInput.parse("yes"));
        assertEquals(null, PreWorldDecisionInput.parse("/stop"));
    }
}
