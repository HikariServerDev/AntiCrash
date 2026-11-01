package com.atsukimc.anticrash.legacy;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 1.16.1 stores ordered ticks globally per world, with one JVM-global ScheduledTick.idCounter. A
 * value-only cut; native capture/restore hooks are intentionally not claimed by this core.
 */
public final class LegacyTickLedger {
    public static final int MAX_ENTRIES = 262144;
    private static final int MAGIC = 0x41435431;
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public enum Kind {
        BLOCK,
        FLUID
    }

    public record Entry(
            String dimension,
            Kind kind,
            String objectId,
            int x,
            int y,
            int z,
            long absoluteTime,
            int priority,
            long sequence) {
        public Entry {
            identifier(dimension);
            identifier(objectId);
            Objects.requireNonNull(kind);
            if (priority < -3 || priority > 3 || sequence < 0)
                throw new IllegalArgumentException("Invalid legacy tick priority/sequence");
        }

        private Key key() {
            return new Key(dimension, kind, objectId, x, y, z);
        }
    }

    private record Key(String dimension, Kind kind, String objectId, int x, int y, int z) {}

    private static void identifier(String value) {
        if (value == null || value.length() > 256 || !ID.matcher(value).matches())
            throw new IllegalArgumentException("Invalid registry/dimension identifier");
    }

    public record Snapshot(long nextSequence, List<Entry> entries) {
        public Snapshot {
            Objects.requireNonNull(entries);
            if (nextSequence < 0 || entries.size() > MAX_ENTRIES)
                throw new IllegalArgumentException("Legacy tick snapshot exceeds supported limits");
            var ids = new HashSet<Long>();
            var keys = new HashSet<Key>();
            for (Entry entry : entries) {
                Objects.requireNonNull(entry);
                if (entry.sequence() >= nextSequence
                        || !ids.add(entry.sequence())
                        || !keys.add(entry.key()))
                    throw new IllegalArgumentException("Inconsistent global legacy tick ledger");
            }
            // Reconstruction order is global sequence order, NOT dimension or chunk order.
            entries = entries.stream().sorted(Comparator.comparingLong(Entry::sequence)).toList();
        }

        public List<Entry> schedulerOrder(String dimension, Kind kind) {
            return entries.stream()
                    .filter(t -> t.dimension().equals(dimension) && t.kind() == kind)
                    .sorted(
                            Comparator.comparingLong(Entry::absoluteTime)
                                    .thenComparingInt(Entry::priority)
                                    .thenComparingLong(Entry::sequence))
                    .toList();
        }

        public void write(DataOutput output) throws IOException {
            output.writeInt(MAGIC);
            output.writeLong(nextSequence);
            output.writeInt(entries.size());
            for (Entry e : entries) {
                output.writeUTF(e.dimension());
                output.writeByte(e.kind().ordinal());
                output.writeUTF(e.objectId());
                output.writeInt(e.x());
                output.writeInt(e.y());
                output.writeInt(e.z());
                output.writeLong(e.absoluteTime());
                output.writeInt(e.priority());
                output.writeLong(e.sequence());
            }
        }

        public String fingerprint() {
            try {
                var digest = java.security.MessageDigest.getInstance("SHA-256");
                try (var output =
                        new DataOutputStream(
                                new java.security.DigestOutputStream(
                                        OutputStream.nullOutputStream(), digest))) {
                    write(output);
                }
                return HexFormat.of().formatHex(digest.digest());
            } catch (IOException | java.security.NoSuchAlgorithmException error) {
                throw new IllegalStateException("Cannot fingerprint legacy tick ledger", error);
            }
        }

        public static Snapshot read(DataInput input) throws IOException {
            if (input.readInt() != MAGIC) throw new IOException("Unknown legacy ledger format");
            long next = input.readLong();
            int count = input.readInt();
            if (count < 0 || count > MAX_ENTRIES)
                throw new IOException("Legacy tick count exceeds limit");
            var entries = new ArrayList<Entry>(count);
            try {
                for (int i = 0; i < count; i++) {
                    String dimension = input.readUTF();
                    int kind = input.readUnsignedByte();
                    if (kind > 1) throw new IOException("Unknown tick kind");
                    entries.add(
                            new Entry(
                                    dimension,
                                    kind == 0 ? Kind.BLOCK : Kind.FLUID,
                                    input.readUTF(),
                                    input.readInt(),
                                    input.readInt(),
                                    input.readInt(),
                                    input.readLong(),
                                    input.readInt(),
                                    input.readLong()));
                }
                return new Snapshot(next, entries);
            } catch (IllegalArgumentException invalid) {
                throw new IOException("Invalid legacy tick ledger", invalid);
            }
        }
    }

    /**
     * Native tick clears both queues on normal return. Nonempty means no exact tick-boundary cut.
     */
    public static Snapshot atBoundary(
            long nextSequence,
            List<Entry> entries,
            int currentTickActions,
            int consumedTickActions) {
        if (currentTickActions != 0 || consumedTickActions != 0)
            throw new IllegalStateException(
                    "Legacy scheduler is inside a tick; checkpoint must defer");
        return new Snapshot(nextSequence, entries);
    }

    private LegacyTickLedger() {}
}
