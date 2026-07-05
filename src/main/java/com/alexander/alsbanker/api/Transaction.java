package com.alexander.alsbanker.api;

import java.time.LocalDateTime;

/**
 * A single line item for a player's transaction history screen.
 */
public class Transaction {

    private final LocalDateTime timestamp;
    private final String type;
    private final double amount;
    private final double balanceAfter;
    private final String description;

    public Transaction(LocalDateTime timestamp, String type, double amount, double balanceAfter, String description) {
        this.timestamp = timestamp;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.description = description;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getType() {
        return type;
    }

    public double getAmount() {
        return amount;
    }

    public double getBalanceAfter() {
        return balanceAfter;
    }

    public String getDescription() {
        return description;
    }
}
