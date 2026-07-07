package com.alexander.alsbanker.api;

/**
 * Credit score snapshot for a player, for rendering a bank-app / credit-report screen.
 *
 * @param score          300-850, see config.yml's credit section
 * @param rating         "Poor" | "Fair" | "Good" | "Excellent"
 * @param maxLoanAmount  the largest new /loan this player currently qualifies for
 */
public record CreditInfo(int score, String rating, double maxLoanAmount) {
}
