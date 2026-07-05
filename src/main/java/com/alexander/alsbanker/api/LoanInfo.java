package com.alexander.alsbanker.api;

import java.time.LocalDate;

/**
 * Snapshot of a player's active loan, meant for rendering a bank-app style screen.
 */
public class LoanInfo {

    private final double outstanding;
    private final LocalDate nextDueDate;
    private final double nextAmountDue;

    public LoanInfo(double outstanding, LocalDate nextDueDate, double nextAmountDue) {
        this.outstanding = outstanding;
        this.nextDueDate = nextDueDate;
        this.nextAmountDue = nextAmountDue;
    }

    public boolean hasActiveLoan() {
        return outstanding > 0;
    }

    public double getOutstanding() {
        return outstanding;
    }

    public LocalDate getNextDueDate() {
        return nextDueDate;
    }

    public double getNextAmountDue() {
        return nextAmountDue;
    }
}
