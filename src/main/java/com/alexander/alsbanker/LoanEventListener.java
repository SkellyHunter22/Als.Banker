package com.alexander.alsbanker;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public class LoanEventListener {

    public static void notifyLoanCreated(Player p) {
        String uuid = p.getUniqueId().toString();
        boolean linked = DiscordLinkManager.isLinked(uuid);

        if (!linked) {
            p.sendMessage(ChatColor.YELLOW + "Tip: use /linkdiscord to get loan notifications on Discord too.");
        }

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "phone_loan_created " + p.getName());

        if (linked) {
            DiscordNotifier.dm(uuid, "You have taken out a new loan.");
        }
    }

    public static void notifyMinecraft(String uuid) {
        Player p = Bukkit.getPlayer(java.util.UUID.fromString(uuid));
        if (p != null && p.isOnline()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "phone_overdue_notification " + p.getName());
        }
    }
}
