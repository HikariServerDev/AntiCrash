package com.atsukimc.anticrash.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

class MessageCatalogTest {
    @Test
    void defaultsToJapaneseAndSupportsEnglish() {
        assertEquals("ja", MessageCatalog.normalize(null));
        assertEquals("en", MessageCatalog.normalize("EN"));
        assertTrue(
                MessageCatalog.text("ja", "command.checkpoint.requested")
                        .contains("checkpointを要求"));
        assertTrue(
                MessageCatalog.text("en", "command.checkpoint.requested")
                        .contains("Checkpoint requested"));
        assertTrue(MessageCatalog.text("ja", "checkpoint.shutdown_timeout", 5).contains("5秒以内"));
        assertTrue(
                MessageCatalog.text("en", "checkpoint.shutdown_timeout", 5)
                        .contains("within 5 seconds"));
        assertEquals(
                "ゲーム内の時間が停止しています。\n/ac startで再開できます。",
                MessageCatalog.text("ja", "recovery.join_frozen_notice", "/ac start"));
        assertEquals(
                "In-game time is paused.\nUse /ac start to resume.",
                MessageCatalog.text("en", "recovery.join_frozen_notice", "/ac start"));
        assertEquals(
                "[AntiCrash] ゲーム内の時間を再開しました。",
                MessageCatalog.text("ja", "recovery.player_resumed_notice"));
        assertEquals(
                "[AntiCrash] In-game time has resumed.",
                MessageCatalog.text("en", "recovery.player_resumed_notice"));
    }

    @Test
    void japaneseAndEnglishCatalogsExposeExactlyTheSameKeys() throws Exception {
        Properties japanese = load("/anticrash/messages_ja.properties");
        Properties english = load("/anticrash/messages_en.properties");

        assertEquals(japanese.stringPropertyNames(), english.stringPropertyNames());
    }

    private static Properties load(String resource) throws Exception {
        Properties properties = new Properties();
        try (var stream = MessageCatalogTest.class.getResourceAsStream(resource)) {
            assertTrue(stream != null, "Missing resource " + resource);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        }
        return properties;
    }
}
