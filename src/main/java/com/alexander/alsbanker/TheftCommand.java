package com.alexander.alsbanker;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /steal <player> — attempt to pickpocket another online player's Vault balance.
 * Success isn't guaranteed by the command itself: it opens a timing minigame
 * (TheftMinigameManager) and the actual theft only happens if that's won.
 */
public class TheftCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use /steal.");
            return true;
        }

        Player thief = (Player) sender;

        if (!AlsBanker.get().getConfig().getBoolean("theft.enabled", true)) {
            thief.sendMessage(ChatColor.RED + "Theft is disabled on this server.");
            return true;
        }

        if (args.length != 1) {
            thief.sendMessage(ChatColor.RED + "Usage: /steal <player>");
            return true;
        }

        Player victim = Bukkit.getPlayerExact(args[0]);
        if (victim == null) {
            thief.sendMessage(ChatColor.RED + "Player '" + args[0] + "' is not online.");
            return true;
        }

        if (victim.getUniqueId().equals(thief.getUniqueId())) {
            thief.sendMessage(ChatColor.RED + "You can't steal from yourself.");
            return true;
        }

        long thiefCooldown = TheftMinigameManager.remainingThiefCooldownSeconds(thief.getUniqueId());
        if (thiefCooldown > 0) {
            thief.sendMessage(ChatColor.RED + "You need to lay low for " + thiefCooldown + " more second(s) before trying again.");
            return true;
        }

        long victimCooldown = TheftMinigameManager.remainingVictimCooldownSeconds(victim.getUniqueId());
        if (victimCooldown > 0) {
            thief.sendMessage(ChatColor.RED + victim.getName() + " was already robbed recently; try someone else.");
            return true;
        }

        if (TheftMinigameManager.hasActiveSession(thief.getUniqueId())) {
            thief.sendMessage(ChatColor.RED + "You're already in the middle of a heist.");
            return true;
        }

        double maxDistance = AlsBanker.get().getConfig().getDouble("theft.max_distance", 5.0);
        if (!thief.getWorld().equals(victim.getWorld()) || thief.getLocation().distance(victim.getLocation()) > maxDistance) {
            thief.sendMessage(ChatColor.RED + "You need to get closer to " + victim.getName() + " to try that.");
            return true;
        }

        Economy econ = VaultEconomy.get();
        if (econ == null) {
            thief.sendMessage(ChatColor.RED + "Economy is unavailable; cannot attempt theft right now.");
            return true;
        }

        double minTargetBalance = AlsBanker.get().getConfig().getDouble("theft.min_target_balance", 100.0);
        double victimBalance = econ.getBalance(victim);
        if (victimBalance < minTargetBalance) {
            thief.sendMessage(ChatColor.RED + victim.getName() + " doesn't have enough on them to be worth the risk.");
            return true;
        }

        double stealPercent = AlsBanker.get().getConfig().getDouble("theft.steal_percent", 0.15);
        double maxStealAmount = AlsBanker.get().getConfig().getDouble("theft.max_steal_amount", 500.0);
        double stakeAmount = Math.min(victimBalance * stealPercent, maxStealAmount);

        TheftMinigameManager.start(thief, victim, stakeAmount);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length != 1) return new ArrayList<>();
        List<String> matches = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) matches.add(p.getName());
        }
        return matches;
    }
}
