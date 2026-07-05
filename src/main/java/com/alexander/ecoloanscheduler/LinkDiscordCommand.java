package com.alexander.ecoloanscheduler;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LinkDiscordCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can link Discord.");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /linkdiscord <discordID>");
            return true;
        }

        Player p = (Player) sender;
        DiscordLinkManager.link(p.getUniqueId().toString(), args[0]);

        p.sendMessage(ChatColor.GREEN + "Your Discord account has been linked!");
        return true;
    }
}
