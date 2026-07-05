package com.alexander.alsbanker;

import java.sql.*;
import java.time.LocalDate;

public class LoanDataService {

    public static Connection openConnection() throws SQLException {
        return DatabaseManager.getConnection();
    }

    public static void initializeTables() {
        try (Connection conn = openConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS ecoxpert_loans (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, player_uuid VARCHAR(36), " +
                    "principal DOUBLE, outstanding DOUBLE, interest_rate DOUBLE)");
            stmt.execute("CREATE TABLE IF NOT EXISTS ecoxpert_loan_schedules (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, loan_id INT, installment_no INT, " +
                    "due_date DATE, amount_due DOUBLE, paid_amount DOUBLE DEFAULT 0, " +
                    "late_fee_charged BOOLEAN DEFAULT FALSE, last_penalized_date DATE, " +
                    "status VARCHAR(16) DEFAULT 'PENDING')");
            stmt.execute("CREATE TABLE IF NOT EXISTS alsbanker_balances (" +
                    "player_uuid VARCHAR(36) PRIMARY KEY, balance DOUBLE DEFAULT 0)");

            // Defensive: the schedules table may already exist from before these
            // columns existed, and CREATE TABLE IF NOT EXISTS won't add them.
            addColumnIfMissing(stmt, "ecoxpert_loan_schedules", "late_fee_charged", "BOOLEAN DEFAULT FALSE");
            addColumnIfMissing(stmt, "ecoxpert_loan_schedules", "last_penalized_date", "DATE");
        } catch (SQLException e) {
            AlsBanker.get().getLogger().severe("DB Init Failed: " + e.getMessage());
        }
    }

    private static void addColumnIfMissing(Statement stmt, String table, String column, String definition) {
        try {
            stmt.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        } catch (SQLException ignored) {
            // Column already exists.
        }
    }

