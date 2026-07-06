package com.alexander.alsbanker.api;

/**
 * Savings account summary for a player, for rendering a bank-app screen.
 *
 * @param dailyInterestRate e.g. 0.01 for 1%/day, straight from config.yml's savings.interest_rate
 */
public record SavingsInfo(boolean available, double balance, double dailyInterestRate) {
    public static final SavingsInfo NONE = new SavingsInfo(false, 0, 0);
}
