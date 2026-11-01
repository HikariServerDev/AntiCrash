package com.atsukimc.anticrash.logging;

import com.atsukimc.anticrash.config.AntiCrashConfig;
import com.atsukimc.anticrash.i18n.MessageCatalog;
import com.atsukimc.anticrash.util.FileSecurity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

public final class AntiCrashLogger {
    public enum Level {
        OFF,
        IMPORTANT,
        NORMAL,
        VERBOSE,
        TRACE
    }

    private static final int MAX_MESSAGE_CHARS = 64 * 1024;

    private static final Logger LOGGER = LoggerFactory.getLogger("anticrash");
    private final Path logDirectory;
    private final Supplier<String> languageSupplier;
    private AntiCrashConfig.Logging config;
    private final ConsoleRouter consoleRouter = new ConsoleRouter();

    public AntiCrashLogger(Path logDirectory, AntiCrashConfig.Logging config) {
        this(logDirectory, config, () -> MessageCatalog.DEFAULT_LANGUAGE);
    }

    public AntiCrashLogger(
            Path logDirectory, AntiCrashConfig.Logging config, Supplier<String> languageSupplier) {
        this.logDirectory = logDirectory;
        this.config = config;
        this.languageSupplier = Objects.requireNonNull(languageSupplier, "languageSupplier");
    }

    public void update(AntiCrashConfig.Logging newConfig) {
        this.config = newConfig;
    }

    public void important(String message, Object... args) {
        write(Level.IMPORTANT, "IMPORTANT", message, args, false);
    }

    public void normal(String message, Object... args) {
        write(Level.NORMAL, "NORMAL", message, args, false);
    }

    public void fatalKey(String key, Object... args) {
        writeKey(Level.IMPORTANT, "FATAL", key, args, true);
    }

    public void importantKey(String key, Object... args) {
        writeKey(Level.IMPORTANT, "IMPORTANT", key, args, false);
    }

    public void normalKey(String key, Object... args) {
        writeKey(Level.NORMAL, "NORMAL", key, args, false);
    }

    public void verboseKey(String key, Object... args) {
        writeKey(Level.VERBOSE, "VERBOSE", key, args, false);
    }

    public boolean enabled(Level required) {
        Level active;
        try {
            active = Level.valueOf(config.level.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            active = Level.NORMAL;
        }
        return required != Level.OFF && active.ordinal() >= required.ordinal();
    }

    private void write(Level required, String label, String message, Object[] args, boolean fatal) {
        if (!fatal && !enabled(required)) return;
        String rendered = render(message, args);
        // Keep one event on one physical line. Exception text and filesystem paths may be
        // attacker- or operator-controlled; raw CR/LF would forge follow-up log entries.
        rendered = rendered.replace("\r", "\\r").replace("\n", "\\n");
        if (rendered.length() > MAX_MESSAGE_CHARS) {
            rendered = rendered.substring(0, MAX_MESSAGE_CHARS) + " <message truncated>";
        }
        if (config.console || fatal) console(required, label, rendered, fatal);
        if (config.file) {
            try {
                append(label, rendered);
            } catch (IOException exception) {
                LOGGER.warn(
                        MessageCatalog.text(languageSupplier.get(), "logging.file_write_failed"),
                        exception);
            }
        }
    }

    private void console(Level required, String label, String rendered, boolean fatal) {
        if (consoleColor()) {
            org.apache.logging.log4j.Level level =
                    fatal
                            ? org.apache.logging.log4j.Level.ERROR
                            : required == Level.IMPORTANT
                                    ? org.apache.logging.log4j.Level.WARN
                                    : org.apache.logging.log4j.Level.INFO;
            if (consoleRouter.log(
                    level, "[" + label + "] " + rendered, MessageStyle.ansiLine(label, rendered)))
                return;
        }
        if (fatal) LOGGER.error("[{}] {}", label, rendered);
        else if (required == Level.IMPORTANT) LOGGER.warn("[{}] {}", label, rendered);
        else LOGGER.info("[{}] {}", label, rendered);
    }

    /** ON, OFF, or AUTO: colour only when the server's output is a terminal. */
    private boolean consoleColor() {
        String mode =
                config.consoleColor == null ? "AUTO" : config.consoleColor.toUpperCase(Locale.ROOT);
        return mode.equals("ON") || (mode.equals("AUTO") && System.console() != null);
    }

    /**
     * Prints a command reply on the server console, one log line per message line, coloured like
     * every other AntiCrash line. Used instead of Vanilla's plain console feedback.
     */
    public void commandReply(String message, boolean error) {
        if (message == null) return;
        for (String line : message.split("\\R", -1)) {
            String safe =
                    line.length() > MAX_MESSAGE_CHARS ? line.substring(0, MAX_MESSAGE_CHARS) : line;
            if (consoleColor()) {
                if (consoleRouter.log(
                        org.apache.logging.log4j.Level.INFO,
                        safe,
                        MessageStyle.ansiReply(safe, error))) continue;
            }
            LOGGER.info(safe);
        }
    }

    private void writeKey(Level required, String label, String key, Object[] args, boolean fatal) {
        write(
                required,
                label,
                MessageCatalog.text(languageSupplier.get(), key, args),
                new Object[0],
                fatal);
    }

    private synchronized void append(String label, String message) throws IOException {
        Files.createDirectories(logDirectory);
        Path active = logDirectory.resolve("anticrash.log");
        if (Files.exists(active, LinkOption.NOFOLLOW_LINKS)
                && (!Files.isRegularFile(active, LinkOption.NOFOLLOW_LINKS)
                        || Files.isSymbolicLink(active))) {
            throw new IOException("Unsafe AntiCrash log file: " + active);
        }
        if (Files.exists(active)
                && Files.size(active) >= Math.max(1, config.maxFileMb) * 1024L * 1024L)
            rotate(active);
        String line = Instant.now() + " [" + label + "] " + message + System.lineSeparator();
        boolean created = !Files.exists(active, LinkOption.NOFOLLOW_LINKS);
        Files.writeString(
                active,
                line,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
                LinkOption.NOFOLLOW_LINKS);
        if (created) FileSecurity.hardenCreatedFile(active);
    }

    private void rotate(Path active) throws IOException {
        int keep = Math.max(1, config.retainedFiles);
        Path oldest = logDirectory.resolve("anticrash.log." + keep);
        rejectUnsafeRotationTarget(oldest);
        Files.deleteIfExists(oldest);
        for (int index = keep - 1; index >= 1; index--) {
            Path source = logDirectory.resolve("anticrash.log." + index);
            if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) continue;
            rejectUnsafeRotationTarget(source);
            Path target = logDirectory.resolve("anticrash.log." + (index + 1));
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.move(
                active,
                logDirectory.resolve("anticrash.log.1"),
                StandardCopyOption.REPLACE_EXISTING);
    }

    private static void rejectUnsafeRotationTarget(Path path) throws IOException {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)
                && (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                        || Files.isSymbolicLink(path))) {
            throw new IOException("Unsafe AntiCrash rotated log file: " + path);
        }
    }

    private static String render(String message, Object[] args) {
        String result = message == null ? "null" : message;
        if (args != null)
            for (Object arg : args)
                result =
                        result.replaceFirst(
                                "\\{}",
                                java.util.regex.Matcher.quoteReplacement(String.valueOf(arg)));
        return result;
    }
}
