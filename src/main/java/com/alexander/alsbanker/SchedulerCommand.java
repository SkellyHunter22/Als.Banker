package com.alexander.alsbanker;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public class SchedulerCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "/loanscheduler reload");
            sender.sendMessage(ChatColor.YELLOW + "/loanscheduler runnow");
            sender.sendMessage(ChatColor.YELLOW + "/loanscheduler gui");
            sender.sendMessage(ChatColor.YELLOW + "/loanscheduler stats");
            sender.sendMessage(ChatColor.YELLOW + "/loanscheduler testdm [message]");
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
            AlsBanker.get().reloadConfig();
            DiscordLinkManager.load();
            sender.sendMessage(ChatColor.GREEN + "Reloaded.");
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
}
