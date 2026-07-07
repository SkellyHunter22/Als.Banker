package com.alexander.alsbanker;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Set;

/**
 * Logs every use of an AlsBanker command (name + UUID + full command line) to
 * the audit trail, regardless of which specific executor handles it.
 */
public class CommandAuditListener implements Listener {

    private static final Set<String> OWNED_LABELS = Set.of(
            "loan", "loanscheduler", "savings", "stocks", "steal",
            "creditcard", "linkdiscord", "unlinkdiscord");

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message.length() < 2) return;

        String withoutSlash = message.substring(1);
        String label = withoutSlash.split(" ", 2)[0].toLowerCase();
        if (!OWNED_LABELS.contains(label)) return;

        AuditLogger.log(event.getPlayer().getUniqueId(), "command", label, message);
    }
}
