package com.alexander.alsbanker;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Drives the pickpocket minigame: a marker sweeps back and forth along the top
 * row of a 2-row inventory, and the player has one click to catch it lined up
 * with the highlighted "unlock" column on the bottom row. Miss the timing (or
 * click the wrong slot, or run out of time) and the heist fails.
 */
public final class TheftMinigameManager {

    private static final int TRACK_LENGTH = 9;
    private static final String TITLE = ChatColor.DARK_RED + "Pick the Pocket...";

    private static final Map<UUID, Session> activeSessions = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> thiefCooldowns = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> victimCooldowns = new ConcurrentHashMap<>();

    private TheftMinigameManager() {
    }

    public static boolean hasActiveSession(UUID thiefUuid) {
        return activeSessions.containsKey(thiefUuid);
    }

    public static long remainingThiefCooldownSeconds(UUID thiefUuid) {
        return remaining(thiefCooldowns, thiefUuid);
    }

    public static long remainingVictimCooldownSeconds(UUID victimUuid) {
        return remaining(victimCooldowns, victimUuid);
    }

    private static long remaining(Map<UUID, Long> cooldowns, UUID uuid) {
        Long expiresAt = cooldowns.get(uuid);
        if (expiresAt == null) return 0;
        long remainingMillis = expiresAt - System.currentTimeMillis();
        return remainingMillis > 0 ? (remainingMillis + 999) / 1000 : 0;
    }

    public static void start(Player thief, Player victim, double stakeAmount) {
        int targetSlot = ThreadLocalRandom.current().nextInt(TRACK_LENGTH);
        int markerSpeedTicks = Math.max(1, AlsBanker.get().getConfig().getInt("theft.marker_speed_ticks", 4));

        Inventory inv = Bukkit.createInventory(null, 18, TITLE);
        for (int i = 0; i < TRACK_LENGTH; i++) {
            inv.setItem(i, pane(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " "));
            inv.setItem(i + TRACK_LENGTH, pane(
                    i == targetSlot ? Material.RED_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE,
                    i == targetSlot ? ChatColor.RED + "Click here!" : " "));
        }

        Session session = new Session(victim.getUniqueId(), inv, targetSlot, stakeAmount, markerSpeedTicks);
        activeSessions.put(thief.getUniqueId(), session);

        thief.openInventory(inv);
        thief.sendMessage(ChatColor.YELLOW + "Click the slot below the red marker the instant the glowing pane reaches it!");

        session.task = Bukkit.getScheduler().runTaskTimer(AlsBanker.get(), () -> tick(thief, session), 0L, markerSpeedTicks);
    }

    private static void tick(Player thief, Session session) {
        if (session.elapsedTicks > 20L * 8) {
            finish(thief, session, false, "You hesitated too long and the moment passed.");
            thief.closeInventory();
            return;
        }
        session.elapsedTicks += session.markerSpeedTicks;

        session.inventory.setItem(session.markerSlot, pane(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " "));

        session.markerSlot += session.direction;
        if (session.markerSlot >= TRACK_LENGTH - 1) {
            session.markerSlot = TRACK_LENGTH - 1;
            session.direction = -1;
        } else if (session.markerSlot <= 0) {
            session.markerSlot = 0;
            session.direction = 1;
        }

        session.inventory.setItem(session.markerSlot, pane(Material.LIME_STAINED_GLASS_PANE, ChatColor.GREEN + "Click now!"));
        thief.playSound(thief.getLocation(), Sound.UI_BUTTON_CLICK, 0.3f, 1.5f);
    }

    public static void handleClick(Player thief, int rawSlot) {
        Session session = activeSessions.get(thief.getUniqueId());
        if (session == null) return;

        if (rawSlot >= TRACK_LENGTH) {
            // Clicked the info row (bottom), not an attempt.
            return;
        }

        boolean success = rawSlot == session.markerSlot && rawSlot == session.targetSlot;
        thief.closeInventory();
        finish(thief, session, success, success ? null
                : "You clicked at the wrong moment and got caught reaching for their pocket.");
    }

    public static void handleClose(Player thief) {
        Session session = activeSessions.get(thief.getUniqueId());
        if (session == null) return;
        // Closing the GUI without clicking counts as backing out — no penalty, but still
        // consumes the attempt's cooldown so it can't be used to "peek" for free retries.
        finish(thief, session, false, null);
    }

