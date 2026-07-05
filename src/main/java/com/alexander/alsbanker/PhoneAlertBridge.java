package com.alexander.alsbanker;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Pushes every player-facing notification through AllyPhone's alerts inbox
 * ({@code phonealert <player> <source> <message...>}), instead of just chat/Discord.
 * A no-op if AllyPhone isn't installed.
 */
public class PhoneAlertBridge {

    public static void send(Player player, String message) {
        send(player.getName(), message);
    }

    /** Most AlsBanker call sites only have a stored UUID string; resolves via OfflinePlayer. */
    public static void send(String uuidOrName, String message) {
        String name = resolveName(uuidOrName);
        if (name == null) return;
        dispatch(name, message);
    }

    private static String resolveName(String uuidOrName) {
        try {
            UUID uuid = UUID.fromString(uuidOrName);
            OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
            return player.getName();
        } catch (IllegalArgumentException notAUuid) {
            return uuidOrName;
        }
    }

    private static void dispatch(String playerName, String message) {
        if (!Bukkit.getPluginManager().isPluginEnabled("AllyPhone")) return;

        // Bukkit splits command args on spaces, so the message can't be quoted through
        // dispatchCommand; PhoneAlertCommand rejoins everything after <player> <source>.
        String command = "phonealert " + playerName + " AlsBanker " + message;
        Bukkit.getScheduler().runTask(AlsBanker.get(), () ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
    }
}
