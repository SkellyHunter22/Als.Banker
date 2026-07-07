package com.alexander.alsbanker;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Tracks a per-player credit score (300-850, same range as real-world FICO)
 * that determines how much they're allowed to borrow via /loan and /creditcard.
 * Good behavior (on-time payments, paying a loan off, keeping savings, low
 * credit card utilization) raises it; missed payments and carrying debt lower it.
 */
public class CreditScoreService {

    public static void initializeTable() {
        try (Connection conn = LoanDataService.openConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS alsbanker_credit (" +
                    "player_uuid VARCHAR(36) PRIMARY KEY, score INT NOT NULL)");
        } catch (SQLException e) {
            AlsBanker.get().getLogger().severe("Credit table init failed: " + e.getMessage());
        }
    }

    public static int getScore(String uuid) throws SQLException {
        try (Connection conn = LoanDataService.openConnection();
             PreparedStatement select = conn.prepareStatement(
                     "SELECT score FROM alsbanker_credit WHERE player_uuid = ?")) {
            select.setString(1, uuid);
            try (ResultSet rs = select.executeQuery()) {
                if (rs.next()) return rs.getInt("score");
            }
        }

        int startingScore = AlsBanker.get().getConfig().getInt("credit.starting_score", 650);
        setScore(uuid, startingScore);
        return startingScore;
    }

    private static void setScore(String uuid, int score) throws SQLException {
        try (Connection conn = LoanDataService.openConnection();
             PreparedStatement upsert = conn.prepareStatement(
                     "INSERT INTO alsbanker_credit (player_uuid, score) VALUES (?, ?) " +
                     "ON DUPLICATE KEY UPDATE score = ?")) {
            upsert.setString(1, uuid);
            upsert.setInt(2, score);
            upsert.setInt(3, score);
            upsert.executeUpdate();
        }
    }

    /**
     * Adjusts a player's score by delta (positive or negative), clamped to the
     * configured min/max, and returns the resulting score.
     */
    public static int adjustScore(String uuid, int delta) throws SQLException {
        int min = AlsBanker.get().getConfig().getInt("credit.min_score", 300);
        int max = AlsBanker.get().getConfig().getInt("credit.max_score", 850);

        int current = getScore(uuid);
        int updated = Math.max(min, Math.min(max, current + delta));
        setScore(uuid, updated);
        return updated;
    }

    /**
     * The largest new loan a player with this score is allowed to take out,
     * on top of the server-wide `loan.max_amount` ceiling.
     */
    public static double maxLoanForScore(int score) {
        return Math.min(
                AlsBanker.get().getConfig().getDouble("loan.max_amount", 5000.0),
                tierCap(score, "loan"));
    }

    /**
     * The credit limit a new credit card should be issued with for this score.
     */
    public static double cardLimitForScore(int score) {
        return tierCap(score, "credit_card");
    }

    private static double tierCap(int score, String configPrefix) {
        int tier3Min = AlsBanker.get().getConfig().getInt("credit.tier3_min_score", 750);
        int tier2Min = AlsBanker.get().getConfig().getInt("credit.tier2_min_score", 650);
        int tier1Min = AlsBanker.get().getConfig().getInt("credit.tier1_min_score", 500);

        double tier3Cap = AlsBanker.get().getConfig().getDouble("credit." + configPrefix + "_tier3_max", 5000.0);
        double tier2Cap = AlsBanker.get().getConfig().getDouble("credit." + configPrefix + "_tier2_max", 2500.0);
        double tier1Cap = AlsBanker.get().getConfig().getDouble("credit." + configPrefix + "_tier1_max", 1000.0);

        if (score >= tier3Min) return tier3Cap;
        if (score >= tier2Min) return tier2Cap;
        if (score >= tier1Min) return tier1Cap;
        return 0.0;
    }

    public static String rating(int score) {
        if (score >= 750) return "Excellent";
        if (score >= 650) return "Good";
        if (score >= 500) return "Fair";
        return "Poor";
    }
}
