package com.alexander.alsbanker;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class DatabaseManager {
    private static HikariDataSource dataSource;

    public static void setup() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(AlsBanker.get().getConfig().getString("mysql.url"));
        config.setUsername(AlsBanker.get().getConfig().getString("mysql.user"));
        config.setPassword(AlsBanker.get().getConfig().getString("mysql.password"));
        config.setMaximumPoolSize(10);
        config.addDataSourceProperty("cachePrepStmts", "true");
        dataSource = new HikariDataSource(config);
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public static double getBalance(UUID uuid) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT balance FROM alsbanker_balances WHERE player_uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble("balance") : 0.0;
            }
        } catch (SQLException e) {
            AlsBanker.get().getLogger().severe("Failed to read balance: " + e.getMessage());
            return 0.0;
        }
    }

    public static boolean withdraw(UUID uuid, double amount) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                double balance;
                try (PreparedStatement select = conn.prepareStatement(
                        "SELECT balance FROM alsbanker_balances WHERE player_uuid = ? FOR UPDATE")) {
                    select.setString(1, uuid.toString());
                    try (ResultSet rs = select.executeQuery()) {
                        balance = rs.next() ? rs.getDouble("balance") : 0.0;
                    }
                }
                if (balance < amount) {
                    conn.rollback();
                    return false;
                }
                try (PreparedStatement update = conn.prepareStatement(
                        "UPDATE alsbanker_balances SET balance = balance - ? WHERE player_uuid = ?")) {
                    update.setDouble(1, amount);
                    update.setString(2, uuid.toString());
                    update.executeUpdate();
                }
                conn.commit();
                TransactionService.record(uuid.toString(), "WITHDRAWAL", amount, balance - amount, "Bank withdrawal");
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            AlsBanker.get().getLogger().severe("Withdraw failed: " + e.getMessage());
            return false;
        }
    }

    public static boolean deposit(UUID uuid, double amount) {
        try (Connection conn = getConnection();
             PreparedStatement upsert = conn.prepareStatement(
                     "INSERT INTO alsbanker_balances (player_uuid, balance) VALUES (?, ?) " +
                     "ON DUPLICATE KEY UPDATE balance = balance + ?")) {
            upsert.setString(1, uuid.toString());
            upsert.setDouble(2, amount);
            upsert.setDouble(3, amount);
            upsert.executeUpdate();
            TransactionService.record(uuid.toString(), "DEPOSIT", amount, getBalance(uuid), "Bank deposit");
            return true;
        } catch (SQLException e) {
            AlsBanker.get().getLogger().severe("Deposit failed: " + e.getMessage());
            return false;
        }
    }

    public static void close() {
        if (dataSource != null) dataSource.close();
    }
}