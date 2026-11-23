# CUE 147 - Viva Guide (updated for the official marking scheme)

## 0. Marks and where to show them
| Marks | What the professor checks | Where in CUE 147 |
|---|---|---|
| R1 GUI (5) | Swing screens | Login + 10 screens, menu bar, toolbar, popup menu, tabs, timers (section 8) |
| R1 Database (5) | Tables, keys | `sql/schema.sql`, `docs/diagrams/er-diagram.png` (section 6) |
| R1 JDBC (5) | Java <-> MySQL | `database/DBConnection`, `dao/*` (section 4) |
| R2 Packages + interfaces (5) | Organisation | 8 packages, 3 interfaces (sections 2, 3) |
| R2 Libraries + exceptions (5) | Java API, try-catch | Section 5 |
| R3 Application (15) | Everything works | Demo script (section 9) |
| Report (10), Viva (10) | Document, questions | `docs/CUE147_Project_Report.docx`, this file |

## 1. How to run
```
./setup-db.sh        # creates the database + tables (once)
./run.sh             # starts MySQL if it is not running, makes today's backup, compiles and starts the app
./upgrade-db.sh      # adds the newest columns to an existing database (safe to repeat)
./test.sh            # runs all automatic tests (297 checks, including a GUI test that clicks the real buttons)
./backup-db.sh       # backup now (run.sh already does it once a day; files in backups/)
```
First logins: **owner / owner123** and **staff / staff123** (change them before real use).

## 2. Packages - why each exists
A **package** is a folder of related classes. It keeps a big project organised, avoids name clashes and lets us say "the SQL is only in `dao`".

| Package | Job | Classes |
|---|---|---|
| `app` | Starts the program | Main |
| `model` | Plain data classes | Customer, GameTable, GameSession, Bill, Expense, Membership, Booking, Shift, Price, Person, User, Owner, Staff, **Receiptable** (interface) |
| `database` | Opens the MySQL connection | DBConnection |
| `dao` | **All SQL.** One class per table | CustomerDAO, SessionDAO, BillDAO ... (10) |
| `service` | Business rules | AuthService, SessionService, BillService, BillingCalculator, ReportService, **DiscountPolicy** (interface), NoDiscount, MembershipDiscount |
| `exception` | Our own exceptions | ClubException, InvalidInputException, BusinessException |
| `util` | Helper classes using Java libraries | Validator, AppLogger, CsvExporter, AppSettings |
| `ui` | Swing screens | LoginFrame, MainFrame, 10 panels, **Refreshable** (interface) ... |

Flow of one click (End Game): `TablesPanel` -> `SessionService.endSession()` -> `BillingCalculator` + `SessionDAO.endSession()` -> `DBConnection` -> **MySQL**. See `docs/diagrams/architecture.png`.

## 3. Interfaces - what, why, where
An **interface** is a contract: a list of methods (no bodies) that any class can promise to implement with `implements`. It lets different classes be used in the same way.

| Interface | Methods | Implemented by | Why we need it |
|---|---|---|---|
| `Refreshable` (ui) | `refresh()`, `tick()` | `ScreenPanel` (so all 10 screens) | `MainFrame` can refresh/tick ANY screen without knowing which one it is. |
| `DiscountPolicy` (service) | `getDiscountPercent()`, `getName()` | `NoDiscount`, `MembershipDiscount` | `BillingCalculator` takes any discount rule; a new rule (e.g. festival offer) is just a new class. |
| `Receiptable` (model) | `toReceipt()` | `Bill`, `Expense` | One window (`ReceiptDialog.showText`) shows and prints a bill OR an expense voucher. |

Example code: `public class Bill implements Receiptable { @Override public String toReceipt() {...} }`.
Difference from abstract class: an interface has no fields/constructor and a class can implement many interfaces but extend only one class.
See `docs/diagrams/inheritance-interfaces.png`.

