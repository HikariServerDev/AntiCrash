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
    private static Object versionOnly(Object value) {
        if (!(value instanceof String text)) return value;
        if (text.equals("1.0.0")) return "1.0.1";
        return text.replace("# AntiCrash 1.0.0 - complete", "# AntiCrash 1.0.1 - complete");
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
        if (args.length != 2)
            throw new IllegalArgumentException("BytecodeParity <1.0.0.jar> <1.0.1.jar>");
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
