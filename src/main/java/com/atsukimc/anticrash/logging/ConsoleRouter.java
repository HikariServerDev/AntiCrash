package com.atsukimc.anticrash.logging;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * Sends a coloured line to the console and the same line without colours to every other log4j
 * appender (latest.log, the server GUI, appenders other mods add), so no log file ever contains
 * escape codes. Two non-additive loggers are kept in step with the root logger's appenders, which
 * other mods may change after startup.
 */
final class ConsoleRouter {
    private static final String PLAIN = "anticrash.plain";
    private static final String CONSOLE = "anticrash.console";

    private Map<String, Appender> routed = Map.of();
    private boolean hasConsole;
    private boolean broken;

    /**
     * @return false when routing is unavailable and the caller must log the plain line itself
     */
    synchronized boolean log(Level level, String plain, String coloured) {
        if (broken) return false;
        try {
            LoggerContext context = (LoggerContext) LogManager.getContext(false);
            Configuration configuration = context.getConfiguration();
            Map<String, Appender> current = configuration.getRootLogger().getAppenders();
            if (!sameAppenders(current) || configuration.getLoggers().get(PLAIN) == null) {
                rebuild(context, configuration, current);
            }
            if (!hasConsole) return false;
            LogManager.getLogger(PLAIN).log(level, plain);
            LogManager.getLogger(CONSOLE).log(level, coloured);
            return true;
        } catch (Throwable unavailable) {
            // A different logging backend or a mod that replaced the configuration: stay plain.
            broken = true;
            return false;
        }
    }

    private boolean sameAppenders(Map<String, Appender> current) {
        if (current.size() != routed.size()) return false;
        for (Map.Entry<String, Appender> entry : current.entrySet()) {
            if (routed.get(entry.getKey()) != entry.getValue()) return false;
        }
        return true;
    }

    private void rebuild(
            LoggerContext context, Configuration configuration, Map<String, Appender> current) {
        configuration.removeLogger(PLAIN);
        configuration.removeLogger(CONSOLE);
        LoggerConfig plain = new LoggerConfig(PLAIN, Level.INFO, false);
        LoggerConfig console = new LoggerConfig(CONSOLE, Level.INFO, false);
        boolean foundConsole = false;
        for (Appender appender : current.values()) {
            if (appender instanceof ConsoleAppender) {
                console.addAppender(appender, null, null);
                foundConsole = true;
            } else {
                plain.addAppender(appender, null, null);
            }
        }
        configuration.addLogger(PLAIN, plain);
        configuration.addLogger(CONSOLE, console);
        context.updateLoggers();
        routed = new HashMap<>(current);
        hasConsole = foundConsole;
    }
}
