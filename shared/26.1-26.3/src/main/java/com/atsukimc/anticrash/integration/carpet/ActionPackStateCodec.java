package com.atsukimc.anticrash.integration.carpet;

import com.atsukimc.anticrash.util.ReflectionUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import net.minecraft.core.BlockPos;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reflective, version-tolerant ActionPack capsule. Carpet is optional, so no Carpet class is
 * referenced here. Field names such as next, interval, limit, currentBlock and blockHitDelay are
 * preserved when the installed Carpet exposes them.
 */
public final class ActionPackStateCodec {
    private static final int MAX_DEPTH = 8;

    private ActionPackStateCodec() {}

    public static JsonObject capture(Object actionPack) {
        return capture(actionPack, true, true, true, true);
    }

    public static JsonObject capture(
            Object actionPack,
            boolean actions,
            boolean internalTiming,
            boolean itemUseCooldown,
            boolean blockBreakProgress) {
        if (actionPack == null) return null;
        Selection selection =
                new Selection(actions, internalTiming, itemUseCooldown, blockBreakProgress);
        JsonObject root = new JsonObject();
        root.addProperty("class", actionPack.getClass().getName());
        root.add("fields", captureFields(actionPack, 0, new IdentityHashMap<>(), selection));
        return root;
    }

    public static void restore(Object actionPack, JsonObject snapshot) {
        restore(actionPack, snapshot, true, true, true, true);
    }

    public static void restore(
            Object actionPack,
            JsonObject snapshot,
            boolean actions,
            boolean internalTiming,
            boolean itemUseCooldown,
            boolean blockBreakProgress) {
        if (actionPack == null || snapshot == null || !snapshot.has("fields")) return;
        Selection selection =
                new Selection(actions, internalTiming, itemUseCooldown, blockBreakProgress);
        restoreFields(
                actionPack,
                snapshot.getAsJsonObject("fields"),
                0,
                new IdentityHashMap<>(),
                selection);
    }

    /** Re-captures the selected fields and verifies the restore before the first World tick. */
    public static boolean matches(
            JsonObject expected,
            Object actionPack,
            boolean actions,
            boolean internalTiming,
            boolean itemUseCooldown,
            boolean blockBreakProgress) {
        if (expected == null || actionPack == null) return false;
        Selection selection =
                new Selection(actions, internalTiming, itemUseCooldown, blockBreakProgress);
        JsonObject actual =
                capture(actionPack, actions, internalTiming, itemUseCooldown, blockBreakProgress);
        // fakeplayers.json never contains null members (Gson omits them), while a live capture
        // holds them explicitly, e.g. currentBlock while nothing is mined.
        return comparable(filter(expected, selection)).equals(comparable(actual));
    }

    /**
     * The form two captures are compared in: without the null members the file never holds, and
     * with an enum's class recorded as the enum itself. Earlier builds recorded the anonymous class
     * of a constant with a body (ActionType$1) instead of ActionType.
     */
    private static JsonElement comparable(JsonElement element) {
        JsonElement withoutNulls = FakePlayerSnapshot.withoutNullMembers(element);
        return normalizeEnumClasses(withoutNulls);
    }

