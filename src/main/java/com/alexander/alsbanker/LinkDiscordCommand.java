package com.alexander.alsbanker;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public class LinkDiscordCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can link Discord.");
            return true;
        }

        Player p = (Player) sender;

        if (args.length == 0) {
            String token = AlsBanker.get().getConfig().getString("discord_bot_token");
            if (token == null || token.isEmpty()) {
                p.sendMessage(ChatColor.RED + "Discord linking isn't set up on this server yet. Ask an admin.");
                return true;
            }

            String code = PendingDiscordLinkManager.generateCode(p.getUniqueId().toString());
            p.sendMessage(ChatColor.GOLD + "=== Link Your Discord ===");
            p.sendMessage(ChatColor.YELLOW + "1. Open a DM with the server's Discord bot.");
            p.sendMessage(ChatColor.YELLOW + "2. Send: " + ChatColor.WHITE + "link " + code);
            p.sendMessage(ChatColor.GRAY + "This code expires in 10 minutes. (Still have your Discord ID handy? /linkdiscord <id> also works.)");
            return true;
        }

        DiscordLinkManager.link(p.getUniqueId().toString(), args[0]);
        AuditLogger.log(p.getUniqueId(), "command", "LINK_DISCORD", "discordId=" + args[0]);

        p.sendMessage(ChatColor.GREEN + "Your Discord account has been linked!");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        // No fixed vocabulary to suggest here (a Discord snowflake ID is free text) —
        // returning an explicit empty list stops Bukkit's default fallback from
        // suggesting online player names, which would be misleading for this argument.
        return Collections.emptyList();
    }
}