## 4. JDBC (Java Database Connectivity)
```java
try (Connection con = DBConnection.getConnection();                                        // 1. connect
     PreparedStatement ps = con.prepareStatement("SELECT * FROM customers WHERE name LIKE ?")) { // 2. SQL with ? holes
    ps.setString(1, "%" + text + "%");                                                    // 3. fill the hole safely
    ResultSet rs = ps.executeQuery();                                                     // 4. run it
    while (rs.next()) list.add(new Customer(rs.getInt("customer_id"), ...));              // 5. row -> object
}                                                                                         // 6. auto-closes
```
- URL: `jdbc:mysql://127.0.0.1:3306/cue147`, driver jar in `lib/`.
- **CRUD** = Create (`INSERT`), Read (`SELECT`), Update (`UPDATE`), Delete (`DELETE`). Example: `CustomerDAO.add / search / update`; `GameTableDAO.remove` (DELETE or switch off).
- **PreparedStatement** sends values separately from the SQL, so typed text can never change the SQL (no SQL injection).
- **Transaction** (`SessionDAO.endSession`): 4 statements (finish session, insert bill, insert payment, +1 visit) run with `setAutoCommit(false)`, then `commit()`; on error `rollback()` undoes all.
- **Demo:** add a customer in the app, then in a MySQL window run `SELECT * FROM customers;` - the new row is there.

## 5. Java libraries and exception handling
| Library | Used for | Class |
|---|---|---|
| `javax.swing`, `java.awt` | GUI | ui |
| `java.sql` | JDBC | database, dao |
| `java.time` | Dates, times, durations | Session, Bill, Booking |
| `java.math.BigDecimal` | Exact money maths | BillingCalculator |
| `java.util` (ArrayList, Map) | Collections | everywhere |
| `java.util.regex` | Validate phone/name/username | Validator |
| `java.util.logging` | Error log file `cue147.log` | AppLogger |
| `java.io`, `java.nio.file` | Export CSV files | CsvExporter |
| `javax.crypto`, `java.security` | Password hashing (PBKDF2) | AuthService |
| `java.util.prefs` | Save club settings | SettingsPanel |

**Exception** = an object that describes an error. `try { risky } catch (Type e) { handle }` stops a crash and shows a message.
- `SQLException` (Java) - database problem -> message + log (`ScreenPanel.showDbError`).
- `InvalidInputException` (ours) - wrong typed data, thrown by `Validator` (empty name, bad phone, bad date, amount 0).
- `BusinessException` (ours) - club rule broken (table busy, wrong password, bill already paid).
- Both of ours extend `ClubException`, so one `catch (ClubException e)` handles both.
- `IOException` - file could not be saved (CSV export). `PrinterException` - printing failed.
- Start-up: if MySQL is not running, `LoginFrame` shows how to start it. Unexpected errors: `Main` installs a default handler that logs and shows a message.
- `try-with-resources` closes connections and files automatically. `throws` passes an error to the caller; `throw` creates one.

## 6. Database design (11 tables)
users, customers, game_tables, prices, memberships, sessions, bills, payments, expenses, staff_shifts, bookings. Diagram: `docs/diagrams/er-diagram.png`.
- **Primary key**: unique id of a row. **Foreign key**: column pointing to another table's primary key, so you cannot create a session for a customer who does not exist.
- Relationships: customer 1-N sessions, table 1-N sessions, session 1-1 bill, bill 1-1 payment, customer 1-N memberships/bookings, user 1-N expenses/shifts.
- A UNIQUE key on a generated column (`active_table_id`) makes MySQL itself refuse a second ACTIVE session on one table.
- Bills, payments and sessions are never deleted; a table with history is only switched off.
- Passwords are stored as `salt:PBKDF2 hash`, never as text.

## 7. OOP examples
- **Encapsulation** - `private` fields + getters/setters (`Customer`, `Bill`).
- **Inheritance** - `Owner`, `Staff` extend `User`; `Customer` extends `Person`; screens extend `ScreenPanel`; `InvalidInputException` extends `ClubException`.
- **Abstraction** - abstract class `User`; interfaces; UI never writes SQL.
- **Polymorphism** - `user.canViewReports()` is true for `Owner`, false for `Staff`; `DiscountPolicy` variable holds `NoDiscount` or `MembershipDiscount`; `Receiptable` holds `Bill` or `Expense`.
- **Overloading** - `SessionService.startSession(...)` (2 versions), `BillingCalculator.calculateFinalAmount(...)` (percent or policy). **Overriding** - `toReceipt()`, `getRole()`, `refresh()`.

