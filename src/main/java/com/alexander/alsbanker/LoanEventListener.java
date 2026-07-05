package com.alexander.alsbanker;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public class LoanEventListener implements Listener {

    public static void register() {
        Bukkit.getPluginManager().registerEvents(new LoanEventListener(), AlsBanker.get());
    }

    @EventHandler
    public void onLoanRequest(PlayerCommandPreprocessEvent e) {
        if (!e.getMessage().toLowerCase().startsWith("/loan request")) return;

        Player p = e.getPlayer();
        String uuid = p.getUniqueId().toString();

        boolean linked = DiscordLinkManager.isLinked(uuid);

        p.sendTitle(
                ChatColor.GOLD + "Loan Created!",
                linked ? ChatColor.GREEN + "Discord Linked" : ChatColor.RED + "Use /linkdiscord",
                10, 70, 20
        );

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
