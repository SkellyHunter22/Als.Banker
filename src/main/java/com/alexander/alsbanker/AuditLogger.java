package com.alexander.alsbanker;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Catch-all audit trail: every command, GUI action, admin action, and
 * economy movement that touches a player, tagged with both their name and
 * UUID. Separate from TransactionService (which only tracks money movements
 * inside AlsBanker's own ledger) so this can also cover things like commands
 * run, Discord linking, and balance changes made by other plugins via Vault.
 */
public class AuditLogger {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void initializeTable() {
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS alsbanker_audit_log (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, player_uuid VARCHAR(36), player_name VARCHAR(16), " +
                    "source VARCHAR(64), action VARCHAR(64), details VARCHAR(255), " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        } catch (SQLException e) {
            AlsBanker.get().getLogger().severe("Audit log table init failed: " + e.getMessage());
        }
    }

    public static void log(UUID uuid, String source, String action, String details) {
        String name = resolveName(uuid);
        log(uuid != null ? uuid.toString() : null, name, source, action, details);
    }

    public static void log(String uuid, String playerName, String source, String action, String details) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO alsbanker_audit_log (player_uuid, player_name, source, action, details) " +
                     "VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, uuid);
            ps.setString(2, playerName);
            ps.setString(3, source);
            ps.setString(4, action);
            ps.setString(5, details);
            ps.executeUpdate();
        } catch (SQLException e) {
            AlsBanker.get().getLogger().severe("Failed to record audit log entry: " + e.getMessage());
        }

        writeToFile(uuid, playerName, source, action, details);
    }

    private static void writeToFile(String uuid, String playerName, String source, String action, String details) {
        Path dir = AlsBanker.get().getDataFolder().toPath().resolve("audit");
        try {
            Files.createDirectories(dir);
            Path file = dir.resolve("audit-" + LocalDate.now() + ".log");
            String line = String.format("[%s] player=%s uuid=%s source=%s action=%s | %s",
                    LocalDateTime.now().format(TIMESTAMP), playerName, uuid, source, action, details);
            try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {
                out.println(line);
            }
        } catch (IOException e) {
            AlsBanker.get().getLogger().warning("Failed to write audit log file: " + e.getMessage());
        }
    }

    static String resolveName(UUID uuid) {
        if (uuid == null) return "?";
        try {
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            String name = op.getName();
            return name != null ? name : uuid.toString();
        } catch (Exception e) {
            return uuid.toString();
        }
    }
}
