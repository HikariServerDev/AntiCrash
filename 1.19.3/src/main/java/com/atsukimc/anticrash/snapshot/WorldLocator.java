package com.atsukimc.anticrash.snapshot;

import com.atsukimc.anticrash.util.AtomicFiles;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Properties;

public final class WorldLocator {
    private WorldLocator() { }

    public static Path resolve(Path gameDir) throws IOException {
        String levelName="world";
        Path properties=gameDir.resolve("server.properties");
        if(Files.exists(properties,LinkOption.NOFOLLOW_LINKS)){
            if(!Files.isRegularFile(properties,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(properties))throw new IOException("Unsafe server.properties: "+properties);
            Properties values=new Properties();
            try(StringReader reader=new StringReader(AtomicFiles.readText(properties,1024L*1024L))){values.load(reader);}
            String configured=values.getProperty("level-name");if(configured!=null&&!configured.isBlank())levelName=configured.trim();
        }
        Path root=gameDir.toAbsolutePath().normalize();
        final Path world;
        try{world=root.resolve(levelName).normalize();}catch(RuntimeException exception){throw new IOException("Invalid level-name",exception);}
        if(!world.startsWith(root)||world.equals(root))throw new IOException("level-name escapes the server root: "+levelName);
        Path relative=root.relativize(world);Path current=root;
        for(Path part:relative){current=current.resolve(part);if(Files.isSymbolicLink(current))throw new IOException("World path contains a symbolic link: "+current);}
        return world;
    }
}
