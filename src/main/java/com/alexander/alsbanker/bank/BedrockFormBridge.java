package com.alexander.alsbanker.bank;

import com.alexander.alsbanker.AlsBanker;
import com.alexander.alsbanker.TransactionService;
import com.alexander.alsbanker.VaultEconomy;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.floodgate.api.FloodgateApi;

public class BedrockFormBridge {
    public static void sendTransferForm(Player player) {
        CustomForm form = CustomForm.builder()
                .title("AllyCraft Bank")
                .input("Target Player", "Name")
                .input("Amount", "0.00")
                .validResultHandler(res -> Bukkit.getScheduler().runTask(AlsBanker.get(), () ->
                        handleTransfer(player, res.asInput(0), res.asInput(1))))
                .build();
        FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
    }

    private static void handleTransfer(Player sender, String targetName, String rawAmount) {
        if (targetName == null || targetName.isBlank()) {
            sender.sendMessage(ChatColor.RED + "You must enter a target player.");
            return;
        }

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player '" + targetName + "' is not online.");
            return;
        }

        if (target.getUniqueId().equals(sender.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + "You can't transfer money to yourself.");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(rawAmount);
        } catch (NumberFormatException | NullPointerException e) {
            sender.sendMessage(ChatColor.RED + "Amount must be a number.");
            return;
        }
        if (!Double.isFinite(amount) || amount <= 0) {
            sender.sendMessage(ChatColor.RED + "Amount must be a positive, finite number.");
            return;
        }

        Economy econ = VaultEconomy.get();
        if (econ == null) {
            sender.sendMessage(ChatColor.RED + "Economy is unavailable; cannot process transfer right now.");
            return;
        }

        if (!econ.has(sender, amount)) {
            sender.sendMessage(ChatColor.RED + "You don't have $" + String.format("%.2f", amount) + " to send.");
            return;
        }

        econ.withdrawPlayer(sender, amount);
        econ.depositPlayer(target, amount);

        String senderUuid = sender.getUniqueId().toString();
        String targetUuid = target.getUniqueId().toString();
        TransactionService.record(senderUuid, "TRANSFER_SENT", amount, econ.getBalance(sender),
                "Sent to " + target.getName());
        TransactionService.record(targetUuid, "TRANSFER_RECEIVED", amount, econ.getBalance(target),
                "Received from " + sender.getName());

        sender.sendMessage(ChatColor.GREEN + "Sent $" + String.format("%.2f", amount) + " to " + target.getName() + ".");
        target.sendMessage(ChatColor.GREEN + "Received $" + String.format("%.2f", amount) + " from " + sender.getName() + ".");
    }
}