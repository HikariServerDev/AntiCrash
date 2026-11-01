package com.atsukimc.anticrash.runtime;

import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeExtensionNbtTest {
    @AfterEach
    void reset() {
        RuntimeExtensionNbt.entitySave(false);
        RuntimeExtensionNbt.entityUnload(false);
        RuntimeExtensionNbt.loadedChunkSave(false);
    }

    @Test
    void extensionIsOneVersionedCompoundBesideVanillaKeys() {
        NbtCompound nbt = new NbtCompound();
        nbt.putFloat("progress", 0.5f);
        RuntimeExtensionNbt.write(nbt, extension -> extension.putFloat("Progress", 1.0f));

        assertEquals(0.5f, nbt.getFloat("progress").orElseThrow());
        NbtCompound extension = RuntimeExtensionNbt.read(nbt);
        assertNotNull(extension);
        assertEquals(1.0f, extension.getFloat("Progress").orElseThrow());
        assertEquals(RuntimeExtensionNbt.VERSION, nbt.getCompoundOrEmpty(RuntimeExtensionNbt.KEY).getInt("v").orElseThrow());
    }

    @Test
    void nothingIsAddedWhenThereIsNothingToSave() {
        NbtCompound nbt = new NbtCompound();
        RuntimeExtensionNbt.write(nbt, extension -> { });
        assertFalse(nbt.contains(RuntimeExtensionNbt.KEY));
        assertNull(RuntimeExtensionNbt.read(nbt));

        RuntimeExtensionNbt.write(nbt, extension -> extension.putInt("TeleportCooldown", 7));
        RuntimeExtensionNbt.write(nbt, extension -> extension.remove("TeleportCooldown"));
        assertFalse(nbt.contains(RuntimeExtensionNbt.KEY));
    }

    @Test
    void aNewerFormatIsIgnoredInsteadOfGuessed() {
        NbtCompound nbt = new NbtCompound();
        NbtCompound future = new NbtCompound();
        future.putInt("v", RuntimeExtensionNbt.VERSION + 1);
        future.putFloat("Progress", 1.0f);
        nbt.put(RuntimeExtensionNbt.KEY, future);
        assertNull(RuntimeExtensionNbt.read(nbt));
    }

    @Test
    void entityStateIsWrittenOnlyForEntitiesThatStayLoaded() {
        assertFalse(RuntimeExtensionNbt.savingLoadedEntities());
        RuntimeExtensionNbt.entitySave(true);
        assertTrue(RuntimeExtensionNbt.savingLoadedEntities());
        // Vanilla unloading a section saves it too; that reload must behave as Vanilla's does.
        RuntimeExtensionNbt.entityUnload(true);
        assertFalse(RuntimeExtensionNbt.savingLoadedEntities());
        RuntimeExtensionNbt.entityUnload(false);
        RuntimeExtensionNbt.entitySave(false);
        assertFalse(RuntimeExtensionNbt.savingLoadedEntities());

        assertFalse(RuntimeExtensionNbt.savingLoadedChunk());
        RuntimeExtensionNbt.loadedChunkSave(true);
        assertTrue(RuntimeExtensionNbt.savingLoadedChunk());
    }

    /**
     * Permanent regression test. Entity#copyFrom (a dimension change) is writeData on the old entity
     * followed by readNbt on the new one. An earlier build wrote the pending portal state on every
     * writeData, so the copy arrived "inside a portal" and went straight back. The extension may only
     * be written inside an entity chunk save of entities that stay loaded.
     */
    @Test
    void portalStateIsNeverWrittenDuringADimensionChangeCopy() {
        // Entity#copyFrom, /data get, spawn eggs, structure blocks: no save scope at all.
        assertFalse(RuntimeExtensionNbt.savingLoadedEntities());
        // A chunk being serialised on this thread does not open the entity scope either.
        RuntimeExtensionNbt.loadedChunkSave(true);
        assertFalse(RuntimeExtensionNbt.savingLoadedEntities());
        // The old entity is removed with CHANGED_DIMENSION; a section unloading at that moment
        // is the only save that can overlap, and it is excluded as well.
        RuntimeExtensionNbt.entitySave(true);
        RuntimeExtensionNbt.entityUnload(true);
        assertFalse(RuntimeExtensionNbt.savingLoadedEntities());
    }

    @Test
    void scopeBelongsToTheSavingThreadOnly() throws Exception {
        RuntimeExtensionNbt.entitySave(true);
        boolean[] other = new boolean[1];
        Thread thread = new Thread(() -> other[0] = RuntimeExtensionNbt.savingLoadedEntities());
        thread.start();
        thread.join();
        assertFalse(other[0]);
        assertTrue(RuntimeExtensionNbt.savingLoadedEntities());
    }

    /** Every mixin that adds to a Vanilla writeData must be gated by one of the two save scopes. */
    @Test
    void everyWriteDataInjectionIsGatedByASaveScope() throws Exception {
        java.nio.file.Path mixins = java.nio.file.Path.of("src/main/java/com/atsukimc/anticrash/mixin");
        org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.isDirectory(mixins));
        int injections = 0;
        try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.list(mixins)) {
            for (java.nio.file.Path file : files.toList()) {
                String source = java.nio.file.Files.readString(file);
                int at = 0;
                while ((at = source.indexOf("@Inject(method = \"writeData\"", at)) >= 0) {
                    injections++;
                    int body = source.indexOf('{', at);
                    String head = source.substring(body, Math.min(source.length(), body + 200));
                    assertTrue(head.contains("savingLoadedChunk()) return;")
                                    || head.contains("savingLoadedEntities()) return;"),
                            file.getFileName() + " writes to writeData outside a save scope");
                    at = body;
                }
            }
        }
        // Sculk listener NBT is owned by Vanilla since 1.19, leaving four extension hooks.
        assertTrue(injections >= 4, "writeData injections found: " + injections);
    }
}
