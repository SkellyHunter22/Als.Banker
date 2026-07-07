package com.alexander.alsbanker;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prevents a player from having two money-moving commands (loan request/pay,
 * savings deposit/withdraw, stock buy/sell) in flight at once. Each of those
 * paths checks a balance/state synchronously, then finishes the actual mutation
 * on an async task scheduled back to the main thread — without this, spamming
 * the command lets several in-flight copies all pass the same stale check
 * before any of them lands, over-spending/over-borrowing past what the
 * player's real balance allows.
 */
public final class PlayerActionLock {

    private static final Set<String> busy = ConcurrentHashMap.newKeySet();

    private PlayerActionLock() {
    }

    public static boolean tryLock(String uuid) {
        return busy.add(uuid);
    }

    public static void unlock(String uuid) {
        busy.remove(uuid);
    }
}
