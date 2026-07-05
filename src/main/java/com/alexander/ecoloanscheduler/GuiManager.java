package com.alexander.ecoloanscheduler;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;

public class GuiManager {

    public static void register() {
        Bukkit.getPluginManager().registerEvents(new GuiListener(), EcoLoanScheduler.get());
    }

    public static void open(Player p) {
        Inventory inv = Bukkit.createInventory(null, 9, ChatColor.BLUE + "Loan Scheduler Admin");

        inv.setItem(3, item(Material.PAPER, ChatColor.YELLOW + "Interval: " +
                EcoLoanScheduler.get().getConfig().getInt("interval_minutes")));

        inv.setItem(4, item(Material.EMERALD, ChatColor.GREEN + "Run Now"));
        inv.setItem(5, item(Material.REDSTONE, ChatColor.AQUA + "Reload Config"));

        p.openInventory(inv);
    }

    private static ItemStack item(Material m, String name) {
        ItemStack i = new ItemStack(m);
        ItemMeta meta = i.getItemMeta();
        meta.setDisplayName(name);
        i.setItemMeta(meta);
        return i;
    }
}
