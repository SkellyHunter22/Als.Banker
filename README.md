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
| `/savings deposit\|withdraw\|info\|gui <amount>` | Move money to/from your savings balance (daily interest via `savings.interest_rate`), or open the savings GUI | `alsbanker.user` |
| `/stocks list\|buy\|sell\|portfolio\|gui [symbol] [shares]` | Trade on the virtual stock market against your Vault balance, or open the stocks GUI | `alsbanker.user` |
| `/loan credit` | Show your credit score, rating, and current max loan amount | `alsbanker.user` |
| `/creditcard apply\|info\|charge\|pay [amount]` | Apply for / manage a revolving credit card | `alsbanker.user` |
| `/steal <player>` | Attempt to pickpocket another online player (timing minigame — see below) | `alsbanker.steal` |
| `/loanscheduler reload\|runnow\|gui\|stats\|testdm` | Admin controls: reload config, force an overdue-loan cycle, open the admin GUI, view server-wide stats, or send yourself a test Discord DM | `alsbanker.admin` |
| `/linkdiscord <discordId>` | Link your Discord account for loan/overdue DMs | `alsbanker.user` |
| `/unlinkdiscord` | Unlink your Discord account | `alsbanker.user` |

Every command above has tab completion registered — `<Tab>` after `/loan`, `/savings`, `/stocks`,
`/creditcard`, or `/loanscheduler` cycles through its subcommands; `/steal <Tab>` suggests online
player names; `/stocks buy|sell <Tab>` suggests known stock symbols; and dollar-amount arguments
suggest a few common values (`100`, `250`, `500`, `1000`). `/linkdiscord` and `/unlinkdiscord`
deliberately suggest nothing, since a Discord ID is free text and suggesting online player names
there would be actively misleading.

## How loans work

1. **Request** (`/loan request <amount>`) — capped at `loan.max_amount`; blocked while you already
   have an active loan. The loan is split into `loan.installments` equal payments, one due every
   `loan.installment_interval_days`, and the full amount is deposited into your Vault balance
   immediately.
2. **Repay** (`/loan pay <amount>`) — withdraws from your Vault balance and pays off installments
   oldest-due-first.
3. **Overdue handling** — a background cycle (interval set by `interval_minutes`) scans for
   installments that missed their due date, charges a one-time late fee
   (`late_fee.rate` / `late_fee.min_amount`), then applies ongoing daily interest
   (`interest.mode`: `outstanding`, `principal`, or `average`) plus a capped penalty
   (`penalty_rate` / `penalty_cap_fraction`) for every day the loan stays unpaid past that.
   The player is notified in-game, on their phone, and via Discord (if linked) each time.

## Savings & stocks

- **Savings** (`/savings deposit|withdraw|info|gui`) — moves money to/from your Vault balance into
  a separate savings ledger, which earns daily interest (`savings.interest_rate`) credited once per
  calendar day by the same background cycle that handles loan penalties.
- **Stocks** (`/stocks list|buy|sell|portfolio|gui`) — a self-contained virtual market. Prices
  fluctuate on a timer (`stocks.fluctuation_interval_minutes`, capped at `±stocks.max_change_percent`
  per cycle, floored at `stocks.min_price`); buying/selling moves money through your Vault balance.
- Both commands open an in-game GUI by default when run with no arguments (or via the explicit `gui`
  subcommand) — `/savings deposit <amount>`, `/stocks buy <symbol> <shares>`, etc. still work directly
  for scripting/macros.

## Credit score & credit cards

- Every player has a credit score (300-850, `credit.starting_score` default 650), stored in
  `alsbanker_credit`. It gates how much they can borrow — `/loan request` and `/creditcard apply`
  both check it — rather than everyone getting the same flat `loan.max_amount`.
- **Raises your score**: making a loan payment (`credit.score_gain_on_time_payment`), paying a loan
  off entirely (`credit.score_gain_loan_paid_off`), depositing into savings
  (`credit.score_gain_savings_deposit`), paying down a credit card (`credit.score_gain_card_payment`).
