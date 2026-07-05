package com.alexander.alsbanker;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Placeholders:
 *  %alsbanker_linked%       - "Yes"/"No"
 *  %alsbanker_discord_id%   - linked Discord ID, or "Not linked"
 *  %alsbanker_interval%     - configured cycle interval, in minutes
 *  %alsbanker_penalty_rate% - configured penalty rate
 *  %alsbanker_outstanding%  - player's total outstanding loan balance
 *  %alsbanker_active_loans% - player's active loan count
 */
public class AlsBankerPlaceholderExpansion extends PlaceholderExpansion {

    // DB lookups are refreshed asynchronously and served from cache so that
    // a synchronous placeholder request (e.g. from a scoreboard) never blocks the main thread on JDBC.
    private final Map<String, Double> outstandingCache = new ConcurrentHashMap<>();
    private final Map<String, Integer> activeLoansCache = new ConcurrentHashMap<>();

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
}