    /**
     * Creates a loan for the player and splits it into evenly-sized installments,
     * one due every intervalDays starting from today.
     */
    public static void createLoan(String uuid, double principal, double interestRate,
                                   int installments, int intervalDays) throws SQLException {
        try (Connection conn = openConnection()) {
            conn.setAutoCommit(false);
            try {
                long loanId;
                try (PreparedStatement insertLoan = conn.prepareStatement(
                        "INSERT INTO ecoxpert_loans (player_uuid, principal, outstanding, interest_rate) VALUES (?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    insertLoan.setString(1, uuid);
                    insertLoan.setDouble(2, principal);
                    insertLoan.setDouble(3, principal);
                    insertLoan.setDouble(4, interestRate);
                    insertLoan.executeUpdate();
                    try (ResultSet keys = insertLoan.getGeneratedKeys()) {
                        keys.next();
                        loanId = keys.getLong(1);
                    }
                }

                double installmentAmount = principal / installments;
                try (PreparedStatement insertSchedule = conn.prepareStatement(
                        "INSERT INTO ecoxpert_loan_schedules (loan_id, installment_no, due_date, amount_due) VALUES (?, ?, ?, ?)")) {
                    LocalDate dueDate = LocalDate.now();
                    for (int i = 1; i <= installments; i++) {
                        dueDate = dueDate.plusDays(intervalDays);
                        insertSchedule.setLong(1, loanId);
                        insertSchedule.setInt(2, i);
                        insertSchedule.setDate(3, Date.valueOf(dueDate));
                        insertSchedule.setDouble(4, installmentAmount);
                        insertSchedule.addBatch();
                    }
                    insertSchedule.executeBatch();
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    /**
     * Applies a repayment to the player's active loan, oldest installment first.
     * Returns the remaining outstanding balance, or null if the player has no active loan.
     */
    public static Double applyPayment(String uuid, double amount) throws SQLException {
        try (Connection conn = openConnection()) {
            conn.setAutoCommit(false);
            try {
                long loanId;
                double outstanding;
                try (PreparedStatement select = conn.prepareStatement(
                        "SELECT id, outstanding FROM ecoxpert_loans WHERE player_uuid = ? AND outstanding > 0 " +
                        "ORDER BY id LIMIT 1 FOR UPDATE")) {
                    select.setString(1, uuid);
                    try (ResultSet rs = select.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return null;
                        }
                        loanId = rs.getLong("id");
                        outstanding = rs.getDouble("outstanding");
                    }
                }

                double applied = Math.min(amount, outstanding);

                try (PreparedStatement updateLoan = conn.prepareStatement(
                        "UPDATE ecoxpert_loans SET outstanding = outstanding - ? WHERE id = ?")) {
                    updateLoan.setDouble(1, applied);
                    updateLoan.setLong(2, loanId);
                    updateLoan.executeUpdate();
                }

                double remaining = applied;
                try (PreparedStatement selectSchedules = conn.prepareStatement(
                        "SELECT id, amount_due, paid_amount, status FROM ecoxpert_loan_schedules " +
                        "WHERE loan_id = ? AND status IN ('PENDING', 'OVERDUE') ORDER BY due_date ASC")) {
                    selectSchedules.setLong(1, loanId);
                    try (ResultSet rs = selectSchedules.executeQuery();
                         PreparedStatement updateSchedule = conn.prepareStatement(
                                 "UPDATE ecoxpert_loan_schedules SET paid_amount = ?, status = ? WHERE id = ?")) {
                        while (remaining > 0 && rs.next()) {
                            long scheduleId = rs.getLong("id");
                            double amountDue = rs.getDouble("amount_due");
                            double paidAmount = rs.getDouble("paid_amount");
                            String status = rs.getString("status");
                            double due = amountDue - paidAmount;
                            if (due <= 0) continue;

                            double payment = Math.min(remaining, due);
                            double newPaid = paidAmount + payment;
                            remaining -= payment;

                            updateSchedule.setDouble(1, newPaid);
                            updateSchedule.setString(2, newPaid >= amountDue ? "PAID" : status);
                            updateSchedule.setLong(3, scheduleId);
                            updateSchedule.addBatch();
                        }
                        updateSchedule.executeBatch();
                    }
                }

                conn.commit();
                return outstanding - applied;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public static LoanSummary getLoanSummary(String uuid) throws SQLException {
        try (Connection conn = openConnection();
             PreparedStatement select = conn.prepareStatement(
                     "SELECT id, outstanding FROM ecoxpert_loans WHERE player_uuid = ? AND outstanding > 0 " +
                     "ORDER BY id LIMIT 1")) {
            select.setString(1, uuid);
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) return null;

                long loanId = rs.getLong("id");
                double outstanding = rs.getDouble("outstanding");

                try (PreparedStatement nextDue = conn.prepareStatement(
                        "SELECT due_date, amount_due, paid_amount FROM ecoxpert_loan_schedules " +
                        "WHERE loan_id = ? AND status IN ('PENDING', 'OVERDUE') ORDER BY due_date ASC LIMIT 1")) {
                    nextDue.setLong(1, loanId);
                    try (ResultSet nrs = nextDue.executeQuery()) {
                        if (nrs.next()) {
                            double amountLeft = nrs.getDouble("amount_due") - nrs.getDouble("paid_amount");
                            return new LoanSummary(outstanding, nrs.getDate("due_date").toLocalDate(), amountLeft);
                        }
                        return new LoanSummary(outstanding, null, 0);
                    }
                }
            }
        }
    }

    public static double getOutstandingBalance(String uuid) throws SQLException {
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COALESCE(SUM(outstanding), 0) AS total FROM ecoxpert_loans WHERE player_uuid = ?")) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble("total") : 0.0;
            }
        }
    }

    public static double getTotalOutstanding() throws SQLException {
        try (Connection conn = openConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COALESCE(SUM(outstanding), 0) AS total FROM ecoxpert_loans")) {
            return rs.next() ? rs.getDouble("total") : 0.0;
        }
    }

    public static int getActiveLoanCount(String uuid) throws SQLException {
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) AS count FROM ecoxpert_loans WHERE player_uuid = ? AND outstanding > 0")) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("count") : 0;
            }
        }
    }

    public static int getActiveLoanCount() throws SQLException {
        try (Connection conn = openConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS count FROM ecoxpert_loans WHERE outstanding > 0")) {
            return rs.next() ? rs.getInt("count") : 0;
        }
    }

    public static int getOverdueScheduleCount() throws SQLException {
        try (Connection conn = openConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) AS count FROM ecoxpert_loan_schedules WHERE status = 'OVERDUE'")) {
            return rs.next() ? rs.getInt("count") : 0;
        }
    }

    public static class LoanSummary {
        public final double outstanding;
        public final LocalDate nextDueDate;
        public final double nextAmountDue;

        public LoanSummary(double outstanding, LocalDate nextDueDate, double nextAmountDue) {
            this.outstanding = outstanding;
            this.nextDueDate = nextDueDate;
            this.nextAmountDue = nextAmountDue;
        }
    }
}
