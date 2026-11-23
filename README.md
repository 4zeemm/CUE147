# CUE 147 - Snooker & Pool Club Management System
Java Swing + MySQL desktop app for the club counter. Runs on Windows and macOS (Java 17 or newer).

All computers at the club use **one shared MySQL database** on the club server, so everyone sees the same live data.

## Build (developers)
The project uses Maven through the Maven Wrapper, so Maven does not need to be installed.

```
./mvnw package          # Mac/Linux: compiles, runs the JUnit tests, makes target/cue147-<version>.jar
mvnw.cmd package        # Windows
java -jar target/cue147-2.0.0-SNAPSHOT.jar
```
The MySQL driver is copied to `target/lib/` next to the jar. Windows and Mac installers come in Phase 5.

Developer scripts (macOS, local MySQL in `~/cue147-mysql`): `./setup-db.sh` (once), `./run.sh`, `./test.sh`
(all automatic tests), `./upgrade-db.sh`, `./backup-db.sh`, `./start-mysql.sh`, `./stop-mysql.sh`.

## Daily use (counter staff)
- **Start a game:** Tables > Start Game > type the customer's name or phone. If they are new, press **Add new customer**
  (name and phone only). The customer's membership is shown at once.
- **End a game:** the checkout opens. Choose **Billed by**, add a discount if needed (Rs. or %, optional occasion),
  then press **Pay Cash** or **Pay UPI**. For a split bill, type the first amount, **Add Payment**, then pay the rest.
  The remaining amount is always shown; the bill closes only when it is fully paid. **Pay Later** keeps it unpaid
  (Billing > Checkout / Pay finishes it later).
- **Food & drinks:** while a game runs, press **Food & Drinks** on the table card and add items. They appear on the same
  bill. More items can also be added (or removed) at the checkout.
- **Memberships:** Membership > **Sell Membership** > search the customer > choose the plan > take the payment (split allowed).
  The dates are set automatically; buying again before the end extends the membership.
- The owner manages the **Billed by** names (Settings > Staff List), the **membership plan prices** (Settings > Membership Plans;
  a plan cannot be sold until its price is set) and the **menu** (Settings > Food & Drinks Menu).

## Database
- **Settings** are not in the code. Each computer saves them in `.cue147/database.properties` in the user's home folder
  (Windows: `C:\Users\NAME\.cue147\`). Change them with **Database settings** on the login screen
  (server address, port, database, user, password; **Test Connection** checks them).
- **Tables are created and updated by the app itself.** Every table change is a numbered file in `resources/db`
  (`V001__base_schema.sql`, ...). When a newer CUE 147 starts, it applies the missing files and keeps all data.
  Never edit an old file; add a new one and list it in `database/SchemaMigrator.java`.

## Setting up the club server (once)
The server is the computer that stays on during opening hours (for example the counter PC). On it:
1. Install **MySQL Server 8.4** (Windows: MySQL Installer from dev.mysql.com, "Server only"; choose *Run as Windows Service*
   and *Start at system startup*). Give the root user a strong password.
2. Open `sql/server-setup.sql`, change `CHANGE_THIS_PASSWORD` to a new strong password, and run it as root
   (MySQL Workbench: File > Open SQL Script, then Execute).
3. Allow other club computers to reach MySQL, **on the private club network only**. Windows PowerShell as administrator:
   `New-NetFirewallRule -DisplayName "MySQL for CUE 147" -Direction Inbound -Protocol TCP -LocalPort 3306 -Profile Private -Action Allow`
4. Give the server a fixed address in the Wi-Fi router (DHCP reservation). `ipconfig` shows the current address.
5. Turn off sleep while the club is open.
6. Start CUE 147 on the server. In **Database settings** enter `127.0.0.1`, user `cue147_app` and the password from step 2.
   The app creates all tables, the starting prices and tables, and the first logins (owner / owner123, staff / staff123
   - change these at once on the Staff screen).

Every other computer (staff PCs, the owner's Mac): start CUE 147, open **Database settings**, enter the server's address
(for example `192.168.1.20`), user `cue147_app` and the password, press **Test Connection**, then **Save**.

Never open port 3306 to the internet. For access from outside the club use a remote-desktop tool or a private VPN.

## Moving existing data to the club server
1. On the old computer: `./backup-db.sh` (makes `backups/cue147-DATE.sql`). Copy that file to the server.
2. On the server, after steps 1-2 above and **before** step 6: import the file into the `cue147` database
   (MySQL Workbench: Server > Data Import > Import from Self-Contained File, target schema `cue147`;
   or `mysql -u root -p cue147 < cue147-DATE.sql`).
3. Start CUE 147 on the server: it recognises the existing data and only adds what is missing.

## Project layout
- `src/` packages: `app` `model` `dao` (all SQL) `service` (business rules) `database` (connection, settings, migrations) `exception` `util` `ui`
- `resources/db` database migrations, `sql/` one-time database/user scripts, `test/` JUnit tests (Maven) and the test programs run by `./test.sh`
- `CLAUDE.md` the full requirements and the phase plan. `docs/` report, presentations, screenshots, `test-results/`
