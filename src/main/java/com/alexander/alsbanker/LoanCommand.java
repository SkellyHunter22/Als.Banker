package com.alexander.alsbanker;

import com.alexander.alsbanker.api.Transaction;
import com.alexander.alsbanker.bank.LoanGuiManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LoanCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList("request", "pay", "info", "history", "gui");
    private static final List<String> AMOUNT_SUGGESTIONS = Arrays.asList("100", "250", "500", "1000");

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use /loan.");
            return true;
        }

        Player p = (Player) sender;

        if (args.length == 0) {
            LoanGuiManager.openMenu(p);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "request":
                if (args.length != 2) {
                    p.sendMessage(ChatColor.RED + "Usage: /loan request <amount>");
                    return true;
                }
                double requestAmount = parseAmount(p, args[1]);
                if (!Double.isNaN(requestAmount)) requestLoan(p, requestAmount);
                return true;
            case "pay":
                if (args.length != 2) {
                    p.sendMessage(ChatColor.RED + "Usage: /loan pay <amount>");
                    return true;
                }
                double payAmount = parseAmount(p, args[1]);
                if (!Double.isNaN(payAmount)) payLoan(p, payAmount);
                return true;
            case "info":
                handleInfo(p);
                return true;
            case "history":
                handleHistory(p);
                return true;
            case "gui":
                LoanGuiManager.openMenu(p);
                return true;
            default:
                p.sendMessage(ChatColor.RED + "Unknown /loan subcommand.");
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

        if (args.length == 2 && (args[0].equalsIgnoreCase("request") || args[0].equalsIgnoreCase("pay"))) {
            List<String> matches = new ArrayList<>();
            for (String amount : AMOUNT_SUGGESTIONS) {
                if (amount.startsWith(args[1])) matches.add(amount);
            }
            return matches;
        }

        return new ArrayList<>();
    }

    /**
     * Requests a new loan for the player. Shared by the /loan command, the in-game
     * GUI numpad, and the Bedrock form, so all three paths apply the same rules.
     */
    public static void requestLoan(Player p, double amount) {
        double maxAmount = AlsBanker.get().getConfig().getDouble("loan.max_amount", 5000.0);
        if (amount > maxAmount) {
            p.sendMessage(ChatColor.RED + "Loans are capped at " + String.format("%.2f", maxAmount) + ".");
            return;
        }

        Economy econ = VaultEconomy.get();
        if (econ == null) {
            p.sendMessage(ChatColor.RED + "Economy is unavailable; cannot process loan right now.");
            return;
        }

        String uuid = p.getUniqueId().toString();

        Bukkit.getScheduler().runTaskAsynchronously(AlsBanker.get(), () -> {
            try {
                if (LoanDataService.getActiveLoanCount(uuid) > 0) {
                    Bukkit.getScheduler().runTask(AlsBanker.get(), () ->
                            p.sendMessage(ChatColor.RED + "You already have an active loan. Pay it off before requesting another."));
                    return;
                }

                double interestRate = AlsBanker.get().getConfig().getDouble("loan.interest_rate", 0.05);
                int installments = AlsBanker.get().getConfig().getInt("loan.installments", 4);
                int intervalDays = AlsBanker.get().getConfig().getInt("loan.installment_interval_days", 7);

                LoanDataService.createLoan(uuid, amount, interestRate, installments, intervalDays);

                TransactionService.record(uuid, "LOAN_REQUEST", amount, amount,
                        "Loan approved, split into " + installments + " payments");

                Bukkit.getScheduler().runTask(AlsBanker.get(), () -> {
                    econ.depositPlayer(p, amount);
                    p.sendTitle(ChatColor.GOLD + "Loan Approved!",
                            ChatColor.GREEN + String.format("$%.2f deposited", amount), 10, 70, 20);
                    p.sendMessage(ChatColor.GREEN + "Your loan of $" + String.format("%.2f", amount) +
                            " has been approved, split into " + installments + " payments.");
                    LoanEventListener.notifyLoanCreated(p, "Your loan of $" + String.format("%.2f", amount) +
                            " was approved, split into " + installments + " payments.");
                });
            } catch (SQLException e) {
                AlsBanker.get().getLogger().severe("Loan creation failed: " + e.getMessage());
                Bukkit.getScheduler().runTask(AlsBanker.get(), () ->
                        p.sendMessage(ChatColor.RED + "Failed to create loan due to a database error."));
            }
        });
    }

    /**
     * Applies a payment toward the player's active loan. Shared by the /loan command,
     * the in-game GUI numpad, and the Bedrock form.
     */
    public static void payLoan(Player p, double amount) {
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

        Bukkit.getScheduler().runTaskAsynchronously(AlsBanker.get(), () -> {
            try {
                Double remainingOutstanding = LoanDataService.applyPayment(uuid, amount);
                if (remainingOutstanding == null) {
                    Bukkit.getScheduler().runTask(AlsBanker.get(), () ->
                            p.sendMessage(ChatColor.RED + "You don't have an active loan to pay off."));
                    return;
                }

                TransactionService.record(uuid, "LOAN_PAYMENT", amount, remainingOutstanding,
                        "Payment towards active loan");

                Bukkit.getScheduler().runTask(AlsBanker.get(), () -> {
                    econ.withdrawPlayer(p, amount);
                    p.sendMessage(ChatColor.GREEN + "Paid $" + String.format("%.2f", amount) + " towards your loan.");
                    PhoneAlertBridge.send(p, "Paid $" + String.format("%.2f", amount) +
                            " towards your loan. Remaining: $" + String.format("%.2f", remainingOutstanding) + ".");
                });
            } catch (SQLException e) {
                AlsBanker.get().getLogger().severe("Loan payment failed: " + e.getMessage());
                Bukkit.getScheduler().runTask(AlsBanker.get(), () ->
                        p.sendMessage(ChatColor.RED + "Failed to process payment due to a database error."));
            }
        });
    }

    public static void handleInfo(Player p) {
        String uuid = p.getUniqueId().toString();

        Bukkit.getScheduler().runTaskAsynchronously(AlsBanker.get(), () -> {
            try {
                LoanDataService.LoanSummary summary = LoanDataService.getLoanSummary(uuid);
                Bukkit.getScheduler().runTask(AlsBanker.get(), () -> {
                    if (summary == null) {
                        p.sendMessage(ChatColor.YELLOW + "You have no active loan.");
                        return;
                    }
                    p.sendMessage(ChatColor.GOLD + "=== Your Loan ===");
                    p.sendMessage(ChatColor.YELLOW + "Outstanding: $" + String.format("%.2f", summary.outstanding));
                    if (summary.nextDueDate != null) {
                        p.sendMessage(ChatColor.YELLOW + "Next payment: $" + String.format("%.2f", summary.nextAmountDue) +
                                " due " + summary.nextDueDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
                    }
                });
            } catch (SQLException e) {
                AlsBanker.get().getLogger().severe("Loan info lookup failed: " + e.getMessage());
                Bukkit.getScheduler().runTask(AlsBanker.get(), () ->
                        p.sendMessage(ChatColor.RED + "Failed to load loan info due to a database error."));
            }
        });
    }

    public static void handleHistory(Player p) {
        String uuid = p.getUniqueId().toString();

        Bukkit.getScheduler().runTaskAsynchronously(AlsBanker.get(), () -> {
            try {
                List<Transaction> history = TransactionService.getRecent(uuid, 10);
                Bukkit.getScheduler().runTask(AlsBanker.get(), () -> {
                    if (history.isEmpty()) {
                        p.sendMessage(ChatColor.YELLOW + "You have no transaction history yet.");
                        return;
                    }
                    p.sendMessage(ChatColor.GOLD + "=== Recent Transactions ===");
                    for (Transaction t : history) {
                        p.sendMessage(ChatColor.YELLOW + t.getTimestamp().format(DateTimeFormatter.ofPattern("MM-dd HH:mm")) +
                                ChatColor.GRAY + " | " + ChatColor.AQUA + t.getType() +
                                ChatColor.GRAY + " | " + ChatColor.WHITE + String.format("$%.2f", t.getAmount()) +
                                ChatColor.GRAY + " - " + t.getDescription());
                    }
                });
            } catch (SQLException e) {
                AlsBanker.get().getLogger().severe("Transaction history lookup failed: " + e.getMessage());
                Bukkit.getScheduler().runTask(AlsBanker.get(), () ->
                        p.sendMessage(ChatColor.RED + "Failed to load transaction history due to a database error."));
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