- **Lowers your score**: a missed installment triggering a late fee (`credit.score_loss_late_fee`),
  each additional day a loan stays overdue (`credit.score_loss_daily_penalty`), and carrying a credit
  card balance above `credit_card.high_utilization_threshold` (`credit.score_loss_high_utilization`,
  applied once per day it stays that high).
- **Tiers**: `credit.tier1_min_score` / `tier2_min_score` / `tier3_min_score` (default 500/650/750)
  each unlock a higher `loan_tierN_max` / `credit_card_tierN_max`, always capped by the server-wide
  `loan.max_amount`. Below `credit.min_score_for_loan` / `min_score_for_card`, borrowing is refused
  outright. Check yours with `/loan credit`.
- **Credit cards** (`/creditcard apply|info|charge|pay`) are a revolving line, unlike `/loan`'s fixed
  installments: `charge <amount>` draws against the limit and deposits straight into your Vault
  balance, `pay <amount>` reduces what you owe, and any carried balance accrues `credit_card.daily_apr`
  interest each day (same background cycle as loan penalties/savings interest).

## Stealing

`/steal <player>` opens a timing minigame — a marker sweeps back and forth along a 9-slot bar, and
you get exactly one click to catch it lined up with the highlighted "unlock" slot (a fixed
random position each attempt). Miss the timing, click the wrong slot, or let it run out
(`theft.marker_speed_ticks` controls difficulty) and the heist fails.

- You must be within `theft.max_distance` blocks of the target, who must be holding at least
  `theft.min_target_balance`.
- **Success**: `theft.steal_percent` of the victim's Vault balance (capped at
  `theft.max_steal_amount`) moves to you; the victim gets a `theft.victim_cooldown_seconds` grace
  period before they can be robbed again, and is notified in-game, on their phone, and via Discord
  (same `LoanEventListener.notify` path as loan/savings/stock events).
- **Failure**: you pay the victim a `theft.fail_fine_percent` fine as a "getting caught" penalty.
- Either way, you're on `theft.cooldown_seconds` cooldown before trying again (multiplied by
  `theft.fail_cooldown_multiplier` after a failed attempt).
- Set `theft.enabled: false` to disable the whole feature.

## Discord integration

- **DMs** (`DiscordNotifier`) are sent over the plain Discord REST API whenever a linked player's
  loan, savings, or stock event fires (via `PhoneAlertBridge`) — no persistent connection required
  for this to work, just a valid `discord_bot_token`.
- **Online presence** (`DiscordGateway`) is a separate, persistent Gateway websocket connection
  opened on `onEnable()` purely so the bot shows as *online* in Discord (REST calls alone don't do
  this). It auto-reconnects on disconnect and closes cleanly on `onDisable()`.
- Use `/loanscheduler testdm [message]` to verify the whole path (token validity, your Discord link,
  and REST delivery) without waiting for a real loan/savings/stock event.

## Configuration (`config.yml`)

```yaml
mysql:
  url: "jdbc:mysql://localhost:3306/ecoxpert_inf"
  user: "root"
  password: ""

interval_minutes: 60        # how often the overdue-loan/savings-interest/late-fee cycle runs

penalty_rate: 0.02          # ongoing daily penalty on overdue balance
penalty_cap_fraction: 0.50  # penalty is capped at this fraction of outstanding balance

late_fee:
  rate: 0.10                # one-time fee charged the moment an installment first goes overdue
  min_amount: 10.0

interest:
  mode: "outstanding"        # outstanding | principal | average

loan:
  max_amount: 5000.0
  interest_rate: 0.05
  installments: 3
  installment_interval_days: 3

savings:
  interest_rate: 0.01        # 1% per day, credited once per calendar day

stocks:
  fluctuation_interval_minutes: 30
  max_change_percent: 0.05   # each cycle, price moves by up to +/-5%
  min_price: 0.50

discord_webhook: ""
discord_bot_token: ""        # used for both DMs (REST) and online presence (Gateway)

theft:
  enabled: true
  cooldown_seconds: 300
  fail_cooldown_multiplier: 2
  victim_cooldown_seconds: 600
  max_distance: 5
  min_target_balance: 100.0
  steal_percent: 0.15
  max_steal_amount: 500.0
  fail_fine_percent: 0.05
  marker_speed_ticks: 4

credit:
  starting_score: 650
  min_score: 300
  max_score: 850
  min_score_for_loan: 500
  min_score_for_card: 500
  tier1_min_score: 500
  loan_tier1_max: 1000.0
  credit_card_tier1_max: 500.0
  tier2_min_score: 650
  loan_tier2_max: 2500.0
  credit_card_tier2_max: 1500.0
  tier3_min_score: 750
  loan_tier3_max: 5000.0
  credit_card_tier3_max: 3000.0
  score_gain_on_time_payment: 5
  score_gain_loan_paid_off: 20
  score_gain_savings_deposit: 2
  score_gain_card_payment: 3
  score_loss_late_fee: 15
  score_loss_daily_penalty: 5
  score_loss_high_utilization: 3

credit_card:
  daily_apr: 0.001
  high_utilization_threshold: 0.5
```

