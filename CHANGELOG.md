# Changelog

All notable changes to this project are documented in this file.

## [0.9b] - 2026-07-06

### Added
- `SavingsGuiManager` and `StocksGuiManager`: in-game menus for `/savings` and `/stocks`, opened by default when either command is run with no arguments (or explicitly via the new `gui` subcommand). Deposit/withdraw/list/trade/portfolio logic itself is unchanged — the GUIs drive the same `SavingsCommand`/`StockCommand` methods, now `public` so the GUI classes can call them.
- `BankingAPI.getSavingsInfo(uuid)` / `getStockPortfolio(uuid)`, plus new `SavingsInfo` and `StockHolding` API DTOs, so companion plugins (e.g. AllyPhone) can render savings balances and stock holdings alongside the existing loan/transaction data.
- `DiscordGateway`: opens a persistent Discord Gateway websocket connection (built on Java's `java.net.http.WebSocket`, no new dependency) and IDENTIFYs with `discord_bot_token` so the bot actually shows **online** in Discord — previously `DiscordNotifier` only ever made one-off REST calls, which never establishes presence. Connects in `onEnable()`, disconnects in `onDisable()`, and auto-reconnects on close/error.
- `/loanscheduler testdm [message]` admin command to send a real Discord DM to your own linked account on demand, for verifying `discord_bot_token` and the link/REST path without waiting for a loan/savings/stock event to trigger one.

## [0.9a] - 2026-07-05

### Added
- Savings accounts: `/savings deposit|withdraw|info`, backed by a new `alsbanker_savings` table, moving money to/from the player's Vault balance and crediting daily interest (`savings.interest_rate` in `config.yml`).
- Virtual stock market: `/stocks list|buy|sell|portfolio`, driven by a new `StockMarketEngine` that fluctuates prices on a timer (`stocks.fluctuation_interval_minutes`, `stocks.max_change_percent`, `stocks.min_price`).
- One-time late fee charged the moment an installment first goes overdue (`late_fee.rate`, `late_fee.min_amount`), separate from the existing ongoing daily penalty.
- `LoanGuiManager` GUI and tab completion on `/loan`, `/savings`, and `/stocks`.
- `PhoneAlertBridge` to centralize sending phone/Discord notifications for loan, savings, and stock events.
- Rotating launch log files under `plugins/AlsBanker/logs/` (`launch-1.log` newest, `launch-2.log`, `launch-3.log` oldest), rewritten on every startup.
- Transactions log folder under `plugins/AlsBanker/transactions/`, one file per day (`transactions-YYYY-MM-DD.log`), recording every loan request, loan payment, deposit, and withdrawal.
- `alsbanker_transactions` database table so transaction history survives restarts and can be queried, not just grepped from a log file.
- `/loan history` command showing a player's last 10 transactions in-game.
- `BankingAPI.getLoanInfo(uuid)` and `BankingAPI.getTransactionHistory(uuid, limit)`, exposed via `AlsBanker.get().getBankingAPI()` for other plugins (e.g. AllyPhone) to render a bank-app style screen (balance, active loan, transaction history).
- Friendlier, step-by-step console logging during `onEnable()` (config, database, GUIs, commands, scheduler, PlaceholderAPI).
- ASCII success banner printed to console once `onEnable()` finishes without error.

### Changed
- `SchedulerEngine`'s daily cycle now applies the one-time late fee and the ongoing daily penalty as two distinct passes (`chargeLateFees` / `chargeDailyPenalties`), tracked per-schedule via a new `last_penalized_date` column so penalties apply at most once per calendar day regardless of `interval_minutes`.
- `SchedulerEngine` also credits savings interest each cycle via `SavingsDataService.applyDailyInterest`.
- `LoanEventListener` notification methods now take an explicit message (`notifyLoanCreated(Player, String)`, `notify(uuid, message)`) instead of hardcoding loan-specific text, so the same path can be reused for savings and stock notifications.
- `onEnable()` forces ownership of the `/loan` command via reflection once all plugins finish enabling, so EcoXpert's own `loan` alias can no longer steal it.
- Default `loan.installments` changed from 4 to 3 and `loan.installment_interval_days` from 7 to 3; default `penalty_rate` changed from 0.01 to 0.02.
- `LoanDataService.applyPayment()` now returns the remaining outstanding balance (or `null` if no active loan) instead of a plain boolean, so callers can log the resulting balance without an extra query.

## [0.6] - 2026-07-05

### Changed
- Renamed the plugin and all Java packages/classes from `EcoLoanScheduler` (`com.alexander.ecoloanscheduler`) to **Al's Banker** (`com.alexander.alsbanker`), including `pom.xml`, `plugin.yml`, and IDE project files.
- `plugin.yml` Vault dependency changed from a hard `depend: [Vault]` to `softdepend: [Vault, PlaceholderAPI]` — the hard dependency combined with Vault's `load: startup` phase was silently excluding AlsBanker from Bukkit's enable order entirely (no error, no enable, nothing).

### Added
- Real loan lifecycle: `/loan request <amount>`, `/loan pay <amount>`, `/loan info`, backed by new `ecoxpert_loans` / `ecoxpert_loan_schedules` tables (evenly-split installments, configurable interest rate and cadence via `config.yml`'s new `loan:` section).
- Vault economy integration (`net.milkbowl.vault.economy.Economy`) so loan proceeds and repayments move through the same balance EcoXpert/Vault show everywhere else in-game, looked up lazily per-call to avoid load-order issues.
- `onEnable()` now actually wires everything up: registers all listeners, binds all three commands (`/loanscheduler`, `/loan`, `/linkdiscord`, `/unlinkdiscord`) to their executors, starts `SchedulerEngine`, and registers the PlaceholderAPI expansion if present. Previously none of this happened and the plugin was inert.
- `DatabaseManager.getBalance/withdraw/deposit`, backed by a new `alsbanker_balances` table, implementing the `BankingAPI`/`AlsBankingAPI` surface.
- `onDisable()` now closes the Hikari connection pool.

### Fixed
- `BankGuiManager` referenced a nonexistent `JavaTargetGUI.openSelector()` — corrected to the real `JavaNumpadGUI.openNumpad()`.
- `LoanDataService` was missing `openConnection()`, `getOutstandingBalance()`, `getTotalOutstanding()`, `getActiveLoanCount()`, `getOverdueScheduleCount()` — all referenced elsewhere but never implemented, so the project didn't compile.
- `SchedulerEngine` queried `ecoxpert_loans` / `ecoxpert_loan_schedules`, tables that were never created by any code path — `initializeTables()` now creates the schema the rest of the plugin actually depends on.
- Three "bank" classes (`BankGuiManager`, `BedrockFormBridge`, `JavaNumpadGUI`) declared package `...ecoloanscheduler.bank` while physically sitting in the flat parent folder; moved to match their declared package.

## [0.1]

- Initial project skeleton: `SchedulerEngine` (interest/penalty cycle), Discord account linking, PlaceholderAPI expansion, admin GUI. Loan creation, repayment, and several referenced data-access methods did not yet exist.
