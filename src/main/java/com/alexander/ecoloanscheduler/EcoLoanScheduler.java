package com.alexander.ecoloanscheduler;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class EcoLoanScheduler extends JavaPlugin {

    private static EcoLoanScheduler instance;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        DiscordLinkManager.load();
        SchedulerEngine.start();
        GuiManager.register();
        LoanEventListener.register();

        getCommand("loanscheduler").setExecutor(new SchedulerCommand());
        getCommand("linkdiscord").setExecutor(new LinkDiscordCommand());
        getCommand("unlinkdiscord").setExecutor(new UnlinkDiscordCommand());

        getLogger().info("EcoLoanScheduler enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("EcoLoanScheduler disabled.");
    }

    public static EcoLoanScheduler get() {
        return instance;
    }
}
