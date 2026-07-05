package com.alexander.alsbanker;

import com.alexander.alsbanker.api.Transaction;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Records every loan/balance transaction to both the database (so AllyPhone or other
 * plugins can query a player's history) and a human-readable file under
 * plugins/AlsBanker/transactions, one file per day.
 */
public class TransactionService {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void initializeTable() {
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS alsbanker_transactions (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, player_uuid VARCHAR(36), " +
                    "type VARCHAR(32), amount DOUBLE, balance_after DOUBLE, " +
                    "description VARCHAR(255), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        } catch (SQLException e) {
            AlsBanker.get().getLogger().severe("Transaction table init failed: " + e.getMessage());
        }
    }

    public static void record(String uuid, String type, double amount, double balanceAfter, String description) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO alsbanker_transactions (player_uuid, type, amount, balance_after, description) " +
                     "VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, uuid);
            ps.setString(2, type);
            ps.setDouble(3, amount);
            ps.setDouble(4, balanceAfter);
            ps.setString(5, description);
            ps.executeUpdate();
        } catch (SQLException e) {
            AlsBanker.get().getLogger().severe("Failed to record transaction: " + e.getMessage());
        }

        writeToFile(uuid, type, amount, balanceAfter, description);
    }

    private static void writeToFile(String uuid, String type, double amount, double balanceAfter, String description) {
        Path dir = AlsBanker.get().getDataFolder().toPath().resolve("transactions");
        try {
            Files.createDirectories(dir);
            Path file = dir.resolve("transactions-" + LocalDate.now() + ".log");
            String line = String.format("[%s] player=%s type=%s amount=%.2f balanceAfter=%.2f | %s",
                    LocalDateTime.now().format(TIMESTAMP), uuid, type, amount, balanceAfter, description);
            try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {
                out.println(line);
            }
        } catch (IOException e) {
            AlsBanker.get().getLogger().warning("Failed to write transaction log file: " + e.getMessage());
        }
    }

    public static List<Transaction> getRecent(String uuid, int limit) throws SQLException {
        List<Transaction> result = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT type, amount, balance_after, description, created_at FROM alsbanker_transactions " +
                     "WHERE player_uuid = ? ORDER BY created_at DESC LIMIT ?")) {
            ps.setString(1, uuid);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new Transaction(
                            rs.getTimestamp("created_at").toLocalDateTime(),
                            rs.getString("type"),
                            rs.getDouble("amount"),
                            rs.getDouble("balance_after"),
                            rs.getString("description")));
                }
            }
        }
        return result;
    }
}
