package com.alexander.alsbanker;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

public class TheftGuiListener implements Listener {

    private static final String TITLE = ChatColor.DARK_RED + "Pick the Pocket...";

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!e.getView().getTitle().equals(TITLE)) return;
        e.setCancelled(true);

        if (!(e.getWhoClicked() instanceof Player)) return;
        Player thief = (Player) e.getWhoClicked();

        // rawSlot < 18 means the click landed in the minigame's own inventory, not the
        // player's own inventory (which shares the same InventoryView while this is open).
        if (e.getRawSlot() >= 18) return;

        TheftMinigameManager.handleClick(thief, e.getRawSlot());
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!e.getView().getTitle().equals(TITLE)) return;
        if (!(e.getPlayer() instanceof Player)) return;
        TheftMinigameManager.handleClose((Player) e.getPlayer());
    }
}
