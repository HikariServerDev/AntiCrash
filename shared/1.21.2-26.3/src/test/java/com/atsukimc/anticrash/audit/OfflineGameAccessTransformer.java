package com.atsukimc.anticrash.audit;

import net.fabricmc.loader.impl.transformer.PackageAccessFixer;

import org.objectweb.asm.*;

import java.nio.file.*;
import java.util.zip.*;

/**
 * Test fixture only: use Loader's own development package-access transform. No Mixin application,
 * gameplay alterations, server entrypoint, threads, or writes to the shared Loom game cache.
 */
public final class OfflineGameAccessTransformer {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[1]);
        Files.createDirectories(output.getParent());
        try (ZipFile source = new ZipFile(args[0]);
                ZipOutputStream target = new ZipOutputStream(Files.newOutputStream(output))) {
            var entries = source.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                target.putNextEntry(new ZipEntry(entry.getName()));
                try (var input = source.getInputStream(entry)) {
                    if (entry.getName().endsWith(".class")
                            && entry.getName().startsWith("net/minecraft/")) {
                        ClassReader reader = new ClassReader(input);
                        ClassWriter writer = new ClassWriter(reader, 0);
                        reader.accept(new PackageAccessFixer(Opcodes.ASM9, writer), 0);
                        target.write(writer.toByteArray());
                    } else input.transferTo(target);
                }
                target.closeEntry();
            }
        }
    }
}
