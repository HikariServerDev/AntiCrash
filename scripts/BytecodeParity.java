import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipFile;

/** Offline comparison of executable classes, excluding debug information and the version bump. */
public final class BytecodeParity {
    private static String oldVersion = "1.0.0";
    private static String newVersion = "1.0.1";

    private static Object versionOnly(Object value) {
        if (!(value instanceof String text)) return value;
        if (text.equals(oldVersion)) return newVersion;
        return text.replace(
                "# AntiCrash " + oldVersion + " - complete",
                "# AntiCrash " + newVersion + " - complete");
    }

    private static byte[] normalized(byte[] bytes) throws Exception {
        var node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG);
        node.fields.forEach(field -> field.value = versionOnly(field.value));
        for (var method : node.methods) {
            for (var instruction : method.instructions) {
                if (instruction instanceof LdcInsnNode constant)
                    constant.cst = versionOnly(constant.cst);
            }
        }
        var writer = new ClassWriter(0);
        node.accept(writer);
        return MessageDigest.getInstance("SHA-256").digest(writer.toByteArray());
    }

    private static Set<String> classes(ZipFile jar) {
        var classes = new TreeSet<String>();
        jar.stream()
                .filter(entry -> entry.getName().endsWith(".class"))
                .forEach(entry -> classes.add(entry.getName()));
        return classes;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2 && args.length != 4)
            throw new IllegalArgumentException(
                    "BytecodeParity <old.jar> <new.jar> [oldVersion newVersion]");
        if (args.length == 4) {
            oldVersion = args[2];
            newVersion = args[3];
        }
        try (var expected = new ZipFile(Path.of(args[0]).toFile());
                var actual = new ZipFile(Path.of(args[1]).toFile())) {
            var names = classes(expected);
            if (!names.equals(classes(actual)))
                throw new AssertionError("Runtime class membership changed");
            for (String name : names) {
                byte[] oldCode =
                        normalized(expected.getInputStream(expected.getEntry(name)).readAllBytes());
                byte[] newCode =
                        normalized(actual.getInputStream(actual.getEntry(name)).readAllBytes());
                if (!Arrays.equals(oldCode, newCode))
                    throw new AssertionError("Executable bytecode differs: " + name);
            }
            System.out.println(
                    "Equivalent executable classes: "
                            + names.size()
                            + " (debug/version-only normalization)");
        }
    }
}
