# CUE 147 - project instructions for Claude

These are the owner's full requirements for turning CUE 147 into a production app.
Follow them in every session. Work phase by phase; after each phase stop, summarise,
explain how to test, wait for the owner's go-ahead, and commit.

## Requirements (as given by the owner)

You are working on CUE 147, an existing Java Swing management app for a snooker/billiards club. It started as a basic college project. Upgrade it into a real, production-ready app that club staff use every day.

BEFORE YOU START
- Read the existing code first and keep everything that already works. Migrate existing data; do not lose it.
- Save these full requirements to CLAUDE.md in the project root so they persist across sessions.
- If the project is not a git repo, run git init and make an initial commit.
- Work in the phases listed at the end. After each phase, stop, summarise what changed, tell me how to test it, and wait for my go-ahead. Commit after each phase.

GENERAL RULES
- Currency is Indian Rupees (₹).
- Simplicity is the top priority. Counter staff must be able to use every screen without training. Use fewer screens, fewer clicks, big clear buttons and sensible defaults.
- Show only the daily-use screens on the main page. Put setup items (staff, menu, membership plans, backups, reports) under one Settings/Admin area.
- Validate every input and show clear, friendly error messages.

1. REAL DESKTOP APP (WINDOWS + MAC)
- Staff use Windows PCs. The owner sometimes uses a Mac. The same codebase must run on both.
- Use JDK 17+ and set up Maven or Gradle if not already present.
- Package with jpackage so the app installs like a normal app:
  - Windows: .exe or .msi installer with a Start menu entry and a desktop shortcut, and no console window.
  - Mac: .app inside a .dmg.
  - The app must open by double-clicking, never from the terminal.
- Add an app icon and version number.
- Add a GitHub Actions workflow that builds the Windows installer on a Windows runner and the Mac .dmg on a macOS runner, and uploads both as downloadable artifacts or release files when a version tag is pushed.

2. SHARED DATABASE
- All Windows PCs and the Mac must see the same live data.
- If the app currently stores data in local files or a local-only database, move it to one central database (for example MySQL or PostgreSQL) that every installed copy connects to.
- Keep connection settings (host, port, user, password) in a config file or a settings screen, not hard-coded.
- Handle connection failures gracefully with a clear message.

3. MODERN, SIMPLE UI
- Apply a modern look-and-feel (for example FlatLaf) with consistent fonts, colours and spacing defined in one place.
- Use a sidebar or tab navigation instead of many popup windows.
- Use sortable, searchable tables, plus dropdowns and date pickers instead of free text where possible.
- Add a home dashboard: today's revenue, active tables, and memberships expiring soon.

4. SIMPLE CUSTOMER FLOW
- One search box: staff type a name or phone number. If the customer exists, select them. If not, show one "Add" button with only Name and Phone required.
- Once a customer is selected, show their active membership (plan and end date) automatically. Staff must not have to look it up or pick it by hand.

5. MEMBERSHIPS
- Plans: Monthly, Quarterly, Yearly. The owner sets each plan's price and duration in Settings.
- "Sell Membership" option: choose the customer, choose the plan, take payment (split payments allowed, see section 8). Store start and end dates automatically.
- Show membership status on the customer record, and warn when a membership is close to expiry or expired.
- Membership sales count as revenue in reports.

6. FOOD & BEVERAGES
- A menu the owner manages: item name, category (Coffee, Beverages, Snacks, etc.), price, and available yes/no.
- Staff can add F&B items with quantities to a table session or a bill.
- F&B items appear on the same bill as table time, so the customer pays once.
- F&B sales are stored with dates for reports.

7. ONE-SCREEN CHECKOUT
- Table time, F&B items, discount, payment and "Billed by" all appear on a single checkout screen, with the running total always visible.

8. SPLIT PAYMENTS
- A bill can be paid with more than one payment method, for example ₹500 paid as ₹200 UPI + ₹300 cash.
- As each payment is entered, show the remaining balance.
- The bill can only be closed when payments equal the final total.
- Store each payment separately (method + amount) so reports can show Cash and UPI totals separately.

9. DISCOUNTS
- Every bill has an optional discount (default: no discount).
- Discount type: flat amount in ₹, or percentage.
- Rules: a percentage cannot exceed 100, and the total cannot go below ₹0.
- Show Subtotal, Discount and Final Total on the bill.
- Optional occasion label (for example Christmas, New Year) so reports can show discount totals per occasion.
- Split payments are calculated on the discounted total.

10. BILLED BY (STAFF)
- The owner manages a staff list in Settings (for example Azeem, Ken).
- Every bill must record who billed it, chosen from a "Billed by" dropdown at checkout, defaulting to the last staff member who used that counter.
- Show "Billed by" on the bill and in bill history. Reports can filter and total by staff member.

11. MONTHLY HISTORY & EXPENSES
- Add expense entry (date, category, amount, note) if it does not already exist.
- Never overwrite old records. Every bill, payment, membership sale, F&B sale and expense is stored with its date.
- Reports screen with a month/year selector showing: revenue (table time, F&B, memberships), expenses, profit, discounts given, cash vs UPI totals, and totals by staff member.
- Comparison with the previous month and simple trend charts.
- Export reports to CSV and PDF.

12. BACKUPS
- Automatic scheduled backup (daily) of the full database to a configurable folder, keeping the last N backups.
- Manual "Backup now" button and a "Restore from backup" option with a confirmation step.
- Show the date and time of the last successful backup in Settings.

