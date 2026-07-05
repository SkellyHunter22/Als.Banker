package com.alexander.alsbanker;

import com.alexander.alsbanker.bank.BankGuiManager;
import org.bukkit.plugin.java.JavaPlugin;

public class AlsBanker extends JavaPlugin {
    private static AlsBanker instance;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        DatabaseManager.setup(); // Initialize isolated pool
        LoanDataService.initializeTables();
        getServer().getPluginManager().registerEvents(new BankGuiManager(), this);
        // ... rest of your initialization
    }

    public static AlsBanker get() { return instance; }
}