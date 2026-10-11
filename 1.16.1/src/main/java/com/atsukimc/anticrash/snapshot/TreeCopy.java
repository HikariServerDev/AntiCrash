package com.atsukimc.anticrash.snapshot;

import com.atsukimc.anticrash.util.PathSafety;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

public final class TreeCopy {
    private TreeCopy() { }

    public static void copy(Path source, Path destination) throws IOException {
        CheckpointWriteGuard.requireMutable(destination);
        if(!Files.isDirectory(source,LinkOption.NOFOLLOW_LINKS)) throw new IOException("Missing copy source directory: "+source);
        PathSafety.rejectSymlink(source);
        if(Files.exists(destination,LinkOption.NOFOLLOW_LINKS))PathSafety.rejectSymlink(destination);
        Files.createDirectories(destination);
        try(var stream=Files.walk(source)){
            for(Path input:stream.sorted().toList()){
                // Files.walk includes the source root itself. Its relative path is empty,
                // which is the destination root rather than a child and must not be rejected.
                if (input.equals(source)) continue;
                PathSafety.rejectSymlink(input); Path relative=source.relativize(input); Path output=PathSafety.requireChild(destination,destination.resolve(relative));
                if(Files.isDirectory(input,LinkOption.NOFOLLOW_LINKS))Files.createDirectories(output); else if(Files.isRegularFile(input,LinkOption.NOFOLLOW_LINKS)){Files.createDirectories(output.getParent());Files.copy(input,output,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.COPY_ATTRIBUTES);}
            }
        }
    }

    public static void replaceContents(Path source, Path destination) throws IOException {
        CheckpointWriteGuard.requireMutable(destination);
        if(!Files.isDirectory(source,LinkOption.NOFOLLOW_LINKS)) throw new IOException("Missing restore source: "+source);
        PathSafety.rejectSymlink(source);
        try(var stream=Files.walk(source)){for(Path path:stream.toList())PathSafety.rejectSymlink(path);}
        if(Files.exists(destination,LinkOption.NOFOLLOW_LINKS)){
            PathSafety.rejectSymlink(destination);
            try(var stream=Files.walk(destination)){for(Path path:stream.toList())PathSafety.rejectSymlink(path);}
        }
        Files.createDirectories(destination);
        try(var stream=Files.walk(destination)){
            for(Path existing:stream.sorted(java.util.Comparator.reverseOrder()).toList())if(!existing.equals(destination))Files.deleteIfExists(existing);
        }
        copy(source,destination);
        forceTree(destination);
    }

    private static void forceTree(Path root) throws IOException {
        try(var stream=Files.walk(root)){
            for(Path path:stream.toList())if(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))com.atsukimc.anticrash.util.AtomicFiles.force(path);
        }
        try(var stream=Files.walk(root)){
            for(Path path:stream.sorted(java.util.Comparator.reverseOrder()).toList())if(Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))com.atsukimc.anticrash.util.AtomicFiles.forceDirectory(path);
        }
    }
}
