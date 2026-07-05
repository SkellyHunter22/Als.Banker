package com.alexander.alsbanker;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Collection;

public class VaultEconomy {

    // Multiple plugins on this server (EssentialsX, EcoXpert, eco) can each register
    // as a Vault Economy provider. Vault's default getRegistration() just returns
    // whichever has the highest service priority, which isn't necessarily the one
    // players actually see their balance in. EssentialsX is the server's chosen
    // source of truth, so it's matched by plugin name explicitly; if it's ever
    // absent, fall back to whatever Vault ranks highest so the plugin still works.
    private static final String PREFERRED_PROVIDER = "Essentials";

    // Looked up fresh on every call rather than cached at onEnable, since the
    // actual economy provider may register with Vault after this plugin has
    // already enabled.
    public static Economy get() {
        Collection<RegisteredServiceProvider<Economy>> registrations =
                Bukkit.getServicesManager().getRegistrations(Economy.class);
        for (RegisteredServiceProvider<Economy> reg : registrations) {
            if (reg.getPlugin().getName().equalsIgnoreCase(PREFERRED_PROVIDER)) {
                return reg.getProvider();
            }
        }

        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        return rsp != null ? rsp.getProvider() : null;
    }
}