QUALITY
- Keep the code organised (separate UI, business logic and database layers).
- Add or update tests for the billing logic (discounts, split payments, totals) and run them after each phase.
- Write a short README covering how to build the installers, how to configure the database, and how staff install and open the app on Windows.

PHASES (stop after each one)
Phase 1: Build setup (Maven/Gradle) + shared database migration.
Phase 2: Simple customer flow + one-screen checkout with discounts, split payments and Billed by.
Phase 3: Memberships + food & beverages.
Phase 4: Expenses, monthly history, reports and backups.
Phase 5: UI polish (FlatLaf, dashboard), installers and GitHub Actions.

## Project notes (kept up to date by Claude)

- Layers: `ui` (Swing screens) -> `service` (business rules) -> `dao` (all SQL, PreparedStatement only) -> `database.DBConnection` -> MySQL 8.4.
- Money is always `BigDecimal`. Billing rules live in `service.BillingCalculator`.
- Never delete money records: bills and payments get a status (CANCELLED / REFUNDED) and a reason.
- Build: Maven via the wrapper (`./mvnw package`, Windows `mvnw.cmd package`). Source stays in `src/`, tests in `test/`,
  resources in `resources/` (configured in pom.xml). Target Java 17 (`maven.compiler.release`).
- Database settings: `database.DatabaseConfig` (file `~/.cue147/database.properties`, edited by the
  "Database settings" window on the login screen). Nothing about the connection is hard-coded except the defaults.
- Schema changes: add a NEW file `resources/db/V00N__name.sql` and append it to `SchemaMigrator.MIGRATIONS`.
  Never edit an applied file. The app migrates on start (MySQL lock, table `schema_version`).
  Existing databases from the college version are recorded as version 1 automatically.
- Tests: `./test.sh` runs `./mvnw package` (JUnit tests in `test/service`, `test/database`) and then the
  main-method test programs against the separate database `cue147_test` (`-Dcue147.db=cue147_test`).
  They never touch the real `cue147` database. GUI tests must never press Save in Database settings
  (that would overwrite the developer's real settings file).
- Phase status: Phase 1 done (Maven, shared-database settings, migrations). Phase 2 done (customer
  search-or-add picker with membership, one-screen checkout, discounts, split payments, Billed by). Phase 3 done
  (membership plans + Sell Membership with split payment; food & drinks menu, orders during a game, F&B on the bill).
  Phase 4 done (expense history + soft delete, monthly reports with comparison, charts, CSV/PDF export,
  Billed-by filter, Java backups: daily automatic, Backup now, Restore). Next: Phase 5.
- Reports: SQL in `dao.ReportDAO`, numbers in `model.MonthlyReport`. Money counts on the day it was RECEIVED
  (payments PAID, bills + memberships). Revenue = table time + food - bill discounts + memberships (tested).
- Old records are never overwritten: expenses are edited with their old values copied to `expense_changes`;
  "delete" sets `expenses.status = 'DELETED'`. Bills/payments/memberships use statuses, never DELETE.
- Backups: `database.DatabaseBackup` (pure JDBC dump/restore, works on Windows and Mac without mysqldump),
  `service.BackupService` (folder, keep N, daily automatic from MainFrame's timer, `backup_log` table).
  Restore drops every table, loads the file, then runs the migrator; a BEFORE_RESTORE backup is made first.
  Test programs must call `AppSettings.setAutoBackup(false)` before opening windows.
- PDF export uses Apache PDFBox (standard fonts cannot draw the rupee sign: PDFs say "Rs.").
- Checkout money rules live in `service.Checkout` (pure, JUnit-tested in `test/service/CheckoutTest.java`).
  UI: `ui.CheckoutDialog` (opened after End Game and from Billing > Checkout / Pay). Saving: `BillService.completeCheckout`
  -> `BillDAO.completeCheckout` (one transaction). Bill subtotal = `bills.table_amount` (+ F&B in Phase 3).
- Payments: `payments` keeps ONE row per bill (status PENDING/PAID/CANCELLED/REFUNDED, amount = final total,
  method CASH/UPI/SPLIT). Each part (method + amount) is in `payment_parts`. Revenue still sums `payments` (PAID).
- "Billed by" names: table `staff_members` (Settings > Staff List), separate from login `users`. Never delete a name,
  switch it off. The last name used is remembered per computer (`AppSettings.getLastBilledById`).
- Customer choosing: `ui.CustomerPicker` (one search box, Add with name + phone only, shows membership).
- Payments belong to a bill (`payments.bill_id`) OR a membership sale (`payments.membership_id`); both count as revenue.
  `ui.PaymentPanel` is the shared split-payment widget (checkout and Sell Membership).
- Memberships: plans in `membership_plans` (price 0 = cannot be sold yet). `MembershipService.sell` works out the dates
  (starts after the current membership if still active). `MembershipDAO.findLatestForCustomer` prefers ACTIVE ones.
- Food & drinks: `menu_items` (never deleted, switched off) and `order_items` (per game/session; name and price COPIED).
  Bill subtotal = `table_amount + items_amount`. The checkout replaces a game's order lines when the bill is saved,
  so it must always work on a freshly loaded bill (`BillDAO.findById` loads the items) - `CheckoutDialog.open` does this.
- Local development MySQL lives in `~/cue147-mysql` (socket `~/cue147-mysql/mysql.sock`, root has no password locally).
- The code started as a first-year student project: keep it readable and explainable, no clever tricks.
