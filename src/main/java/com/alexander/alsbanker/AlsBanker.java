package com.alexander.alsbanker;

import com.alexander.alsbanker.api.AlsBankingAPI;
import com.alexander.alsbanker.api.BankingAPI;
import com.alexander.alsbanker.bank.BankGuiManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class AlsBanker extends JavaPlugin {

    private static AlsBanker instance;
    private BankingAPI bankingAPI;

    @Override
    public void onEnable() {
        instance = this;

        // Rotating log file (keeps this launch + the two before it) lives under
        // plugins/AlsBanker/logs, separate from the main server console log.
        AlsBankerFileLogger.start();
        AlsBankerFileLogger.log("onEnable() starting");

        // Initialize API wrapper - this is what other plugins (e.g. AllyPhone) call into.
        bankingAPI = new AlsBankingAPI();

        // Load config.yml (interest rate, loan caps, MySQL credentials, etc.)
        saveDefaultConfig();
        getLogger().info("Config loaded.");

        // Connect to the shared EcoXpert database and make sure our tables exist.
        DatabaseManager.setup();
        LoanDataService.initializeTables();
        TransactionService.initializeTable();
        getLogger().info("Database connected and tables verified.");

        // Pull in previously-linked Discord accounts.
        DiscordLinkManager.load();

        // GUIs: the in-game "Bank App" menu and the admin panel.
        getServer().getPluginManager().registerEvents(new BankGuiManager(), this);
        GuiManager.register();
        getLogger().info("GUIs registered.");

        // Player + admin commands.
        getCommand("loanscheduler").setExecutor(new SchedulerCommand());
        getCommand("loan").setExecutor(new LoanCommand());
        getCommand("linkdiscord").setExecutor(new LinkDiscordCommand());
        getCommand("unlinkdiscord").setExecutor(new UnlinkDiscordCommand());
        getLogger().info("Commands registered: /loanscheduler, /loan, /linkdiscord, /unlinkdiscord.");

        // Kick off the recurring job that applies interest/penalties to overdue loans.
        SchedulerEngine.start();
        getLogger().info("Overdue-loan scheduler started.");

        // Hook PlaceholderAPI only if it's actually installed.
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new AlsBankerPlaceholderExpansion().register();
            getLogger().info("PlaceholderAPI expansion registered.");
        }

        AlsBankerFileLogger.log("onEnable() finished successfully");
        printSuccessBanner();
    }

    @Override
    public void onDisable() {
        AlsBankerFileLogger.log("onDisable() called");
        DatabaseManager.close();
        AlsBankerFileLogger.stop();
    }

    private void printSuccessBanner() {
        getLogger().info("   .-\"\"\"-.");
        getLogger().info("  /  o o  \\   Al's Banker is online!");
        getLogger().info(" |    ^    |  Loans, payments, and transaction");
        getLogger().info("  \\  \\_/  /   history are ready to go.");
        getLogger().info("   '-...-'");
    }

    public static AlsBanker get() {
        return instance;
    }

    public BankingAPI getBankingAPI() {
        return bankingAPI;
    }
}
