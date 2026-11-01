package com.atsukimc.anticrash.audit;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/** Development helper: lists the instance fields of a class family straight from the game jar. */
final class RuntimeFieldDump {
    private RuntimeFieldDump() {}

    /**
     * Non-static fields declared by every loadable class under {@code packagePath} assignable to
     * {@code base}.
     */
    static Map<String, List<String>> declaredFields(Class<?> base, String... packagePaths)
            throws Exception {
        Map<String, List<String>> result = new TreeMap<>();
        URI jar = base.getProtectionDomain().getCodeSource().getLocation().toURI();
        try (FileSystem fs = FileSystems.newFileSystem(Path.of(jar), (ClassLoader) null)) {
            for (String packagePath : packagePaths) {
                try (Stream<Path> stream = Files.walk(fs.getPath(packagePath))) {
                    for (Path path : stream.filter(p -> p.toString().endsWith(".class")).toList()) {
                        String name =
                                path.toString()
                                        .replaceFirst("^/", "")
                                        .replace('/', '.')
                                        .replaceAll("\\.class$", "");
                        // Raw mapped jars require Knot's access transformation before JVM linking.
                        // Read bytecode rather than silently skipping classes on a VerifyError.
                        ClassNode type = new ClassNode();
                        new ClassReader(Files.readAllBytes(path))
                                .accept(
                                        type,
                                        ClassReader.SKIP_CODE
                                                | ClassReader.SKIP_DEBUG
                                                | ClassReader.SKIP_FRAMES);
                        if (!inherits(type, base.getName().replace('.', '/'))) continue;
                        List<String> fields = new ArrayList<>();
                        for (var field : type.fields) {
                            if ((field.access & (Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC)) != 0)
                                continue;
                            fields.add(field.name);
                        }
                        result.put(name, fields);
                    }
                }
            }
        }
        return result;
    }

    private static boolean inherits(ClassNode type, String base) throws Exception {
        if (type.name.equals(base)) return true;
        if (type.superName == null || type.superName.equals("java/lang/Object")) return false;
        try (var input =
                RuntimeFieldDump.class
                        .getClassLoader()
                        .getResourceAsStream(type.superName + ".class")) {
            if (input == null)
                throw new IllegalStateException("Missing superclass: " + type.superName);
            ClassNode parent = new ClassNode();
            new ClassReader(input)
                    .accept(
                            parent,
                            ClassReader.SKIP_CODE
                                    | ClassReader.SKIP_DEBUG
                                    | ClassReader.SKIP_FRAMES);
            return inherits(parent, base);
        }
    }
}
