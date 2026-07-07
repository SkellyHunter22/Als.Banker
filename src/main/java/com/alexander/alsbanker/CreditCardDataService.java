package com.alexander.alsbanker;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.BiConsumer;

/**
 * Backs /creditcard: a revolving credit line (unlike /loan's fixed installments).
 * Charging draws against the limit and deposits straight into the player's Vault
 * balance; paying reduces the balance owed; interest accrues daily on whatever's
 * carried over, same cadence as loan penalties and savings interest.
 */
public class CreditCardDataService {

    public static void initializeTable() {
        try (Connection conn = LoanDataService.openConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS alsbanker_credit_cards (" +
                    "player_uuid VARCHAR(36) PRIMARY KEY, credit_limit DOUBLE, balance DOUBLE DEFAULT 0, " +
                    "apr DOUBLE, last_interest_date DATE)");
        } catch (SQLException e) {
            AlsBanker.get().getLogger().severe("Credit card table init failed: " + e.getMessage());
        }
    }

    public static boolean hasCard(String uuid) throws SQLException {
        return getCard(uuid) != null;
    }

    public static Card getCard(String uuid) throws SQLException {
        try (Connection conn = LoanDataService.openConnection();
             PreparedStatement select = conn.prepareStatement(
                     "SELECT credit_limit, balance, apr FROM alsbanker_credit_cards WHERE player_uuid = ?")) {
            select.setString(1, uuid);
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) return null;
                return new Card(rs.getDouble("credit_limit"), rs.getDouble("balance"), rs.getDouble("apr"));
            }
        }
    }

    public static void openCard(String uuid, double limit, double apr) throws SQLException {
        try (Connection conn = LoanDataService.openConnection();
             PreparedStatement insert = conn.prepareStatement(
                     "INSERT INTO alsbanker_credit_cards (player_uuid, credit_limit, balance, apr) VALUES (?, ?, 0, ?)")) {
            insert.setString(1, uuid);
            insert.setDouble(2, limit);
            insert.setDouble(3, apr);
            insert.executeUpdate();
        }
    }

    /**
     * Draws against the card's available credit. Returns false (no change) if the
     * charge would exceed the limit or the player has no card.
     */
    public static boolean charge(String uuid, double amount) throws SQLException {
        try (Connection conn = LoanDataService.openConnection()) {
            conn.setAutoCommit(false);
            try {
                double limit;
                double balance;
                try (PreparedStatement select = conn.prepareStatement(
                        "SELECT credit_limit, balance FROM alsbanker_credit_cards WHERE player_uuid = ? FOR UPDATE")) {
                    select.setString(1, uuid);
                    try (ResultSet rs = select.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return false;
                        }
                        limit = rs.getDouble("credit_limit");
                        balance = rs.getDouble("balance");
                    }
                }

                if (balance + amount > limit) {
                    conn.rollback();
                    return false;
                }

                try (PreparedStatement update = conn.prepareStatement(
                        "UPDATE alsbanker_credit_cards SET balance = balance + ? WHERE player_uuid = ?")) {
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
     * Pays down the card balance. Returns the remaining balance, or null if the
     * player has no card. Overpaying simply clamps to zero (extra isn't withdrawn
     * by this method — callers should cap the amount to the current balance first).
     */
    public static Double pay(String uuid, double amount) throws SQLException {
        try (Connection conn = LoanDataService.openConnection()) {
            conn.setAutoCommit(false);
            try {
                double balance;
                try (PreparedStatement select = conn.prepareStatement(
                        "SELECT balance FROM alsbanker_credit_cards WHERE player_uuid = ? FOR UPDATE")) {
                    select.setString(1, uuid);
                    try (ResultSet rs = select.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return null;
                        }
                        balance = rs.getDouble("balance");
                    }
                }

                double applied = Math.min(amount, balance);
                try (PreparedStatement update = conn.prepareStatement(
                        "UPDATE alsbanker_credit_cards SET balance = balance - ? WHERE player_uuid = ?")) {
                    update.setDouble(1, applied);
                    update.setString(2, uuid);
                    update.executeUpdate();
                }

                conn.commit();
                return balance - applied;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    /**
     * Applies daily interest to every carried card balance not yet charged today.
     * Returns the number of cards processed, for logging.
     */
    public static int applyDailyInterest(BiConsumer<String, Double> onInterestCharged) throws SQLException {
        int processed = 0;
        try (Connection conn = LoanDataService.openConnection();
             PreparedStatement select = conn.prepareStatement(
                     "SELECT player_uuid, balance, apr FROM alsbanker_credit_cards " +
                     "WHERE balance > 0 AND (last_interest_date IS NULL OR last_interest_date < CURDATE())");
             PreparedStatement update = conn.prepareStatement(
                     "UPDATE alsbanker_credit_cards SET balance = balance + ?, last_interest_date = CURDATE() " +
                     "WHERE player_uuid = ?")) {

            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    String uuid = rs.getString("player_uuid");
                    double balance = rs.getDouble("balance");
                    double apr = rs.getDouble("apr");
                    double interest = balance * apr;

                    update.setDouble(1, interest);
                    update.setString(2, uuid);
                    update.executeUpdate();

                    onInterestCharged.accept(uuid, interest);
                    processed++;
                }
            }
        }
        return processed;
    }

    public record Card(double limit, double balance, double apr) {
        public double available() {
            return limit - balance;
        }

        public double utilization() {
            return limit <= 0 ? 0 : balance / limit;
        }
    }
}
