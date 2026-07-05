# Al's Banker

A Paper/Spigot plugin that stands in for EcoXpert's loan feature: players can request a loan,
pay it back in installments, and get charged interest/penalties if they fall behind — all backed
by a real database and exposed through an API so companion plugins (like AllyPhone) can build a
bank-app UI on top of it.

See [`CHANGELOG.md`](CHANGELOG.md) for a version-by-version history of what's changed.

## Requirements

- Java 21
- Paper/Spigot 1.20.4+
- MySQL (this plugin shares EcoXpert's `ecoxpert_inf` database by default — see `config.yml`)
- [Vault](https://www.spigotmc.org/resources/vault.34315/) with an economy provider registered (e.g. EcoXpert) — soft dependency; loan commands report a clear error if it's missing rather than failing silently
- [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) — optional, soft dependency
- [Geyser](https://geysermc.org/)/Floodgate — optional, used for the Bedrock bank-transfer form

## Building

```
mvn package
```

The shaded jar is written to `target/AlsBanker-<version>.jar`. Drop it into your server's
`plugins/` folder alongside Vault and (optionally) PlaceholderAPI.

## Commands

| Command | Description | Permission |
|---|---|---|
| `/loan request <amount>` | Take out a loan, capped at `loan.max_amount` in `config.yml`, split into installments | `alsbanker.user` |
| `/loan pay <amount>` | Pay down your active loan; money is withdrawn from your Vault balance | `alsbanker.user` |
| `/loan info` | Show your outstanding balance and next payment due | `alsbanker.user` |
| `/loan history` | Show your last 10 transactions | `alsbanker.user` |
| `/loanscheduler reload\|runnow\|gui\|stats` | Admin controls: reload config, force an overdue-loan cycle, open the admin GUI, view server-wide stats | `alsbanker.admin` |
| `/linkdiscord <discordId>` | Link your Discord account for loan/overdue DMs | `alsbanker.user` |
| `/unlinkdiscord` | Unlink your Discord account | `alsbanker.user` |
| `/savings deposit\|withdraw\|info [amount]` | Move money between your Vault balance and your savings account, or check your balance | `alsbanker.user` |
| `/stocks list\|buy\|sell\|portfolio [symbol] [shares]` | Browse and trade the virtual stock market against your Vault balance | `alsbanker.user` |

## How loans work

1. **Request** (`/loan request <amount>`) — capped at `loan.max_amount`; blocked while you already
   have an active loan. The loan is split into `loan.installments` equal payments, one due every
   `loan.installment_interval_days`, and the full amount is deposited into your Vault balance
   immediately.
2. **Repay** (`/loan pay <amount>`) — withdraws from your Vault balance and pays off installments
   oldest-due-first.
3. **Overdue handling** — a background cycle (interval set by `interval_minutes`) scans for
   installments past their due date. The moment an installment first goes overdue it's charged a
   one-time late fee (`late_fee.rate` of the missed installment, floored at `late_fee.min_amount`).
   From then on, once per calendar day it accrues interest (`loan.interest_rate`, calculated per
   `interest.mode`: `outstanding`, `principal`, or `average`) plus a capped penalty
   (`penalty_rate` / `penalty_cap_fraction`), and notifies the player in-game and via Discord
   (if linked).

## Savings and stocks

- **Savings** (`/savings deposit|withdraw|info`) — money moved into savings earns
  `savings.interest_rate` (default 1%) once per calendar day, credited by the same background
  cycle that handles overdue loans.
- **Stock market** (`/stocks list|buy|sell|portfolio`) — a `StockMarketEngine` timer
  (`stocks.fluctuation_interval_minutes`) nudges each stock's price by up to
  `stocks.max_change_percent` per cycle, never below `stocks.min_price`. Buying/selling trades
  against the player's Vault balance.

## Configuration (`config.yml`)

```yaml
mysql:
  url: "jdbc:mysql://localhost:3306/ecoxpert_inf"
  user: "root"
  password: ""

interval_minutes: 60        # how often the overdue-loan cycle runs

penalty_rate: 0.02          # ongoing daily penalty applied to overdue installments
penalty_cap_fraction: 0.50  # penalty is capped at this fraction of outstanding balance

late_fee:
  rate: 0.10       # one-time fee, as a fraction of the missed installment's amount_due
  min_amount: 10.0 # fee is never lower than this, even on tiny installments

interest:
  mode: "outstanding"        # outstanding | principal | average

loan:
  max_amount: 5000.0
  interest_rate: 0.05
  installments: 3
  installment_interval_days: 3

savings:
  interest_rate: 0.01 # 1% per day, credited once per calendar day

stocks:
  fluctuation_interval_minutes: 30
  max_change_percent: 0.05 # each cycle, price moves by up to +/-5%
  min_price: 0.50

discord_webhook: ""
discord_bot_token: ""
```

## Data storage

- **Database** (shared MySQL instance, tables auto-created on startup):
  - `ecoxpert_loans`, `ecoxpert_loan_schedules` — loan principal/outstanding and installment schedule
  - `alsbanker_balances` — the standalone ledger backing `BankingAPI.getBalance/withdraw/deposit`
  - `alsbanker_savings` — savings account balances, credited daily interest by the scheduler
  - `alsbanker_transactions` — full transaction history (loan requests, payments, deposits, withdrawals, savings interest)
- **Logs** (`plugins/AlsBanker/logs/`): rotating `launch-1.log` (most recent) through `launch-3.log`,
  rewritten each startup.
- **Transaction log files** (`plugins/AlsBanker/transactions/`): one human-readable file per day
  (`transactions-YYYY-MM-DD.log`), in addition to the database table.

## Integrating another plugin (e.g. AllyPhone)

Al's Banker exposes a small public API for other plugins to build on, rather than requiring them
to touch its internal classes or the database directly.

```java
Plugin plugin = Bukkit.getPluginManager().getPlugin("AlsBanker");
if (plugin instanceof AlsBanker) {
    BankingAPI api = ((AlsBanker) plugin).getBankingAPI();

    double balance = api.getBalance(player.getUniqueId());
    LoanInfo loan = api.getLoanInfo(player.getUniqueId());       // check loan.hasActiveLoan()
    List<Transaction> history = api.getTransactionHistory(player.getUniqueId(), 20);
}
```

To make this handshake reliable:

1. Add `softdepend: [AlsBanker]` (or `depend:`) to your plugin's `plugin.yml` so Bukkit loads
   Al's Banker first.
2. Add the built `AlsBanker-<version>.jar` as a `provided`-scope build dependency, and only import
   from `com.alexander.alsbanker.api` (`BankingAPI`, `LoanInfo`, `Transaction`) — not internal
   classes.
3. Look up the API lazily at the point of use (e.g. when a player opens the bank screen) rather
   than caching it once at your own `onEnable()`.

A successful startup prints a small ASCII banner to console right after Al's Banker finishes
enabling — a quick visual confirmation before your plugin attempts to hook in.
