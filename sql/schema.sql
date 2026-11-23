-- =====================================================================
--  CUE 147 - Snooker & Pool Club Management System
--  Run once:  ./setup-db.sh      (or run this file in any MySQL client)
-- =====================================================================

CREATE DATABASE IF NOT EXISTS cue147 CHARACTER SET utf8mb4;
USE cue147;

-- A normal (non-root) MySQL user that the Java program logs in with.
CREATE USER IF NOT EXISTS 'cue147_app'@'localhost' IDENTIFIED BY 'cue147_pass';
GRANT ALL PRIVILEGES ON cue147.* TO 'cue147_app'@'localhost';
-- (the test database used by the automatic tests)
CREATE DATABASE IF NOT EXISTS cue147_test CHARACTER SET utf8mb4;
GRANT ALL PRIVILEGES ON cue147_test.* TO 'cue147_app'@'localhost';

-- 1. USERS: people who log in (owner or staff). Passwords are stored as hashes.
CREATE TABLE IF NOT EXISTS users (
    user_id       INT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(30)  NOT NULL UNIQUE,
    full_name     VARCHAR(60)  NOT NULL,
    password_hash VARCHAR(200) NOT NULL,
    role          ENUM('OWNER','STAFF') NOT NULL,
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. CUSTOMERS: gender decides which price (Men / Ladies) is used.
CREATE TABLE IF NOT EXISTS customers (
    customer_id   INT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(60) NOT NULL,
    phone         VARCHAR(10) NOT NULL UNIQUE,
    gender        ENUM('MEN','LADIES') NOT NULL,
    registered_on DATE NOT NULL,
    total_visits  INT NOT NULL DEFAULT 0,
    total_spent   DECIMAL(10,2) NOT NULL DEFAULT 0,
    is_walk_in    BOOLEAN NOT NULL DEFAULT FALSE   -- the two built-in "walk-in guest" rows
);

-- 3. GAME_TABLES: pool tables, snooker table, PS5 stations.
CREATE TABLE IF NOT EXISTS game_tables (
    table_id   INT AUTO_INCREMENT PRIMARY KEY,
    table_name VARCHAR(40) NOT NULL UNIQUE,
    table_type ENUM('POOL','SNOOKER','PS5') NOT NULL,
    is_active  BOOLEAN NOT NULL DEFAULT TRUE,      -- "deleted" tables with history are only switched off
    is_under_maintenance BOOLEAN NOT NULL DEFAULT FALSE   -- no games can be started while TRUE
);

-- Hourly price for every (type, gender) pair. The owner can change these.
CREATE TABLE IF NOT EXISTS prices (
    price_id    INT AUTO_INCREMENT PRIMARY KEY,
    table_type  ENUM('POOL','SNOOKER','PS5') NOT NULL,
    gender      ENUM('MEN','LADIES') NOT NULL,
    hourly_rate DECIMAL(8,2) NOT NULL CHECK (hourly_rate > 0),
    UNIQUE KEY one_price_per_type_and_gender (table_type, gender)
);

-- 8. MEMBERSHIPS: a member gets discount_percent off every bill until expiry_date.
CREATE TABLE IF NOT EXISTS memberships (
    membership_id    INT AUTO_INCREMENT PRIMARY KEY,
    customer_id      INT NOT NULL,
    start_date       DATE NOT NULL,
    expiry_date      DATE NOT NULL,
    discount_percent DECIMAL(5,2) NOT NULL DEFAULT 0,
    status           ENUM('ACTIVE','CANCELLED') NOT NULL DEFAULT 'ACTIVE',
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id)
);

-- 4. SESSIONS: one row for every game played.
CREATE TABLE IF NOT EXISTS sessions (
    session_id  INT AUTO_INCREMENT PRIMARY KEY,
    table_id    INT NOT NULL,
    customer_id INT NOT NULL,
    started_by  INT NOT NULL,
    start_time  DATETIME NOT NULL,
    end_time    DATETIME NULL,
    hourly_rate DECIMAL(8,2) NOT NULL,             -- price copied at start, so later price changes do not affect it
    status      ENUM('ACTIVE','COMPLETED','CANCELLED') NOT NULL DEFAULT 'ACTIVE',
    ended_by      INT NULL,                        -- staff member who ended the game
    cancelled_by  INT NULL,                        -- staff member who cancelled a mistaken game
    cancel_reason VARCHAR(200) NULL,
    -- Trick: this column holds the table id ONLY while the session is ACTIVE.
    -- The UNIQUE key below then stops two active sessions on the same table.
    active_table_id INT GENERATED ALWAYS AS (IF(status = 'ACTIVE', table_id, NULL)) STORED,
    UNIQUE KEY one_active_session_per_table (active_table_id),
    FOREIGN KEY (table_id)    REFERENCES game_tables(table_id),
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
    FOREIGN KEY (started_by)  REFERENCES users(user_id),
    FOREIGN KEY (ended_by)     REFERENCES users(user_id),
    FOREIGN KEY (cancelled_by) REFERENCES users(user_id)
);

