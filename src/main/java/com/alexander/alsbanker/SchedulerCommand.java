package com.alexander.alsbanker;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SchedulerCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList("reload", "runnow", "gui", "stats", "testdm");

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("testdm")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Only players can use testdm (need a linked Discord account).");
                return true;
            }

            Player p = (Player) sender;
            String uuid = p.getUniqueId().toString();

            if (!DiscordLinkManager.isLinked(uuid)) {
                sender.sendMessage(ChatColor.RED + "You don't have a linked Discord account. Use /linkdiscord <discordID> first.");
                return true;
            }

            String message = args.length > 1
                    ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))
                    : "Test message from AlsBanker.";

            DiscordNotifier.dm(uuid, message);
            sender.sendMessage(ChatColor.GREEN + "Test DM dispatched, check server console for warnings.");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            AlsBanker.get().performReload(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("runnow")) {
            sender.sendMessage(ChatColor.GREEN + "Cycle started in background...");
            Bukkit.getScheduler().runTaskAsynchronously(AlsBanker.get(), SchedulerEngine::runCycle);
            return true;
        }

        if (args[0].equalsIgnoreCase("gui") && sender instanceof Player) {
            GuiManager.open((Player) sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("stats")) {
            Stats.show(sender);
            return true;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length != 1) return new ArrayList<>();
        List<String> matches = new ArrayList<>();
        for (String sub : SUBCOMMANDS) {
            if (sub.startsWith(args[0].toLowerCase())) matches.add(sub);
        }
        return matches;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== AlsBanker Admin ===");
        sendClickable(sender, "/loanscheduler reload", "Full soft-restart of AlsBanker (config, DB, schedulers, Discord).");
        sendClickable(sender, "/loanscheduler runnow", "Run the overdue-loan/interest cycle immediately.");
        sendClickable(sender, "/loanscheduler gui", "Open the admin panel.");
        sendClickable(sender, "/loanscheduler stats", "Show plugin-wide stats.");
        sendClickable(sender, "/loanscheduler testdm", "Send yourself a test Discord DM.");
        sender.sendMessage(ChatColor.GOLD + "=== Player Commands ===");
        sendClickable(sender, "/loan", "Request or repay a loan.");
        sendClickable(sender, "/savings", "Manage your savings account.");
        sendClickable(sender, "/stocks", "Buy and sell stocks.");
        sendClickable(sender, "/creditcard", "Apply for and manage a credit card.");
        sendClickable(sender, "/linkdiscord", "Link your Discord account.");
        sendClickable(sender, "/steal", "Attempt to pickpocket another player.");
    }

    private void sendClickable(CommandSender sender, String command, String description) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.YELLOW + command + ChatColor.GRAY + " - " + description);
            return;
        }

        BaseComponent[] component = new ComponentBuilder(command)
                .color(net.md_5.bungee.api.ChatColor.YELLOW)
                .event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(description + "\n" + ChatColor.GRAY + "Click to fill this in.")))
                .append(ChatColor.GRAY + " - " + description)
                .create();
        ((Player) sender).spigot().sendMessage(component);
    }
}
