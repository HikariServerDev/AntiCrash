package com.atsukimc.anticrash.command;

import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AntiCrashCommandTest {
    @Test
    void capsLongListsBeforeSendingThemToChat() {
        String summary = AntiCrashCommand.summarize(
                IntStream.range(0, 25).boxed().toList());

        assertTrue(summary.contains("0"));
        assertTrue(summary.contains("19"));
        assertFalse(summary.contains("20,"));
        assertTrue(summary.endsWith("… (+5)"));
    }

    @Test
    void sanitizesControlCharactersAndCapsExceptionDetails() {
        String detail = AntiCrashCommand.safeDetail(
                new IllegalArgumentException("first\nforged\rline\u0000" + "x".repeat(700)));

        assertFalse(detail.contains("\n"));
        assertFalse(detail.contains("\r"));
        assertFalse(detail.contains("\u0000"));
        assertTrue(detail.length() <= 514);
        assertTrue(detail.endsWith("…"));
    }
}
