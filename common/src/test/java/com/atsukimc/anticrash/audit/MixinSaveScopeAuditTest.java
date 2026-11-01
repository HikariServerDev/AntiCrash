package com.atsukimc.anticrash.audit;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

class MixinSaveScopeAuditTest {
    @TempDir Path directory;

    private int audit(String source) throws Exception {
        Path file = directory.resolve("Example.java");
        Files.writeString(file, source);
        String previous = System.getProperty("anticrash.mixinSources");
        System.setProperty("anticrash.mixinSources", file.toString());
        try {
            return MixinSaveScopeAudit.verifyFromProperty(Set.of("writeNbt"));
        } finally {
            if (previous == null) System.clearProperty("anticrash.mixinSources");
            else System.setProperty("anticrash.mixinSources", previous);
        }
    }

    @Test
    void formattedAnnotationsAndBlocksAreRecognized() throws Exception {
        assertEquals(
                1,
                audit(
                        "class Example { @Inject(\n"
                            + " method={\"writeNbt\", \"other\"}, at=@At(\"TAIL\")) void save() {"
                            + " if ((!RuntimeExtensionNbt.savingLoadedChunk())) { return; }"
                            + " write(); } }"));
    }

    @Test
    void missingGuardFails() {
        assertThrows(
                AssertionError.class,
                () ->
                        audit(
                                "class Example { @Inject(method=\"writeNbt\") void save() {"
                                        + " write(); } }"));
    }

    @Test
    void guardInCommentDoesNotCount() {
        assertThrows(
                AssertionError.class,
                () ->
                        audit(
                                "class Example { @Inject(method=\"writeNbt\") void save() { /* if"
                                        + " (!RuntimeExtensionNbt.savingLoadedChunk()) return; */"
                                        + " write(); } }"));
    }

    @Test
    void guardAfterMutationFails() {
        assertThrows(
                AssertionError.class,
                () ->
                        audit(
                                "class Example { @Inject(method=\"writeNbt\") void save() {"
                                        + " write(); if (!RuntimeExtensionNbt.savingLoadedChunk())"
                                        + " return; } }"));
    }

    @Test
    void unrelatedMethodCannotPretendToBeASaveScope() {
        assertThrows(
                AssertionError.class,
                () ->
                        audit(
                                "class Example { @Inject(method=\"writeNbt\") void save() { if"
                                        + " (!Other.savingLoadedChunk()) return; write(); } }"));
    }

    @Test
    void invalidSyntaxFailsInsteadOfSkipping() {
        assertThrows(
                AssertionError.class,
                () -> audit("class Example { @Inject(method=\"writeNbt\") void save( {"));
    }
}