-- 5. BILLS: created when a session ends. Never deleted.
CREATE TABLE IF NOT EXISTS bills (
    bill_id          INT AUTO_INCREMENT PRIMARY KEY,
    bill_number      VARCHAR(20) NOT NULL UNIQUE,
    session_id       INT NOT NULL,
    billed_minutes   INT NOT NULL,
    hourly_rate      DECIMAL(8,2) NOT NULL,
    subtotal         DECIMAL(10,2) NOT NULL,
    discount_percent DECIMAL(5,2) NOT NULL DEFAULT 0,
    final_amount     DECIMAL(10,2) NOT NULL,
    created_at       DATETIME NOT NULL,
    status           ENUM('ACTIVE','CANCELLED') NOT NULL DEFAULT 'ACTIVE',   -- wrong bills are cancelled, never deleted
    cancel_reason    VARCHAR(200) NULL,
    cancelled_by     INT NULL,
    cancelled_at     DATETIME NULL,
    -- Same trick as sessions: only ONE live (ACTIVE) bill is allowed for a session.
    live_session_id  INT GENERATED ALWAYS AS (IF(status = 'ACTIVE', session_id, NULL)) STORED,
    UNIQUE KEY one_live_bill_per_session (live_session_id),
    FOREIGN KEY (session_id)   REFERENCES sessions(session_id),
    FOREIGN KEY (cancelled_by) REFERENCES users(user_id)
);

-- 6. PAYMENTS: one row per bill. PENDING until the customer pays.
CREATE TABLE IF NOT EXISTS payments (
    payment_id INT AUTO_INCREMENT PRIMARY KEY,
    bill_id    INT NOT NULL UNIQUE,
    amount     DECIMAL(10,2) NOT NULL,
    method     ENUM('CASH','UPI') NULL,
    status     ENUM('PENDING','PAID','CANCELLED','REFUNDED') NOT NULL DEFAULT 'PENDING',
    paid_at    DATETIME NULL,
    received_by INT NULL,                          -- staff member who took the money
    FOREIGN KEY (bill_id) REFERENCES bills(bill_id),
    FOREIGN KEY (received_by) REFERENCES users(user_id)
);

-- 7. EXPENSES: rent, electricity, salaries ...
CREATE TABLE IF NOT EXISTS expenses (
    expense_id     INT AUTO_INCREMENT PRIMARY KEY,
    category       ENUM('RENT','ELECTRICITY','SALARIES','MAINTENANCE','EQUIPMENT','MISCELLANEOUS') NOT NULL,
    amount         DECIMAL(10,2) NOT NULL CHECK (amount > 0),
    expense_date   DATE NOT NULL,
    description    VARCHAR(200),
    payment_method ENUM('CASH','UPI') NOT NULL,
    recorded_by    INT NOT NULL,
    FOREIGN KEY (recorded_by) REFERENCES users(user_id)
);

-- 9. STAFF_SHIFTS: when a staff member started and ended work.
CREATE TABLE IF NOT EXISTS staff_shifts (
    shift_id    INT AUTO_INCREMENT PRIMARY KEY,
    user_id     INT NOT NULL,
    shift_start DATETIME NOT NULL,
    shift_end   DATETIME NULL,
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

-- Extra table: advance bookings (used by the Bookings screen).
CREATE TABLE IF NOT EXISTS bookings (
    booking_id     INT AUTO_INCREMENT PRIMARY KEY,
    customer_id    INT NOT NULL,
    table_id       INT NOT NULL,
    booking_date   DATE NOT NULL,
    booking_time   TIME NOT NULL,
    duration_hours DECIMAL(3,1) NOT NULL,
    source         ENUM('PHONE','WALK_IN','ONLINE') NOT NULL,
    status         ENUM('CONFIRMED','CANCELLED') NOT NULL DEFAULT 'CONFIRMED',
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
    FOREIGN KEY (table_id)    REFERENCES game_tables(table_id)
);

-- ---------------- starting data (only inserted when the tables are empty) ----------------
INSERT INTO prices (table_type, gender, hourly_rate)
SELECT * FROM (
    SELECT 'POOL' AS t, 'MEN' AS g, 270 AS r UNION ALL SELECT 'POOL', 'LADIES', 220 UNION ALL
    SELECT 'SNOOKER', 'MEN', 320 UNION ALL SELECT 'SNOOKER', 'LADIES', 270 UNION ALL
    SELECT 'PS5', 'MEN', 120 UNION ALL SELECT 'PS5', 'LADIES', 120
) AS start_prices WHERE NOT EXISTS (SELECT 1 FROM prices);

INSERT INTO game_tables (table_name, table_type)
SELECT * FROM (
    SELECT 'Pool Table 1' AS n, 'POOL' AS t UNION ALL SELECT 'Pool Table 2', 'POOL' UNION ALL
    SELECT 'Pool Table 3', 'POOL' UNION ALL SELECT 'Snooker Table 1', 'SNOOKER' UNION ALL
    SELECT 'PS5 Station 1', 'PS5'
) AS start_tables WHERE NOT EXISTS (SELECT 1 FROM game_tables);

INSERT INTO customers (name, phone, gender, registered_on, is_walk_in)
SELECT * FROM (
    SELECT 'Walk-in Guest (Men)' AS n, '0000000001' AS p, 'MEN' AS g, CURDATE() AS d, TRUE AS w
    UNION ALL SELECT 'Walk-in Guest (Ladies)', '0000000002', 'LADIES', CURDATE(), TRUE
) AS start_customers WHERE NOT EXISTS (SELECT 1 FROM customers);
-- The default owner and staff accounts are created by the Java program on first start
-- (Java creates the password hash, so no password is stored in this file).
