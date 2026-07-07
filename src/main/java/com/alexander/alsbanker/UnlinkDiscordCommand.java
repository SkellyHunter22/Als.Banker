package com.alexander.alsbanker;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public class UnlinkDiscordCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can unlink Discord.");
            return true;
        }

        Player p = (Player) sender;
        DiscordLinkManager.unlink(p.getUniqueId().toString());
        AuditLogger.log(p.getUniqueId(), "command", "UNLINK_DISCORD", "");

        p.sendMessage(ChatColor.YELLOW + "Your Discord account has been unlinked.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        return Collections.emptyList();
    }
}
