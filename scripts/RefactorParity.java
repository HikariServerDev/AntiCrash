import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipFile;

/** Audits the bounded 1.0.1 readability refactor; all other executable methods must match. */
public final class RefactorParity {
    static ClassNode read(ZipFile jar, String name) throws Exception {
        var node = new ClassNode();
        try (var input = jar.getInputStream(jar.getEntry(name))) {
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG);
        }
        return node;
    }

    static String hash(ClassNode node) throws Exception {
        var writer = new ClassWriter(0);
        node.accept(writer);
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(writer.toByteArray()));
    }

    static Map<String, String> methods(ClassNode node) throws Exception {
        var result = new TreeMap<String, String>();
        for (MethodNode method : node.methods) {
            var container = new ClassNode();
            container.version = node.version;
            container.access = node.access;
            container.name = node.name;
            container.superName = node.superName;
            container.methods.add(method);
            result.put(method.name + method.desc, hash(container));
        }
        return result;
    }

    static Set<String> classes(ZipFile jar) {
        var names = new TreeSet<String>();
        jar.stream()
                .filter(e -> e.getName().endsWith(".class"))
                .forEach(e -> names.add(e.getName()));
        return names;
    }

    public static void main(String[] args) throws Exception {
        var additions =
                Set.of(
                        "com/atsukimc/anticrash/recovery/RecoveryChecks.class",
                        "com/atsukimc/anticrash/checkpoint/CheckpointEnginePolicy.class",
                        "com/atsukimc/anticrash/checkpoint/CheckpointDiagnostics.class",
                        "com/atsukimc/anticrash/integration/carpet/FakePlayerSnapshotOptions.class");
        var allowed =
                Map.of(
                        "com/atsukimc/anticrash/AntiCrashController.class",
                                Set.of(
                                        "prepareRestoredServer",
                                        "refreshEquivalenceReport",
                                        "strictAllowedUnsupportedCategories",
                                        "validateStrictManifest"),
                        "com/atsukimc/anticrash/checkpoint/CheckpointManager.class",
                                Set.of(
                                        "beginCaptureWithWritesHeld",
                                        "checkpointDiagnostic",
                                        "dimensionDiagnostic",
                                        "fingerprint",
                                        "hasUnsafeLogicalOptions",
                                        "logicalDeltaRequested",
                                        "logicalEngineUsable",
                                        "prepareDirtyChunksWithinRollingBudget",
                                        "runCapture",
                                        "size",
                                        "usePortableGeneration",
                                        "worldRuntimeSupport"),
                        "com/atsukimc/anticrash/integration/carpet/CarpetIntegration.class",
                                Set.of("apply", "captureOne", "validate"));
        int unchanged = 0, changed = 0;
        try (var old = new ZipFile(args[0]);
                var current = new ZipFile(args[1])) {
            var a = classes(old);
            var b = classes(current);
            var added = new TreeSet<>(b);
            added.removeAll(a);
            Set<String> expectedAdditions =
                    old.getEntry("fabric.mod.json") == null ? Set.of() : additions;
            if (!added.equals(expectedAdditions) || !b.containsAll(a))
                throw new AssertionError("Unexpected class membership " + added);
            for (String name : a) {
                var first = read(old, name);
                var second = read(current, name);
                var before = methods(first);
                var after = methods(second);
                first.methods.clear();
                second.methods.clear();
                // javac lists referenced nested types too; those move with extracted methods.
                // Preserve this class's own nested declarations, fields, and annotations.
                first.innerClasses.removeIf(entry -> !first.name.equals(entry.outerName));
                second.innerClasses.removeIf(entry -> !second.name.equals(entry.outerName));
                if (!hash(first).equals(hash(second)))
                    throw new AssertionError("Class/field contract changed: " + name);
                var keys = new TreeSet<>(before.keySet());
                keys.addAll(after.keySet());
                for (String key : keys) {
                    if (Objects.equals(before.get(key), after.get(key))) {
                        unchanged++;
                        continue;
                    }
                    String methodName = key.substring(0, key.indexOf('('));
                    if (!allowed.getOrDefault(name, Set.of()).contains(methodName))
                        throw new AssertionError(
                                "Unexpected executable change: " + name + "#" + key);
                    if (!before.containsKey(key))
                        throw new AssertionError("Unexpected new method: " + name + "#" + key);
                    if (!after.containsKey(key)) {
                        MethodNode removed =
                                read(old, name).methods.stream()
                                        .filter(method -> (method.name + method.desc).equals(key))
                                        .findFirst()
                                        .orElseThrow();
                        if ((removed.access & Opcodes.ACC_PRIVATE) == 0)
                            throw new AssertionError(
                                    "Non-private API removed: " + name + "#" + key);
                    }
                    changed++;
                    System.out.println("REFACTORED_METHOD " + name + "#" + key);
                }
            }
        }
        System.out.println("UNCHANGED_METHODS=" + unchanged + " REVIEWED_METHODS=" + changed);
    }
}
