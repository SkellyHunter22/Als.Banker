package com.alexander.alsbanker;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public class LoanEventListener {

    public static void notifyLoanCreated(Player p, String message) {
        String uuid = p.getUniqueId().toString();
        boolean linked = DiscordLinkManager.isLinked(uuid);

        if (!linked) {
            p.sendMessage(ChatColor.YELLOW + "Tip: use /linkdiscord to get loan notifications on Discord too.");
        }

        PhoneAlertBridge.send(p, message);

        if (linked) {
            DiscordNotifier.dm(uuid, message);
        }
    }

    public static void notify(String uuid, String message) {
        PhoneAlertBridge.send(uuid, message);
        DiscordNotifier.dm(uuid, message);
    }
}
