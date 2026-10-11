package com.atsukimc.anticrash.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/** Resolves AntiCrash-owned messages without changing Minecraft's own locale. */
public final class MessageCatalog {
    public static final String DEFAULT_LANGUAGE = "ja";

    private static final Map<String, Map<String, String>> CATALOGS = loadCatalogs();

    private MessageCatalog() {
    }

    public static boolean isSupported(String language) {
        return "ja".equalsIgnoreCase(language) || "en".equalsIgnoreCase(language);
    }

    public static String normalize(String language) {
        return "en".equalsIgnoreCase(language) ? "en" : DEFAULT_LANGUAGE;
    }

    public static String text(String language, String key, Object... arguments) {
        String normalized = normalize(language);
        String template = CATALOGS.getOrDefault(normalized, Map.of()).get(key);
        if (template == null) template = CATALOGS.getOrDefault("en", Map.of()).getOrDefault(key, key);
        String result = template;
        for (int index = 0; index < arguments.length; index++) {
            result = result.replace("{" + index + "}", String.valueOf(arguments[index]));
        }
        return result;
    }

    private static Map<String, Map<String, String>> loadCatalogs() {
        Map<String, Map<String, String>> catalogs = new HashMap<>();
        catalogs.put("ja", load("ja"));
        catalogs.put("en", load("en"));
        return Map.copyOf(catalogs);
    }

    private static Map<String, String> load(String language) {
        String resource = "/anticrash/messages_" + language + ".properties";
        try (InputStream stream = MessageCatalog.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IOException("Missing message catalog: " + resource);
            Properties properties = new Properties();
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            Map<String, String> result = new HashMap<>();
            for (String key : properties.stringPropertyNames()) result.put(key, properties.getProperty(key));
            return Map.copyOf(result);
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
