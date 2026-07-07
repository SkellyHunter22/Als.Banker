package com.alexander.alsbanker;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Placeholders:
 *  %alsbanker_linked%              - "Yes"/"No"
 *  %alsbanker_discord_id%          - linked Discord ID, or "Not linked"
 *  %alsbanker_interval%            - configured cycle interval, in minutes
 *  %alsbanker_penalty_rate%        - configured penalty rate
 *  %alsbanker_outstanding%         - player's total outstanding loan balance
 *  %alsbanker_active_loans%        - player's active loan count
 *  %alsbanker_credit_score%        - player's credit score (300-850)
 *  %alsbanker_credit_rating%       - "Poor"/"Fair"/"Good"/"Excellent"
 *  %alsbanker_credit_max_loan%     - largest loan the player currently qualifies for
 *  %alsbanker_creditcard_limit%    - credit card limit, or "0.00" if none
 *  %alsbanker_creditcard_balance%  - credit card balance owed
 *  %alsbanker_creditcard_available%  - remaining available credit
 *  %alsbanker_creditcard_utilization% - balance/limit as a percentage, e.g. "42"
 */
public class AlsBankerPlaceholderExpansion extends PlaceholderExpansion {

    // DB lookups are refreshed asynchronously and served from cache so that
    // a synchronous placeholder request (e.g. from a scoreboard) never blocks the main thread on JDBC.
    private final Map<String, Double> outstandingCache = new ConcurrentHashMap<>();
    private final Map<String, Integer> activeLoansCache = new ConcurrentHashMap<>();
    private final Map<String, Integer> creditScoreCache = new ConcurrentHashMap<>();
    private final Map<String, double[]> creditCardCache = new ConcurrentHashMap<>(); // [limit, balance]

    @Override
    public String getIdentifier() {
        return "alsbanker";
    }

    @Override
    public String getAuthor() {
        return "Alexander";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) return "";

        String uuid = player.getUniqueId().toString();

        switch (params.toLowerCase()) {
            case "linked":
                return DiscordLinkManager.isLinked(uuid) ? "Yes" : "No";

            case "discord_id":
                String id = DiscordLinkManager.isLinked(uuid) ? DiscordLinkManager.getDiscordId(uuid) : null;
                return id != null ? id : "Not linked";

            case "interval":
                return String.valueOf(AlsBanker.get().getConfig().getInt("interval_minutes"));

            case "penalty_rate":
                return String.valueOf(AlsBanker.get().getConfig().getDouble("penalty_rate"));

            case "outstanding":
                refreshPlayerStats(uuid);
                return String.format("%.2f", outstandingCache.getOrDefault(uuid, 0.0));

            case "active_loans":
                refreshPlayerStats(uuid);
                return String.valueOf(activeLoansCache.getOrDefault(uuid, 0));

            case "credit_score": {
                refreshCreditScore(uuid);
                int score = creditScoreCache.getOrDefault(uuid, AlsBanker.get().getConfig().getInt("credit.starting_score", 650));
                return String.valueOf(score);
            }

            case "credit_rating": {
                refreshCreditScore(uuid);
                int score = creditScoreCache.getOrDefault(uuid, AlsBanker.get().getConfig().getInt("credit.starting_score", 650));
                return CreditScoreService.rating(score);
            }

            case "credit_max_loan": {
                refreshCreditScore(uuid);
                int score = creditScoreCache.getOrDefault(uuid, AlsBanker.get().getConfig().getInt("credit.starting_score", 650));
                return String.format("%.2f", CreditScoreService.maxLoanForScore(score));
            }

            case "creditcard_limit":
                refreshCreditCard(uuid);
                return String.format("%.2f", creditCardCache.getOrDefault(uuid, new double[]{0, 0})[0]);

            case "creditcard_balance":
                refreshCreditCard(uuid);
                return String.format("%.2f", creditCardCache.getOrDefault(uuid, new double[]{0, 0})[1]);

            case "creditcard_available": {
                refreshCreditCard(uuid);
                double[] card = creditCardCache.getOrDefault(uuid, new double[]{0, 0});
                return String.format("%.2f", card[0] - card[1]);
            }

            case "creditcard_utilization": {
                refreshCreditCard(uuid);
                double[] card = creditCardCache.getOrDefault(uuid, new double[]{0, 0});
                return String.valueOf(card[0] <= 0 ? 0 : Math.round(card[1] / card[0] * 100));
            }

            default:
                return null;
        }
    }

    private void refreshPlayerStats(String uuid) {
        Bukkit.getScheduler().runTaskAsynchronously(AlsBanker.get(), () -> {
            try {
                outstandingCache.put(uuid, LoanDataService.getOutstandingBalance(uuid));
                activeLoansCache.put(uuid, LoanDataService.getActiveLoanCount(uuid));
            } catch (Exception e) {
                AlsBanker.get().getLogger().warning(
                        "Failed to refresh placeholder stats for " + uuid + ": " + e.getMessage());
            }
        });
    }

    private void refreshCreditScore(String uuid) {
        Bukkit.getScheduler().runTaskAsynchronously(AlsBanker.get(), () -> {
            try {
                creditScoreCache.put(uuid, CreditScoreService.getScore(uuid));
            } catch (Exception e) {
                AlsBanker.get().getLogger().warning(
                        "Failed to refresh credit score placeholder for " + uuid + ": " + e.getMessage());
            }
        });
    }

    private void refreshCreditCard(String uuid) {
        Bukkit.getScheduler().runTaskAsynchronously(AlsBanker.get(), () -> {
            try {
                CreditCardDataService.Card card = CreditCardDataService.getCard(uuid);
                creditCardCache.put(uuid, card == null ? new double[]{0, 0} : new double[]{card.limit(), card.balance()});
            } catch (Exception e) {
                AlsBanker.get().getLogger().warning(
                        "Failed to refresh credit card placeholder for " + uuid + ": " + e.getMessage());
            }
        });
    }
}
