package com.atsukimc.anticrash.snapshot.logical;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded immutable candidate cache. Eviction is a performance event only: a missing/stale entry
 * forces a fresh server-thread capture or a safe fallback, and can never weaken epoch validation.
 */
public final class ChunkCandidateCache {
    public record Candidate(
            String key, long captureEpoch, long mutationGeneration, byte[] payload, String sha256) {
        public Candidate {
            payload = payload == null ? new byte[0] : payload.clone();
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }
    }

    private final long maxBytes;
    private final LinkedHashMap<String, Candidate> entries = new LinkedHashMap<>(16, 0.75f, true);
    private long bytes;
    private long evictions;

    public ChunkCandidateCache(long maxBytes) {
        this.maxBytes = Math.max(0L, maxBytes);
    }

    public synchronized void put(
            String key, long epoch, long mutationGeneration, byte[] payload, String sha256) {
        if (key == null || payload == null || payload.length > maxBytes) return;
        Candidate old = entries.remove(key);
        if (old != null) bytes -= old.payload.length;
        // Evict BEFORE cloning the incoming immutable value: a full cache must not temporarily
        // retain two cache-sized generations. Internal accounting never calls the copying API.
        Iterator<Map.Entry<String, Candidate>> iterator = entries.entrySet().iterator();
        while (bytes + payload.length > maxBytes && iterator.hasNext()) {
            bytes -= iterator.next().getValue().payload.length;
            iterator.remove();
            evictions++;
        }
        entries.put(key, new Candidate(key, epoch, mutationGeneration, payload, sha256));
        bytes += payload.length;
        trim();
    }

    public synchronized Candidate getIfValid(String key, long epoch, long mutationGeneration) {
        Candidate candidate = entries.get(key);
        if (candidate == null) return null;
        // A candidate captured after the requested cut cannot be used for that cut.  In normal
        // operation captureEpoch is <= epoch; this check also protects tests and reload paths.
        if (candidate.captureEpoch() > epoch
                || candidate.mutationGeneration() != mutationGeneration) {
            return null;
        }
        return candidate;
    }

    public synchronized void invalidate(String key) {
        Candidate removed = entries.remove(key);
        if (removed != null) bytes -= removed.payload.length;
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized long bytes() {
        return bytes;
    }

    public synchronized long evictions() {
        return evictions;
    }

    public long maxBytes() {
        return maxBytes;
    }

    public synchronized void clear() {
        entries.clear();
        bytes = 0L;
    }

    private void trim() {
        Iterator<Map.Entry<String, Candidate>> iterator = entries.entrySet().iterator();
        while (bytes > maxBytes && iterator.hasNext()) {
            Candidate candidate = iterator.next().getValue();
            bytes -= candidate.payload.length;
            iterator.remove();
            evictions++;
        }
    }
}
