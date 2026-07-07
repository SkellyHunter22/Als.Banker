package com.alexander.alsbanker.api;

/**
 * Credit card summary for a player, for rendering a bank-app screen.
 *
 * @param available true if the player has applied for and holds a card
 * @param limit     total credit limit
 * @param balance   currently owed
 * @param dailyApr  daily interest rate applied to any carried balance
 */
public record CreditCardInfo(boolean available, double limit, double balance, double dailyApr) {
    public static final CreditCardInfo NONE = new CreditCardInfo(false, 0, 0, 0);

    public double availableCredit() {
        return limit - balance;
    }

    public double utilization() {
        return limit <= 0 ? 0 : balance / limit;
    }
}
