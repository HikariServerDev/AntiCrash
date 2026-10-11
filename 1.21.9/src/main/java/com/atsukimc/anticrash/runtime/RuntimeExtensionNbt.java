package com.atsukimc.anticrash.runtime;

import net.minecraft.nbt.NbtCompound;

import java.util.function.Consumer;

/**
 * The one place AntiCrash keeps runtime state inside Vanilla chunk and entity data.
 *
 * <p>Vanilla drops some state that changes what the next tick does (a moving piston's real
 * progress, a gateway's cooldown, an entity about to change dimension). It is stored in a single
 * versioned compound beside Vanilla's own keys and never replaces them: Vanilla's value stays the
 * authority for everything Vanilla saves, and a server without AntiCrash ignores the compound.
 * Every serialized field is explicitly documented and intentionally scoped.</p>
 */
public final class RuntimeExtensionNbt {
    public static final String KEY = "AntiCrash";
    public static final int VERSION = 1;
    private static final String VERSION_KEY = "v";

    /**
     * The extension describes something that keeps running: it is written only when data is saved
     * while it stays loaded (a checkpoint, an autosave, the save at shutdown). When Vanilla unloads
     * a chunk or an entity section and loads it again later, losing this state is what Vanilla
     * does in an uninterrupted game, and that must stay as it is. Entity#writeNbt and
     * BlockEntity#writeNbt also feed dimension changes, /data, structure blocks and client
     * packets; none of those get the extension either.
     */
    private static final class Scope {
        boolean entitySave;
        boolean entityUnload;
        boolean loadedChunkSave;
        boolean extensionWritten;
        final java.util.IdentityHashMap<net.minecraft.storage.WriteView, NbtCompound> viewExtensions = new java.util.IdentityHashMap<>();
    }

    private static final ThreadLocal<Scope> SCOPE = ThreadLocal.withInitial(Scope::new);

    private RuntimeExtensionNbt() {
    }

    public static void entitySave(boolean active) {
        Scope scope = SCOPE.get(); scope.entitySave = active;
        if (!active && !scope.loadedChunkSave) scope.viewExtensions.clear();
    }

    public static void entityUnload(boolean active) {
        SCOPE.get().entityUnload = active;
    }

    public static void loadedChunkSave(boolean active) {
        Scope scope = SCOPE.get();
        scope.loadedChunkSave = active;
        if (active) scope.extensionWritten = false;
        if (!active && !scope.entitySave) scope.viewExtensions.clear();
    }

    /** True when the chunk save that just ran on this thread stored an extension somewhere. */
    public static boolean extensionWritten() {
        return SCOPE.get().extensionWritten;
    }

    /** Entities are being written to their chunk and remain in the world afterwards. */
    public static boolean savingLoadedEntities() {
        Scope scope = SCOPE.get();
        return scope.entitySave && !scope.entityUnload;
    }

    /** A chunk that stays loaded is being serialised. */
    public static boolean savingLoadedChunk() {
        return SCOPE.get().loadedChunkSave;
    }

    /** Adds the extension to {@code nbt} when {@code writer} stored anything in it. */
    public static void write(NbtCompound nbt, Consumer<NbtCompound> writer) {
        NbtCompound extension = com.atsukimc.anticrash.runtime.VersionNbt.contains(nbt, KEY, 10) ? nbt.getCompoundOrEmpty(KEY) : new NbtCompound();
        writer.accept(extension);
        extension.remove(VERSION_KEY);
        if (extension.isEmpty()) {
            nbt.remove(KEY);
            return;
        }
        extension.putInt(VERSION_KEY, VERSION);
        nbt.put(KEY, extension);
        Scope scope = SCOPE.get();
        if (scope.loadedChunkSave) scope.extensionWritten = true;
    }

    /** The extension, or null when absent or written by a newer format this build cannot read. */
    public static NbtCompound read(NbtCompound nbt) {
        if (nbt == null || !com.atsukimc.anticrash.runtime.VersionNbt.contains(nbt, KEY, 10)) return null;
        NbtCompound extension = nbt.getCompoundOrEmpty(KEY);
        int version = extension.getInt(VERSION_KEY).orElse(0);
        return version >= 1 && version <= VERSION ? extension : null;
    }

    /** 1.21.6 views preserve registry/error context; do not cast a mod-provided view to NBT. */
    public static void write(net.minecraft.storage.WriteView view, Consumer<NbtCompound> writer) {
        Scope scope = SCOPE.get();
        // WriteView.get creates/replaces the nested compound on every call. Preserve superclass
        // and subclass extension contributions in one bounded save scope, then discard references.
        NbtCompound encoded = scope.entitySave || scope.loadedChunkSave
                ? scope.viewExtensions.computeIfAbsent(view, ignored -> new NbtCompound()) : new NbtCompound();
        write(encoded, writer);
        NbtCompound extension = encoded.getCompound(KEY).orElse(null);
        if (extension == null) { view.remove(KEY); return; }
        view.put(KEY, NbtCompound.CODEC, extension);
    }

    public static NbtCompound read(net.minecraft.storage.ReadView view) {
        return view.read(KEY, NbtCompound.CODEC).map(extension -> {
            int version = extension.getInt(VERSION_KEY).orElse(0);
            return version >= 1 && version <= VERSION ? extension : null;
        }).orElse(null);
    }
}
