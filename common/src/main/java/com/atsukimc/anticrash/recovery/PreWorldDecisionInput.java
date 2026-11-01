package com.atsukimc.anticrash.recovery;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Locale;

/**
 * Reads the one-shot pre-world Y/N decision without creating a competing BufferedReader.
 *
 * <p>The dedicated server starts its own {@code BufferedReader} in {@code setupServer()}, after
 * Fabric entrypoints have run. This reader is therefore used only before that point. It checks
 * {@link InputStream#available()} before every byte and has no blocking thread left behind after
 * the answer or timeout. The production overload intentionally reads raw bytes rather than wrapping
 * stdin, because a second buffered reader could consume a later Minecraft command.
 */
public final class PreWorldDecisionInput {
    public enum Answer {
        RESTORE,
        CONTINUE,
        TIMEOUT,
        UNAVAILABLE
    }

    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private PreWorldDecisionInput() {}

    public static Answer await(String prompt, long timeoutMillis, Answer timeoutAnswer) {
        // With an unbounded wait there is no safe way to wait for a future pipe attachment in a
        // headless service: no thread may remain behind to steal commands after the server starts.
        // A real terminal remains interactive; a finite timeout still gives supervisors a chance
        // to forward Y/N through their inherited pipe.
        if (System.console() == null && timeoutMillis <= 0) {
            try {
                if (System.in.available() == 0) return Answer.UNAVAILABLE;
            } catch (IOException exception) {
                return Answer.UNAVAILABLE;
            }
        }
        return await(
                System.in,
                System.out,
                prompt,
                timeoutMillis,
                timeoutAnswer,
                millis -> Thread.sleep(Math.min(50L, Math.max(1L, millis))));
    }

    /** Testable implementation. No stream is wrapped or closed by this method. */
    static Answer await(
            InputStream input,
            PrintStream output,
            String prompt,
            long timeoutMillis,
            Answer timeoutAnswer,
            Sleeper sleeper) {
        if (input == null || output == null || sleeper == null) return Answer.UNAVAILABLE;
        if (prompt != null && !prompt.isBlank()) {
            output.println(prompt);
            output.flush();
        }
        final long deadline = timeoutMillis <= 0 ? Long.MAX_VALUE : safeDeadline(timeoutMillis);
        StringBuilder line = new StringBuilder(8);
        try {
            while (System.nanoTime() < deadline) {
                if (input.available() > 0) {
                    int value = input.read();
                    if (value < 0) return Answer.UNAVAILABLE;
                    if (value == '\n' || value == '\r') {
                        Answer parsed = parse(line);
                        if (parsed != null) return parsed;
                        line.setLength(0);
                    } else if (!Character.isISOControl(value) && line.length() < 64) {
                        line.append((char) value);
                    }
                    continue;
                }
                if (timeoutMillis > 0 && System.nanoTime() >= deadline) break;
                sleeper.sleep(25L);
            }
        } catch (IOException exception) {
            return Answer.UNAVAILABLE;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Answer.UNAVAILABLE;
        }
        return timeoutAnswer == null ? Answer.TIMEOUT : timeoutAnswer;
    }

    static Answer parse(CharSequence raw) {
        if (raw == null) return null;
        String answer = raw.toString().trim().toUpperCase(Locale.ROOT);
        if (answer.equals("Y")) return Answer.RESTORE;
        if (answer.equals("N")) return Answer.CONTINUE;
        return null;
    }

    private static long safeDeadline(long timeoutMillis) {
        long now = System.nanoTime();
        long nanos;
        try {
            nanos = Math.multiplyExact(timeoutMillis, 1_000_000L);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
        return now > Long.MAX_VALUE - nanos ? Long.MAX_VALUE : now + nanos;
    }
}
