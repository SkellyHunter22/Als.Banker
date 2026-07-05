package com.alexander.alsbanker;

import org.bukkit.Bukkit;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Periodically nudges every stock's price by a bounded random percentage. */
public class StockMarketEngine {

    public static void start() {
        int interval = AlsBanker.get().getConfig().getInt("stocks.fluctuation_interval_minutes", 30);

        Bukkit.getScheduler().runTaskTimerAsynchronously(
                AlsBanker.get(),
                StockMarketEngine::runCycle,
                20L * 60,
                interval * 60L * 20L
        );
    }

    public static void runCycle() {
        double maxChange = AlsBanker.get().getConfig().getDouble("stocks.max_change_percent", 0.05);
        double minPrice = AlsBanker.get().getConfig().getDouble("stocks.min_price", 0.50);

        try {
            List<StockDataService.Stock> stocks = StockDataService.listStocks();
            for (StockDataService.Stock stock : stocks) {
                double changePercent = ThreadLocalRandom.current().nextDouble(-maxChange, maxChange);
                double newPrice = Math.max(minPrice, stock.price() * (1 + changePercent));
                StockDataService.updatePrice(stock.symbol(), newPrice);
            }
        } catch (Exception e) {
            AlsBanker.get().getLogger().severe("Stock market cycle error: " + e.getMessage());
        }
    }
}
