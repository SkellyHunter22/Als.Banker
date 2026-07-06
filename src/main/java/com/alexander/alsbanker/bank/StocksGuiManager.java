package com.alexander.alsbanker.bank;

import com.alexander.alsbanker.AlsBanker;
import com.alexander.alsbanker.StockCommand;
import com.alexander.alsbanker.StockDataService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.floodgate.api.FloodgateApi;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class StocksGuiManager implements Listener {

    private static final String MENU_TITLE = ChatColor.GOLD + "Stock Market";
    private static final String PICK_TITLE = ChatColor.DARK_GRAY + "Choose a Stock";
    private static final String PAD_TITLE = ChatColor.DARK_GRAY + "Shares Amount";

    private enum Action { BUY, SELL }

    private static final Map<UUID, Action> pendingAction = new HashMap<>();
    private static final Map<UUID, String> pendingSymbol = new HashMap<>();
    private static final Map<UUID, Double> pendingShares = new HashMap<>();

    public static void register() {
        Bukkit.getPluginManager().registerEvents(new StocksGuiManager(), AlsBanker.get());
    }

    public static void openMenu(Player p) {
        if (isBedrockPlayer(p)) {
            sendMenuForm(p);
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 9, MENU_TITLE);
        inv.setItem(2, item(Material.EMERALD, ChatColor.GREEN + "Buy"));
        inv.setItem(3, item(Material.GOLD_NUGGET, ChatColor.YELLOW + "Sell"));
        inv.setItem(5, item(Material.CHEST, ChatColor.AQUA + "Portfolio"));
        inv.setItem(6, item(Material.BOOK, ChatColor.LIGHT_PURPLE + "Market List"));
        p.openInventory(inv);
    }

    private static void openStockPicker(Player p, Action action) {
        Bukkit.getScheduler().runTaskAsynchronously(AlsBanker.get(), () -> {
            try {
                List<StockDataService.Stock> stocks = StockDataService.listStocks();
                Bukkit.getScheduler().runTask(AlsBanker.get(), () -> {
                    if (isBedrockPlayer(p)) {
                        sendStockPickerForm(p, action, stocks);
                        return;
                    }

                    Inventory inv = Bukkit.createInventory(null, 54, PICK_TITLE);
                    int slot = 0;
                    for (StockDataService.Stock stock : stocks) {
                        if (slot >= 54) break;
                        inv.setItem(slot++, item(Material.PAPER,
                                ChatColor.YELLOW + stock.symbol() + ChatColor.GRAY + " - " + stock.name() +
                                        ChatColor.GRAY + " ($" + String.format("%.2f", stock.price()) + ")"));
                    }
                    pendingAction.put(p.getUniqueId(), action);
                    p.openInventory(inv);
                });
            } catch (SQLException e) {
                AlsBanker.get().getLogger().severe("Stock picker lookup failed: " + e.getMessage());
                Bukkit.getScheduler().runTask(AlsBanker.get(), () ->
                        p.sendMessage(ChatColor.RED + "Failed to load stock list due to a database error."));
            }
        });
    }

    private static void openSharesPad(Player p, Action action, String symbol) {
        if (isBedrockPlayer(p)) {
            sendSharesForm(p, action, symbol);
            return;
        }

        pendingAction.put(p.getUniqueId(), action);
        pendingSymbol.put(p.getUniqueId(), symbol);
        pendingShares.put(p.getUniqueId(), 0.0);

        Inventory inv = Bukkit.createInventory(null, 27, PAD_TITLE);
        inv.setItem(4, sharesItem(symbol, 0.0));
        inv.setItem(10, item(Material.LIME_STAINED_GLASS_PANE, ChatColor.GREEN + "+1"));
        inv.setItem(11, item(Material.LIME_STAINED_GLASS_PANE, ChatColor.GREEN + "+10"));
        inv.setItem(12, item(Material.LIME_STAINED_GLASS_PANE, ChatColor.GREEN + "+100"));
        inv.setItem(14, item(Material.RED_STAINED_GLASS_PANE, ChatColor.RED + "-1"));
        inv.setItem(15, item(Material.RED_STAINED_GLASS_PANE, ChatColor.RED + "-10"));
        inv.setItem(16, item(Material.RED_STAINED_GLASS_PANE, ChatColor.RED + "-100"));
        inv.setItem(26, item(Material.EMERALD_BLOCK, ChatColor.GREEN + "CONFIRM"));
        p.openInventory(inv);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        String title = e.getView().getTitle();
        if (!title.equals(MENU_TITLE) && !title.equals(PICK_TITLE) && !title.equals(PAD_TITLE)) return;
        e.setCancelled(true);

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR || !clicked.hasItemMeta()) return;

        Player p = (Player) e.getWhoClicked();
        String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

        if (title.equals(MENU_TITLE)) {
            switch (name) {
                case "Buy":
                    openStockPicker(p, Action.BUY);
                    return;
                case "Sell":
                    openStockPicker(p, Action.SELL);
                    return;
                case "Portfolio":
                    p.closeInventory();
                    StockCommand.portfolio(p);
                    return;
                case "Market List":
                    p.closeInventory();
                    StockCommand.list(p);
                    return;
                default:
                    return;
            }
        }

        if (title.equals(PICK_TITLE)) {
            Action action = pendingAction.get(p.getUniqueId());
            if (action == null) return;
            String symbol = name.split(" ")[0];
            openSharesPad(p, action, symbol);
            return;
        }

        // Shares pad
        String symbol = pendingSymbol.get(p.getUniqueId());
        double current = pendingShares.getOrDefault(p.getUniqueId(), 0.0);
        switch (name) {
            case "+1": current += 1; break;
            case "+10": current += 10; break;
            case "+100": current += 100; break;
            case "-1": current = Math.max(0, current - 1); break;
            case "-10": current = Math.max(0, current - 10); break;
            case "-100": current = Math.max(0, current - 100); break;
            case "CONFIRM": {
                Action action = pendingAction.remove(p.getUniqueId());
                pendingSymbol.remove(p.getUniqueId());
                pendingShares.remove(p.getUniqueId());
                p.closeInventory();
                if (current <= 0 || action == null || symbol == null) {
                    p.sendMessage(ChatColor.RED + "Shares must be positive.");
                    return;
                }
                StockCommand.trade(p, symbol, current, action == Action.BUY);
                return;
            }
            default:
                return;
        }
        pendingShares.put(p.getUniqueId(), current);
        e.getInventory().setItem(4, sharesItem(symbol, current));
    }

    private static boolean isBedrockPlayer(Player p) {
        return Bukkit.getPluginManager().isPluginEnabled("floodgate") &&
                FloodgateApi.getInstance().isFloodgatePlayer(p.getUniqueId());
    }

    private static void sendMenuForm(Player p) {
        SimpleForm form = SimpleForm.builder()
                .title("Stock Market")
                .button("Buy")
                .button("Sell")
                .button("Portfolio")
                .button("Market List")
                .validResultHandler(res -> {
                    int choice = res.clickedButtonId();
                    Bukkit.getScheduler().runTask(AlsBanker.get(), () -> {
                        switch (choice) {
                            case 0: openStockPicker(p, Action.BUY); break;
                            case 1: openStockPicker(p, Action.SELL); break;
                            case 2: StockCommand.portfolio(p); break;
                            case 3: StockCommand.list(p); break;
                            default: break;
                        }
                    });
                })
                .build();
        FloodgateApi.getInstance().sendForm(p.getUniqueId(), form);
    }

    private static void sendStockPickerForm(Player p, Action action, List<StockDataService.Stock> stocks) {
        SimpleForm.Builder builder = SimpleForm.builder().title("Choose a Stock");
        List<String> symbols = new ArrayList<>();
        for (StockDataService.Stock stock : stocks) {
            builder.button(stock.symbol() + " - " + stock.name() + " ($" + String.format("%.2f", stock.price()) + ")");
            symbols.add(stock.symbol());
        }
        builder.validResultHandler(res -> {
            int choice = res.clickedButtonId();
            if (choice < 0 || choice >= symbols.size()) return;
            Bukkit.getScheduler().runTask(AlsBanker.get(), () -> sendSharesForm(p, action, symbols.get(choice)));
        });
        FloodgateApi.getInstance().sendForm(p.getUniqueId(), builder.build());
    }

    private static void sendSharesForm(Player p, Action action, String symbol) {
        CustomForm form = CustomForm.builder()
                .title((action == Action.BUY ? "Buy " : "Sell ") + symbol)
                .input("Shares", "0")
                .validResultHandler(res -> {
                    double shares;
                    try {
                        shares = Double.parseDouble(res.asInput(0).trim());
                    } catch (NumberFormatException ex) {
                        Bukkit.getScheduler().runTask(AlsBanker.get(), () ->
                                p.sendMessage(ChatColor.RED + "Shares must be a number."));
                        return;
                    }
                    if (shares <= 0) {
                        Bukkit.getScheduler().runTask(AlsBanker.get(), () ->
                                p.sendMessage(ChatColor.RED + "Shares must be positive."));
                        return;
                    }
                    double finalShares = shares;
                    Bukkit.getScheduler().runTask(AlsBanker.get(), () ->
                            StockCommand.trade(p, symbol, finalShares, action == Action.BUY));
                })
                .build();
        FloodgateApi.getInstance().sendForm(p.getUniqueId(), form);
    }

    private static ItemStack sharesItem(String symbol, double shares) {
        return item(Material.PAPER, ChatColor.YELLOW + (symbol == null ? "" : symbol + ": ") + shares + " shares");
    }

    private static ItemStack item(Material m, String name) {
        ItemStack i = new ItemStack(m);
        ItemMeta meta = i.getItemMeta();
        meta.setDisplayName(name);
        i.setItemMeta(meta);
        return i;
    }
}
