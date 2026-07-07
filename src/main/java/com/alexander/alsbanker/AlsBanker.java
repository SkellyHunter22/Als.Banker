package com.alexander.alsbanker;

import com.alexander.alsbanker.api.AlsBankingAPI;
import com.alexander.alsbanker.api.BankingAPI;
import com.alexander.alsbanker.bank.BankGuiManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
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
        try {
            DatabaseManager.setup();
        } catch (IllegalStateException e) {
            getLogger().severe(e.getMessage());
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        LoanDataService.initializeTables();
        TransactionService.initializeTable();
        SavingsDataService.initializeTable();
        StockDataService.initializeTables();
        CreditScoreService.initializeTable();
        CreditCardDataService.initializeTable();
        AuditLogger.initializeTable();
        getLogger().info("Database connected and tables verified.");

        // Pull in previously-linked Discord accounts.
        DiscordLinkManager.load();

        // Open the Discord gateway connection so the bot shows as online (DMs
        // themselves still go over the plain REST API in DiscordNotifier).
        DiscordGateway.connect();

        // GUIs: the in-game "Bank App" menu and the admin panel.
        getServer().getPluginManager().registerEvents(new BankGuiManager(), this);
        com.alexander.alsbanker.bank.LoanGuiManager.register();
        com.alexander.alsbanker.bank.SavingsGuiManager.register();
        com.alexander.alsbanker.bank.StocksGuiManager.register();
        GuiManager.register();
        getServer().getPluginManager().registerEvents(new TheftGuiListener(), this);
        getServer().getPluginManager().registerEvents(new CommandAuditListener(), this);
        getLogger().info("GUIs registered.");

        // Player + admin commands.
        SchedulerCommand schedulerCommand = new SchedulerCommand();
        getCommand("loanscheduler").setExecutor(schedulerCommand);
        getCommand("loanscheduler").setTabCompleter(schedulerCommand);
        LoanCommand loanCommand = new LoanCommand();
        getCommand("loan").setExecutor(loanCommand);
        getCommand("loan").setTabCompleter(loanCommand);
        LinkDiscordCommand linkDiscordCommand = new LinkDiscordCommand();
        getCommand("linkdiscord").setExecutor(linkDiscordCommand);
        getCommand("linkdiscord").setTabCompleter(linkDiscordCommand);
        UnlinkDiscordCommand unlinkDiscordCommand = new UnlinkDiscordCommand();
        getCommand("unlinkdiscord").setExecutor(unlinkDiscordCommand);
        getCommand("unlinkdiscord").setTabCompleter(unlinkDiscordCommand);
        SavingsCommand savingsCommand = new SavingsCommand();
        getCommand("savings").setExecutor(savingsCommand);
        getCommand("savings").setTabCompleter(savingsCommand);
        StockCommand stockCommand = new StockCommand();
        getCommand("stocks").setExecutor(stockCommand);
        getCommand("stocks").setTabCompleter(stockCommand);
        TheftCommand theftCommand = new TheftCommand();
        getCommand("steal").setExecutor(theftCommand);
        getCommand("steal").setTabCompleter(theftCommand);
        CreditCardCommand creditCardCommand = new CreditCardCommand();
        getCommand("creditcard").setExecutor(creditCardCommand);
        getCommand("creditcard").setTabCompleter(creditCardCommand);
        getLogger().info("Commands registered: /loanscheduler, /loan, /linkdiscord, /unlinkdiscord, /savings, /stocks, /steal, /creditcard.");

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

        // Same reasoning as claimLoanCommand: the real economy plugin (EssentialsX)
        // may not have registered its Vault service yet at this point in startup.
        Bukkit.getScheduler().runTask(this, VaultEconomyAuditWrapper::installOrRefresh);

        AlsBankerFileLogger.log("onEnable() finished successfully");
        printSuccessBanner();
    }

    /**
     * Full soft-restart of the plugin, triggered by /loanscheduler reload. Prefers
     * handing off to PlugMan/PlugManX (if installed) since it disables and
     * re-enables the plugin through Bukkit's real plugin manager — firing the
     * PluginDisableEvent/PluginEnableEvent other plugins may expect — rather than
     * this plugin quietly re-running its own lifecycle methods in place.
     */
    public void performReload(CommandSender sender) {
        String plugManName = Bukkit.getPluginManager().isPluginEnabled("PlugManX") ? "PlugManX"
                : Bukkit.getPluginManager().isPluginEnabled("PlugMan") ? "PlugMan"
                : null;

        AuditLogger.log((java.util.UUID) null, "admin",
                sender instanceof org.bukkit.entity.Player ? "RELOAD_REQUESTED" : "RELOAD_REQUESTED_CONSOLE",
                "requestedBy=" + sender.getName() + " via=" + (plugManName != null ? plugManName : "fallback"));

        if (plugManName != null) {
            sender.sendMessage(org.bukkit.ChatColor.YELLOW + "Reloading AlsBanker via " + plugManName + "...");
            boolean dispatched = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "plugman reload AlsBanker");
            if (dispatched) {
                sender.sendMessage(org.bukkit.ChatColor.GREEN + "Reload dispatched to " + plugManName + ".");
                return;
            }
            sender.sendMessage(org.bukkit.ChatColor.RED + plugManName + " reload command failed; falling back to a direct reload.");
        }

        try {
            onDisable();
            onEnable();
            sender.sendMessage(org.bukkit.ChatColor.GREEN + "AlsBanker reloaded.");
        } catch (Exception e) {
            getLogger().severe("Reload failed: " + e.getMessage());
            sender.sendMessage(org.bukkit.ChatColor.RED + "Reload failed; check console for details. The plugin has NOT been disabled.");
        }
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
        DiscordGateway.disconnect();
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