## Data storage

- **Database** (shared MySQL instance, tables auto-created on startup):
  - `ecoxpert_loans`, `ecoxpert_loan_schedules` — loan principal/outstanding and installment schedule
  - `alsbanker_balances` — the standalone ledger backing `BankingAPI.getBalance/withdraw/deposit`
  - `alsbanker_savings` — savings balances, credited daily interest
  - stock market tables backing `/stocks` (symbol/price history and per-player holdings)
  - `alsbanker_credit` — per-player credit score
  - `alsbanker_credit_cards` — credit card limit/balance/APR
  - `alsbanker_transactions` — every loan request/payment, deposit/withdrawal, trade, theft, and credit card charge/payment
- **Logs** (`plugins/AlsBanker/logs/`): rotating `launch-1.log` (most recent) through `launch-3.log`,
  rewritten each startup.
- **Transaction log files** (`plugins/AlsBanker/transactions/`): one human-readable file per day
  (`transactions-YYYY-MM-DD.log`), in addition to the database table.

## Security notes

- **SQL injection**: every database query in the plugin uses `PreparedStatement` with bound
  parameters — none of it string-concatenates player input (names, Discord IDs, stock symbols, chat
  messages) into SQL. The only raw `Statement` usage is fixed DDL (`CREATE TABLE`) and static seed
  data with no user input involved.
- **Money-moving amounts** are validated everywhere a player (or another plugin) can supply one:
  `/loan`, `/savings`, `/stocks`, `/creditcard`, and the Bedrock transfer form all reject non-finite
  (`NaN`/`Infinity`) and non-positive amounts before they reach the database or Vault. The public
  `BankingAPI.withdraw`/`deposit` do the same at the API boundary, and `DatabaseManager.withdraw`/
  `deposit` enforce it again underneath — a negative "withdrawal" would otherwise flip into a free
  deposit once it hits `balance - amount`, so this is checked at both layers rather than trusting
  the caller.
- **Race conditions**: `PlayerActionLock` serializes a player's own loan/savings/stock/credit-card
  commands so spamming a command can't fire two overlapping requests that both pass the same stale
  balance check before either lands (a classic TOCTOU double-spend). Database-level state changes
  that need to be atomic (loan payments, savings/stock/credit-card balance updates) additionally use
  `SELECT ... FOR UPDATE` inside a transaction, not just the in-memory lock.
- **Scheduler double-processing**: an `AtomicBoolean` guard in `SchedulerEngine` stops the periodic
  overdue-loan cycle and a manual `/loanscheduler runnow` from running concurrently and
  double-charging the same day's late fee or penalty.
- **GUI integrity**: all bank/theft/steal inventory click handlers cancel the click
  (`InventoryClickEvent#setCancelled(true)`) before any other logic runs, so players can't drag,
  shift-click, or otherwise extract/duplicate the decorative GUI items.
