package com.alexander.alsbanker;

import org.bukkit.Bukkit;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class DiscordNotifier {

    public static void dm(String uuid, String message) {
        if (!DiscordLinkManager.isLinked(uuid)) return;

        String discordId = DiscordLinkManager.getDiscordId(uuid);
        String token = AlsBanker.get().getConfig().getString("discord_bot_token");

        if (discordId == null || token == null || token.isEmpty()) return;

        Bukkit.getScheduler().runTaskAsynchronously(AlsBanker.get(), () -> {
            try {
                URL url = new URL("https://discord.com/api/v10/users/@me/channels");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bot " + token);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                String json = "{\"recipient_id\":\"" + escapeJson(discordId) + "\"}";
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes());
                }

                if (conn.getResponseCode() >= 300) {
                    AlsBanker.get().getLogger().warning(
                            "Discord DM channel creation failed with HTTP " + conn.getResponseCode());
                    return;
                }

                String response = new String(conn.getInputStream().readAllBytes());
                String channelId = response.split("\"id\":\"")[1].split("\"")[0];

                sendToChannel(token, channelId, message);
            } catch (Exception e) {
                AlsBanker.get().getLogger().warning("Discord DM failed: " + e.getMessage());
            }
        });
    }

    /** Posts a message to an already-known channel ID (e.g. a DM channel we were just messaged in). */
    public static void sendToChannel(String token, String channelId, String message) {
        try {
            URL msgUrl = new URL("https://discord.com/api/v10/channels/" + channelId + "/messages");
            HttpURLConnection msgConn = (HttpURLConnection) msgUrl.openConnection();
            msgConn.setRequestMethod("POST");
            msgConn.setRequestProperty("Authorization", "Bot " + token);
            msgConn.setRequestProperty("Content-Type", "application/json");
            msgConn.setDoOutput(true);

            String msgJson = "{\"content\":\"" + escapeJson(message) + "\"}";
            try (OutputStream os = msgConn.getOutputStream()) {
                os.write(msgJson.getBytes());
            }

            if (msgConn.getResponseCode() >= 300) {
                AlsBanker.get().getLogger().warning(
                        "Discord channel send failed with HTTP " + msgConn.getResponseCode());
            }
        } catch (Exception e) {
            AlsBanker.get().getLogger().warning("Discord channel send failed: " + e.getMessage());
        }
    }

    private static String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