## 8. Billing rules (`BillingCalculator`)
1. Under 1 hour = 1 full hour. 2. First 5 minutes after each full hour are free. 3. After that, each minute costs rate/60. 4. Final bill rounded DOWN to a multiple of Rs. 5 (after member discount). Example: rate 300, 1 h 6 min -> 300 + 1 x 5 = **305**.
Swing Timer = fires every second (clock, running game timers). SwingWorker = runs the dashboard SQL on a background thread so the window never freezes.
Roles: Owner everything; Staff cannot manage tables/prices, expenses, reports, settings, user accounts.

## 9. Demo script (about 6 minutes)
1. **Login** as staff with a wrong password (error), then correct. 2. **Tables**: Start Game for a customer; see the live timer; right-click menu. 3. **End Game** -> bill window -> Pay UPI -> Print. 4. **Customers**: add one with a bad phone (message), then a good one; History. 5. **Bookings**: book a table. 6. Log out, log in as **owner**: **Dashboard** (live numbers), **Expenses** (add, View Voucher), **Reports** (Export CSV), **Settings > Pricing** (change a price), **Staff** (shifts). 7. Show `cue147.log` and `SELECT * FROM bills;` in MySQL.

## 10. Short viva Q&A
- **Package?** A folder of related classes. **Why?** Organisation, reuse, avoid name clashes. **Interface?** A contract of methods a class promises to implement. **Why `Refreshable`?** So the main window treats every screen the same way.
- **Class / object / encapsulation / inheritance / polymorphism / abstraction?** See section 7 with our examples.
- **JDBC? MySQL? PreparedStatement? CRUD? Transaction?** Section 4. **Primary key / foreign key?** Section 6.
- **Exception? Checked vs unchecked?** Checked (`SQLException`, `IOException`, our `ClubException`) must be caught or declared; unchecked (`NullPointerException`) need not. **Why our own exceptions?** Clear messages for the user and to tell input errors from rule errors.
- **`throw` vs `throws`?** `throw` creates an error, `throws` declares that a method may pass one on. **finally / try-with-resources?** Code that always runs / auto-closes resources.
- **How does login work?** `AuthService.login` finds the user, hashes the typed password with the stored salt, compares, returns an `Owner` or `Staff` object.
- **How is the bill calculated?** Section 8. **Why BigDecimal?** Exact decimal maths for money.
- **Why a DAO class?** All SQL of a table in one place; UI stays simple.
- **What is `@Override`?** Tells the compiler we replace a parent/interface method (it catches spelling mistakes).
- **What is a regular expression?** A rule describing allowed text, e.g. `\d{10}` = exactly 10 digits (`Validator.requirePhone`).


## 11. Fixing mistakes at the counter (new)
Real clubs make mistakes, so every mistake has a simple fix. **Money records are never deleted**: a wrong bill gets the status CANCELLED together with the reason and who did it.

| Mistake | Fix | Who | Java classes | What happens in MySQL |
|---|---|---|---|---|
| Game started on the wrong table / by accident | Right-click table > **Cancel Game** (reason needed) | Staff for the first 10 minutes, owner any time | `SessionService.cancelSession`, `SessionDAO.cancelSession` | session status `CANCELLED`, `cancelled_by`, `cancel_reason`. No bill. Table free again. |
| Forgot to press Start | Right-click > **Adjust Start Time** (not in the future, max 12 hours back) | Staff, owner | `SessionService.adjustStartTime` | `UPDATE sessions SET start_time`. Change is written to `cue147.log`. |
| Pressed End Game by accident | Billing > **Reopen Game** (only while the bill is unpaid; staff within 30 minutes, owner any time) | Staff, owner | `SessionService.reopenSession`, `SessionDAO.reopenSession` (transaction) | old bill `CANCELLED`, its payment `CANCELLED`, session `ACTIVE` again, visit count -1. Ending again makes a NEW bill. |
| Wrong bill | Billing > **Cancel Bill** (reason needed). A PAID bill is refunded | Owner only | `BillService.cancelBill`, `BillDAO.cancelBill` (transaction) | bill `CANCELLED`, payment `CANCELLED` (unpaid) or `REFUNDED` (paid), customer spending reduced. Revenue only counts `PAID`, so it drops automatically. |
| Cash entered instead of UPI | Billing > **Change Method** | Owner only | `BillService.changePaymentMethod` | `UPDATE payments SET method` |
| Wrong expense | Expenses > **Edit** or **Delete** | Owner only | `ExpenseDAO.update / delete` | `UPDATE` / `DELETE ... WHERE expense_id = ?` (by id, never by row position) |
| Table broken | Tables > **Maintenance** (owner) | Owner | `GameTableDAO.setMaintenance` | `is_under_maintenance = TRUE`; games cannot start; dashboard does not count it as available |

