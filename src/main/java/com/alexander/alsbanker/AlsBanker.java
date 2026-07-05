package com.alexander.alsbanker;

import com.alexander.alsbanker.api.AlsBankingAPI;
import com.alexander.alsbanker.api.BankingAPI;
import com.alexander.alsbanker.bank.BankGuiManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.Map;

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
        SavingsDataService.initializeTable();
        StockDataService.initializeTables();
        getLogger().info("Database connected and tables verified.");

        // Pull in previously-linked Discord accounts.
        DiscordLinkManager.load();

        // GUIs: the in-game "Bank App" menu and the admin panel.
        getServer().getPluginManager().registerEvents(new BankGuiManager(), this);
        com.alexander.alsbanker.bank.LoanGuiManager.register();
        GuiManager.register();
        getLogger().info("GUIs registered.");

        // Player + admin commands.
        getCommand("loanscheduler").setExecutor(new SchedulerCommand());
        LoanCommand loanCommand = new LoanCommand();
        getCommand("loan").setExecutor(loanCommand);
        getCommand("loan").setTabCompleter(loanCommand);
        getCommand("linkdiscord").setExecutor(new LinkDiscordCommand());
        getCommand("unlinkdiscord").setExecutor(new UnlinkDiscordCommand());
        SavingsCommand savingsCommand = new SavingsCommand();
        getCommand("savings").setExecutor(savingsCommand);
        getCommand("savings").setTabCompleter(savingsCommand);
        StockCommand stockCommand = new StockCommand();
        getCommand("stocks").setExecutor(stockCommand);
        getCommand("stocks").setTabCompleter(stockCommand);
        getLogger().info("Commands registered: /loanscheduler, /loan, /linkdiscord, /unlinkdiscord, /savings, /stocks.");

        // Kick off the recurring job that applies interest/penalties to overdue loans,
        // credits savings interest, and updates stock prices.
        SchedulerEngine.start();
        StockMarketEngine.start();
        getLogger().info("Overdue-loan scheduler started.");

        // Hook PlaceholderAPI only if it's actually installed.
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new AlsBankerPlaceholderExpansion().register();
            getLogger().info("PlaceholderAPI expansion registered.");
        }

        // EcoXpert registers a "loans" command with a "loan" alias, and Bukkit's command
        // map arbitration between plugins isn't reliably controllable via plugin.yml
        // load-order hints alone. Force ownership of the bare /loan slot once every
        // plugin has finished enabling, so EcoXpert's alias never wins it.
        Bukkit.getScheduler().runTask(this, this::claimLoanCommand);

        AlsBankerFileLogger.log("onEnable() finished successfully");
        printSuccessBanner();
    }

    private void claimLoanCommand() {
        try {
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            SimpleCommandMap commandMap = (SimpleCommandMap) commandMapField.get(Bukkit.getServer());

            Field knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
            knownCommandsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(commandMap);

            Command ours = getCommand("loan");
            if (ours != null) {
                knownCommands.put("loan", ours);
                getLogger().info("Claimed ownership of /loan (overriding any conflicting alias, e.g. EcoXpert's).");
            }
        } catch (ReflectiveOperationException | ClassCastException e) {
            getLogger().warning("Could not force ownership of /loan: " + e.getMessage());
        }
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
