package com.alexander.ecoloanscheduler;

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
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            EcoLoanScheduler.get().reloadConfig();
            DiscordLinkManager.load();
            sender.sendMessage(ChatColor.GREEN + "Reloaded.");
            return true;
        }

        if (args[0].equalsIgnoreCase("runnow")) {
            SchedulerEngine.runCycle();
            sender.sendMessage(ChatColor.GREEN + "Cycle executed.");
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
