package com.atsukimc.anticrash.snapshot;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Bounded external-process runner used only by optional filesystem backends. */
final class ExternalCommand {
    private static final int MAX_OUTPUT_BYTES = 64 * 1024;

    private ExternalCommand() {}

    static Result run(Duration timeout, String... command) throws IOException {
        Process process = new ProcessBuilder(List.of(command)).redirectErrorStream(true).start();
        CompletableFuture<String> output =
                CompletableFuture.supplyAsync(() -> readBounded(process.getInputStream()));
        boolean finished;
        try {
            finished = process.waitFor(Math.max(1L, timeout.toMillis()), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("Interrupted while running external command", exception);
        }
        if (!finished) {
            process.destroy();
            try {
                if (!process.waitFor(1, TimeUnit.SECONDS)) process.destroyForcibly();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
            throw new IOException(
                    "External command timed out after " + timeout.toSeconds() + " seconds");
        }
        String captured;
        try {
            captured = output.get(2, TimeUnit.SECONDS);
        } catch (Exception exception) {
            captured = "<output unavailable>";
        }
        return new Result(process.exitValue(), captured);
    }

    private static String readBounded(InputStream input) {
        try (input;
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                int accepted = Math.min(read, Math.max(0, MAX_OUTPUT_BYTES - total));
                if (accepted > 0) output.write(buffer, 0, accepted);
                total += read;
            }
            String text = output.toString(StandardCharsets.UTF_8);
            return total > MAX_OUTPUT_BYTES ? text + "\n<output truncated>" : text;
        } catch (IOException exception) {
            return "<output read failed: " + exception.getMessage() + ">";
        }
    }

    record Result(int exitCode, String output) {}
}
