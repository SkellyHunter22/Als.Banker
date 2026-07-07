package com.alexander.alsbanker;

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

/**
 * /creditcard apply|info|charge|pay — a revolving credit line gated by
 * CreditScoreService, separate from the fixed-installment /loan.
 */
public class CreditCardCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList("apply", "info", "charge", "pay");

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use /creditcard.");
            return true;
        }
        Player p = (Player) sender;

        if (args.length == 0) {
            p.sendMessage(ChatColor.YELLOW + "/creditcard apply");
            p.sendMessage(ChatColor.YELLOW + "/creditcard info");
            p.sendMessage(ChatColor.YELLOW + "/creditcard charge <amount>");
            p.sendMessage(ChatColor.YELLOW + "/creditcard pay <amount>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "apply":
                apply(p);
                return true;
            case "info":
                info(p);
                return true;
            case "charge":
                if (args.length != 2) {
                    p.sendMessage(ChatColor.RED + "Usage: /creditcard charge <amount>");
                    return true;
                }
                double chargeAmount = parseAmount(p, args[1]);
                if (!Double.isNaN(chargeAmount)) charge(p, chargeAmount);
                return true;
            case "pay":
                if (args.length != 2) {
                    p.sendMessage(ChatColor.RED + "Usage: /creditcard pay <amount>");
                    return true;
                }
                double payAmount = parseAmount(p, args[1]);
                if (!Double.isNaN(payAmount)) pay(p, payAmount);
                return true;
            default:
                p.sendMessage(ChatColor.RED + "Unknown /creditcard subcommand.");
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
        return new ArrayList<>();
    }

    private static void apply(Player p) {
        String uuid = p.getUniqueId().toString();

        Bukkit.getScheduler().runTaskAsynchronously(AlsBanker.get(), () -> {
            try {
                if (CreditCardDataService.hasCard(uuid)) {
                    Bukkit.getScheduler().runTask(AlsBanker.get(), () ->
                            p.sendMessage(ChatColor.RED + "You already have a credit card. Check /creditcard info."));
                    return;
                }

                int score = CreditScoreService.getScore(uuid);
                int minScore = AlsBanker.get().getConfig().getInt("credit.min_score_for_card", 500);
                if (score < minScore) {
                    Bukkit.getScheduler().runTask(AlsBanker.get(), () ->
                            p.sendMessage(ChatColor.RED + "Your credit score (" + score +
                                    ") is too low for a card. You need at least " + minScore + "."));
                    return;
                }

                double limit = CreditScoreService.cardLimitForScore(score);
                double apr = AlsBanker.get().getConfig().getDouble("credit_card.daily_apr", 0.001);
                CreditCardDataService.openCard(uuid, limit, apr);

                Bukkit.getScheduler().runTask(AlsBanker.get(), () -> {
                    p.sendMessage(ChatColor.GREEN + "Approved! Credit limit: $" + String.format("%.2f", limit) +
                            " at " + String.format("%.3f", apr * 100) + "% daily interest on any carried balance.");
                    LoanEventListener.notifyLoanCreated(p, "Your credit card application was approved for a $" +
                            String.format("%.2f", limit) + " limit.");
                });
            } catch (SQLException e) {
                AlsBanker.get().getLogger().severe("Credit card application failed: " + e.getMessage());
                Bukkit.getScheduler().runTask(AlsBanker.get(), () ->
                        p.sendMessage(ChatColor.RED + "Failed to apply due to a database error."));
            }
        });
    }

    private static void info(Player p) {
        String uuid = p.getUniqueId().toString();

        Bukkit.getScheduler().runTaskAsynchronously(AlsBanker.get(), () -> {
            try {
                CreditCardDataService.Card card = CreditCardDataService.getCard(uuid);
                Bukkit.getScheduler().runTask(AlsBanker.get(), () -> {
                    if (card == null) {
                        p.sendMessage(ChatColor.YELLOW + "You don't have a credit card yet. Try /creditcard apply.");
                        return;
                    }
                    p.sendMessage(ChatColor.GOLD + "=== Your Credit Card ===");
                    p.sendMessage(ChatColor.YELLOW + "Limit: $" + String.format("%.2f", card.limit()));
                    p.sendMessage(ChatColor.YELLOW + "Balance owed: $" + String.format("%.2f", card.balance()));
                    p.sendMessage(ChatColor.YELLOW + "Available: $" + String.format("%.2f", card.available()));
                    p.sendMessage(ChatColor.YELLOW + "Utilization: " + String.format("%.0f", card.utilization() * 100) + "%");
                });
            } catch (SQLException e) {
                AlsBanker.get().getLogger().severe("Credit card info lookup failed: " + e.getMessage());
                Bukkit.getScheduler().runTask(AlsBanker.get(), () ->
                        p.sendMessage(ChatColor.RED + "Failed to load card info due to a database error."));
            }
        });
    }

    private static void charge(Player p, double amount) {
        Economy econ = VaultEconomy.get();
        if (econ == null) {
            p.sendMessage(ChatColor.RED + "Economy is unavailable; cannot process charge right now.");
            return;
        }

        String uuid = p.getUniqueId().toString();

        if (!PlayerActionLock.tryLock(uuid)) {
            p.sendMessage(ChatColor.RED + "Please wait for your previous request to finish.");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(AlsBanker.get(), () -> {
            try {
                boolean success = CreditCardDataService.charge(uuid, amount);
                if (!success) {
                    Bukkit.getScheduler().runTask(AlsBanker.get(), () ->
                            p.sendMessage(ChatColor.RED + "That charge would exceed your available credit, " +
                                    "or you don't have a card yet."));
                    return;
                }

                TransactionService.record(uuid, "CREDIT_CARD_CHARGE", amount, amount, "Charged to credit card");

                Bukkit.getScheduler().runTask(AlsBanker.get(), () -> {
                    econ.depositPlayer(p, amount);
                    p.sendMessage(ChatColor.GREEN + "Charged $" + String.format("%.2f", amount) + " to your card.");
                });
            } catch (SQLException e) {
                AlsBanker.get().getLogger().severe("Credit card charge failed: " + e.getMessage());
                Bukkit.getScheduler().runTask(AlsBanker.get(), () ->
                        p.sendMessage(ChatColor.RED + "Failed to charge due to a database error."));
            } finally {
                PlayerActionLock.unlock(uuid);
            }
        });
    }

    private static void pay(Player p, double amount) {
        Economy econ = VaultEconomy.get();
        if (econ == null) {
            p.sendMessage(ChatColor.RED + "Economy is unavailable; cannot process payment right now.");
            return;
        }

        if (!econ.has(p, amount)) {
            p.sendMessage(ChatColor.RED + "You don't have $" + String.format("%.2f", amount) + " to pay.");
            return;
        }

        String uuid = p.getUniqueId().toString();

        if (!PlayerActionLock.tryLock(uuid)) {
            p.sendMessage(ChatColor.RED + "Please wait for your previous request to finish.");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(AlsBanker.get(), () -> {
            try {
                CreditCardDataService.Card card = CreditCardDataService.getCard(uuid);
                if (card == null) {
                    Bukkit.getScheduler().runTask(AlsBanker.get(), () ->
                            p.sendMessage(ChatColor.RED + "You don't have a credit card to pay off."));
                    return;
                }

                double applied = Math.min(amount, card.balance());
                Double remaining = CreditCardDataService.pay(uuid, applied);
                if (remaining == null) {
                    Bukkit.getScheduler().runTask(AlsBanker.get(), () ->
                            p.sendMessage(ChatColor.RED + "You don't have a credit card to pay off."));
                    return;
                }

                TransactionService.record(uuid, "CREDIT_CARD_PAYMENT", applied, remaining, "Credit card payment");

                int gain = AlsBanker.get().getConfig().getInt("credit.score_gain_card_payment", 3);
                int newScore = CreditScoreService.adjustScore(uuid, gain);

                Bukkit.getScheduler().runTask(AlsBanker.get(), () -> {
                    econ.withdrawPlayer(p, applied);
                    p.sendMessage(ChatColor.GREEN + "Paid $" + String.format("%.2f", applied) +
                            " towards your card. Remaining balance: $" + String.format("%.2f", remaining));
                    p.sendMessage(ChatColor.AQUA + "Credit score: " + newScore + " (" + CreditScoreService.rating(newScore) + ")");
                });
            } catch (SQLException e) {
                AlsBanker.get().getLogger().severe("Credit card payment failed: " + e.getMessage());
                Bukkit.getScheduler().runTask(AlsBanker.get(), () ->
                        p.sendMessage(ChatColor.RED + "Failed to process payment due to a database error."));
            } finally {
                PlayerActionLock.unlock(uuid);
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
        if (!Double.isFinite(amount) || amount <= 0) {
            p.sendMessage(ChatColor.RED + "Amount must be a positive, finite number.");
            return Double.NaN;
        }
        return amount;
    }
}
