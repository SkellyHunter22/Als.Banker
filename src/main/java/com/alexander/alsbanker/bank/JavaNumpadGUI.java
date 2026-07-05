package com.alexander.alsbanker.bank;

import com.alexander.alsbanker.AlsBanker;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class JavaNumpadGUI {
    private static final Map<UUID, Double> pendingAmounts = new HashMap<>();

    public static void openNumpad(Player player) {
        pendingAmounts.put(player.getUniqueId(), 0.0);
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_GRAY + "Bank Pad - Amount");

        // Populate GUI with items
        inv.setItem(4, item(Material.PAPER, ChatColor.YELLOW + "Current Amount: $0.00"));
        inv.setItem(11, item(Material.LIME_STAINED_GLASS_PANE, ChatColor.GREEN + "+$100"));
        inv.setItem(15, item(Material.RED_STAINED_GLASS_PANE, ChatColor.RED + "-$100"));
        inv.setItem(26, item(Material.EMERALD_BLOCK, ChatColor.GREEN + "CONFIRM"));

        player.openInventory(inv);
    }

    private static ItemStack item(Material m, String name) {
        ItemStack i = new ItemStack(m);
        ItemMeta meta = i.getItemMeta();
        meta.setDisplayName(name);
        i.setItemMeta(meta);
        return i;
    }
}