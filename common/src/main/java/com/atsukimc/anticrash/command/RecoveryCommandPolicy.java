package com.atsukimc.anticrash.command;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

/** Pure policy used by the command mixin while recovery simulation is frozen. */
public final class RecoveryCommandPolicy {
    private static final ThreadLocal<Integer> INTERNAL_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final Set<String> SAFE_ROOTS = Set.of(
            "help", "list", "locate", "msg", "tell", "w", "say", "seed"
    );

    private RecoveryCommandPolicy() { }

    /** Executes one AntiCrash-owned recovery command without granting a player-facing bypass. */
    public static <T> T runInternal(Supplier<T> action) {
        int previous = INTERNAL_DEPTH.get();
        INTERNAL_DEPTH.set(previous + 1);
        try { return action.get(); }
        finally {
            if (previous == 0) INTERNAL_DEPTH.remove();
            else INTERNAL_DEPTH.set(previous);
        }
    }

    public static boolean isInternalExecution() { return INTERNAL_DEPTH.get() > 0; }

    /** Commands that cannot mutate the point-in-time copy while a checkpoint barrier is active. */
    public static boolean allowedDuringCheckpoint(String command) {
        String normalized = normalize(command);
        return normalized.equals("stop") || normalized.equals("ac")
                || normalized.equals("ac help") || normalized.equals("ac status");
    }

    public static boolean allowed(String command, boolean allowCommands) {
        String normalized = normalize(command);
        if (normalized.isEmpty()) return false;
        String[] words = normalized.split("\\s+");
        String root = words[0];
        // Recovery controls and a deliberate server stop remain available even when ordinary
        // commands are disabled, otherwise the operator could lock themselves out.
        if (root.equals("ac") || root.equals("y") || root.equals("n") || root.equals("stop")) return true;
        if (!allowCommands) return false;
        if (SAFE_ROOTS.contains(root)) return true;
        if (root.equals("time")) return words.length == 3 && words[1].equals("query")
                && Set.of("day", "daytime", "gametime").contains(words[2]);
        if (root.equals("difficulty")) return words.length == 1;
        if (root.equals("gamerule")) return words.length <= 2;
        if (root.equals("whitelist")) return words.length == 2 && words[1].equals("list");
        if (root.equals("data")) return words.length >= 2 && words[1].equals("get");
        if (root.equals("scoreboard")) return scoreboardReadOnly(List.of(words));
        return false;
    }

    public static String normalize(String command) {
        String normalized = command == null ? "" : command.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("/")) normalized = normalized.substring(1).trim();
        return normalized;
    }

    private static boolean scoreboardReadOnly(List<String> words) {
        if (words.size() < 3) return false;
        if (words.get(1).equals("objectives")) return words.get(2).equals("list");
        if (!words.get(1).equals("players")) return false;
        return words.get(2).equals("list") || words.get(2).equals("get");
    }
}
