package com.atsukimc.anticrash.consistency;

import com.atsukimc.anticrash.core.InternalStateManager;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.LinkOption;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class TransactionJournal implements AutoCloseable {
    private static final Gson COMPACT_GSON = new Gson();
    private static final int MAX_LINE_CHARS = 1024 * 1024;
    private static final int MAX_LINE_BYTES = 1024 * 1024;
    private static final String OMITTED = "OMITTED_TOO_LARGE";
    private final Path file;
    private FileChannel channel;
    private BufferedWriter writer;
    private long count;

    public TransactionJournal(InternalStateManager internal) throws IOException {
        Files.createDirectories(internal.journalDir());
        file = internal.journalDir().resolve("transactions-" + internal.currentRunId() + ".jsonl");
        channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.APPEND, LinkOption.NOFOLLOW_LINKS);
        writer = new BufferedWriter(new OutputStreamWriter(Channels.newOutputStream(channel), StandardCharsets.UTF_8));
    }

    public synchronized void append(TransactionRecord transaction) throws IOException {
        if (writer == null) throw new IOException("journal is closed");
        writer.write(boundedJson(transaction)); writer.newLine(); writer.flush(); channel.force(false); count++;
    }

    private static String boundedJson(TransactionRecord transaction) throws IOException {
        String line = COMPACT_GSON.toJson(transaction);
        if (withinLimit(line)) return line;

        // Item NBT can be arbitrarily large. Preserve the transaction boundary and identity while
        // replacing only bulky details; do not mutate the record delivered to external bridges.
        JsonObject object = COMPACT_GSON.toJsonTree(transaction).getAsJsonObject();
        object.addProperty("nbt", OMITTED);
        object.addProperty("before", OMITTED);
        object.addProperty("after", OMITTED);
        object.addProperty("detailsOmitted", true);
        line = COMPACT_GSON.toJson(object);
        if (!withinLimit(line)) throw new IOException("Transaction journal record exceeds 1 MiB");
        return line;
    }

    private static boolean withinLimit(String line) {
        return line.length() <= MAX_LINE_CHARS
                && line.getBytes(StandardCharsets.UTF_8).length <= MAX_LINE_BYTES;
    }

    public synchronized long count() { return count; }
    public Path file() { return file; }

    public static List<Path> list(Path directory) throws IOException {
        if (!Files.exists(directory)) return List.of();
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(directory)) throw new IOException("Unsafe journal directory");
        try (var stream = Files.list(directory)) {
            return stream.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString().startsWith("transactions-") && path.toString().endsWith(".jsonl"))
                    .sorted(java.util.Comparator.reverseOrder()).collect(Collectors.toList());
        }
    }

    public static ScanResult scanUnresolved(Path directory, String checkpointId) throws IOException {
        long unresolved = 0;
        long malformed = 0;
        for (Path journal : list(directory)) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    Files.newInputStream(journal, LinkOption.NOFOLLOW_LINKS), StandardCharsets.UTF_8))) {
                BoundedLine bounded;
                while ((bounded = readBoundedLine(reader)) != null) {
                    if (bounded.tooLong) { malformed++; continue; }
                    String line = bounded.text;
                    if (line.isBlank()) continue;
                    try {
                        TransactionRecord record = COMPACT_GSON.fromJson(line, TransactionRecord.class);
                        if (record == null || record.formatVersion != 1 || record.transactionId == null) {
                            malformed++;
                        } else if (("*".equals(record.checkpointId)
                                || "*".equals(record.fallbackCheckpointId)
                                || java.util.Objects.equals(checkpointId, record.checkpointId)
                                || java.util.Objects.equals(checkpointId, record.fallbackCheckpointId))
                                && "UNRESOLVED".equals(record.reconciliationStatus)) {
                            unresolved++;
                        }
                    } catch (RuntimeException exception) {
                        malformed++;
                    }
                }
            }
        }
        return new ScanResult(unresolved, malformed);
    }

    /** Avoids BufferedReader.readLine allocating an attacker-controlled multi-gigabyte String. */
    private static BoundedLine readBoundedLine(Reader reader) throws IOException {
        StringBuilder value = new StringBuilder(Math.min(4096, MAX_LINE_CHARS));
        boolean any = false;
        boolean tooLong = false;
        int character;
        while ((character = reader.read()) >= 0) {
            any = true;
            if (character == '\n') break;
            if (character == '\r') continue;
            if (value.length() < MAX_LINE_CHARS) value.append((char) character);
            else tooLong = true;
        }
        if (!any && character < 0) return null;
        String text = value.toString();
        return new BoundedLine(text, tooLong
                || text.getBytes(StandardCharsets.UTF_8).length > MAX_LINE_BYTES);
    }

    @Override public synchronized void close() throws IOException {
        if (writer != null) {
            writer.flush();
            if (channel != null) channel.force(false);
            writer.close();
            writer=null;
            channel=null;
        }
    }

    public record ScanResult(long unresolved, long malformed) { }
    private record BoundedLine(String text, boolean tooLong) { }
}
