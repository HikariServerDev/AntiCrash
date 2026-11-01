package com.atsukimc.anticrash.logging;

import java.util.ArrayList;
import java.util.List;

/**
 * Colours only what needs attention: fatal lines and errors are red, important lines yellow,
 * verbose lines grey, and everything normal stays in the default colour. Headings are bold.
 * Purely presentational: the characters never change.
 */
public final class MessageStyle {
    public enum Kind { PLAIN, PREFIX, HEADER }

    public record Span(String text, Kind kind) {
    }

    private static final String PREFIX = "[AntiCrash]";
    private static final String RESET = "\u001B[0m";

    private MessageStyle() {
    }

    /** Splits one line into the tag and the rest; a heading line is a single span. */
    public static List<Span> spans(String line) {
        List<Span> spans = new ArrayList<>();
        if (line == null || line.isEmpty()) return spans;
        if (line.stripLeading().startsWith("-----")) {
            spans.add(new Span(line, Kind.HEADER));
        } else if (line.startsWith(PREFIX)) {
            spans.add(new Span(PREFIX, Kind.PREFIX));
            if (line.length() > PREFIX.length()) spans.add(new Span(line.substring(PREFIX.length()), Kind.PLAIN));
        } else {
            spans.add(new Span(line, Kind.PLAIN));
        }
        return spans;
    }

    /** One console line: the level tag and the message in that level's colour. */
    public static String ansiLine(String label, String message) {
        String color = ansiLevel(label);
        String line = "[" + label + "] " + message;
        return color.isEmpty() ? line : color + line + RESET;
    }

    /** A command reply on the console: red for an error, a bold heading, otherwise uncoloured. */
    public static String ansiReply(String line, boolean error) {
        if (error) return "\u001B[91m" + line + RESET;
        StringBuilder out = new StringBuilder(line.length() + 16);
        for (Span span : spans(line)) {
            switch (span.kind()) {
                case HEADER -> out.append("\u001B[1m").append(span.text()).append(RESET);
                case PREFIX, PLAIN -> out.append(span.text());
            }
        }
        return out.toString();
    }

    private static String ansiLevel(String label) {
        return switch (label) {
            case "FATAL" -> "\u001B[1;91m";
            case "IMPORTANT" -> "\u001B[93m";
            case "VERBOSE", "TRACE" -> "\u001B[90m";
            default -> "";
        };
    }
}
