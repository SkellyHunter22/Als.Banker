package com.alexander.alsbanker;

import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class SchedulerEngine {

    public static void start() {
        int interval = AlsBanker.get().getConfig().getInt("interval_minutes");

        Bukkit.getScheduler().runTaskTimerAsynchronously(
                AlsBanker.get(),
                SchedulerEngine::runCycle,
                20L,
                interval * 60L * 20L
        );
    }

    public static void runCycle() {
        long start = System.currentTimeMillis();
        int processed = 0;

        try (Connection conn = LoanDataService.openConnection();
             PreparedStatement overdueStmt = conn.prepareStatement(
                     "SELECT s.id, s.loan_id, l.player_uuid, l.outstanding, l.principal, l.interest_rate, " +
                     "s.amount_due, s.paid_amount " +
                     "FROM ecoxpert_loan_schedules s " +
                     "JOIN ecoxpert_loans l ON l.id = s.loan_id " +
                     "WHERE s.status = 'PENDING' AND s.due_date < CURDATE()");
             PreparedStatement updateLoan = conn.prepareStatement(
                     "UPDATE ecoxpert_loans SET outstanding = outstanding + ? WHERE id = ?");
             PreparedStatement markOverdue = conn.prepareStatement(
                     "UPDATE ecoxpert_loan_schedules SET status = 'OVERDUE' WHERE id = ?")) {

            double penaltyRate = AlsBanker.get().getConfig().getDouble("penalty_rate");
            double penaltyCapFraction = AlsBanker.get().getConfig().getDouble("penalty_cap_fraction", 1.0);

            try (ResultSet rs = overdueStmt.executeQuery()) {
                while (rs.next()) {
                    long loanId = rs.getLong("loan_id");
                    long scheduleId = rs.getLong("id");
                    String uuid = rs.getString("player_uuid");

                    double outstanding = rs.getDouble("outstanding");
                    double principal = rs.getDouble("principal");
                    double rate = rs.getDouble("interest_rate");

                    double interest = InterestFormula.calculate(outstanding, principal, rate);

                    double penalty = outstanding * penaltyRate;
                    double penaltyCap = outstanding * penaltyCapFraction;
                    if (penalty > penaltyCap) {
                        penalty = penaltyCap;
                    }

                    double totalIncrease = interest + penalty;

                    updateLoan.setDouble(1, totalIncrease);
                    updateLoan.setLong(2, loanId);
                    updateLoan.executeUpdate();

                    markOverdue.setLong(1, scheduleId);
                    markOverdue.executeUpdate();

                    notifyPlayer(uuid);
                    processed++;
                }
            }

        } catch (Exception e) {
            AlsBanker.get().getLogger().severe("Scheduler error: " + e.getMessage());
        }

        long end = System.currentTimeMillis();
        AlsBanker.get().getLogger().info(
                "Cycle completed in " + (end - start) + "ms, " + processed + " overdue schedule(s) processed.");
    }

    private static void notifyPlayer(String uuid) {
        // Bukkit API (player lookup, command dispatch) must run on the main thread,
        // but runCycle executes on an async scheduler task.
        Bukkit.getScheduler().runTask(AlsBanker.get(), () -> LoanEventListener.notifyMinecraft(uuid));
        DiscordNotifier.dm(uuid, "Your loan is overdue or has incurred interest.");
    }
}
