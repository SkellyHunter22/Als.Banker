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

public class StockCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList("list", "buy", "sell", "portfolio");

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use /stocks.");
            return true;
        }
        Player p = (Player) sender;

        if (args.length == 0) {
            p.sendMessage(ChatColor.YELLOW + "/stocks list");
            p.sendMessage(ChatColor.YELLOW + "/stocks buy <symbol> <shares>");
            p.sendMessage(ChatColor.YELLOW + "/stocks sell <symbol> <shares>");
            p.sendMessage(ChatColor.YELLOW + "/stocks portfolio");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "list":
                list(p);
                return true;
            case "buy":
                if (args.length != 3) {
                    p.sendMessage(ChatColor.RED + "Usage: /stocks buy <symbol> <shares>");
                    return true;
                }
                trade(p, args[1], parseShares(p, args[2]), true);
                return true;
            case "sell":
                if (args.length != 3) {
                    p.sendMessage(ChatColor.RED + "Usage: /stocks sell <symbol> <shares>");
                    return true;
                }
                trade(p, args[1], parseShares(p, args[2]), false);
                return true;
            case "portfolio":
                portfolio(p);
                return true;
            default:
                p.sendMessage(ChatColor.RED + "Unknown /stocks subcommand.");
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
        if (args.length == 2 && (args[0].equalsIgnoreCase("buy") || args[0].equalsIgnoreCase("sell"))) {
            try {
                List<String> matches = new ArrayList<>();
                for (StockDataService.Stock stock : StockDataService.listStocks()) {
                    if (stock.symbol().startsWith(args[1].toUpperCase())) matches.add(stock.symbol());
                }
                return matches;
            } catch (SQLException e) {
                return new ArrayList<>();
            }
        }
        return new ArrayList<>();
    }

    private static void list(Player p) {
        Bukkit.getScheduler().runTaskAsynchronously(AlsBanker.get(), () -> {
            try {
                List<StockDataService.Stock> stocks = StockDataService.listStocks();
                Bukkit.getScheduler().runTask(AlsBanker.get(), () -> {
                    p.sendMessage(ChatColor.GOLD + "=== Stock Market ===");
                    for (StockDataService.Stock stock : stocks) {
                        p.sendMessage(ChatColor.YELLOW + stock.symbol() + ChatColor.GRAY + " - " +
                                ChatColor.WHITE + stock.name() + ChatColor.GRAY + ": " +
                                ChatColor.GREEN + "$" + String.format("%.2f", stock.price()));
                    }
                });
            } catch (SQLException e) {
                AlsBanker.get().getLogger().severe("Stock list lookup failed: " + e.getMessage());
                Bukkit.getScheduler().runTask(AlsBanker.get(), () ->
                        p.sendMessage(ChatColor.RED + "Failed to load stock list due to a database error."));
            }
        });
    }

    private static void trade(Player p, String symbol, double shares, boolean buying) {
        if (Double.isNaN(shares)) return;

        Economy econ = VaultEconomy.get();
        if (econ == null) {
            p.sendMessage(ChatColor.RED + "Economy is unavailable; cannot process trade right now.");
            return;
        }

        String uuid = p.getUniqueId().toString();
        String upperSymbol = symbol.toUpperCase();

        Bukkit.getScheduler().runTaskAsynchronously(AlsBanker.get(), () -> {
            try {
                StockDataService.Stock stock = StockDataService.getStock(upperSymbol);
                if (stock == null) {
                    Bukkit.getScheduler().runTask(AlsBanker.get(), () ->
                            p.sendMessage(ChatColor.RED + "No stock with symbol '" + upperSymbol + "'."));
                    return;
                }

                double cost = stock.price() * shares;

                if (buying) {
                    buy(p, econ, uuid, stock, shares, cost);
                } else {
                    sell(p, econ, uuid, stock, shares, cost);
                }
            } catch (SQLException e) {
                AlsBanker.get().getLogger().severe("Stock trade failed: " + e.getMessage());
                Bukkit.getScheduler().runTask(AlsBanker.get(), () ->
                        p.sendMessage(ChatColor.RED + "Trade failed due to a database error."));
            }
        });
    }

    private static void buy(Player p, Economy econ, String uuid, StockDataService.Stock stock,
                             double shares, double cost) throws SQLException {
        if (!econ.has(p, cost)) {
            Bukkit.getScheduler().runTask(AlsBanker.get(), () ->
                    p.sendMessage(ChatColor.RED + "You don't have $" + String.format("%.2f", cost) +
                            " to buy " + shares + " shares of " + stock.symbol() + "."));
            return;
        }

        StockDataService.addShares(uuid, stock.symbol(), shares);
        TransactionService.record(uuid, "STOCK_BUY", cost, cost,
                "Bought " + shares + " shares of " + stock.symbol() + " at $" + String.format("%.2f", stock.price()));

        Bukkit.getScheduler().runTask(AlsBanker.get(), () -> {
            econ.withdrawPlayer(p, cost);
            p.sendMessage(ChatColor.GREEN + "Bought " + shares + " shares of " + stock.symbol() +
                    " for $" + String.format("%.2f", cost) + ".");
            PhoneAlertBridge.send(p, "Bought " + shares + " shares of " + stock.symbol() +
                    " for $" + String.format("%.2f", cost) + ".");
        });
    }

    private static void sell(Player p, Economy econ, String uuid, StockDataService.Stock stock,
                              double shares, double proceeds) throws SQLException {
        boolean success = StockDataService.removeShares(uuid, stock.symbol(), shares);
        if (!success) {
            Bukkit.getScheduler().runTask(AlsBanker.get(), () ->
                    p.sendMessage(ChatColor.RED + "You don't own " + shares + " shares of " + stock.symbol() + "."));
            return;
        }

        TransactionService.record(uuid, "STOCK_SELL", proceeds, proceeds,
                "Sold " + shares + " shares of " + stock.symbol() + " at $" + String.format("%.2f", stock.price()));

        Bukkit.getScheduler().runTask(AlsBanker.get(), () -> {
            econ.depositPlayer(p, proceeds);
            p.sendMessage(ChatColor.GREEN + "Sold " + shares + " shares of " + stock.symbol() +
                    " for $" + String.format("%.2f", proceeds) + ".");
            PhoneAlertBridge.send(p, "Sold " + shares + " shares of " + stock.symbol() +
                    " for $" + String.format("%.2f", proceeds) + ".");
        });
    }

    private static void portfolio(Player p) {
        String uuid = p.getUniqueId().toString();

        Bukkit.getScheduler().runTaskAsynchronously(AlsBanker.get(), () -> {
            try {
                List<StockDataService.Holding> holdings = StockDataService.getPortfolio(uuid);
                if (holdings.isEmpty()) {
                    Bukkit.getScheduler().runTask(AlsBanker.get(), () ->
                            p.sendMessage(ChatColor.YELLOW + "You don't own any stocks."));
                    return;
                }

                double totalValue = 0;
                List<String> lines = new ArrayList<>();
                for (StockDataService.Holding holding : holdings) {
                    StockDataService.Stock stock = StockDataService.getStock(holding.symbol());
                    if (stock == null) continue;
                    double value = stock.price() * holding.shares();
                    totalValue += value;
                    lines.add(ChatColor.YELLOW + holding.symbol() + ChatColor.GRAY + ": " +
                            ChatColor.WHITE + holding.shares() + " shares " + ChatColor.GRAY + "@ $" +
                            String.format("%.2f", stock.price()) + " = " + ChatColor.GREEN +
                            "$" + String.format("%.2f", value));
                }

                double finalTotal = totalValue;
                Bukkit.getScheduler().runTask(AlsBanker.get(), () -> {
                    p.sendMessage(ChatColor.GOLD + "=== Your Portfolio ===");
                    lines.forEach(p::sendMessage);
                    p.sendMessage(ChatColor.GOLD + "Total value: $" + String.format("%.2f", finalTotal));
                });
            } catch (SQLException e) {
                AlsBanker.get().getLogger().severe("Portfolio lookup failed: " + e.getMessage());
                Bukkit.getScheduler().runTask(AlsBanker.get(), () ->
                        p.sendMessage(ChatColor.RED + "Failed to load portfolio due to a database error."));
            }
        });
    }

    private static double parseShares(Player p, String raw) {
        double shares;
        try {
            shares = Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            p.sendMessage(ChatColor.RED + "Shares must be a number.");
            return Double.NaN;
        }
        if (shares <= 0) {
            p.sendMessage(ChatColor.RED + "Shares must be positive.");
            return Double.NaN;
        }
        return shares;
    }
}
