package com.alexander.alsbanker;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class StockDataService {

    public record Stock(String symbol, String name, double price) {
    }

    public record Holding(String symbol, double shares) {
    }

    public static void initializeTables() {
        try (Connection conn = LoanDataService.openConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS alsbanker_stocks (" +
                    "symbol VARCHAR(8) PRIMARY KEY, name VARCHAR(64), price DOUBLE)");
            stmt.execute("CREATE TABLE IF NOT EXISTS alsbanker_stock_holdings (" +
                    "player_uuid VARCHAR(36), symbol VARCHAR(8), shares DOUBLE, " +
                    "PRIMARY KEY (player_uuid, symbol))");

            seedIfEmpty(stmt);
        } catch (SQLException e) {
            AlsBanker.get().getLogger().severe("Stock table init failed: " + e.getMessage());
        }
    }

    private static void seedIfEmpty(Statement stmt) throws SQLException {
        try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS count FROM alsbanker_stocks")) {
            if (rs.next() && rs.getInt("count") > 0) return;
        }
        stmt.executeUpdate("INSERT INTO alsbanker_stocks (symbol, name, price) VALUES " +
                "('ECOX', 'EcoXpert Holdings', 50.00), " +
                "('TOWN', 'Towny Land Co', 100.00), " +
                "('CRFT', 'Craft Corp', 25.00), " +
                "('VLT', 'Vault Bank', 75.00), " +
                "('ORE', 'Ore Mining Co', 10.00)");
    }

    public static List<Stock> listStocks() throws SQLException {
        List<Stock> out = new ArrayList<>();
        try (Connection conn = LoanDataService.openConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT symbol, name, price FROM alsbanker_stocks ORDER BY symbol")) {
            while (rs.next()) {
                out.add(new Stock(rs.getString("symbol"), rs.getString("name"), rs.getDouble("price")));
            }
        }
        return out;
    }

    public static Stock getStock(String symbol) throws SQLException {
        try (Connection conn = LoanDataService.openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT symbol, name, price FROM alsbanker_stocks WHERE symbol = ?")) {
            ps.setString(1, symbol.toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new Stock(rs.getString("symbol"), rs.getString("name"), rs.getDouble("price"));
            }
        }
    }

    public static List<Holding> getPortfolio(String uuid) throws SQLException {
        List<Holding> out = new ArrayList<>();
        try (Connection conn = LoanDataService.openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT symbol, shares FROM alsbanker_stock_holdings WHERE player_uuid = ? AND shares > 0")) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Holding(rs.getString("symbol"), rs.getDouble("shares")));
                }
            }
        }
        return out;
    }

    public static double getShares(String uuid, String symbol) throws SQLException {
        try (Connection conn = LoanDataService.openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT shares FROM alsbanker_stock_holdings WHERE player_uuid = ? AND symbol = ?")) {
            ps.setString(1, uuid);
            ps.setString(2, symbol.toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble("shares") : 0.0;
            }
        }
    }

    public static void addShares(String uuid, String symbol, double shares) throws SQLException {
        try (Connection conn = LoanDataService.openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO alsbanker_stock_holdings (player_uuid, symbol, shares) VALUES (?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE shares = shares + ?")) {
            ps.setString(1, uuid);
            ps.setString(2, symbol.toUpperCase());
            ps.setDouble(3, shares);
            ps.setDouble(4, shares);
            ps.executeUpdate();
        }
    }

    /** Returns false (no change made) if the player doesn't hold enough shares. */
    public static boolean removeShares(String uuid, String symbol, double shares) throws SQLException {
        try (Connection conn = LoanDataService.openConnection()) {
            conn.setAutoCommit(false);
            try {
                double owned;
                try (PreparedStatement select = conn.prepareStatement(
                        "SELECT shares FROM alsbanker_stock_holdings WHERE player_uuid = ? AND symbol = ? FOR UPDATE")) {
                    select.setString(1, uuid);
                    select.setString(2, symbol.toUpperCase());
                    try (ResultSet rs = select.executeQuery()) {
                        owned = rs.next() ? rs.getDouble("shares") : 0.0;
                    }
                }
                if (owned < shares) {
                    conn.rollback();
                    return false;
                }
                try (PreparedStatement update = conn.prepareStatement(
                        "UPDATE alsbanker_stock_holdings SET shares = shares - ? WHERE player_uuid = ? AND symbol = ?")) {
                    update.setDouble(1, shares);
                    update.setString(2, uuid);
                    update.setString(3, symbol.toUpperCase());
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

    public static void updatePrice(String symbol, double newPrice) throws SQLException {
        try (Connection conn = LoanDataService.openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE alsbanker_stocks SET price = ? WHERE symbol = ?")) {
            ps.setDouble(1, newPrice);
            ps.setString(2, symbol);
            ps.executeUpdate();
        }
    }
}