**Who did it?** `sessions.started_by`, `sessions.ended_by`, `payments.received_by`, `bills.cancelled_by`, `expenses.recorded_by`. The bill window shows "Served by" and "Received".
**Duplicate bills are impossible:** `bills.live_session_id` is a generated column that holds the session id only while the bill is ACTIVE, and it has a UNIQUE key (same trick as one active session per table). A cancelled bill gives the session free again.
**Why a business rule like "10 minutes"?** `SessionService.MISTAKE_MINUTES` - a constant. Staff can fix honest mistakes quickly; anything bigger needs the owner. It is a simple example of a business rule in the service layer.
**Auto refresh:** every 15 seconds the Tables and Dashboard screens look at MySQL again, so two counters see the same tables (`ScreenPanel.autoRefresh()`, overridden by two screens = polymorphism).
**Restart safety:** a running game is a row in `sessions` with status ACTIVE, so it is still there after closing the program (`RestartTest` proves it with two separate programs).

Viva Q&A for this part:
- **Why not just delete a wrong bill?** Money records must stay so we can explain what happened; a status plus reason and user keeps the history.
- **What is a transaction used for here?** Reopening or cancelling changes several tables; all changes succeed together or `rollback()` undoes them.
- **How does revenue drop after a refund?** Revenue is `SUM(amount)` of payments with status `PAID`; a refund changes the status to `REFUNDED`.
- **How do you stop staff from cancelling bills?** `User.canCorrectFinancialRecords()` is true for `Owner` and false for `Staff` (polymorphism); the button is also hidden from staff.
- **What does `COALESCE` do in `BillDAO`?** It takes the first value that is not NULL: a reopened game has no end time, so the bill shows its own creation time.


## 11. Added in the final round (closing the last gaps)
| Feature | Where | Say this in the viva |
|---|---|---|
| A booking keeps the table | `SessionService.startSession`, `BookingDAO.findBookingAt` | Before a game starts we ask MySQL if a CONFIRMED booking covers this moment. If it is for another customer we throw a `BusinessException`. |
| No overlapping bookings | `BookingDAO.isTableBooked` | Two time ranges overlap when *new start < old end* AND *new end > old start*. `TIME_TO_SEC` turns a time into seconds so MySQL can compare. |
| Change My Password | `AuthService.changeOwnPassword`, Staff screen | The current password is checked first, then the new one is validated, typed twice and saved as a new salted hash. |
| Delete a customer | `CustomerDAO.deleteIfNoHistory`, Customers screen (owner) | Only a customer with no games, bookings or membership is deleted, so no bill ever loses its customer. |
| Appearance settings | `AppSettings`, `Theme.loadAppearance`, Settings > Appearance | Saved with `java.util.prefs`. At start-up `Theme` reads the colour and text size. Notifications on/off works at once. |
| Club name on bills | `Bill.toReceipt`, Settings > Club Settings | The receipt header reads the saved club name and phone. |
| MySQL starts by itself | `ensure-mysql.sh` (called by `run.sh`) | It pings MySQL; if there is no answer it starts it and waits up to 30 seconds. |
| Daily backup | `backup-db.sh` | `mysqldump` writes `backups/cue147-DATE.sql`; only the newest 7 are kept. |
| Printing tested | `GuiClickTest.printToFile` | The bill text area's `Printable` (the same one the Print button uses) is printed to a PostScript file. |

Still honest limits: tested on macOS only; a paper printer has not been tried (use Print > Save as PDF to check).
