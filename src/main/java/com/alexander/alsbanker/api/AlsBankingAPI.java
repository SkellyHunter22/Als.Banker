package com.alexander.alsbanker.api;

import com.alexander.alsbanker.AlsBanker;
import com.alexander.alsbanker.DatabaseManager;
import com.alexander.alsbanker.LoanDataService;
import com.alexander.alsbanker.SavingsDataService;
import com.alexander.alsbanker.StockDataService;
import com.alexander.alsbanker.TransactionService;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class AlsBankingAPI implements BankingAPI {

    @Override
    public double getBalance(UUID uuid) {
        return DatabaseManager.getBalance(uuid);
    }

    @Override
    public boolean withdraw(UUID uuid, double amount) {
        return DatabaseManager.withdraw(uuid, amount);
    }

    @Override
    public boolean deposit(UUID uuid, double amount) {
        return DatabaseManager.deposit(uuid, amount);
    }

    @Override
    public LoanInfo getLoanInfo(UUID uuid) {
        try {
            LoanDataService.LoanSummary summary = LoanDataService.getLoanSummary(uuid.toString());
            if (summary == null) return new LoanInfo(0, null, 0);
            return new LoanInfo(summary.outstanding, summary.nextDueDate, summary.nextAmountDue);
        } catch (SQLException e) {
            AlsBanker.get().getLogger().severe("getLoanInfo failed: " + e.getMessage());
            return new LoanInfo(0, null, 0);
        }
    }

    @Override
    public List<Transaction> getTransactionHistory(UUID uuid, int limit) {
        try {
            return TransactionService.getRecent(uuid.toString(), limit);
        } catch (SQLException e) {
            AlsBanker.get().getLogger().severe("getTransactionHistory failed: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public SavingsInfo getSavingsInfo(UUID uuid) {
        try {
            double balance = SavingsDataService.getBalance(uuid.toString());
            double rate = AlsBanker.get().getConfig().getDouble("savings.interest_rate", 0.01);
            return new SavingsInfo(true, balance, rate);
        } catch (SQLException e) {
            AlsBanker.get().getLogger().severe("getSavingsInfo failed: " + e.getMessage());
            return SavingsInfo.NONE;
        }
    }

    @Override
    public List<StockHolding> getStockPortfolio(UUID uuid) {
        try {
            List<StockHolding> out = new ArrayList<>();
            for (StockDataService.Holding holding : StockDataService.getPortfolio(uuid.toString())) {
                StockDataService.Stock stock = StockDataService.getStock(holding.symbol());
                if (stock == null) continue;
                out.add(new StockHolding(stock.symbol(), stock.name(), holding.shares(), stock.price(), stock.price()));
            }
            return out;
        } catch (SQLException e) {
            AlsBanker.get().getLogger().severe("getStockPortfolio failed: " + e.getMessage());
            return Collections.emptyList();
        }
    }
}
