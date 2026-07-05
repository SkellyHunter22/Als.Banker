package com.alexander.alsbanker;

import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;

public class DiscordLinkManager {

    private static Map<String, Object> links = new HashMap<>();

    public static void load() {
        ConfigurationSection section = AlsBanker.get().getConfig().getConfigurationSection("discord_links");
        links = section != null ? section.getValues(false) : new HashMap<>();
    }

    public static boolean isLinked(String uuid) {
        return links.containsKey(uuid);
    }

    public static String getDiscordId(String uuid) {
        Object value = links.get(uuid);
        return value != null ? value.toString() : null;
    }

    public static void link(String uuid, String discordId) {
        AlsBanker.get().getConfig().set("discord_links." + uuid, discordId);
        AlsBanker.get().saveConfig();
        load();
    }

    public static void unlink(String uuid) {
        AlsBanker.get().getConfig().set("discord_links." + uuid, null);
        AlsBanker.get().saveConfig();
        load();
    }
}
