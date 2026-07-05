package com.alexander.alsbanker;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public class GuiListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        // Spigot 1.20+ uses InventoryView#getTitle()
        String title = ChatColor.stripColor(e.getView().getTitle());
        if (!title.equals("Loan Scheduler Admin"))
            return;

        e.setCancelled(true);

        Player p = (Player) e.getWhoClicked();

        switch (e.getSlot()) {
            case 4: // Run Now
                p.sendMessage(ChatColor.GREEN + "Cycle started in background...");
                p.closeInventory();
                Bukkit.getScheduler().runTaskAsynchronously(AlsBanker.get(), SchedulerEngine::runCycle);
                break;

            case 5: // Reload Config
                AlsBanker.get().reloadConfig();
                DiscordLinkManager.load();
                p.sendMessage(ChatColor.AQUA + "Config reloaded.");
                p.closeInventory();
                break;

            default:
                break;
        }
    }
}
