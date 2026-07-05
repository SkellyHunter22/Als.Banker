package com.alexander.alsbanker.bank;

import com.alexander.alsbanker.AlsBanker;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.geysermc.floodgate.api.FloodgateApi;

public class BankGuiManager implements Listener {
    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        if (!e.getView().getTitle().contains("Bank App")) return;
        e.setCancelled(true);
        Player p = (Player) e.getWhoClicked();

        if (e.getCurrentItem().getType() == Material.GOLD_INGOT) {
            p.closeInventory();
            if (Bukkit.getPluginManager().isPluginEnabled("floodgate") &&
                    FloodgateApi.getInstance().isFloodgatePlayer(p.getUniqueId())) {
                BedrockFormBridge.sendTransferForm(p);
            } else {
                JavaTargetGUI.openSelector(p);
            }
        }
    }
}