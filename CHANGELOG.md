# Changelog

All notable changes to this project are documented in this file.

## [0.11] - 2026-07-06

### Added
- `/loanscheduler reload`: full soft-restart of the plugin instead of just a config re-read. Hands off to PlugManX/PlugMan (if installed) to properly disable/re-enable the plugin through Bukkit's plugin manager; falls back to calling `onDisable()`/`onEnable()` directly if neither is present. Never disables the plugin on failure — it reports the error and leaves the previous state running.
- **Audit logging** (`AuditLogger`, `alsbanker_audit_log` table + `plugins/AlsBanker/audit/audit-*.log`): every AlsBanker command (via `CommandAuditListener`), every transaction (`TransactionService`), Discord link/unlink, and admin reload is now logged with both player name and UUID. `alsbanker_transactions` also gained a `player_name` column (auto-migrated on existing installs).
- **Vault economy audit wrapper** (`VaultEconomyAuditWrapper`): registers a decorator over whichever Economy provider Vault has (EssentialsX, etc.) at the highest service priority, so deposits/withdrawals made through Vault by *other* plugins get logged too, not just AlsBanker's own. Delegates every call unchanged to the real provider — it only observes, never alters behavior.
- **Easier Discord linking**: `/linkdiscord` with no arguments now generates a one-time 6-digit code and asks the player to DM `link <code>` to the bot; `DiscordGateway` listens for that DM (new `DIRECT_MESSAGES` + `MESSAGE_CONTENT` gateway intents) and completes the link automatically. The old `/linkdiscord <discordID>` still works as a manual fallback. Requires enabling the **Message Content Intent** for the bot in the Discord Developer Portal.
- `/loan request` now requires a linked Discord account (loan notifications matter enough that we don't want players missing overdue/late-fee warnings because they never linked).
- More auto-message variety: savings interest, credit card interest, loan-approved, and loan-payment notifications now pull from a pool of varied phrasings (`LoanServicerMessages`), matching the existing late-fee/daily-penalty treatment.
- Clickable, hoverable help list: `/loanscheduler` with no arguments now renders each command as a clickable chat component (click to fill the command in, hover for a description) instead of plain text.

## [0.10] - 2026-07-06

### Added
- **Credit scores**: every player has a 300-850 credit score (`alsbanker_credit` table, `CreditScoreService`), starting at `credit.starting_score`. It rises with on-time loan payments, paying a loan off entirely, savings deposits, and credit card payments; it falls with late fees, ongoing overdue penalties, and carrying a high-utilization credit card balance. `/loan request` and `/creditcard apply` now both gate on it — below `credit.min_score_for_loan`/`min_score_for_card` borrowing is refused outright, and the max amount scales through three configurable tiers (`credit.tier1_min_score`/`tier2_min_score`/`tier3_min_score`) instead of everyone sharing the same flat cap. Check yours with the new `/loan credit`.
- **Credit cards** (`/creditcard apply|info|charge|pay`, `CreditCardDataService`, `alsbanker_credit_cards` table): a revolving credit line separate from `/loan`'s fixed installments. `charge <amount>` draws against the score-based limit and deposits straight into Vault; `pay <amount>` reduces the balance owed; any carried balance accrues `credit_card.daily_apr` interest each day via the same scheduler cycle as loan penalties, and staying above `credit_card.high_utilization_threshold` costs credit score.
- **Theft** (`/steal <player>`, `TheftMinigameManager`, `TheftGuiListener`): pickpocket another online player, gated by a timing minigame — a marker sweeps across a 9-slot bar and you get one click to catch it on the highlighted slot. Success moves `theft.steal_percent` of the victim's balance (capped at `theft.max_steal_amount`) to the thief and puts the victim on a robbery-immunity cooldown; failure costs the thief a `theft.fail_fine_percent` fine paid to the victim. Both outcomes notify the victim in-game/phone/Discord through the existing `LoanEventListener.notify` path.
- `BankingAPI.getCreditInfo(uuid)` / `getCreditCardInfo(uuid)`, plus new `CreditInfo` and `CreditCardInfo` API DTOs, so companion plugins (e.g. AllyPhone) can render credit score and credit card state alongside the existing loan/savings/stock data. README now has a dedicated section of instructions for hooking AllyPhone into these.
- Seven new PlaceholderAPI placeholders: `%alsbanker_credit_score%`, `_credit_rating%`, `_credit_max_loan%`, `_creditcard_limit%`, `_creditcard_balance%`, `_creditcard_available%`, `_creditcard_utilization%`.
- Tab completion for every command: `/loanscheduler`, `/linkdiscord`, and `/unlinkdiscord` previously had none (so Bukkit's default fallback suggested online player names even where that made no sense, e.g. for a Discord ID argument). `/loanscheduler` now cycles its subcommands; `/linkdiscord`/`/unlinkdiscord` explicitly suggest nothing.

### Fixed
- `BankingAPI.withdraw(uuid, amount)` / `deposit(uuid, amount)` didn't validate `amount` — a negative or `NaN`/`Infinity` value passed by a buggy or malicious companion plugin would flip `withdraw` into a free deposit (`balance - (-x)`) or let `deposit` drain a balance below zero with no floor. Validated at both the `AlsBankingAPI` boundary and again inside `DatabaseManager.withdraw`/`deposit` itself, since the latter is reachable from anywhere in the plugin, not just through the public API.
- Every other `BankingAPI` method now null-checks its `UUID` parameter and returns its documented safe default (`0`, empty list, or a `NONE` constant) instead of risking an NPE if a caller passes `null`.
- Added Javadoc to `BankingAPI` spelling out the actual contract: every method does blocking JDBC I/O (don't call from a hot path on the main thread), already catches and logs its own `SQLException`, and expects non-null UUIDs.

## [0.9b] - 2026-07-06

### Added
- `SavingsGuiManager` and `StocksGuiManager`: in-game menus for `/savings` and `/stocks`, opened by default when either command is run with no arguments (or explicitly via the new `gui` subcommand). Deposit/withdraw/list/trade/portfolio logic itself is unchanged — the GUIs drive the same `SavingsCommand`/`StockCommand` methods, now `public` so the GUI classes can call them.
- `BankingAPI.getSavingsInfo(uuid)` / `getStockPortfolio(uuid)`, plus new `SavingsInfo` and `StockHolding` API DTOs, so companion plugins (e.g. AllyPhone) can render savings balances and stock holdings alongside the existing loan/transaction data.
- `DiscordGateway`: opens a persistent Discord Gateway websocket connection (built on Java's `java.net.http.WebSocket`, no new dependency) and IDENTIFYs with `discord_bot_token` so the bot actually shows **online** in Discord — previously `DiscordNotifier` only ever made one-off REST calls, which never establishes presence. Connects in `onEnable()`, disconnects in `onDisable()`, and auto-reconnects on close/error.
- `/loanscheduler testdm [message]` admin command to send a real Discord DM to your own linked account on demand, for verifying `discord_bot_token` and the link/REST path without waiting for a loan/savings/stock event to trigger one.
- `LoanServicerMessages`: a pool of varied flavor-text lines for the automated late-fee and daily-overdue-penalty notifications, so players don't see the exact same wording every time the scheduler charges them.

### Fixed
- `LoanCommand`/`SavingsCommand`/`StockCommand` amount/share parsing accepted `NaN` and `Infinity` (`Double.parseDouble` doesn't reject them, and comparisons like `NaN <= 0` are always `false`), letting `/loan request NaN` or `/savings deposit Infinity` bypass the positive-amount and max-amount checks entirely. Now rejected via `Double.isFinite`.
- `LoanCommand.requestLoan`/`payLoan`, `SavingsCommand.deposit`/`withdraw`, and `StockCommand.trade` checked the player's balance/loan state once, then finished the actual database write and Vault withdrawal on a separate async-then-main-thread hop — spamming the command fired multiple overlapping requests that all passed the same stale check before any of them landed, letting a player overspend/over-borrow past their real balance or stack more than one active loan. Added `PlayerActionLock`, a per-player mutex that serializes these commands.
- `BankGuiManager.onMenuClick` threw an NPE when a player clicked an empty slot in the Bank App GUI (`getCurrentItem()` can return `null`); now null-checked.
- `BedrockFormBridge.sendTransferForm`'s player-to-player transfer form was a dead stub (the valid-result handler did nothing); implemented the actual Vault transfer, with online-target/self-transfer/amount validation and transaction logging on both ends.
- `DatabaseManager.setup()` passed a `null` `mysql.url`/`mysql.user` straight into Hikari, crashing `onEnable()` with an opaque exception if `config.yml` was missing MySQL credentials; now validated up front, plugin disables itself cleanly with a clear log message instead.
- `SchedulerEngine.runCycle()` had no guard against running twice concurrently — the periodic timer and a manual `/loanscheduler runnow` could overlap and double-charge the same overdue schedule for the same day. Added an `AtomicBoolean` guard that skips a cycle if one is already in progress.

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
