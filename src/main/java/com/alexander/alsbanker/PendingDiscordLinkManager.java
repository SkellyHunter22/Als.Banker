package com.alexander.alsbanker;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Tracks short-lived link codes for the easy Discord-linking flow: a player runs
 * /linkdiscord in-game to get a code, then DMs "link CODE" to the bot on Discord.
 * DiscordGateway completes the link when it sees that message come in.
 */
public class PendingDiscordLinkManager {

    private static final long CODE_TTL_SECONDS = 600; // 10 minutes

    private static final Map<String, Pending> byCode = new ConcurrentHashMap<>();

    private record Pending(String uuid, Instant expiresAt) {
    }

    public static String generateCode(String uuid) {
        // Remove any code the player already had pending, so only their latest is valid.
        byCode.values().removeIf(p -> p.uuid.equals(uuid));

        String code;
        do {
            code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        } while (byCode.containsKey(code));

        byCode.put(code, new Pending(uuid, Instant.now().plusSeconds(CODE_TTL_SECONDS)));
        return code;
    }

    /**
     * Resolves a code to the Minecraft UUID that requested it, consuming it in the
     * process. Returns null if the code doesn't exist or has expired.
     */
    public static String consume(String code) {
        Pending pending = byCode.remove(code.trim());
        if (pending == null) return null;
        if (Instant.now().isAfter(pending.expiresAt)) return null;
        return pending.uuid;
    }
}
