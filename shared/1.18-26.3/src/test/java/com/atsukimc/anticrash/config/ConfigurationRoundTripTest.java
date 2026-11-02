package com.atsukimc.anticrash.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

class ConfigurationRoundTripTest {
    @TempDir Path temp;

    @TestFactory
    Stream<DynamicTest> everyUserSettingSurvivesSaveLoadAndReload() {
        Map<String, String> defaults = entries(new AntiCrashConfig().toToml());
        assertEquals(
                156, defaults.size(), "Update explicit coverage when configuration schema changes");
        return defaults.entrySet().stream()
                .map(entry ->
                        DynamicTest.dynamicTest(
                                entry.getKey(),
                                () -> {
                                    String key = entry.getKey();
                                    String value = alternative(key, entry.getValue());
                                    Path file = temp.resolve(key + ".toml");
                                    AntiCrashConfig written = new AntiCrashConfig();
                                    written.apply(Map.of(key, value), warning -> {});
                                    written.validate(warning -> {});
                                    assertEquals(
                                            value,
                                            entries(written.toToml()).get(key),
                                            "valid nondefault fixture: " + key);
                                    written.save(file);
                                    AntiCrashConfig loaded =
                                            AntiCrashConfig.load(file, warning -> {});
                                    assertTrue(loaded.loadedSafely());
                                    assertEquals(
                                            entries(written.toToml()),
                                            entries(loaded.toToml()),
                                            "save/load: " + key);
                                    AntiCrashConfig reloaded = new AntiCrashConfig();
                                    reloaded.reloadFrom(file, warning -> {});
                                    assertEquals(
                                            entries(written.toToml()),
                                            entries(reloaded.toToml()),
                                            "reload/copy: " + key);
                                }));
    }

    private static String alternative(String key, String old) {
        if (key.equals("config_version"))
            return "1"; // fixed schema marker, deliberately not a user option
        if (old.equals("true")) return "false";
        if (old.equals("false")) return "true";
        if (!old.matches("-?[0-9]+(\\.[0-9]+)?"))
            return switch (key) {
                case "language" -> "en";
                case "checkpoint.durability.mode" -> "BALANCED";
                case "checkpoint.engine.mode" -> "PORTABLE_GENERATION";
                case "checkpoint.engine.fallback" -> "LEGACY_SAFE";
                case "snapshot.backend" -> "PORTABLE_GENERATION";
                case "recovery.policy" -> "ASK";
                case "recovery.fidelity" -> "STRICT";
                case "recovery.start_command" -> "/ac resume";
                case "recovery.ask_default_action" -> "CONTINUE";
                case "recovery.preload_mode" -> "ENTITY_TICKING";
                case "consistency.mode" -> "JOURNAL";
                case "logging.level" -> "VERBOSE";
                case "logging.console_color" -> "OFF";
                default -> throw new AssertionError("No nondefault fixture for " + key);
            };
        if (key.equals("recovery.max_preload_chunks")) return "99999";
        if (key.endsWith("permission_level")) return old.equals("4") ? "3" : "1";
        return old.contains(".")
                ? Double.toString(Double.parseDouble(old) + 0.25)
                : Long.toString(Long.parseLong(old) + 1);
    }

    private static Map<String, String> entries(String toml) {
        Map<String, String> result = new LinkedHashMap<>();
        String section = "";
        for (String raw : toml.split("\n")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("[")) {
                section = line.substring(1, line.length() - 1) + ".";
                continue;
            }
            int equals = line.indexOf('=');
            String value = line.substring(equals + 1).strip();
            if (value.startsWith("\"")) value = value.substring(1, value.length() - 1);
            result.put(section + line.substring(0, equals).strip(), value);
        }
        return result;
    }
}
