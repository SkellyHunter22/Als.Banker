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

    private static final List<String> SUBCOMMANDS = Arrays.asList("request", "pay", "info", "history", "gui", "credit");
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
            case "credit":
                handleCredit(p);
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
        Economy econ = VaultEconomy.get();
        if (econ == null) {
            p.sendMessage(ChatColor.RED + "Economy is unavailable; cannot process loan right now.");
            return;
        }

        String uuid = p.getUniqueId().toString();

        if (!DiscordLinkManager.isLinked(uuid)) {
            p.sendMessage(ChatColor.RED + "You must link a Discord account before taking out a loan.");
            p.sendMessage(ChatColor.YELLOW + "Run /linkdiscord to get a code, then DM it to the bot on Discord.");
            return;
        }

        if (!PlayerActionLock.tryLock(uuid)) {
            p.sendMessage(ChatColor.RED + "Please wait for your previous request to finish.");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(AlsBanker.get(), () -> {
            try {
                if (LoanDataService.getActiveLoanCount(uuid) > 0) {
                    Bukkit.getScheduler().runTask(AlsBanker.get(), () ->
                            p.sendMessage(ChatColor.RED + "You already have an active loan. Pay it off before requesting another."));
                    return;
                }

                int score = CreditScoreService.getScore(uuid);
                int minScoreToBorrow = AlsBanker.get().getConfig().getInt("credit.min_score_for_loan", 500);
                if (score < minScoreToBorrow) {
                    Bukkit.getScheduler().runTask(AlsBanker.get(), () ->
                            p.sendMessage(ChatColor.RED + "Your credit score (" + score +
                                    ") is too low to qualify for a loan. You need at least " + minScoreToBorrow + "."));
                    return;
                }

                double maxAmount = CreditScoreService.maxLoanForScore(score);
                if (amount > maxAmount) {
                    Bukkit.getScheduler().runTask(AlsBanker.get(), () ->
                            p.sendMessage(ChatColor.RED + "With your credit score (" + score + "), loans are capped at $" +
                                    String.format("%.2f", maxAmount) + "."));
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
                    String message = LoanServicerMessages.loanApproved(amount, installments);
                    p.sendMessage(ChatColor.GREEN + message);
                    LoanEventListener.notifyLoanCreated(p, message);
                });
            } catch (SQLException e) {
                AlsBanker.get().getLogger().severe("Loan creation failed: " + e.getMessage());
                Bukkit.getScheduler().runTask(AlsBanker.get(), () ->
                        p.sendMessage(ChatColor.RED + "Failed to create loan due to a database error."));
            } finally {
                PlayerActionLock.unlock(uuid);
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

        if (!PlayerActionLock.tryLock(uuid)) {
            p.sendMessage(ChatColor.RED + "Please wait for your previous request to finish.");
            return;
        }

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

                boolean paidOff = remainingOutstanding <= 0;
                int scoreGain = paidOff
                        ? AlsBanker.get().getConfig().getInt("credit.score_gain_loan_paid_off", 20)
                        : AlsBanker.get().getConfig().getInt("credit.score_gain_on_time_payment", 5);
                int newScore = CreditScoreService.adjustScore(uuid, scoreGain);

                Bukkit.getScheduler().runTask(AlsBanker.get(), () -> {
                    econ.withdrawPlayer(p, amount);
                    String message = LoanServicerMessages.loanPayment(amount, remainingOutstanding, paidOff);
                    p.sendMessage(ChatColor.GREEN + message);
                    p.sendMessage(ChatColor.AQUA + "Credit score: " + newScore + " (" + CreditScoreService.rating(newScore) + ")" +
                            (paidOff ? " — loan fully paid off!" : ""));
                    PhoneAlertBridge.send(p, message);
                    DiscordNotifier.dm(uuid, message);
                });
            } catch (SQLException e) {
                AlsBanker.get().getLogger().severe("Loan payment failed: " + e.getMessage());
                Bukkit.getScheduler().runTask(AlsBanker.get(), () ->
                        p.sendMessage(ChatColor.RED + "Failed to process payment due to a database error."));
            } finally {
                PlayerActionLock.unlock(uuid);
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

    public static void handleCredit(Player p) {
        String uuid = p.getUniqueId().toString();

        Bukkit.getScheduler().runTaskAsynchronously(AlsBanker.get(), () -> {
            try {
                int score = CreditScoreService.getScore(uuid);
                double maxLoan = CreditScoreService.maxLoanForScore(score);
                Bukkit.getScheduler().runTask(AlsBanker.get(), () -> {
                    p.sendMessage(ChatColor.GOLD + "=== Your Credit ===");
                    p.sendMessage(ChatColor.YELLOW + "Score: " + score + " (" + CreditScoreService.rating(score) + ")");
                    p.sendMessage(ChatColor.YELLOW + "Max loan amount: $" + String.format("%.2f", maxLoan));
                });
            } catch (SQLException e) {
                AlsBanker.get().getLogger().severe("Credit score lookup failed: " + e.getMessage());
                Bukkit.getScheduler().runTask(AlsBanker.get(), () ->
                        p.sendMessage(ChatColor.RED + "Failed to load credit info due to a database error."));
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
