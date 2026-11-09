package com.atsukimc.anticrash.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.atsukimc.anticrash.logging.ChatStyle;

import net.minecraft.ChatFormatting;

import org.junit.jupiter.api.Test;

class VersionMessagesTest {
    @Test
    void literalKeepsTheTextOnEveryVersion() {
        assertEquals("hello", VersionMessages.literal("hello").getString());
    }

    @Test
    void chatStyleBuildsTextOnEveryVersion() {
        assertEquals("", ChatStyle.text(null, null, true).getString());
        assertEquals("plain", ChatStyle.text("plain", ChatFormatting.RED, false).getString());
        assertEquals("first\nsecond", ChatStyle.text("first\nsecond", null, true).getString());
    }
}
