package com.alexander.ecoloanscheduler;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class UnlinkDiscordCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can unlink Discord.");
            return true;
        }

        Player p = (Player) sender;
        DiscordLinkManager.unlink(p.getUniqueId().toString());

        p.sendMessage(ChatColor.YELLOW + "Your Discord account has been unlinked.");
        return true;
    }
}