    private static void finish(Player thief, Session session, boolean success, String failMessage) {
        Session removed = activeSessions.remove(thief.getUniqueId());
        if (removed == null) return;
        if (removed.task != null) removed.task.cancel();

        Player victim = Bukkit.getPlayer(removed.victimUuid);

        int cooldownSeconds = AlsBanker.get().getConfig().getInt("theft.cooldown_seconds", 300);
        int failMultiplier = success ? 1 : Math.max(1, AlsBanker.get().getConfig().getInt("theft.fail_cooldown_multiplier", 2));
        thiefCooldowns.put(thief.getUniqueId(), System.currentTimeMillis() + (cooldownSeconds * 1000L * failMultiplier));

        String thiefUuid = thief.getUniqueId().toString();
        String victimUuidStr = removed.victimUuid.toString();

        if (success) {
            Economy econ = VaultEconomy.get();
            if (econ == null || victim == null) {
                thief.sendMessage(ChatColor.RED + "The heist fell through — economy or target unavailable.");
                return;
            }

            double amount = Math.min(removed.stakeAmount, econ.getBalance(victim));
            if (amount <= 0) {
                thief.sendMessage(ChatColor.YELLOW + victim.getName() + " didn't have anything left to take.");
                return;
            }

            econ.withdrawPlayer(victim, amount);
            econ.depositPlayer(thief, amount);

            int victimCooldownSeconds = AlsBanker.get().getConfig().getInt("theft.victim_cooldown_seconds", 600);
            victimCooldowns.put(removed.victimUuid, System.currentTimeMillis() + victimCooldownSeconds * 1000L);

            TransactionService.record(thiefUuid, "THEFT_STOLEN", amount, econ.getBalance(thief),
                    "Successfully pickpocketed " + victim.getName());
            TransactionService.record(victimUuidStr, "THEFT_VICTIM", amount, econ.getBalance(victim),
                    "Pickpocketed by " + thief.getName());

            thief.sendMessage(ChatColor.GREEN + "Success! You lifted $" + String.format("%.2f", amount) +
                    " off of " + victim.getName() + ".");
            LoanEventListener.notify(victimUuidStr, "You just got pickpocketed by " + thief.getName() +
                    " for $" + String.format("%.2f", amount) + "!");
        } else {
            if (failMessage != null) {
                thief.sendMessage(ChatColor.RED + failMessage);

                if (victim != null) {
                    double fineRate = AlsBanker.get().getConfig().getDouble("theft.fail_fine_percent", 0.05);
                    double fine = removed.stakeAmount * fineRate;
                    Economy econ = VaultEconomy.get();
                    if (econ != null && fine > 0 && econ.has(thief, fine)) {
                        econ.withdrawPlayer(thief, fine);
                        econ.depositPlayer(victim, fine);
                        TransactionService.record(thiefUuid, "THEFT_FINE", fine, econ.getBalance(thief),
                                "Caught trying to rob " + victim.getName());
                        TransactionService.record(victimUuidStr, "THEFT_COMPENSATION", fine, econ.getBalance(victim),
                                "Compensation after " + thief.getName() + " was caught");
                        thief.sendMessage(ChatColor.RED + "You paid a $" + String.format("%.2f", fine) +
                                " fine to " + victim.getName() + " for getting caught.");
                        victim.sendMessage(ChatColor.YELLOW + thief.getName() +
                                " tried to rob you and got caught! You were compensated $" + String.format("%.2f", fine) + ".");
                    }
                }
            } else {
                thief.sendMessage(ChatColor.YELLOW + "You backed out of the heist.");
            }
        }
    }

    private static ItemStack pane(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private static final class Session {
        final UUID victimUuid;
        final Inventory inventory;
        final int targetSlot;
        final double stakeAmount;
        final int markerSpeedTicks;
        int markerSlot = 0;
        int direction = 1;
        long elapsedTicks = 0;
        BukkitTask task;

        Session(UUID victimUuid, Inventory inventory, int targetSlot, double stakeAmount, int markerSpeedTicks) {
            this.victimUuid = victimUuid;
            this.inventory = inventory;
            this.targetSlot = targetSlot;
            this.stakeAmount = stakeAmount;
            this.markerSpeedTicks = markerSpeedTicks;
        }
    }
}
