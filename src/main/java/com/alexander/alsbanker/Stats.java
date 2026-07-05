package com.alexander.alsbanker;

import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

public class Stats {

    public static void show(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== EcoLoan Scheduler Stats ===");

        sender.sendMessage(ChatColor.YELLOW + "Interval: " +
                AlsBanker.get().getConfig().getInt("interval_minutes") + " minute(s)");

        sender.sendMessage(ChatColor.YELLOW + "Penalty Rate: " +
                AlsBanker.get().getConfig().getDouble("penalty_rate"));

        sender.sendMessage(ChatColor.YELLOW + "Interest Mode: " +
                AlsBanker.get().getConfig().getString("interest.mode", "outstanding"));

        Bukkit.getScheduler().runTaskAsynchronously(AlsBanker.get(), () -> {
            try {
                double totalOutstanding = LoanDataService.getTotalOutstanding();
                int activeLoans = LoanDataService.getActiveLoanCount();
                int overdueSchedules = LoanDataService.getOverdueScheduleCount();

                sender.sendMessage(ChatColor.YELLOW + "Active Loans: " + activeLoans);
                sender.sendMessage(ChatColor.YELLOW + "Total Outstanding: " + String.format("%.2f", totalOutstanding));
                sender.sendMessage(ChatColor.YELLOW + "Overdue Schedules: " + overdueSchedules);
            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + "Failed to load database stats: " + e.getMessage());
            }
        });
    }
}
