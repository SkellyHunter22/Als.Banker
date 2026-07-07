package com.alexander.alsbanker;

import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.atomic.AtomicBoolean;

public class SchedulerEngine {

    // Guards against the periodic cycle and a manual "/loanscheduler runnow" overlapping,
    // which would otherwise double-charge the same overdue schedules for the same day.
    private static final AtomicBoolean running = new AtomicBoolean(false);

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
        if (!running.compareAndSet(false, true)) {
            AlsBanker.get().getLogger().info("Cycle already in progress, skipping this run.");
            return;
        }

        try {
            long start = System.currentTimeMillis();
            int processed = 0;

            try (Connection conn = LoanDataService.openConnection()) {
                processed += chargeLateFees(conn);
                processed += chargeDailyPenalties(conn);
            } catch (Exception e) {
                AlsBanker.get().getLogger().severe("Scheduler error: " + e.getMessage());
            }

            try {
                processed += creditSavingsInterest();
            } catch (Exception e) {
                AlsBanker.get().getLogger().severe("Savings interest error: " + e.getMessage());
            }

            try {
                processed += chargeCreditCardInterest();
            } catch (Exception e) {
                AlsBanker.get().getLogger().severe("Credit card interest error: " + e.getMessage());
            }

            long end = System.currentTimeMillis();
            AlsBanker.get().getLogger().info(
                    "Cycle completed in " + (end - start) + "ms, " + processed + " overdue schedule(s) processed.");
        } finally {
            running.set(false);
        }
    }

    /**
     * One-time flat fee charged the moment a schedule first misses its due date.
     */
    private static int chargeLateFees(Connection conn) throws Exception {
        double lateFeeRate = AlsBanker.get().getConfig().getDouble("late_fee.rate", 0.10);
        double lateFeeMin = AlsBanker.get().getConfig().getDouble("late_fee.min_amount", 0.0);
        int processed = 0;

        try (PreparedStatement newlyOverdue = conn.prepareStatement(
                     "SELECT s.id, s.loan_id, l.player_uuid, s.amount_due " +
                     "FROM ecoxpert_loan_schedules s JOIN ecoxpert_loans l ON l.id = s.loan_id " +
                     "WHERE s.status = 'PENDING' AND s.due_date < CURDATE()");
             PreparedStatement updateLoan = conn.prepareStatement(
                     "UPDATE ecoxpert_loans SET outstanding = outstanding + ? WHERE id = ?");
             PreparedStatement markOverdue = conn.prepareStatement(
                     "UPDATE ecoxpert_loan_schedules SET status = 'OVERDUE', late_fee_charged = TRUE, " +
                     "last_penalized_date = CURDATE() WHERE id = ?")) {

            try (ResultSet rs = newlyOverdue.executeQuery()) {
                while (rs.next()) {
                    long loanId = rs.getLong("loan_id");
                    long scheduleId = rs.getLong("id");
                    String uuid = rs.getString("player_uuid");
                    double amountDue = rs.getDouble("amount_due");

                    double lateFee = Math.max(lateFeeMin, amountDue * lateFeeRate);

                    updateLoan.setDouble(1, lateFee);
                    updateLoan.setLong(2, loanId);
                    updateLoan.executeUpdate();

                    markOverdue.setLong(1, scheduleId);
                    markOverdue.executeUpdate();

                    int scoreLoss = AlsBanker.get().getConfig().getInt("credit.score_loss_late_fee", 15);
                    CreditScoreService.adjustScore(uuid, -scoreLoss);

                    notifyPlayer(uuid, LoanServicerMessages.lateFee(lateFee));
                    processed++;
                }
            }
        }

        return processed;
    }

    /**
     * Ongoing interest/penalty applied once per calendar day to schedules that are
     * still unpaid, for as long as they remain overdue.
     */
    private static int chargeDailyPenalties(Connection conn) throws Exception {
        double penaltyRate = AlsBanker.get().getConfig().getDouble("penalty_rate");
        double penaltyCapFraction = AlsBanker.get().getConfig().getDouble("penalty_cap_fraction", 1.0);
        int processed = 0;

        try (PreparedStatement stillOverdue = conn.prepareStatement(
                     "SELECT s.id, l.id AS loan_id, l.player_uuid, l.outstanding, l.principal, l.interest_rate " +
                     "FROM ecoxpert_loan_schedules s JOIN ecoxpert_loans l ON l.id = s.loan_id " +
                     "WHERE s.status = 'OVERDUE' AND (s.last_penalized_date IS NULL OR s.last_penalized_date < CURDATE())");
             PreparedStatement updateLoan = conn.prepareStatement(
                     "UPDATE ecoxpert_loans SET outstanding = outstanding + ? WHERE id = ?");
             PreparedStatement markPenalized = conn.prepareStatement(
                     "UPDATE ecoxpert_loan_schedules SET last_penalized_date = CURDATE() WHERE id = ?")) {

            try (ResultSet rs = stillOverdue.executeQuery()) {
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

                    markPenalized.setLong(1, scheduleId);
                    markPenalized.executeUpdate();

                    int scoreLoss = AlsBanker.get().getConfig().getInt("credit.score_loss_daily_penalty", 5);
                    CreditScoreService.adjustScore(uuid, -scoreLoss);

                    notifyPlayer(uuid, LoanServicerMessages.dailyPenalty(totalIncrease));
                    processed++;
                }
            }
        }

        return processed;
    }

    /**
     * Ongoing interest applied once per calendar day to every savings account with
     * a positive balance, for as long as it stays deposited.
     */
    private static int creditSavingsInterest() throws Exception {
        double rate = AlsBanker.get().getConfig().getDouble("savings.interest_rate", 0.01);

        return SavingsDataService.applyDailyInterest(rate, (uuid, interest) -> {
            double newBalance;
            try {
                newBalance = SavingsDataService.getBalance(uuid);
            } catch (Exception e) {
                newBalance = interest;
            }
            TransactionService.record(uuid, "SAVINGS_INTEREST", interest, newBalance,
                    "Daily savings interest");
            notifyPlayer(uuid, LoanServicerMessages.savingsInterest(interest));
        });
    }

    /**
     * Ongoing daily interest on any carried credit card balance, plus a small credit
     * score hit for staying at high utilization — encourages paying the card down
     * rather than just letting it ride.
     */
    private static int chargeCreditCardInterest() throws Exception {
        double highUtilizationThreshold = AlsBanker.get().getConfig().getDouble("credit_card.high_utilization_threshold", 0.5);
        int utilizationScoreLoss = AlsBanker.get().getConfig().getInt("credit.score_loss_high_utilization", 3);

        return CreditCardDataService.applyDailyInterest((uuid, interest) -> {
            try {
                CreditCardDataService.Card card = CreditCardDataService.getCard(uuid);
                double newBalance = card != null ? card.balance() : interest;
                TransactionService.record(uuid, "CREDIT_CARD_INTEREST", interest, newBalance,
                        "Daily credit card interest");

                if (card != null && card.utilization() >= highUtilizationThreshold) {
                    CreditScoreService.adjustScore(uuid, -utilizationScoreLoss);
                }

                notifyPlayer(uuid, LoanServicerMessages.creditCardInterest(interest));
            } catch (Exception e) {
                AlsBanker.get().getLogger().severe("Credit card interest post-processing failed: " + e.getMessage());
            }
        });
    }

    private static void notifyPlayer(String uuid, String message) {
        LoanEventListener.notify(uuid, message);
    }
}
