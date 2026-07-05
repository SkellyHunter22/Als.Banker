package com.alexander.alsbanker;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Writes plugin activity to a rotating set of log files under plugins/AlsBanker/logs,
 * keeping the current launch plus the two before it (launch-1.log is always the most recent).
 */
public class AlsBankerFileLogger {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static PrintWriter writer;

    public static void start() {
        Path logDir = AlsBanker.get().getDataFolder().toPath().resolve("logs");
        try {
            Files.createDirectories(logDir);
            rotate(logDir);

            Path current = logDir.resolve("launch-1.log");
            writer = new PrintWriter(Files.newBufferedWriter(current, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING), true);
            log("=== AlsBanker launch started ===");
        } catch (IOException e) {
            AlsBanker.get().getLogger().warning("Could not set up the log file: " + e.getMessage());
        }
    }

    // Shifts launch-2 -> launch-3, launch-1 -> launch-2, dropping whatever used to be launch-3.
    private static void rotate(Path logDir) throws IOException {
        Path l3 = logDir.resolve("launch-3.log");
        Path l2 = logDir.resolve("launch-2.log");
        Path l1 = logDir.resolve("launch-1.log");

        Files.deleteIfExists(l3);
        if (Files.exists(l2)) Files.move(l2, l3);
        if (Files.exists(l1)) Files.move(l1, l2);
    }

    public static void log(String message) {
        if (writer == null) return;
        writer.println("[" + LocalDateTime.now().format(TIMESTAMP) + "] " + message);
    }

    public static void stop() {
        if (writer == null) return;
        log("=== AlsBanker shutting down ===");
        writer.close();
        writer = null;
    }
}
