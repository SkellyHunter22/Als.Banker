package com.alexander.alsbanker;

import java.sql.*;

public class SavingsDataService {

    public static void initializeTable() {
        try (Connection conn = LoanDataService.openConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS alsbanker_savings (" +
                    "player_uuid VARCHAR(36) PRIMARY KEY, balance DOUBLE DEFAULT 0, " +
                    "last_interest_date DATE)");
        } catch (SQLException e) {
            AlsBanker.get().getLogger().severe("Savings table init failed: " + e.getMessage());
        }
    }

    public static double getBalance(String uuid) throws SQLException {
        try (Connection conn = LoanDataService.openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT balance FROM alsbanker_savings WHERE player_uuid = ?")) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble("balance") : 0.0;
            }
        }
    }

    /** Moves money from the player's Vault wallet into their savings balance. */
    public static void deposit(String uuid, double amount) throws SQLException {
        try (Connection conn = LoanDataService.openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO alsbanker_savings (player_uuid, balance) VALUES (?, ?) " +
                     "ON DUPLICATE KEY UPDATE balance = balance + ?")) {
            ps.setString(1, uuid);
            ps.setDouble(2, amount);
            ps.setDouble(3, amount);
            ps.executeUpdate();
        }
    }

    /**
     * Withdraws from savings back to the player's Vault wallet. Returns false (no
     * change made) if the savings balance is insufficient.
     */
    public static boolean withdraw(String uuid, double amount) throws SQLException {
        try (Connection conn = LoanDataService.openConnection()) {
            conn.setAutoCommit(false);
            try {
                double balance;
                try (PreparedStatement select = conn.prepareStatement(
                        "SELECT balance FROM alsbanker_savings WHERE player_uuid = ? FOR UPDATE")) {
                    select.setString(1, uuid);
                    try (ResultSet rs = select.executeQuery()) {
                        balance = rs.next() ? rs.getDouble("balance") : 0.0;
                    }
                }
                if (balance < amount) {
                    conn.rollback();
                    return false;
                }
                try (PreparedStatement update = conn.prepareStatement(
                        "UPDATE alsbanker_savings SET balance = balance - ? WHERE player_uuid = ?")) {
                    update.setDouble(1, amount);
                    update.setString(2, uuid);
                    update.executeUpdate();
                }
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    /**
     * Applies daily interest to every savings account not yet credited today.
     * Returns the number of accounts credited, for logging.
     */
    public static int applyDailyInterest(double rate, java.util.function.BiConsumer<String, Double> onCredited)
            throws SQLException {
        int processed = 0;
        try (Connection conn = LoanDataService.openConnection();
             PreparedStatement select = conn.prepareStatement(
                     "SELECT player_uuid, balance FROM alsbanker_savings " +
                     "WHERE balance > 0 AND (last_interest_date IS NULL OR last_interest_date < CURDATE())");
             PreparedStatement update = conn.prepareStatement(
                     "UPDATE alsbanker_savings SET balance = balance + ?, last_interest_date = CURDATE() " +
                     "WHERE player_uuid = ?")) {

            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    String uuid = rs.getString("player_uuid");
                    double balance = rs.getDouble("balance");
                    double interest = balance * rate;

                    update.setDouble(1, interest);
                    update.setString(2, uuid);
                    update.executeUpdate();

                    onCredited.accept(uuid, interest);
                    processed++;
                }
            }
        }
        return processed;
    }
}
