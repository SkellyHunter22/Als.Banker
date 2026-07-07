package com.alexander.alsbanker.api;

import com.alexander.alsbanker.AlsBanker;
import com.alexander.alsbanker.CreditCardDataService;
import com.alexander.alsbanker.CreditScoreService;
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
        if (uuid == null) return 0.0;
        return DatabaseManager.getBalance(uuid);
    }

    @Override
    public boolean withdraw(UUID uuid, double amount) {
        // A negative or non-finite amount here would let a caller mint money (a
        // negative "withdrawal" is actually a deposit once it hits `balance - amount`
        // in DatabaseManager), so this boundary rejects anything that isn't a
        // strictly positive, finite amount before it reaches SQL.
        if (uuid == null || !Double.isFinite(amount) || amount <= 0) return false;
        return DatabaseManager.withdraw(uuid, amount);
    }

    @Override
    public boolean deposit(UUID uuid, double amount) {
        if (uuid == null || !Double.isFinite(amount) || amount <= 0) return false;
        return DatabaseManager.deposit(uuid, amount);
    }

    @Override
    public LoanInfo getLoanInfo(UUID uuid) {
        if (uuid == null) return new LoanInfo(0, null, 0);
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
        if (uuid == null || limit <= 0) return Collections.emptyList();
        try {
            return TransactionService.getRecent(uuid.toString(), Math.min(limit, 500));
        } catch (SQLException e) {
            AlsBanker.get().getLogger().severe("getTransactionHistory failed: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public SavingsInfo getSavingsInfo(UUID uuid) {
        if (uuid == null) return SavingsInfo.NONE;
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
        if (uuid == null) return Collections.emptyList();
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

    @Override
    public CreditInfo getCreditInfo(UUID uuid) {
        int fallbackScore = AlsBanker.get().getConfig().getInt("credit.starting_score", 650);
        if (uuid == null) {
            return new CreditInfo(fallbackScore, CreditScoreService.rating(fallbackScore),
                    CreditScoreService.maxLoanForScore(fallbackScore));
        }
        try {
            int score = CreditScoreService.getScore(uuid.toString());
            return new CreditInfo(score, CreditScoreService.rating(score), CreditScoreService.maxLoanForScore(score));
        } catch (SQLException e) {
            AlsBanker.get().getLogger().severe("getCreditInfo failed: " + e.getMessage());
            return new CreditInfo(fallbackScore, CreditScoreService.rating(fallbackScore),
                    CreditScoreService.maxLoanForScore(fallbackScore));
        }
    }

    @Override
    public CreditCardInfo getCreditCardInfo(UUID uuid) {
        if (uuid == null) return CreditCardInfo.NONE;
        try {
            CreditCardDataService.Card card = CreditCardDataService.getCard(uuid.toString());
            if (card == null) return CreditCardInfo.NONE;
            double apr = AlsBanker.get().getConfig().getDouble("credit_card.daily_apr", 0.001);
            return new CreditCardInfo(true, card.limit(), card.balance(), apr);
        } catch (SQLException e) {
            AlsBanker.get().getLogger().severe("getCreditCardInfo failed: " + e.getMessage());
            return CreditCardInfo.NONE;
        }
    }
}
