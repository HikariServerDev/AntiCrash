package com.atsukimc.anticrash.runtime;

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.nbt.CompoundTag;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.nbt.CompoundTag;
*///?}

import java.util.function.Consumer;

/**
 * The one place AntiCrash keeps runtime state inside Vanilla chunk and entity data.
 *
 * <p>Vanilla drops some state that changes what the next tick does (a moving piston's real
 * progress, a gateway's cooldown, an entity about to change dimension). It is stored in a single
 * versioned compound beside Vanilla's own keys and never replaces them: Vanilla's value stays the
 * authority for everything Vanilla saves, and a server without AntiCrash ignores the compound.
 * Every serialized field is explicitly documented and intentionally scoped.
 */
public final class RuntimeExtensionNbt {
    public static final String KEY = "AntiCrash";
    public static final int VERSION = 1;
    private static final String VERSION_KEY = "v";

    /**
     * The extension describes something that keeps running: it is written only when data is saved
     * while it stays loaded (a checkpoint, an autosave, the save at shutdown). When Vanilla unloads
     * a chunk or an entity section and loads it again later, losing this state is what Vanilla does
     * in an uninterrupted game, and that must stay as it is. Entity#saveWithoutId and
     * BlockEntity#saveWithoutId also feed dimension changes, /data, structure blocks and client packets;
     * none of those get the extension either.
     */
    private static final class Scope {
        boolean entitySave;
        boolean entityUnload;
        boolean loadedChunkSave;
        boolean extensionWritten;

        //? if (>=1.21.6 && <=1.21.11) {
        /*final java.util.IdentityHashMap<net.minecraft.world.level.storage.ValueOutput, CompoundTag>
                        viewExtensions = new java.util.IdentityHashMap<>();
*///?}
        //? if (>=26.1 && <=26.3) {
        /*final java.util.IdentityHashMap<net.minecraft.world.level.storage.ValueOutput, CompoundTag>
                        viewExtensions = new java.util.IdentityHashMap<>();
*///?}

    }

    private static final ThreadLocal<Scope> SCOPE = ThreadLocal.withInitial(Scope::new);

    private RuntimeExtensionNbt() {}

    public static void entitySave(boolean active) {

        //? if (>=1.18 && <=1.21.5) {
        SCOPE.get().entitySave = active;
        //?}
        //? if (>=1.21.6 && <=26.3) {
        /*Scope scope = SCOPE.get();
*///?}

        //? if (>=1.21.6 && <=26.3) {
        /*scope.entitySave = active;
*///?}

        //? if (>=1.21.6 && <=26.3) {
        /*if (!active && !scope.loadedChunkSave) scope.viewExtensions.clear();
*///?}

    }

    public static void entityUnload(boolean active) {
        SCOPE.get().entityUnload = active;
    }