    private static JsonElement normalizeEnumClasses(JsonElement element) {
        if (element == null || !(element.isJsonObject() || element.isJsonArray())) return element;
        if (element.isJsonArray()) {
            JsonArray copy = new JsonArray();
            for (JsonElement child : element.getAsJsonArray())
                copy.add(normalizeEnumClasses(child));
            return copy;
        }
        JsonObject source = element.getAsJsonObject();
        boolean isEnum =
                source.has("kind")
                        && source.get("kind").isJsonPrimitive()
                        && "enum".equals(source.get("kind").getAsString());
        JsonObject copy = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            JsonElement value = entry.getValue();
            if (isEnum && entry.getKey().equals("class") && value.isJsonPrimitive()) {
                value = new JsonPrimitive(value.getAsString().replaceFirst("\\$\\d+$", ""));
            }
            copy.add(entry.getKey(), normalizeEnumClasses(value));
        }
        return copy;
    }

    /** Names the first differing path between the snapshot and the live pack, for the log. */
    public static String describeMismatch(
            JsonObject expected,
            Object actionPack,
            boolean actions,
            boolean internalTiming,
            boolean itemUseCooldown,
            boolean blockBreakProgress) {
        if (expected == null || actionPack == null) return "snapshot or live ActionPack is missing";
        Selection selection =
                new Selection(actions, internalTiming, itemUseCooldown, blockBreakProgress);
        JsonElement want = comparable(filter(expected, selection));
        JsonElement have =
                comparable(
                        capture(
                                actionPack,
                                actions,
                                internalTiming,
                                itemUseCooldown,
                                blockBreakProgress));
        String difference = firstDifference("$", want, have);
        return difference == null ? "no difference" : difference;
    }

    private static String firstDifference(String path, JsonElement want, JsonElement have) {
        if (want == null ? have == null : want.equals(have)) return null;
        if (want != null && have != null && want.isJsonObject() && have.isJsonObject()) {
            JsonObject a = want.getAsJsonObject(), b = have.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : a.entrySet()) {
                String found =
                        firstDifference(
                                path + "." + entry.getKey(),
                                entry.getValue(),
                                b.get(entry.getKey()));
                if (found != null) return found;
            }
            for (Map.Entry<String, JsonElement> entry : b.entrySet()) {
                if (!a.has(entry.getKey()))
                    return path
                            + "."
                            + entry.getKey()
                            + ": expected <absent>, live "
                            + clip(entry.getValue());
            }
        }
        if (want != null
                && have != null
                && want.isJsonArray()
                && have.isJsonArray()
                && want.getAsJsonArray().size() == have.getAsJsonArray().size()) {
            for (int i = 0; i < want.getAsJsonArray().size(); i++) {
                String found =
                        firstDifference(
                                path + "[" + i + "]",
                                want.getAsJsonArray().get(i),
                                have.getAsJsonArray().get(i));
                if (found != null) return found;
            }
        }
        return path + ": expected " + clip(want) + ", live " + clip(have);
    }

    private static String clip(JsonElement element) {
        String text = String.valueOf(element);
        return text.length() > 300 ? text.substring(0, 300) + "..." : text;
    }

    /** Validates the exact Carpet 1.4.69 fields selected by the user before live state changes. */
    public static void validateSnapshot(
            JsonObject snapshot,
            boolean actions,
            boolean internalTiming,
            boolean itemUseCooldown,
            boolean blockBreakProgress)
            throws IOException {
        if (snapshot == null
                || !snapshot.has("class")
                || !snapshot.has("fields")
                || !snapshot.get("fields").isJsonObject()) {
            throw new IOException("FakePlayer ActionPack snapshot is incomplete");
        }
        JsonObject fields = snapshot.getAsJsonObject("fields");
        if (actions) {
            JsonObject actionMap =
                    objectWithKind(
                            fields.get("actions"), "map", "FakePlayer actions map is missing");
            JsonArray entries = actionMap.getAsJsonArray("entries");
            if (entries == null) throw new IOException("FakePlayer actions entries are missing");
            if (internalTiming) {
                for (JsonElement element : entries) {
                    if (!element.isJsonObject())
                        throw new IOException("FakePlayer action entry is invalid");
                    JsonObject entry = element.getAsJsonObject();
                    JsonObject action =
                            objectWithKind(
                                    entry.get("value"),
                                    "object",
                                    "FakePlayer action state is invalid");
                    JsonObject actionFields = action.getAsJsonObject("fields");
                    if (actionFields == null
                            || !actionFields.has("next")
                            || !actionFields.has("count")
                            || !actionFields.has("done")) {
                        throw new IOException("FakePlayer action timing phase is incomplete");
                    }
                }
            }
        }
        if (itemUseCooldown && !fields.has("itemUseCooldown") && !fields.has("item_use_cooldown")) {
            throw new IOException("FakePlayer item-use cooldown is missing");
        }
        if (blockBreakProgress) {
            // Early 1.0.0 checkpoints omitted currentBlock when Carpet stored null. Null is
            // unambiguous only while no block is being hit and progress is exactly zero; active
            // mining snapshots still fail closed because their missing position cannot be
            // reconstructed safely.
            if (!fields.has("currentBlock") && legacyIdleBlockProgress(fields)) {
                fields.add("currentBlock", JsonNull.INSTANCE);
            }
            for (String required :
                    List.of(
                            "currentBlock",
                            "blockHitDelay",
                            "isHittingBlock",
                            "curBlockDamageMP")) {
                if (!fields.has(required))
                    throw new IOException("FakePlayer block-break field is missing: " + required);
            }
        }
    }

    private static boolean legacyIdleBlockProgress(JsonObject fields) {
        try {
            return fields.has("isHittingBlock")
                    && fields.has("curBlockDamageMP")
                    && !fields.get("isHittingBlock").getAsBoolean()
                    && Double.compare(fields.get("curBlockDamageMP").getAsDouble(), 0.0D) == 0;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static JsonObject objectWithKind(JsonElement element, String kind, String error)
            throws IOException {
        if (element == null || !element.isJsonObject()) throw new IOException(error);
        JsonObject object = element.getAsJsonObject();
        if (!object.has("kind") || !kind.equals(object.get("kind").getAsString())) {
            throw new IOException(error);
        }
        return object;
    }

    private static JsonElement filter(JsonElement element, Selection selection) {
        if (element == null || element.isJsonNull()) return JsonNull.INSTANCE;
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean()) return new JsonPrimitive(primitive.getAsBoolean());
            if (primitive.isNumber()) return new JsonPrimitive(primitive.getAsNumber());
            return new JsonPrimitive(primitive.getAsString());
        }
        if (element.isJsonArray()) {
            JsonArray copy = new JsonArray();
            for (JsonElement child : element.getAsJsonArray()) copy.add(filter(child, selection));
            return copy;
        }
        JsonObject source = element.getAsJsonObject();
        JsonObject copy = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            if (entry.getKey().equals("fields") && entry.getValue().isJsonObject()) {
                JsonObject fields = new JsonObject();
                for (Map.Entry<String, JsonElement> field :
                        entry.getValue().getAsJsonObject().entrySet()) {
                    if (selection.includes(field.getKey()))
                        fields.add(field.getKey(), filter(field.getValue(), selection));
                }
                copy.add("fields", fields);
            } else {
                copy.add(entry.getKey(), filter(entry.getValue(), selection));
            }
        }
        return copy;
    }

    public static Number findNumber(Object object, String fieldName) {
        Field field = findField(object == null ? null : object.getClass(), fieldName);
        if (field == null) return null;
        try {
            return (Number) field.get(object);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static void setNumber(Object object, String fieldName, Number value) {
        if (object == null || value == null) return;
        Field field = findField(object.getClass(), fieldName);
        if (field == null || Modifier.isFinal(field.getModifiers())) return;
        try {
            Class<?> type = field.getType();
            if (type == byte.class || type == Byte.class) field.set(object, value.byteValue());
            else if (type == short.class || type == Short.class)
                field.set(object, value.shortValue());
            else if (type == int.class || type == Integer.class)
                field.set(object, value.intValue());
            else if (type == long.class || type == Long.class) field.set(object, value.longValue());
            else if (type == float.class || type == Float.class)
                field.set(object, value.floatValue());
            else if (type == double.class || type == Double.class)
                field.set(object, value.doubleValue());
        } catch (Exception ignored) {
        }
    }

    private static JsonObject captureFields(
            Object object, int depth, IdentityHashMap<Object, Boolean> seen, Selection selection) {
        JsonObject fields = new JsonObject();
        if (object == null || depth > MAX_DEPTH || seen.put(object, Boolean.TRUE) != null)
            return fields;
        for (Field field : ReflectionUtil.instanceFields(object.getClass())) {
            if (Modifier.isTransient(field.getModifiers())) continue;
            String name = field.getName();
            if (name.equals("player")
                    || name.equals("server")
                    || name.equals("world")
                    || name.equals("owner")) continue;
            if (!selection.includes(name)) continue;
            try {
                fields.add(name, captureValue(field.get(object), depth + 1, seen, selection));
            } catch (Exception ignored) {
            }
        }
        return fields;
    }

    private static JsonElement captureValue(
            Object value, int depth, IdentityHashMap<Object, Boolean> seen, Selection selection) {
        if (value == null) return JsonNull.INSTANCE;
        if (value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character) return new JsonPrimitive(String.valueOf(value));
        if (value instanceof Enum<?> enumValue) {
            // getDeclaringClass, not getClass: a constant with a body (every Carpet ActionType)
            // is an anonymous subclass such as ActionType$1, which is not an enum class.
            JsonObject result = kind("enum");
            result.addProperty("class", enumValue.getDeclaringClass().getName());
            result.addProperty("name", enumValue.name());
            return result;
        }
        if (value instanceof java.util.UUID uuid) {
            JsonObject result = kind("uuid");
            result.addProperty("value", uuid.toString());
            return result;
        }
        // Carpet 1.4.69 keeps the currently damaged block as a BlockPos. A freshly
        // created FakePlayer has this field set to null, so the generic in-place object
        // path cannot reconstruct it. Preserve it as a small, explicit value type.
        if (value instanceof BlockPos position) {
            JsonObject result = kind("block_pos");
            result.addProperty("x", position.getX());
            result.addProperty("y", position.getY());
            result.addProperty("z", position.getZ());
            return result;
        }
        if (depth > MAX_DEPTH) {
            JsonObject result = kind("string");
            result.addProperty("value", String.valueOf(value));
            return result;
        }
        if (value instanceof Map<?, ?> map) {
            JsonObject result = kind("map");
            JsonArray entries = new JsonArray();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                JsonObject item = new JsonObject();
                item.addProperty("key", String.valueOf(entry.getKey()));
                item.add("keySnapshot", captureValue(entry.getKey(), depth + 1, seen, selection));
                item.add("value", captureValue(entry.getValue(), depth + 1, seen, selection));
                entries.add(item);
            }
            result.add("entries", entries);
            return result;
        }
        if (value instanceof Iterable<?> iterable) {
            JsonObject result = kind("list");
            JsonArray items = new JsonArray();
            int count = 0;
            for (Object item : iterable) {
                if (count++ >= 256) break;
                items.add(captureValue(item, depth + 1, seen, selection));
            }
            result.add("items", items);
            return result;
        }
        if (value.getClass().isArray()) {
            JsonObject result = kind("list");
            JsonArray items = new JsonArray();
            int length = Math.min(Array.getLength(value), 256);
            for (int i = 0; i < length; i++)
                items.add(captureValue(Array.get(value, i), depth + 1, seen, selection));
            result.add("items", items);
            return result;
        }
        JsonObject result = kind("object");
        result.addProperty("class", value.getClass().getName());
        result.add("fields", captureFields(value, depth + 1, seen, selection));
        return result;
    }

    private static void restoreFields(
            Object target,
            JsonObject fields,
            int depth,
            IdentityHashMap<Object, Boolean> seen,
            Selection selection) {
        if (target == null
                || fields == null
                || depth > MAX_DEPTH
                || seen.put(target, Boolean.TRUE) != null) return;
        for (Map.Entry<String, JsonElement> entry : fields.entrySet()) {
            if (!selection.includes(entry.getKey())) continue;
            Field field = findField(target.getClass(), entry.getKey());
            if (field == null) continue;
            try {
                Object current = field.get(target);
                Object converted =
                        convert(entry.getValue(), field.getType(), current, depth + 1, selection);
                // Final nested state is restored in place; the final reference itself is never
                // replaced.
                if (converted != Unavailable.INSTANCE && !Modifier.isFinal(field.getModifiers()))
                    field.set(target, converted);
            } catch (Exception ignored) {
            }
        }
    }

    @SuppressWarnings({
        "unchecked",
        "rawtypes"
    }) // Guarded by isEnum/instanceof Map/List checks below.
    private static Object convert(
            JsonElement element,
            Class<?> targetType,
            Object current,
            int depth,
            Selection selection) {
        if (element == null || element.isJsonNull()) return null;
        if (element.isJsonPrimitive()) {
            String value = element.getAsString();
            try {
                if (targetType == String.class || targetType == Object.class) return value;
                if (targetType == boolean.class || targetType == Boolean.class)
                    return Boolean.parseBoolean(value);
                if (targetType == byte.class || targetType == Byte.class)
                    return Byte.parseByte(value);
                if (targetType == short.class || targetType == Short.class)
                    return Short.parseShort(value);
                if (targetType == int.class || targetType == Integer.class)
                    return Integer.parseInt(value);
                if (targetType == long.class || targetType == Long.class)
                    return Long.parseLong(value);
                if (targetType == float.class || targetType == Float.class)
                    return Float.parseFloat(value);
                if (targetType == double.class || targetType == Double.class)
                    return Double.parseDouble(value);
                if (targetType == char.class || targetType == Character.class)
                    return value.isEmpty() ? 0 : value.charAt(0);
            } catch (NumberFormatException ignored) {
                return Unavailable.INSTANCE;
            }
            return Unavailable.INSTANCE;
        }
        JsonObject object = element.getAsJsonObject();
        String kind = object.has("kind") ? object.get("kind").getAsString() : "";
        if (kind.equals("uuid"))
            try {
                return java.util.UUID.fromString(object.get("value").getAsString());
            } catch (Exception e) {
                return Unavailable.INSTANCE;
            }
        if (kind.equals("enum") && targetType.isEnum())
            try {
                return Enum.valueOf((Class) targetType, object.get("name").getAsString());
            } catch (Exception e) {
                return Unavailable.INSTANCE;
            }
        if (kind.equals("block_pos") && targetType.isAssignableFrom(BlockPos.class)) {
            try {
                return new BlockPos(
                        object.get("x").getAsInt(),
                        object.get("y").getAsInt(),
                        object.get("z").getAsInt());
            } catch (Exception exception) {
                return Unavailable.INSTANCE;
            }
        }
        if (kind.equals("object")) {
            Object objectTarget = current;
            if (objectTarget == null)
                objectTarget =
                        instantiate(
                                object.has("class")
                                        ? object.get("class").getAsString()
                                        : targetType.getName(),
                                targetType,
                                object.getAsJsonObject("fields"));
            if (objectTarget == null) return Unavailable.INSTANCE;
            restoreFields(
                    objectTarget,
                    object.getAsJsonObject("fields"),
                    depth,
                    new IdentityHashMap<>(),
                    selection);
            return objectTarget;
        }
        if (kind.equals("map") && current instanceof Map<?, ?> map) {
            restoreMap(
                    (Map<Object, Object>) map, object.getAsJsonArray("entries"), depth, selection);
            return current;
        }
        if (kind.equals("list") && current instanceof List<?> list) {
            // Existing ActionPack lists are normally fixed objects; update entries in place.
            JsonArray items = object.getAsJsonArray("items");
            int size = Math.min(items.size(), list.size());
            for (int i = 0; i < size; i++) {
                Object old = list.get(i);
                Object value =
                        convert(
                                items.get(i),
                                old == null ? Object.class : old.getClass(),
                                old,
                                depth + 1,
                                selection);
                if (value != Unavailable.INSTANCE) ((List<Object>) list).set(i, value);
            }
            return current;
        }
        return Unavailable.INSTANCE;
    }

    private static void restoreMap(
            Map<Object, Object> target, JsonArray entries, int depth, Selection selection) {
        if (entries == null) return;
        Map<Object, Object> restored = new java.util.LinkedHashMap<>();
        for (JsonElement element : entries) {
            JsonObject entry = element.getAsJsonObject();
            String key = entry.get("key").getAsString();
            Object found = null;
            for (Object candidate : target.keySet())
                if (String.valueOf(candidate).equals(key)) {
                    found = candidate;
                    break;
                }
            if (found == null && entry.has("keySnapshot"))
                found = decodeMapKey(entry.get("keySnapshot"));
            if (found == null) return;
            Object current = target.get(found);
            JsonElement valueSnapshot = entry.get("value");
            // Carpet Action has final limit/interval/offset/isContinuous fields. Mutating an
            // already-present Action in place cannot restore those values, so object-valued map
            // entries are reconstructed and atomically replace the map contents.
            Object conversionTarget =
                    valueSnapshot != null
                                    && valueSnapshot.isJsonObject()
                                    && "object"
                                            .equals(
                                                    valueSnapshot.getAsJsonObject().has("kind")
                                                            ? valueSnapshot
                                                                    .getAsJsonObject()
                                                                    .get("kind")
                                                                    .getAsString()
                                                            : null)
                            ? null
                            : current;
            Object value =
                    convert(
                            valueSnapshot,
                            current == null ? Object.class : current.getClass(),
                            conversionTarget,
                            depth + 1,
                            selection);
            if (value == Unavailable.INSTANCE) return;
            restored.put(found, value);
        }
        try {
            target.clear();
            target.putAll(restored);
        } catch (UnsupportedOperationException ignored) {
        }
    }

    private static Object instantiate(String className, Class<?> fallback, JsonObject fields) {
        try {
            // Compatibility with checkpoints produced before BlockPos had a dedicated
            // representation. Those snapshots contain its inherited x/y/z fields.
            if (fallback != null
                    && fallback.isAssignableFrom(BlockPos.class)
                    && (BlockPos.class.getName().equals(className)
                            || "net.minecraft.class_2338".equals(className))) {
                return new BlockPos(
                        intField(fields, "x", 0),
                        intField(fields, "y", 0),
                        intField(fields, "z", 0));
            }
            if (!allowedReflectiveClass(className)) return null;
            ClassLoader loader =
                    fallback != null && fallback.getClassLoader() != null
                            ? fallback.getClassLoader()
                            : Thread.currentThread().getContextClassLoader();
            Class<?> type = Class.forName(className, false, loader);
            if (fallback != null && fallback != Object.class && !fallback.isAssignableFrom(type))
                return null;
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                Class<?>[] parameters = constructor.getParameterTypes();
                if (parameters.length == 4
                        && parameters[0] == int.class
                        && parameters[1] == int.class
                        && parameters[2] == int.class
                        && parameters[3] == boolean.class) {
                    ReflectionUtil.makeAccessible(constructor);
                    return constructor.newInstance(
                            intField(fields, "limit", 1),
                            intField(fields, "interval", 1),
                            intField(fields, "offset", 0),
                            booleanField(fields, "isContinuous", false));
                }
            }
            Constructor<?> ctor = type.getDeclaredConstructor();
            ReflectionUtil.makeAccessible(ctor);
            return ctor.newInstance();
        } catch (Exception ignored) {
            try {
                if (fallback == null || fallback == Object.class) return null;
                Constructor<?> ctor = fallback.getDeclaredConstructor();
                ReflectionUtil.makeAccessible(ctor);
                return ctor.newInstance();
            } catch (Exception ignoredAgain) {
                return null;
            }
        }
    }

    @SuppressWarnings({
        "unchecked",
        "rawtypes"
    }) // The reflective class is checked with isEnum first.
    private static Object decodeMapKey(JsonElement element) {
        try {
            if (element == null || !element.isJsonObject()) return null;
            JsonObject object = element.getAsJsonObject();
            if (!"enum".equals(object.has("kind") ? object.get("kind").getAsString() : null))
                return null;
            String className = object.get("class").getAsString();
            if (!allowedReflectiveClass(className)) return null;
            Class<?> type = loadClass(className);
            // Snapshots taken before the capture recorded the declaring class name the class of
            // a constant with a body (ActionType$1). Its superclass is the enum.
            if (!type.isEnum() && type.getSuperclass() != null && type.getSuperclass().isEnum())
                type = type.getSuperclass();
            if (!type.isEnum()) return null;
            return Enum.valueOf((Class) type, object.get("name").getAsString());
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Carpet is loaded by the mod class loader, which is not necessarily the thread's. */
    private static Class<?> loadClass(String className) throws ClassNotFoundException {
        try {
            return Class.forName(className, false, ActionPackStateCodec.class.getClassLoader());
        } catch (ClassNotFoundException notHere) {
            return Class.forName(className, false, Thread.currentThread().getContextClassLoader());
        }
    }

    private static int intField(JsonObject fields, String name, int fallback) {
        try {
            return fields.get(name).getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean booleanField(JsonObject fields, String name, boolean fallback) {
        try {
            return fields.get(name).getAsBoolean();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean allowedReflectiveClass(String className) {
        return className != null
                && (className.startsWith("carpet.")
                        || className.startsWith("com.atsukimc.anticrash."));
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass())
            try {
                Field f = c.getDeclaredField(name);
                ReflectionUtil.makeAccessible(f);
                return f;
            } catch (NoSuchFieldException ignored) {
            }
        return null;
    }

    private static JsonObject kind(String value) {
        JsonObject object = new JsonObject();
        object.addProperty("kind", value);
        return object;
    }

    private record Selection(
            boolean actions,
            boolean internalTiming,
            boolean itemUseCooldown,
            boolean blockBreakProgress) {
        private boolean includes(String fieldName) {
            String name = fieldName == null ? "" : fieldName.toLowerCase(java.util.Locale.ROOT);
            if (name.equals("itemusecooldown") || name.equals("item_use_cooldown"))
                return itemUseCooldown;
            if (name.equals("currentblock")
                    || name.equals("current_block")
                    || name.equals("blockhitdelay")
                    || name.equals("block_hit_delay")
                    || name.equals("ishittingblock")
                    || name.equals("is_hitting_block")
                    || name.equals("curblockdamagemp")
                    || name.equals("cur_block_damage_mp")
                    || name.equals("blockbreakingprogress")
                    || name.equals("block_breaking_progress")) {
                return blockBreakProgress;
            }
            if (name.equals("next") || name.equals("count") || name.equals("done")) {
                return internalTiming;
            }
            return actions;
        }
    }

    private enum Unavailable {
        INSTANCE
    }
}
