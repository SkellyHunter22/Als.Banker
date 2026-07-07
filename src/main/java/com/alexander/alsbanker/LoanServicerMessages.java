package com.alexander.alsbanker;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Flavor text for the automated notifications SchedulerEngine sends when a loan
 * misses a payment or keeps accruing overdue interest, so players don't see the
 * exact same wording every single day their loan is overdue.
 */
public final class LoanServicerMessages {

    private LoanServicerMessages() {
    }

    private static final List<String> LATE_FEE = List.of(
            "Al's Banker here — you missed a payment, so a $%s late fee just landed on your loan.",
            "This is your friendly neighborhood loan servicer. A payment slipped past its due date, and a $%s late fee came with it.",
            "Heads up from the bank: that installment went unpaid, so we tacked on a $%s late fee.",
            "Al's Banker: missed payment detected. A $%s late fee has been added to your outstanding balance.",
            "Notice from the loan office: your payment is overdue, and a $%s late fee has been charged.",
            "We hate to be the bearer of bad news, but a missed payment just cost you a $%s late fee.",
            "Your loan servicer flagged an overdue installment and applied a $%s late fee.",
            "Al's Banker here again — that payment came in late (or not at all), so add $%s to what you owe."
    );

    private static final List<String> DAILY_PENALTY = List.of(
            "Al's Banker: your loan is still overdue, so today's interest and penalties added $%s to your balance.",
            "This is your loan servicer. Another day overdue means another $%s in interest and penalties.",
            "The clock's still running on that overdue loan — $%s more in interest and penalties today.",
            "Al's Banker here: since the balance is still unpaid, we charged $%s in accrued interest and penalties.",
            "Friendly reminder (well, not that friendly): your overdue loan grew by $%s today.",
            "Your loan servicer is required to inform you: $%s in interest and penalties just hit your account.",
            "Al's Banker: the longer it sits overdue, the more it grows — today that's $%s.",
            "Bank notice: overdue balance accrued $%s in interest and penalties. Consider making a payment soon."
    );

    private static final List<String> SAVINGS_INTEREST = List.of(
            "Al's Banker: your savings account earned $%s in interest today.",
            "Good news from the bank — your savings grew by $%s overnight.",
            "Your savings servicer here: $%s in interest was just credited to your account.",
            "Al's Banker: keep it up! Your savings picked up another $%s in interest."
    );

    private static final List<String> CREDIT_CARD_INTEREST = List.of(
            "Al's Banker: your credit card accrued $%s in interest today.",
            "Card notice: carrying a balance cost you $%s in interest today.",
            "Al's Banker here — your revolving balance grew by $%s in interest.",
            "Your credit card servicer: $%s in interest was added to your card balance."
    );

    private static final List<String> LOAN_APPROVED = List.of(
            "Al's Banker: your loan of $%s has been approved and deposited, split into %s payments.",
            "Congratulations — a $%s loan just landed in your account, split into %s payments.",
            "Al's Banker here: $%s approved and deposited, payable over %s installments.",
            "Loan office notice: your request for $%s was approved. Expect %s payments."
    );

    private static final List<String> LOAN_PAYMENT = List.of(
            "Al's Banker: payment of $%s received. Remaining balance: $%s.",
            "Thanks! Your $%s payment went through. You still owe $%s.",
            "Al's Banker here: $%s applied to your loan, $%s left to go.",
            "Payment confirmed: $%s received, $%s remaining on your loan."
    );

    private static final List<String> LOAN_PAID_OFF = List.of(
            "Al's Banker: that was your final payment — your loan is fully paid off!",
            "Congratulations, your loan is paid in full! Al's Banker thanks you for your business.",
            "Al's Banker here: balance cleared. Your loan is officially paid off.",
            "Loan closed — nice work paying that off in full!"
    );

    public static String lateFee(double amount) {
        return format(LATE_FEE, amount);
    }

    public static String dailyPenalty(double amount) {
        return format(DAILY_PENALTY, amount);
    }

    public static String savingsInterest(double amount) {
        return format(SAVINGS_INTEREST, amount);
    }

    public static String creditCardInterest(double amount) {
        return format(CREDIT_CARD_INTEREST, amount);
    }

    public static String loanApproved(double amount, int installments) {
        String template = LOAN_APPROVED.get(ThreadLocalRandom.current().nextInt(LOAN_APPROVED.size()));
        return String.format(template, String.format("%.2f", amount), installments);
    }

    public static String loanPayment(double amount, double remaining, boolean paidOff) {
        String template = LOAN_PAYMENT.get(ThreadLocalRandom.current().nextInt(LOAN_PAYMENT.size()));
        String message = String.format(template, String.format("%.2f", amount), String.format("%.2f", remaining));
        if (paidOff) {
            message += " " + LOAN_PAID_OFF.get(ThreadLocalRandom.current().nextInt(LOAN_PAID_OFF.size()));
        }
        return message;
    }

    private static String format(List<String> pool, double amount) {
        String template = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        return String.format(template, String.format("%.2f", amount));
    }
}
