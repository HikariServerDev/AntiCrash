package com.atsukimc.anticrash.snapshot;

import com.atsukimc.anticrash.config.AntiCrashConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Optional, explicit opt-in copy of Vanilla playerdata/stats/advancements. */
public final class VanillaFileSnapshot {
    private VanillaFileSnapshot() { }

    public static void capture(Path world, Path checkpointRoot, AntiCrashConfig config) throws IOException {
        copyIfEnabled(world,checkpointRoot,"playerdata",config.playerSnapshot.vanillaFiles.restorePlayerdata);
        copyIfEnabled(world,checkpointRoot,"stats",config.playerSnapshot.vanillaFiles.restoreStats);
        copyIfEnabled(world,checkpointRoot,"advancements",config.playerSnapshot.vanillaFiles.restoreAdvancements);
    }

    public static void restore(Path checkpointRoot, Path world, AntiCrashConfig config) throws IOException {
        restoreIfEnabled(checkpointRoot,world,"playerdata",config.playerSnapshot.vanillaFiles.restorePlayerdata);
        restoreIfEnabled(checkpointRoot,world,"stats",config.playerSnapshot.vanillaFiles.restoreStats);
        restoreIfEnabled(checkpointRoot,world,"advancements",config.playerSnapshot.vanillaFiles.restoreAdvancements);
    }

    private static void copyIfEnabled(Path world,Path root,String name,boolean enabled)throws IOException{if(!enabled)return;Path source=world.resolve(name);Path target=root.resolve("vanilla-files").resolve(name);if(Files.exists(source))TreeCopy.copy(source,target);else Files.createDirectories(target);}
    private static void restoreIfEnabled(Path root,Path world,String name,boolean enabled)throws IOException{if(!enabled)return;Path source=root.resolve("vanilla-files").resolve(name);if(!Files.isDirectory(source))throw new IOException("Enabled Vanilla player snapshot is missing: "+name);TreeCopy.replaceContents(source,world.resolve(name));}
}
