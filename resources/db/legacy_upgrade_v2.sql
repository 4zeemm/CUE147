-- =====================================================================
--  Upgrade 2: corrections for mistakes (run once on an existing database)
--  ./upgrade-db.sh runs this file only when it is needed.
-- =====================================================================

-- Tables can be put under maintenance (cannot start a game on them).
ALTER TABLE game_tables ADD COLUMN is_under_maintenance BOOLEAN NOT NULL DEFAULT FALSE;

-- Sessions: a mistaken game can be CANCELLED; we remember who ended it / cancelled it and why.
ALTER TABLE sessions
    MODIFY status ENUM('ACTIVE','COMPLETED','CANCELLED') NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN ended_by      INT NULL,
    ADD COLUMN cancelled_by  INT NULL,
    ADD COLUMN cancel_reason VARCHAR(200) NULL,
    ADD FOREIGN KEY (ended_by)     REFERENCES users(user_id),
    ADD FOREIGN KEY (cancelled_by) REFERENCES users(user_id);

-- Bills: a wrong bill is CANCELLED (never deleted). Only ONE live bill per session is allowed.
ALTER TABLE bills ADD INDEX idx_bills_session (session_id);
ALTER TABLE bills DROP INDEX session_id;
ALTER TABLE bills
    ADD COLUMN status ENUM('ACTIVE','CANCELLED') NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN cancel_reason VARCHAR(200) NULL,
    ADD COLUMN cancelled_by  INT NULL,
    ADD COLUMN cancelled_at  DATETIME NULL,
    ADD COLUMN live_session_id INT GENERATED ALWAYS AS (IF(status = 'ACTIVE', session_id, NULL)) STORED,
    ADD UNIQUE KEY one_live_bill_per_session (live_session_id),
    ADD FOREIGN KEY (cancelled_by) REFERENCES users(user_id);

-- Payments: CANCELLED (bill cancelled before payment) or REFUNDED (bill cancelled after payment);
-- received_by remembers which staff member took the money.
ALTER TABLE payments
    MODIFY status ENUM('PENDING','PAID','CANCELLED','REFUNDED') NOT NULL DEFAULT 'PENDING',
    ADD COLUMN received_by INT NULL,
    ADD FOREIGN KEY (received_by) REFERENCES users(user_id);
