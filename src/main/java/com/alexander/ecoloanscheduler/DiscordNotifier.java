package com.alexander.ecoloanscheduler;

import org.bukkit.Bukkit;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class DiscordNotifier {

    public static void dm(String uuid, String message) {
        if (!DiscordLinkManager.isLinked(uuid)) return;

        String discordId = DiscordLinkManager.getDiscordId(uuid);
        String token = EcoLoanScheduler.get().getConfig().getString("discord_bot_token");

        if (token == null || token.isEmpty()) return;

        Bukkit.getScheduler().runTaskAsynchronously(EcoLoanScheduler.get(), () -> {
            try {
                URL url = new URL("https://discord.com/api/v10/users/@me/channels");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bot " + token);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                String json = "{\"recipient_id\":\"" + discordId + "\"}";
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes());
                }

                String response = new String(conn.getInputStream().readAllBytes());
                String channelId = response.split("\"id\":\"")[1].split("\"")[0];

                URL msgUrl = new URL("https://discord.com/api/v10/channels/" + channelId + "/messages");
                HttpURLConnection msgConn = (HttpURLConnection) msgUrl.openConnection();
                msgConn.setRequestMethod("POST");
                msgConn.setRequestProperty("Authorization", "Bot " + token);
                msgConn.setRequestProperty("Content-Type", "application/json");
                msgConn.setDoOutput(true);

                String msgJson = "{\"content\":\"" + message + "\"}";
                try (OutputStream os = msgConn.getOutputStream()) {
                    os.write(msgJson.getBytes());
                }

            } catch (Exception ignored) {}
        });
    }
}
