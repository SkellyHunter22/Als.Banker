package com.alexander.alsbanker;

import com.alexander.alsbanker.bank.SavingsGuiManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SavingsCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList("deposit", "withdraw", "info", "gui");
    private static final List<String> AMOUNT_SUGGESTIONS = Arrays.asList("100", "250", "500", "1000");

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use /savings.");
            return true;
        }
        Player p = (Player) sender;

        if (args.length == 0) {
            SavingsGuiManager.openMenu(p);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "deposit":
                if (args.length != 2) {
                    p.sendMessage(ChatColor.RED + "Usage: /savings deposit <amount>");
                    return true;
                }
                double depositAmount = parseAmount(p, args[1]);
                if (!Double.isNaN(depositAmount)) deposit(p, depositAmount);
                return true;
            case "withdraw":
                if (args.length != 2) {
                    p.sendMessage(ChatColor.RED + "Usage: /savings withdraw <amount>");
                    return true;
                }
                double withdrawAmount = parseAmount(p, args[1]);
                if (!Double.isNaN(withdrawAmount)) withdraw(p, withdrawAmount);
                return true;
            case "info":
                info(p);
                return true;
            case "gui":
                SavingsGuiManager.openMenu(p);
                return true;
            default:
                p.sendMessage(ChatColor.RED + "Unknown /savings subcommand.");
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            List<String> matches = new ArrayList<>();
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(args[0].toLowerCase())) matches.add(sub);
            }
            return matches;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("deposit") || args[0].equalsIgnoreCase("withdraw"))) {
            List<String> matches = new ArrayList<>();
            for (String amount : AMOUNT_SUGGESTIONS) {
                if (amount.startsWith(args[1])) matches.add(amount);
            }
            return matches;
        }
        return new ArrayList<>();
    }

    public static void deposit(Player p, double amount) {
        Economy econ = VaultEconomy.get();
        if (econ == null) {
            p.sendMessage(ChatColor.RED + "Economy is unavailable; cannot process deposit right now.");
            return;
        }
        if (!econ.has(p, amount)) {
            p.sendMessage(ChatColor.RED + "You don't have $" + String.format("%.2f", amount) + " to deposit.");
            return;
        }

        String uuid = p.getUniqueId().toString();
        Bukkit.getScheduler().runTaskAsynchronously(AlsBanker.get(), () -> {
            try {
                SavingsDataService.deposit(uuid, amount);
                double newBalance = SavingsDataService.getBalance(uuid);
                TransactionService.record(uuid, "SAVINGS_DEPOSIT", amount, newBalance, "Deposit into savings");

                Bukkit.getScheduler().runTask(AlsBanker.get(), () -> {
                    econ.withdrawPlayer(p, amount);
                    p.sendMessage(ChatColor.GREEN + "Deposited $" + String.format("%.2f", amount) +
                            " into savings. New savings balance: $" + String.format("%.2f", newBalance));
                });
            } catch (SQLException e) {
                AlsBanker.get().getLogger().severe("Savings deposit failed: " + e.getMessage());
                Bukkit.getScheduler().runTask(AlsBanker.get(), () ->
                        p.sendMessage(ChatColor.RED + "Failed to deposit due to a database error."));
            }
        });
    }

    public static void withdraw(Player p, double amount) {
        Economy econ = VaultEconomy.get();
        if (econ == null) {
            p.sendMessage(ChatColor.RED + "Economy is unavailable; cannot process withdrawal right now.");
            return;
        }

        String uuid = p.getUniqueId().toString();
        Bukkit.getScheduler().runTaskAsynchronously(AlsBanker.get(), () -> {
            try {
                boolean success = SavingsDataService.withdraw(uuid, amount);
                if (!success) {
                    Bukkit.getScheduler().runTask(AlsBanker.get(), () ->
                            p.sendMessage(ChatColor.RED + "You don't have $" + String.format("%.2f", amount) +
                                    " in savings."));
                    return;
                }
                double newBalance = SavingsDataService.getBalance(uuid);
                TransactionService.record(uuid, "SAVINGS_WITHDRAWAL", amount, newBalance, "Withdrawal from savings");

                Bukkit.getScheduler().runTask(AlsBanker.get(), () -> {
                    econ.depositPlayer(p, amount);
                    p.sendMessage(ChatColor.GREEN + "Withdrew $" + String.format("%.2f", amount) +
                            " from savings. Remaining savings balance: $" + String.format("%.2f", newBalance));
                });
            } catch (SQLException e) {
                AlsBanker.get().getLogger().severe("Savings withdrawal failed: " + e.getMessage());
                Bukkit.getScheduler().runTask(AlsBanker.get(), () ->
                        p.sendMessage(ChatColor.RED + "Failed to withdraw due to a database error."));
            }
        });
    }

    public static void info(Player p) {
        String uuid = p.getUniqueId().toString();
        double rate = AlsBanker.get().getConfig().getDouble("savings.interest_rate", 0.01);

        Bukkit.getScheduler().runTaskAsynchronously(AlsBanker.get(), () -> {
            try {
                double balance = SavingsDataService.getBalance(uuid);
                Bukkit.getScheduler().runTask(AlsBanker.get(), () -> {
                    p.sendMessage(ChatColor.GOLD + "=== Your Savings ===");
                    p.sendMessage(ChatColor.YELLOW + "Balance: $" + String.format("%.2f", balance));
                    p.sendMessage(ChatColor.YELLOW + "Daily interest rate: " + String.format("%.2f", rate * 100) + "%");
                });
            } catch (SQLException e) {
                AlsBanker.get().getLogger().severe("Savings info lookup failed: " + e.getMessage());
                Bukkit.getScheduler().runTask(AlsBanker.get(), () ->
                        p.sendMessage(ChatColor.RED + "Failed to load savings info due to a database error."));
            }
        });
    }

    private static double parseAmount(Player p, String raw) {
        double amount;
        try {
            amount = Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            p.sendMessage(ChatColor.RED + "Amount must be a number.");
            return Double.NaN;
        }
        if (amount <= 0) {
            p.sendMessage(ChatColor.RED + "Amount must be positive.");
            return Double.NaN;
        }
        return amount;
    }
}
