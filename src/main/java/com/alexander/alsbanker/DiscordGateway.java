package com.alexander.alsbanker;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maintains a persistent Discord Gateway connection purely so the bot shows as
 * "online" in Discord. DiscordNotifier still sends DMs over the plain REST API;
 * this class does not need to (and does not) handle any dispatch events.
 */
public class DiscordGateway {

    private static final String GATEWAY_URL = "wss://gateway.discord.gg/?v=10&encoding=json";
    private static final Pattern OP_PATTERN = Pattern.compile("\"op\":(-?\\d+)");
    private static final Pattern SEQ_PATTERN = Pattern.compile("\"s\":(\\d+)");
    private static final Pattern HEARTBEAT_PATTERN = Pattern.compile("\"heartbeat_interval\":(\\d+)");

    private static volatile boolean shuttingDown = false;
    private static volatile WebSocket webSocket;
    private static volatile BukkitTask heartbeatTask;
    private static volatile int lastSequence = -1;

    private DiscordGateway() {
    }

    public static void connect() {
        String token = AlsBanker.get().getConfig().getString("discord_bot_token");
        if (token == null || token.isEmpty()) {
            AlsBanker.get().getLogger().info("discord_bot_token not set; skipping Discord gateway connection.");
            return;
        }

        shuttingDown = false;
        openSocket(token);
    }

    public static void disconnect() {
        shuttingDown = true;
        stopHeartbeat();

        WebSocket ws = webSocket;
        webSocket = null;
        if (ws != null) {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "Plugin disabling");
        }
    }

    private static void openSocket(String token) {
        HttpClient client = HttpClient.newHttpClient();
        client.newWebSocketBuilder()
                .buildAsync(URI.create(GATEWAY_URL), new GatewayListener(token))
                .whenComplete((ws, error) -> {
                    if (error != null) {
                        AlsBanker.get().getLogger().warning("Discord gateway connection failed: " + error.getMessage());
                        scheduleReconnect(token);
                    }
                });
    }

    private static void scheduleReconnect(String token) {
        if (shuttingDown) return;
        Bukkit.getScheduler().runTaskLaterAsynchronously(AlsBanker.get(), () -> openSocket(token), 20L * 10);
    }

    private static void stopHeartbeat() {
        BukkitTask task = heartbeatTask;
        heartbeatTask = null;
        if (task != null) {
            task.cancel();
        }
    }

    private static class GatewayListener implements WebSocket.Listener {

        private final String token;
        private final StringBuilder buffer = new StringBuilder();

        private GatewayListener(String token) {
            this.token = token;
        }

        @Override
        public void onOpen(WebSocket ws) {
            webSocket = ws;
            ws.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            buffer.append(data);
            ws.request(1);
            if (!last) return null;

            String message = buffer.toString();
            buffer.setLength(0);
            handle(ws, message);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            AlsBanker.get().getLogger().warning("Discord gateway closed (" + statusCode + "): " + reason);
            stopHeartbeat();
            scheduleReconnect(token);
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            AlsBanker.get().getLogger().warning("Discord gateway error: " + error.getMessage());
            stopHeartbeat();
            scheduleReconnect(token);
        }

        private void handle(WebSocket ws, String message) {
            Matcher seqMatcher = SEQ_PATTERN.matcher(message);
            if (seqMatcher.find()) {
                lastSequence = Integer.parseInt(seqMatcher.group(1));
            }

            Matcher opMatcher = OP_PATTERN.matcher(message);
            if (!opMatcher.find()) return;
            int op = Integer.parseInt(opMatcher.group(1));

            switch (op) {
                case 10 -> { // Hello
                    Matcher hbMatcher = HEARTBEAT_PATTERN.matcher(message);
                    long interval = hbMatcher.find() ? Long.parseLong(hbMatcher.group(1)) : 41250L;
                    identify(ws);
                    startHeartbeat(ws, interval);
                }
                case 7, 9 -> { // Reconnect requested / invalid session
                    stopHeartbeat();
                    ws.sendClose(WebSocket.NORMAL_CLOSURE, "Reconnecting");
                }
                default -> {
                    // Dispatch events and heartbeat ACKs don't need handling just to stay online.
                }
            }
        }

        private void identify(WebSocket ws) {
            String payload = "{\"op\":2,\"d\":{\"token\":\"" + escape(token) + "\",\"intents\":0,"
                    + "\"properties\":{\"os\":\"linux\",\"browser\":\"alsbanker\",\"device\":\"alsbanker\"}}}";
            ws.sendText(payload, true);
        }

        private void startHeartbeat(WebSocket ws, long intervalMillis) {
            stopHeartbeat();
            long intervalTicks = Math.max(1L, intervalMillis / 50L);
            heartbeatTask = Bukkit.getScheduler().runTaskTimerAsynchronously(AlsBanker.get(), () -> {
                String seq = lastSequence >= 0 ? String.valueOf(lastSequence) : "null";
                ws.sendText("{\"op\":1,\"d\":" + seq + "}", true);
            }, intervalTicks, intervalTicks);
        }

        private String escape(String value) {
            return value.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }
}
