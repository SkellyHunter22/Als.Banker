package com.alexander.alsbanker.api;

import java.util.List;
import java.util.UUID;

public interface BankingAPI {

    double getBalance(UUID uuid);

    boolean withdraw(UUID uuid, double amount);

    boolean deposit(UUID uuid, double amount);

    /**
     * Active loan summary for a player, for rendering a bank-app screen.
     * Never null; check {@link LoanInfo#hasActiveLoan()} first.
     */
    LoanInfo getLoanInfo(UUID uuid);

    /**
     * Most recent transactions for a player, newest first.
     */
    List<Transaction> getTransactionHistory(UUID uuid, int limit);
}
