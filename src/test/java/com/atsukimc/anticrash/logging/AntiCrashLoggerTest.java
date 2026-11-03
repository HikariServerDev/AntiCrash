package com.atsukimc.anticrash.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.atsukimc.anticrash.config.AntiCrashConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

class AntiCrashLoggerTest {
    @TempDir Path temp;

    @Test
    void keyMessagesFollowTheConfiguredLanguage() throws Exception {
        AntiCrashConfig config = new AntiCrashConfig();
        config.logging.console = false;
        AtomicReference<String> language = new AtomicReference<>("ja");
        AntiCrashLogger logger =
                new AntiCrashLogger(temp.resolve("logs"), config.logging, language::get);

        logger.normalKey("command.checkpoint.requested");
        language.set("en");
        logger.normalKey("command.checkpoint.requested");

        String output = Files.readString(temp.resolve("logs/anticrash.log"));
        assertTrue(output.contains("checkpointを要求しました"));
        assertTrue(output.contains("Checkpoint requested"));
    }

    @Test
    void rotationKeepsTheNewestConfiguredGenerationsInOrder() throws Exception {
        AntiCrashConfig config = new AntiCrashConfig();
        config.logging.console = false;
        config.logging.maxFileMb = 1;
        config.logging.retainedFiles = 2;
        Path logs = temp.resolve("logs");
        Files.createDirectories(logs);
        Files.writeString(logs.resolve("anticrash.log"), "A".repeat(1_100_000));
        AntiCrashLogger logger = new AntiCrashLogger(logs, config.logging);

        logger.normal("BBB");
        Files.writeString(
                logs.resolve("anticrash.log"),
                "B".repeat(1_100_000),
                java.nio.file.StandardOpenOption.APPEND);
        logger.normal("CCC");

        assertTrue(Files.readString(logs.resolve("anticrash.log")).contains("CCC"));
        assertTrue(Files.readString(logs.resolve("anticrash.log.1")).contains("BBB"));
        assertTrue(Files.readString(logs.resolve("anticrash.log.2")).contains("AAA"));
        assertFalse(Files.exists(logs.resolve("anticrash.log.3")));
    }

    @Test
    void untrustedDetailsCannotForgeAdditionalLogLines() throws Exception {
        AntiCrashConfig config = new AntiCrashConfig();
        config.logging.console = false;
        AntiCrashLogger logger = new AntiCrashLogger(temp.resolve("logs"), config.logging);

        logger.important("failure: {}", "first\n2099-01-01 [NORMAL] forged");

        String output = Files.readString(temp.resolve("logs/anticrash.log"));
        assertTrue(output.contains("first\\n2099-01-01"));
        assertEquals(1, output.lines().count());
    }
}