    public static void loadedChunkSave(boolean active) {
        Scope scope = SCOPE.get();
        scope.loadedChunkSave = active;
        if (active) scope.extensionWritten = false;

        //? if (>=1.21.6 && <=26.3) {
        /*if (!active && !scope.entitySave) scope.viewExtensions.clear();
*///?}

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

    //? if (>=1.18 && <=1.21.11) {
    public static void write(CompoundTag nbt, Consumer<CompoundTag> writer)
                //?}
                //? if (>=26.1 && <=26.3) {
                /*public static void write(CompoundTag nbt, Consumer<CompoundTag> writer)
*///?}
            {

        //? if (>=1.18 && <=1.21.4) {
        CompoundTag extension = nbt.contains(KEY, 10) ? nbt.getCompound(KEY) : new CompoundTag();
        //?}
        //? if (>=1.21.5 && <=1.21.11) {
        /*CompoundTag extension =
                        com.atsukimc.anticrash.runtime.VersionNbt.contains(nbt, KEY, 10)
                                ? nbt.getCompoundOrEmpty(KEY)
                                : new CompoundTag();
*///?}
        //? if (>=26.1 && <=26.3) {
        /*CompoundTag extension =
                        com.atsukimc.anticrash.runtime.VersionNbt.contains(nbt, KEY, 10)
                                ? nbt.getCompoundOrEmpty(KEY)
                                : new CompoundTag();
*///?}

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

    //? if (>=1.18 && <=1.21.11) {
    public static CompoundTag read(CompoundTag nbt)
                //?}
                //? if (>=26.1 && <=26.3) {
                /*public static CompoundTag read(CompoundTag nbt)
*///?}
            {

        //? if (>=1.18 && <=1.21.4) {
        if (nbt == null || !nbt.contains(KEY, 10))
            //?}
            //? if (>=1.21.5 && <=26.3) {
            /*if (nbt == null || !com.atsukimc.anticrash.runtime.VersionNbt.contains(nbt, KEY, 10))
*///?}
            return null;

        //? if (>=1.18 && <=1.21.4) {
        CompoundTag extension = nbt.getCompound(KEY);
        //?}
        //? if (>=1.21.5 && <=1.21.11) {
        /*CompoundTag extension = nbt.getCompoundOrEmpty(KEY);
*///?}
        //? if (>=26.1 && <=26.3) {
        /*CompoundTag extension = nbt.getCompoundOrEmpty(KEY);
*///?}

        //? if (>=1.18 && <=1.21.4) {
        int version = extension.getInt(VERSION_KEY);
        //?}
        //? if (>=1.21.5 && <=26.3) {
        /*int version = extension.getInt(VERSION_KEY).orElse(0);
*///?}

        return version >= 1 && version <= VERSION ? extension : null;
    }

    /** 1.21.6 views preserve registry/error context; do not cast a mod-provided view to NBT. */

    //? if (>=1.21.6 && <=1.21.11) {
    /*public static void write(net.minecraft.world.level.storage.ValueOutput view, Consumer<CompoundTag> writer)
*///?}
    //? if (>=26.1 && <=26.3) {
    /*public static void write(
                net.minecraft.world.level.storage.ValueOutput view, Consumer<CompoundTag> writer)
*///?}
    //? if (>=1.21.6 && <=26.3) {
    /*{
            Scope scope = SCOPE.get();
*///?}

    // ValueOutput.get creates/replaces the nested compound on every call. Preserve superclass
    // and subclass extension contributions in one bounded save scope, then discard references.

    //? if (>=1.21.6 && <=1.21.11) {
    /*CompoundTag encoded =
                    scope.entitySave || scope.loadedChunkSave
                            ? scope.viewExtensions.computeIfAbsent(view, ignored -> new CompoundTag())
                            : new CompoundTag();
*///?}
    //? if (>=26.1 && <=26.3) {
    /*CompoundTag encoded =
                    scope.entitySave || scope.loadedChunkSave
                            ? scope.viewExtensions.computeIfAbsent(view, ignored -> new CompoundTag())
                            : new CompoundTag();
*///?}

    //? if (>=1.21.6 && <=26.3) {
    /*write(encoded, writer);
*///?}

    //? if (>=1.21.6 && <=1.21.11) {
    /*CompoundTag extension = encoded.getCompound(KEY).orElse(null);
*///?}
    //? if (>=26.1 && <=26.3) {
    /*CompoundTag extension = encoded.getCompound(KEY).orElse(null);
*///?}

    //? if (>=1.21.6 && <=26.3) {
    /*if (extension == null) {
*///?}
    //? if (>=1.21.6 && <=1.21.11) {
    /*view.discard(KEY);
*///?}
    //? if (>=26.1 && <=26.3) {
    /*view.discard(KEY);
*///?}

    //? if (>=1.21.6 && <=26.3) {
    /*return;
            }
*///?}

    //? if (>=1.21.6 && <=1.21.11) {
    /*view.store(KEY, CompoundTag.CODEC, extension);
*///?}
    //? if (>=26.1 && <=26.3) {
    /*view.store(KEY, CompoundTag.CODEC, extension);
*///?}
    //? if (>=1.21.6 && <=26.3) {

    /*}
*///?}

    //? if (>=1.21.6 && <=1.21.11) {
    /*public static CompoundTag read(net.minecraft.world.level.storage.ValueInput view)
*///?}
    //? if (>=26.1 && <=26.3) {
    /*public static CompoundTag read(net.minecraft.world.level.storage.ValueInput view)
*///?}
    //? if (>=1.21.6 && <=26.3) {
    /*{
*///?}
    //? if (>=1.21.6 && <=1.21.11) {
    /*return view.read(KEY, CompoundTag.CODEC)
                    .map(extension ->
*///?}
    //? if (>=26.1 && <=26.3) {
    /*return view.read(KEY, CompoundTag.CODEC)
                    .map(extension ->
*///?}
    //? if (>=1.21.6 && <=26.3) {
    /*{
                        int version = extension.getInt(VERSION_KEY).orElse(0);
*///?}

    //? if (>=1.21.6 && <=26.3) {
    /*return version >= 1 && version <= VERSION ? extension : null;
                    })
                    .orElse(null);
        }
*///?}

}
