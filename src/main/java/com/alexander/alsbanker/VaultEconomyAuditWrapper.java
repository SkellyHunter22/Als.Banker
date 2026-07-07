package com.alexander.alsbanker;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.Bukkit;

import java.util.List;

/**
 * Decorates the server's real Vault Economy provider (EssentialsX, etc.) so
 * that every deposit/withdraw made through Vault — by AlsBanker or any other
 * plugin — gets written to the audit trail, then delegates unchanged to the
 * real provider. Registered at the highest service priority so plugins that
 * just ask Vault's ServicesManager for "the" Economy provider get this
 * wrapper; AlsBanker's own VaultEconomy.get() looks up EssentialsX by plugin
 * name directly and bypasses this wrapper, so nothing double-logs.
 */
public class VaultEconomyAuditWrapper implements Economy {

    private final Economy delegate;

    private VaultEconomyAuditWrapper(Economy delegate) {
        this.delegate = delegate;
    }

    /**
     * Wraps whatever Economy provider Vault currently has registered and
     * re-registers the wrapper at the highest priority, replacing any
     * previously-registered AlsBanker wrapper. No-ops if no real provider is
     * registered yet (e.g. the economy plugin hasn't finished enabling).
     */
    public static void installOrRefresh() {
        Bukkit.getServicesManager().unregisterAll(AlsBanker.get());

        List<org.bukkit.plugin.RegisteredServiceProvider<Economy>> registrations =
                (List<org.bukkit.plugin.RegisteredServiceProvider<Economy>>)
                        Bukkit.getServicesManager().getRegistrations(Economy.class);

        Economy real = null;
        for (org.bukkit.plugin.RegisteredServiceProvider<Economy> reg : registrations) {
            if (!(reg.getProvider() instanceof VaultEconomyAuditWrapper)) {
                real = reg.getProvider();
                break;
            }
        }

        if (real == null) {
            AlsBanker.get().getLogger().info("No Vault economy provider found yet; audit wrapper not installed.");
            return;
        }

        Bukkit.getServicesManager().register(Economy.class, new VaultEconomyAuditWrapper(real),
                AlsBanker.get(), ServicePriority.Highest);
        AlsBanker.get().getLogger().info("Vault economy audit wrapper installed over " + real.getClass().getSimpleName());
    }

    private void audit(OfflinePlayer player, String action, double amount) {
        String name = player.getName() != null ? player.getName() : "?";
        AuditLogger.log(player.getUniqueId() != null ? player.getUniqueId().toString() : null, name,
                "vault-economy", action, String.format("amount=%.2f", amount));
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        EconomyResponse response = delegate.depositPlayer(player, amount);
        audit(player, "DEPOSIT", amount);
        return response;
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        EconomyResponse response = delegate.withdrawPlayer(player, amount);
        audit(player, "WITHDRAW", amount);
        return response;
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        EconomyResponse response = delegate.depositPlayer(playerName, amount);
        AuditLogger.log((String) null, playerName, "vault-economy", "DEPOSIT", String.format("amount=%.2f", amount));
        return response;
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        EconomyResponse response = delegate.withdrawPlayer(playerName, amount);
        AuditLogger.log((String) null, playerName, "vault-economy", "WITHDRAW", String.format("amount=%.2f", amount));
        return response;
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        EconomyResponse response = delegate.depositPlayer(player, worldName, amount);
        audit(player, "DEPOSIT", amount);
        return response;
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        EconomyResponse response = delegate.withdrawPlayer(player, worldName, amount);
        audit(player, "WITHDRAW", amount);
        return response;
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        EconomyResponse response = delegate.depositPlayer(playerName, worldName, amount);
        AuditLogger.log((String) null, playerName, "vault-economy", "DEPOSIT", String.format("amount=%.2f", amount));
        return response;
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        EconomyResponse response = delegate.withdrawPlayer(playerName, worldName, amount);
        AuditLogger.log((String) null, playerName, "vault-economy", "WITHDRAW", String.format("amount=%.2f", amount));
        return response;
    }

    // --- Everything below is a pure pass-through; it doesn't move money. ---

    @Override public boolean isEnabled() { return delegate.isEnabled(); }
    @Override public String getName() { return delegate.getName(); }
    @Override public boolean hasBankSupport() { return delegate.hasBankSupport(); }
    @Override public int fractionalDigits() { return delegate.fractionalDigits(); }
    @Override public String format(double amount) { return delegate.format(amount); }
    @Override public String currencyNamePlural() { return delegate.currencyNamePlural(); }
    @Override public String currencyNameSingular() { return delegate.currencyNameSingular(); }

    @Override public boolean hasAccount(String playerName) { return delegate.hasAccount(playerName); }
    @Override public boolean hasAccount(OfflinePlayer player) { return delegate.hasAccount(player); }
    @Override public boolean hasAccount(String playerName, String worldName) { return delegate.hasAccount(playerName, worldName); }
    @Override public boolean hasAccount(OfflinePlayer player, String worldName) { return delegate.hasAccount(player, worldName); }

    @Override public double getBalance(String playerName) { return delegate.getBalance(playerName); }
    @Override public double getBalance(OfflinePlayer player) { return delegate.getBalance(player); }
    @Override public double getBalance(String playerName, String world) { return delegate.getBalance(playerName, world); }
    @Override public double getBalance(OfflinePlayer player, String world) { return delegate.getBalance(player, world); }

    @Override public boolean has(String playerName, double amount) { return delegate.has(playerName, amount); }
    @Override public boolean has(OfflinePlayer player, double amount) { return delegate.has(player, amount); }
    @Override public boolean has(String playerName, String worldName, double amount) { return delegate.has(playerName, worldName, amount); }
    @Override public boolean has(OfflinePlayer player, String worldName, double amount) { return delegate.has(player, worldName, amount); }

    @Override public boolean createPlayerAccount(String playerName) { return delegate.createPlayerAccount(playerName); }
    @Override public boolean createPlayerAccount(OfflinePlayer player) { return delegate.createPlayerAccount(player); }
    @Override public boolean createPlayerAccount(String playerName, String worldName) { return delegate.createPlayerAccount(playerName, worldName); }
    @Override public boolean createPlayerAccount(OfflinePlayer player, String worldName) { return delegate.createPlayerAccount(player, worldName); }

    @Override public EconomyResponse createBank(String name, String player) { return delegate.createBank(name, player); }
    @Override public EconomyResponse createBank(String name, OfflinePlayer player) { return delegate.createBank(name, player); }
    @Override public EconomyResponse deleteBank(String name) { return delegate.deleteBank(name); }
    @Override public EconomyResponse bankBalance(String name) { return delegate.bankBalance(name); }
    @Override public EconomyResponse bankHas(String name, double amount) { return delegate.bankHas(name, amount); }
    @Override public EconomyResponse bankWithdraw(String name, double amount) { return delegate.bankWithdraw(name, amount); }
    @Override public EconomyResponse bankDeposit(String name, double amount) { return delegate.bankDeposit(name, amount); }
    @Override public EconomyResponse isBankOwner(String name, String playerName) { return delegate.isBankOwner(name, playerName); }
    @Override public EconomyResponse isBankOwner(String name, OfflinePlayer player) { return delegate.isBankOwner(name, player); }
    @Override public EconomyResponse isBankMember(String name, String playerName) { return delegate.isBankMember(name, playerName); }
    @Override public EconomyResponse isBankMember(String name, OfflinePlayer player) { return delegate.isBankMember(name, player); }
    @Override public List<String> getBanks() { return delegate.getBanks(); }
}
