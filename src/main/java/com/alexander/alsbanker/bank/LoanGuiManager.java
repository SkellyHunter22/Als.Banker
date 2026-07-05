package com.alexander.alsbanker.bank;

import com.alexander.alsbanker.LoanCommand;
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LoanGuiManager implements Listener {

    private static final String MENU_TITLE = ChatColor.GOLD + "Loan Menu";
    private static final String PAD_TITLE = ChatColor.DARK_GRAY + "Loan Amount";

    private enum Action { REQUEST, PAY }

    private static final Map<UUID, Action> pendingAction = new HashMap<>();
    private static final Map<UUID, Double> pendingAmounts = new HashMap<>();

    public static void register() {
        Bukkit.getPluginManager().registerEvents(new LoanGuiManager(), com.alexander.alsbanker.AlsBanker.get());
    }

    public static void openMenu(Player p) {
        if (isBedrockPlayer(p)) {
            sendMenuForm(p);
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 9, MENU_TITLE);
        inv.setItem(2, item(Material.EMERALD, ChatColor.GREEN + "Request Loan"));
        inv.setItem(3, item(Material.GOLD_NUGGET, ChatColor.YELLOW + "Pay Loan"));
        inv.setItem(5, item(Material.PAPER, ChatColor.AQUA + "Loan Info"));
        inv.setItem(6, item(Material.BOOK, ChatColor.LIGHT_PURPLE + "Transaction History"));
        p.openInventory(inv);
    }

    private static void openAmountPad(Player p, Action action) {
        if (isBedrockPlayer(p)) {
            sendAmountForm(p, action);
            return;
        }

        pendingAction.put(p.getUniqueId(), action);
        pendingAmounts.put(p.getUniqueId(), 0.0);

        Inventory inv = Bukkit.createInventory(null, 27, PAD_TITLE);
        inv.setItem(4, amountItem(0.0));
        inv.setItem(10, item(Material.LIME_STAINED_GLASS_PANE, ChatColor.GREEN + "+$100"));
        inv.setItem(11, item(Material.LIME_STAINED_GLASS_PANE, ChatColor.GREEN + "+$500"));
        inv.setItem(12, item(Material.LIME_STAINED_GLASS_PANE, ChatColor.GREEN + "+$1000"));
        inv.setItem(14, item(Material.RED_STAINED_GLASS_PANE, ChatColor.RED + "-$100"));
        inv.setItem(15, item(Material.RED_STAINED_GLASS_PANE, ChatColor.RED + "-$500"));
        inv.setItem(16, item(Material.RED_STAINED_GLASS_PANE, ChatColor.RED + "-$1000"));
        inv.setItem(26, item(Material.EMERALD_BLOCK, ChatColor.GREEN + "CONFIRM"));
        p.openInventory(inv);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        String title = e.getView().getTitle();
        if (!title.equals(MENU_TITLE) && !title.equals(PAD_TITLE)) return;
        e.setCancelled(true);

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR || !clicked.hasItemMeta()) return;

        Player p = (Player) e.getWhoClicked();
        String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

        if (title.equals(MENU_TITLE)) {
            switch (name) {
                case "Request Loan":
                    openAmountPad(p, Action.REQUEST);
                    return;
                case "Pay Loan":
                    openAmountPad(p, Action.PAY);
                    return;
                case "Loan Info":
                    p.closeInventory();
                    LoanCommand.handleInfo(p);
                    return;
                case "Transaction History":
                    p.closeInventory();
                    LoanCommand.handleHistory(p);
                    return;
                default:
                    return;
            }
        }

        // Amount pad
        double current = pendingAmounts.getOrDefault(p.getUniqueId(), 0.0);
        switch (name) {
            case "+$100": current += 100; break;
            case "+$500": current += 500; break;
            case "+$1000": current += 1000; break;
            case "-$100": current = Math.max(0, current - 100); break;
            case "-$500": current = Math.max(0, current - 500); break;
            case "-$1000": current = Math.max(0, current - 1000); break;
            case "CONFIRM": {
                Action action = pendingAction.remove(p.getUniqueId());
                pendingAmounts.remove(p.getUniqueId());
                p.closeInventory();
                if (current <= 0 || action == null) {
                    p.sendMessage(ChatColor.RED + "Amount must be positive.");
                    return;
                }
                if (action == Action.REQUEST) {
                    LoanCommand.requestLoan(p, current);
                } else {
                    LoanCommand.payLoan(p, current);
                }
                return;
            }
            default:
                return;
        }
        pendingAmounts.put(p.getUniqueId(), current);
        e.getInventory().setItem(4, amountItem(current));
    }

    private static boolean isBedrockPlayer(Player p) {
        return Bukkit.getPluginManager().isPluginEnabled("floodgate") &&
                FloodgateApi.getInstance().isFloodgatePlayer(p.getUniqueId());
    }

    private static void sendMenuForm(Player p) {
        SimpleForm form = SimpleForm.builder()
                .title("Loan Menu")
                .button("Request Loan")
                .button("Pay Loan")
                .button("Loan Info")
                .button("Transaction History")
                .validResultHandler(res -> {
                    int choice = res.clickedButtonId();
                    Bukkit.getScheduler().runTask(com.alexander.alsbanker.AlsBanker.get(), () -> {
                        switch (choice) {
                            case 0: sendAmountForm(p, Action.REQUEST); break;
                            case 1: sendAmountForm(p, Action.PAY); break;
                            case 2: LoanCommand.handleInfo(p); break;
                            case 3: LoanCommand.handleHistory(p); break;
                            default: break;
                        }
                    });
                })
                .build();
        FloodgateApi.getInstance().sendForm(p.getUniqueId(), form);
    }

    private static void sendAmountForm(Player p, Action action) {
        CustomForm form = CustomForm.builder()
                .title(action == Action.REQUEST ? "Request Loan" : "Pay Loan")
                .input("Amount", "0.00")
                .validResultHandler(res -> {
                    double amount;
                    try {
                        amount = Double.parseDouble(res.asInput(0).trim());
                    } catch (NumberFormatException ex) {
                        Bukkit.getScheduler().runTask(com.alexander.alsbanker.AlsBanker.get(), () ->
                                p.sendMessage(ChatColor.RED + "Amount must be a number."));
                        return;
                    }
                    if (amount <= 0) {
                        Bukkit.getScheduler().runTask(com.alexander.alsbanker.AlsBanker.get(), () ->
                                p.sendMessage(ChatColor.RED + "Amount must be positive."));
                        return;
                    }
                    double finalAmount = amount;
                    Bukkit.getScheduler().runTask(com.alexander.alsbanker.AlsBanker.get(), () -> {
                        if (action == Action.REQUEST) {
                            LoanCommand.requestLoan(p, finalAmount);
                        } else {
                            LoanCommand.payLoan(p, finalAmount);
                        }
                    });
                })
                .build();
        FloodgateApi.getInstance().sendForm(p.getUniqueId(), form);
    }

    private static ItemStack amountItem(double amount) {
        return item(Material.PAPER, ChatColor.YELLOW + "Current Amount: $" + String.format("%.2f", amount));
    }

    private static ItemStack item(Material m, String name) {
        ItemStack i = new ItemStack(m);
        ItemMeta meta = i.getItemMeta();
        meta.setDisplayName(name);
        i.setItemMeta(meta);
        return i;
    }
}
