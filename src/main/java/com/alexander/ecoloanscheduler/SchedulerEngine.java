package com.alexander.ecoloanscheduler;

import org.bukkit.Bukkit;

import java.sql.*;

public class SchedulerEngine {

    public static void start() {
        int interval = EcoLoanScheduler.get().getConfig().getInt("interval_minutes");

        Bukkit.getScheduler().runTaskTimerAsynchronously(
                EcoLoanScheduler.get(),
                SchedulerEngine::runCycle,
                20L,
                interval * 60L * 20L
        );
    }

    public static void runCycle() {
        long start = System.currentTimeMillis();

        try (Connection conn = DriverManager.getConnection(
                EcoLoanScheduler.get().getConfig().getString("mysql.url"),
                EcoLoanScheduler.get().getConfig().getString("mysql.user"),
                EcoLoanScheduler.get().getConfig().getString("mysql.password")
        )) {

            PreparedStatement overdueStmt = conn.prepareStatement(
                    "SELECT s.id, s.loan_id, l.player_uuid, l.outstanding, l.principal, l.interest_rate, " +
                    "s.amount_due, s.paid_amount " +
                    "FROM ecoxpert_loan_schedules s " +
                    "JOIN ecoxpert_loans l ON l.id = s.loan_id " +
                    "WHERE s.status = 'PENDING' AND s.due_date < CURDATE()"
            );

            ResultSet rs = overdueStmt.executeQuery();

            while (rs.next()) {
                String uuid = rs.getString("player_uuid");

                // Interest
                double outstanding = rs.getDouble("outstanding");
                double principal = rs.getDouble("principal");
                double rate = rs.getDouble("interest_rate");

                double interest = InterestFormula.calculate(outstanding, principal, rate);

                PreparedStatement updateLoan = conn.prepareStatement(
                        "UPDATE ecoxpert_loans SET outstanding = outstanding + ? WHERE id = ?"
                );
                updateLoan.setDouble(1, interest);
                updateLoan.setLong(2, rs.getLong("loan_id"));
                updateLoan.executeUpdate();

                // Penalty
                double penaltyRate = EcoLoanScheduler.get().getConfig().getDouble("penalty_rate");
                double penalty = outstanding * penaltyRate;

                PreparedStatement penaltyStmt = conn.prepareStatement(
                        "UPDATE ecoxpert_loans SET outstanding = outstanding + ? WHERE id = ?"
                );
                penaltyStmt.setDouble(1, penalty);
                penaltyStmt.setLong(2, rs.getLong("loan_id"));
                penaltyStmt.executeUpdate();

                // Mark overdue
                PreparedStatement mark = conn.prepareStatement(
                        "UPDATE ecoxpert_loan_schedules SET status = 'OVERDUE' WHERE id = ?"
                );
                mark.setLong(1, rs.getLong("id"));
                mark.executeUpdate();

                // Notify
                LoanEventListener.notifyMinecraft(uuid);
                DiscordNotifier.dm(uuid, "Your loan is overdue or has incurred interest.");
            }

        } catch (Exception e) {
            EcoLoanScheduler.get().getLogger().severe("Scheduler error: " + e.getMessage());
        }

        long end = System.currentTimeMillis();
        EcoLoanScheduler.get().getLogger().info("Cycle completed in " + (end - start) + "ms");
    }
}
