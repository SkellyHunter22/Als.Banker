package com.alexander.ecoloanscheduler;

import java.util.Map;

public class DiscordLinkManager {

    private static Map<String, Object> links;

    public static void load() {
        links = EcoLoanScheduler.get().getConfig().getConfigurationSection("discord_links").getValues(false);
    }

    public static boolean isLinked(String uuid) {
        return links.containsKey(uuid);
    }

    public static String getDiscordId(String uuid) {
        return links.get(uuid).toString();
    }

    public static void link(String uuid, String discordId) {
        EcoLoanScheduler.get().getConfig().set("discord_links." + uuid, discordId);
        EcoLoanScheduler.get().saveConfig();
        load();
    }

    public static void unlink(String uuid) {
        EcoLoanScheduler.get().getConfig().set("discord_links." + uuid, null);
        EcoLoanScheduler.get().saveConfig();
        load();
    }
}
