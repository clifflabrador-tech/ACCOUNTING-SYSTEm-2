# LedgerBuddy - Accounting Worksheet System

Yellow + blue desktop app (Java Swing + MySQL).

```
AccountingWorksheet/
  Main.java                 <- the ONE main file (design, quick prompt, ledger, 12-column worksheet)
  bridges/DbBridge.java     <- bridge 1: database (MySQL, plus --demo data)
  bridges/ExportBridge.java <- bridge 2: CSV export for Excel
  sql/                      <- 01 schema, 02 chart of accounts, 03 sample data
  config/db.properties      <- MySQL login
  lib/                      <- MySQL driver
  run.bat / run.sh          <- start the app
  AccountingWorksheet.jar   <- prebuilt (used automatically if you only have a JRE)
```

## Run it
1. **Just look first:** `run.bat --demo` (Windows) or `./run.sh --demo` - no database needed, nothing is saved.
2. **Real use:** run `sql/01_schema.sql`, then `sql/02_chart_of_accounts.sql`, (optional `sql/03_sample_data.sql`)
   in MySQL, set your password in `config/db.properties`, then `run.bat` / `./run.sh`.
   If MySQL can't be reached the app offers Retry / Demo Mode.

## Screens (Ctrl+1 to Ctrl+4)
1. **Journal Entry** - Regular or Adjusting. Post is only enabled when Debit = Credit.
2. **Chart of Accounts** - fixed, read-only. When sir gives the chart, replace the rows in
   `sql/02_chart_of_accounts.sql` (account_type + normal_side) and re-run it (before posting entries).
3. **Ledger** - one account or all, running balance with Dr / Cr.
4. **12-Column Worksheet** - Unadjusted TB, Adjustments, Adjusted TB, Income Statement, Balance Sheet.
   The banner shows BALANCED only when the Balance Sheet grand-total difference is **0.00**
   (net income/loss is added to the right side). Totals, net income, grand total and the
   difference row stay pinned at the bottom. Filter buttons show one section at a time.

## Quick Prompt (fastest way to enter)
Type one line and press Enter, then check and Post:

```
Dr Cash 5000, Cr Owner's Capital | Owner invested cash
Dr Supplies Expense 4000, Cr Supplies          (add  adj:  in front for an adjusting entry)
Dr 1010 2,500.50, Cr 4010                      (codes work; one line may skip its amount)
```
Account names can be partial ("cash", "owners cap"); 5k = 5000.