- **Public API surface**: `com.alexander.alsbanker.api` is the only package other plugins should
  depend on. It null-checks its `UUID` parameters and swallows/logs its own `SQLException`s rather
  than leaking database failures to callers as unchecked exceptions — see the Javadoc on
  `BankingAPI` for the exact contract (blocking I/O, null handling, failure fallbacks).

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
    SavingsInfo savings = api.getSavingsInfo(player.getUniqueId());
    List<StockHolding> portfolio = api.getStockPortfolio(player.getUniqueId());
    CreditInfo credit = api.getCreditInfo(player.getUniqueId());           // score, rating, max loan
    CreditCardInfo card = api.getCreditCardInfo(player.getUniqueId());     // check card.available()
}
```

To make this handshake reliable:

1. Add `softdepend: [AlsBanker]` (or `depend:`) to your plugin's `plugin.yml` so Bukkit loads
   Al's Banker first.
2. Add the built `AlsBanker-<version>.jar` as a `provided`-scope build dependency, and only import
   from `com.alexander.alsbanker.api` (`BankingAPI`, `LoanInfo`, `Transaction`, `SavingsInfo`,
   `StockHolding`, `CreditInfo`, `CreditCardInfo`) — not internal classes.
3. Look up the API lazily at the point of use (e.g. when a player opens the bank screen) rather
   than caching it once at your own `onEnable()`.

A successful startup prints a small ASCII banner to console right after Al's Banker finishes
enabling — a quick visual confirmation before your plugin attempts to hook in.

### Instructions for hooking AllyPhone (or any companion plugin) into credit score / credit card

If you're a future Claude session (or a dev) wiring AllyPhone's bank-app screen up to the credit
features added in this version, here's exactly what's available and how to use it:

- **`BankingAPI.getCreditInfo(uuid)`** returns a `CreditInfo` record — never null, since every
  player has a score even if they've never borrowed. Fields: `score()` (int, 300-850), `rating()`
  (`"Poor"`/`"Fair"`/`"Good"`/`"Excellent"`, from `CreditScoreService.rating(int)`), and
  `maxLoanAmount()` (the cap `/loan request` would currently enforce for this player). Use this to
  render a credit-report card in the phone UI, or to gray out a "Request Loan" button client-side
  before the player even tries a doomed request.
- **`BankingAPI.getCreditCardInfo(uuid)`** returns a `CreditCardInfo` record. Check
  `available()` first — `false` means the player hasn't run `/creditcard apply` yet, and `limit()`/
  `balance()`/`dailyApr()` will all be zero. When `true`, use `availableCredit()` (helper method,
  `limit() - balance()`) and `utilization()` (helper method, `balance() / limit()`) to render a
  spending-limit bar; don't recompute those by hand.
- **Don't call into `CreditScoreService`, `CreditCardDataService`, or any other class outside
  `com.alexander.alsbanker.api`** from AllyPhone — those are internal and can change shape between
  versions without notice. The `api` package is the only supported surface.
- **Notifications are already wired up** — you don't need to build a separate alert path. Every
  credit-score-affecting event (loan payment, late fee, daily overdue penalty, credit card interest,
  theft) already calls through `LoanEventListener.notify(uuid, message)` / `notifyLoanCreated`, which
  in turn calls `PhoneAlertBridge.send(...)` (dispatches `phonealert <player> AlsBanker <message>` —
  make sure AllyPhone's `PhoneAlertCommand` is still registered and rejoins args after
  `<player> <source>` into the full message) *and*, if the player has linked Discord via
  `/linkdiscord`, `DiscordNotifier.dm(...)`. If AllyPhone needs a *new* kind of alert this system
  doesn't already send, add the call in `LoanEventListener`/`SchedulerEngine`/`CreditCardCommand` on
  the AlsBanker side rather than polling for changes from AllyPhone.
- **PlaceholderAPI**: if AllyPhone (or its UI) already reads other `%alsbanker_*%` placeholders,
  the new ones are `%alsbanker_credit_score%`, `%alsbanker_credit_rating%`,
  `%alsbanker_credit_max_loan%`, `%alsbanker_creditcard_limit%`, `%alsbanker_creditcard_balance%`,
  `%alsbanker_creditcard_available%`, and `%alsbanker_creditcard_utilization%` (a whole-number
  percentage, e.g. `"42"` not `"0.42"`). These are cache-backed and safe to call from a synchronous
  context (scoreboard, chat format, etc.) — the first call for a given player may return a stale/
  default value while the async refresh is in flight, same as the existing `%alsbanker_outstanding%`.
