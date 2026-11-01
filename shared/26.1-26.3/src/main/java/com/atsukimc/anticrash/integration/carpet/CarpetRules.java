package com.atsukimc.anticrash.integration.carpet;

import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The values of Carpet's rules (and of every Carpet extension's rules) at a checkpoint.
 *
 * <p>Carpet writes only "default" rules to {@code carpet.conf}. A rule changed with {@code /carpet
 * <rule> <value>} lives in memory and is gone after any restart, so a recovered world came back
 * with different rules than the checkpoint had: creativeNoClip off again, or a rule a machine
 * depends on back at its default. The values are kept in the Runtime Capsule and set again through
 * Carpet's own rule setter, which runs its validators and tells its clients.
 *
 * <p>Carpet is reached by reflection only; its classes are not remapped, so the names are stable.
 * Everything here returns "nothing" rather than failing when Carpet is absent or different.
 */
public final class CarpetRules {
    public record Outcome(int changed, int failed, int unknown) {}

    private CarpetRules() {}

    /**
     * What has to be set so the rules equal the checkpoint's.
     *
     * @param current the value of every rule that exists now
     * @param defaults the built-in default of every rule that exists now
     * @param saved the checkpoint's value of every rule that was not at its built-in default
     * @return rule name to the value to set, for the rules that differ
     */
    static Map<String, String> changes(
            Map<String, String> current, Map<String, String> defaults, Map<String, String> saved) {
        Map<String, String> changes = new TreeMap<>();
        for (Map.Entry<String, String> rule : current.entrySet()) {
            String wanted =
                    saved.containsKey(rule.getKey())
                            ? saved.get(rule.getKey())
                            : defaults.get(rule.getKey());
            if (wanted != null && !wanted.equals(rule.getValue()))
                changes.put(rule.getKey(), wanted);
        }
        return changes;
    }

    /** Settings managers by identifier: Carpet's own first, then each extension's. */
    private static Map<String, Object> managers() throws ReflectiveOperationException {
        Class<?> server = Class.forName("carpet.CarpetServer");
        Map<Object, Boolean> seen = new IdentityHashMap<>();
        Map<String, Object> managers = new LinkedHashMap<>();
        Object main = server.getField("settingsManager").get(null);
        if (main != null && seen.put(main, Boolean.TRUE) == null)
            managers.put(identifier(main), main);
        Method custom = Class.forName("carpet.CarpetExtension").getMethod("customSettingsManager");
        for (Object extension : (List<?>) server.getField("extensions").get(null)) {
            Object manager = extension == null ? null : custom.invoke(extension);
            if (manager == null || seen.put(manager, Boolean.TRUE) != null) continue;
            String id = identifier(manager);
            while (managers.containsKey(id)) id = id + "+";
            managers.put(id, manager);
        }
        return managers;
    }

    private static String identifier(Object manager) throws ReflectiveOperationException {
        return String.valueOf(manager.getClass().getMethod("getIdentifier").invoke(manager));
    }

    private static Collection<?> rules(Object manager) throws ReflectiveOperationException {
        return (Collection<?>) manager.getClass().getMethod("getRules").invoke(manager);
    }

    private static String name(Object rule) throws ReflectiveOperationException {
        return String.valueOf(rule.getClass().getField("name").get(rule));
    }

    private static String value(Object rule) throws ReflectiveOperationException {
        return String.valueOf(rule.getClass().getMethod("getAsString").invoke(rule));
    }

    private static String builtInDefault(Object rule) throws ReflectiveOperationException {
        return String.valueOf(rule.getClass().getField("defaultAsString").get(rule));
    }

    /**
     * Every rule that is not at its built-in default, per settings manager.
     *
     * @return null when Carpet is not installed or its settings cannot be read
     */
    public static Map<String, Map<String, String>> capture() {
        if (!CarpetIntegration.isInstalled()) return null;
        try {
            Map<String, Map<String, String>> captured = new LinkedHashMap<>();
            for (Map.Entry<String, Object> manager : managers().entrySet()) {
                Map<String, String> values = new TreeMap<>();
                for (Object rule : rules(manager.getValue())) {
                    String value = value(rule);
                    if (!value.equals(builtInDefault(rule))) values.put(name(rule), value);
                }
                captured.put(manager.getKey(), values);
            }
            return captured;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unreadable) {
            return null;
        }
    }

    /** Sets every rule that differs from {@code saved}. */
    public static Outcome restore(Map<String, Map<String, String>> saved, MinecraftServer server) {
        if (saved == null || !CarpetIntegration.isInstalled()) return new Outcome(0, 0, 0);
        int changed = 0, failed = 0, unknown = 0;
        try {
            Map<String, Object> managers = managers();
            for (Map.Entry<String, Map<String, String>> entry : saved.entrySet()) {
                Object manager = managers.get(entry.getKey());
                if (manager == null) {
                    // The extension that owned these rules is not installed any more.
                    unknown += entry.getValue() == null ? 0 : entry.getValue().size();
                    continue;
                }
                Map<String, String> wanted = entry.getValue() == null ? Map.of() : entry.getValue();
                Map<String, Object> byName = new LinkedHashMap<>();
                Map<String, String> current = new LinkedHashMap<>();
                Map<String, String> defaults = new LinkedHashMap<>();
                for (Object rule : rules(manager)) {
                    String name = name(rule);
                    byName.put(name, rule);
                    current.put(name, value(rule));
                    defaults.put(name, builtInDefault(rule));
                }
                for (String name : wanted.keySet()) if (!byName.containsKey(name)) unknown++;
                for (Map.Entry<String, String> change :
                        changes(current, defaults, wanted).entrySet()) {
                    Object rule = byName.get(change.getKey());
                    if (set(rule, server, change.getValue())
                            && change.getValue().equals(value(rule))) changed++;
                    else failed++;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unreadable) {
            failed++;
        }
        return new Outcome(changed, failed, unknown);
    }

    private static boolean set(Object rule, MinecraftServer server, String value) {
        try {
            for (Method method : rule.getClass().getMethods()) {
                if (method.getName().equals("set")
                        && method.getParameterCount() == 2
                        && method.getParameterTypes()[1] == String.class
                        && method.getParameterTypes()[0].isInstance(
                                server.createCommandSourceStack())) {
                    return method.invoke(rule, server.createCommandSourceStack(), value) != null;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException failure) {
            // counted as failed by the caller
        }
        return false;
    }

    /** Number of rule values in a capture, for reporting. */
    public static int count(Map<String, Map<String, String>> rules) {
        int count = 0;
        if (rules != null)
            for (Map<String, String> manager : rules.values())
                count += manager == null ? 0 : manager.size();
        return count;
    }
}
