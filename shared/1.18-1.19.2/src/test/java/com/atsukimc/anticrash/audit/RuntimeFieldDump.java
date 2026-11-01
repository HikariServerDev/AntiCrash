package com.atsukimc.anticrash.audit;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
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
    private RuntimeFieldDump() {
    }

    /** Non-static fields declared by every loadable class under {@code packagePath} assignable to {@code base}. */
    static Map<String, List<String>> declaredFields(Class<?> base, String... packagePaths) throws Exception {
        Map<String, List<String>> result = new TreeMap<>();
        URI jar = base.getProtectionDomain().getCodeSource().getLocation().toURI();
        try (FileSystem fs = FileSystems.newFileSystem(Path.of(jar), (ClassLoader) null)) {
            for (String packagePath : packagePaths) {
                try (Stream<Path> stream = Files.walk(fs.getPath(packagePath))) {
                    for (Path path : stream.filter(p -> p.toString().endsWith(".class")).toList()) {
                        String name = path.toString().replaceFirst("^/", "").replace('/', '.').replaceAll("\\.class$", "");
                        Class<?> type;
                        try {
                            type = Class.forName(name, false, base.getClassLoader());
                        } catch (Throwable unloadable) {
                            continue;
                        }
                        if (!base.isAssignableFrom(type)) continue;
                        List<String> fields = new ArrayList<>();
                        for (Field field : type.getDeclaredFields()) {
                            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                            fields.add(field.getName());
                        }
                        result.put(name, fields);
                    }
                }
            }
        }
        return result;
    }
}
