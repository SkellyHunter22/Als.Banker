package com.alexander.alsbanker.api;

import java.util.List;
import java.util.UUID;

/**
 * The public surface other plugins (e.g. AllyPhone) should use to read/modify a
 * player's banking data. Do not reach into {@code com.alexander.alsbanker}'s
 * internal classes (e.g. {@code DatabaseManager}, {@code LoanDataService}) directly —
 * only this package is a supported, version-stable contract.
 *
 * <p><b>Threading:</b> every method here does a blocking JDBC round-trip and must
 * never be called from the main server thread in a hot path (a command handler is
 * fine since Bukkit expects those to be synchronous and brief; a per-tick task,
 * GUI render loop, or anything on a listener that fires frequently is not). Call
 * from an async task, or synchronously only where a short, one-off DB call is
 * acceptable (e.g. handling a single command).
 *
 * <p><b>Failure handling:</b> every method already catches its own {@code SQLException}
 * internally and falls back to a safe default (0 / empty list / {@code NONE} constant) —
 * callers never need to catch anything, but should be aware a "no data" result can also
 * mean "the database call failed," not just "this player genuinely has none."
 *
 * <p><b>Null/invalid input:</b> passing a {@code null} {@code uuid} is not supported and
 * may throw; validate on the caller's side before invoking.
 */
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

    /**
     * Savings account summary for a player, or {@link SavingsInfo#NONE} if they have none / savings is disabled.
     */
    SavingsInfo getSavingsInfo(UUID uuid);

    /**
     * Current stock portfolio for a player (empty list if they hold nothing).
     */
    List<StockHolding> getStockPortfolio(UUID uuid);

    /**
     * Credit score snapshot (score, rating, max loan amount). Never null — a player
     * who has never borrowed still has a default starting score.
     */
    CreditInfo getCreditInfo(UUID uuid);

    /**
     * Credit card summary, or {@link CreditCardInfo#NONE} if the player hasn't applied for one.
     */
    CreditCardInfo getCreditCardInfo(UUID uuid);
}
