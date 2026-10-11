package com.atsukimc.anticrash.supervisor;

import java.nio.file.Files;
import java.nio.file.Path;

/** Small child JVM used to exercise the real foreground Supervisor process in tests. */
public final class SupervisorChildFixture {
    private SupervisorChildFixture() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) throw new IllegalArgumentException("mode and counter are required");
        String mode = args[0];
        Path counter = Path.of(args[1]);
        int launch = Files.exists(counter) ? Integer.parseInt(Files.readString(counter)) : 0;
        Files.writeString(counter, Integer.toString(launch + 1));

        switch (mode) {
            case "normal" -> {
                return;
            }
            case "abnormal" -> System.exit(23);
            case "oom" -> throw new OutOfMemoryError("Supervisor integration test");
            case "oom-worker" -> {
                Thread worker = new Thread(() -> {
                    try {
                        Thread.sleep(60_000L);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }, "non-daemon-test-worker");
                worker.setDaemon(false);
                worker.start();
                // Mirrors ChunkRecoveryManager: release temporary state first, then fail fast so
                // an uncaught Error cannot leave this non-daemon worker behind.
                Runtime.getRuntime().halt(137);
            }
            case "controlled" -> {
                if (args.length < 3) throw new IllegalArgumentException("marker is required");
                if (launch == 0) {
                    Path marker = Path.of(args[2]);
                    Files.createDirectories(marker.getParent());
                    Files.writeString(marker, "request=123e4567-e89b-12d3-a456-426614174000\n"
                            + "created_at=2026-08-31T00:00:00Z\n");
                }
            }
            default -> throw new IllegalArgumentException("unknown mode: " + mode);
        }
    }
}
