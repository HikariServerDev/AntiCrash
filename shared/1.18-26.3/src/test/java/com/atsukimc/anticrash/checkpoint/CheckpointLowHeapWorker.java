package com.atsukimc.anticrash.checkpoint;

import com.atsukimc.anticrash.snapshot.logical.ChunkCandidateCache;
import com.atsukimc.anticrash.util.AtomicFiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

/** Isolated 32 MiB heap qualification. No Minecraft server and no real OOM is injected here. */
public final class CheckpointLowHeapWorker {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        ChunkCandidateCache cache = new ChunkCandidateCache(8L * 1024 * 1024);
        byte[] payload = new byte[1024 * 1024];
        for (int cycle = 0; cycle < 50; cycle++) {
            for (int n = 0; n < 16; n++) cache.put("chunk-" + n, cycle, cycle, payload, "hash");
            if (cache.bytes() > cache.maxBytes()) throw new AssertionError("budget");
            for (int n = 0; n < 16; n++) cache.invalidate("chunk-" + n);
            if (cache.bytes() != 0 || cache.size() != 0)
                throw new AssertionError("retained candidates");
        }
        // >60 MiB JSON, but only one small shared value and one streaming writer are in memory.
        Path json = root.resolve("streamed.json");
        if (args.length > 1 && args[1].equals("legacy-whole-string")) {
            // Exact pre-repair serialization algorithm, deliberately confined to this small JVM.
            AtomicFiles.writeText(
                    json,
                    AtomicFiles.GSON.toJson(Collections.nCopies(60, "x".repeat(1024 * 1024))));
            return;
        }
        AtomicFiles.writeJson(json, Collections.nCopies(60, "x".repeat(1024 * 1024)));
        if (Files.size(json) < 60L * 1024 * 1024) throw new AssertionError("incomplete JSON");
        try (var reader = AtomicFiles.boundedJsonReader(json, 64L * 1024 * 1024)) {
            reader.beginArray();
            int count = 0;
            while (reader.hasNext()) {
                reader.skipValue();
                count++;
            }
            reader.endArray();
            if (count != 60) throw new AssertionError("truncated");
        }
        System.out.println("50 cache cycles + 60MiB streaming JSON PASS at -Xmx32m");
    }
}
