package com.atsukimc.anticrash.snapshot;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers the digest of the entity chunk each entity storage last submitted, so an identical
 * rewrite can be skipped.
 *
 * <p>Every save flushes every loaded entity chunk. Between two checkpoints of a real TMC world
 * about 96% of those chunks were byte-identical, and with {@code sync-chunk-writes=true} each
 * rewrite is a synchronous disk write: the entity flush alone froze the cut for over a second.
 * Vanilla already skips repeated writes of an empty chunk; this extends the same rule to chunks
 * whose content did not change. The digest is recorded when the write is submitted, which is the
 * content the storage ends up with (a later submission for the same chunk replaces an earlier
 * pending one), and it is forgotten when the write fails, so a failed write is always retried.</p>
 */
public final class EntityChunkWriteDeduplicator {
    private final Map<Long, byte[]> submitted = new ConcurrentHashMap<>();

    /** @return the digest to submit with, or null when an identical write was already submitted */
    public byte[] digestIfChanged(long chunk, CompoundTag nbt) {
        byte[] digest = digest(nbt);
        if (digest == null) {
            submitted.remove(chunk);
            return new byte[0];
        }
        byte[] previous = submitted.get(chunk);
        return previous != null && Arrays.equals(previous, digest) ? null : digest;
    }

    public void submitted(long chunk, byte[] digest) {
        if (digest == null || digest.length == 0) submitted.remove(chunk);
        else submitted.put(chunk, digest);
    }

    /** The chunk was emptied or its write failed: the next write must reach the storage. */
    public void forget(long chunk) {
        submitted.remove(chunk);
    }

    private static byte[] digest(CompoundTag nbt) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            try (DataOutputStream out = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), sha))) {
                NbtIo.write(nbt, out);
            }
            return sha.digest();
        } catch (IOException | NoSuchAlgorithmException | RuntimeException exception) {
            return null;
        }
    }
}
