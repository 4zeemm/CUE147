-- =====================================================================
--  CUE 147 database version 2: one-screen checkout.
--  * staff_members: the "Billed by" list the owner manages in Settings
--  * bills: an optional discount (flat Rs. or %) with an occasion label, and who billed it
--  * payment_parts: each part of a (split) payment, e.g. Rs. 200 UPI + Rs. 300 cash
--  Existing data is kept: old bills get table_amount = their final amount,
--  old paid bills get one payment part with their method and amount.
-- =====================================================================

CREATE TABLE IF NOT EXISTS staff_members (
    staff_id   INT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(40) NOT NULL UNIQUE,
    is_active  BOOLEAN NOT NULL DEFAULT TRUE,        -- people who left are switched off, never deleted
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- start the list with the names of the current login accounts
INSERT IGNORE INTO staff_members (name) SELECT DISTINCT full_name FROM users WHERE is_active = TRUE;

ALTER TABLE bills
    ADD COLUMN table_amount         DECIMAL(10,2) NOT NULL DEFAULT 0,    -- table time after member discount (rounded)
    ADD COLUMN bill_discount_type   ENUM('NONE','FLAT','PERCENT') NOT NULL DEFAULT 'NONE',
    ADD COLUMN bill_discount_value  DECIMAL(10,2) NOT NULL DEFAULT 0,    -- Rs. for FLAT, % for PERCENT
    ADD COLUMN bill_discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0,    -- Rs. taken off the subtotal
    ADD COLUMN discount_occasion    VARCHAR(40) NULL,                    -- e.g. Christmas, New Year
    ADD COLUMN billed_by_staff_id   INT NULL,
    ADD FOREIGN KEY (billed_by_staff_id) REFERENCES staff_members(staff_id);
UPDATE bills SET table_amount = final_amount WHERE bill_id > 0;

ALTER TABLE payments MODIFY method ENUM('CASH','UPI','SPLIT') NULL;      -- SPLIT = more than one part

CREATE TABLE IF NOT EXISTS payment_parts (
    part_id    INT AUTO_INCREMENT PRIMARY KEY,
    payment_id INT NOT NULL,
    method     ENUM('CASH','UPI') NOT NULL,
    amount     DECIMAL(10,2) NOT NULL CHECK (amount > 0),
    paid_at    DATETIME NOT NULL,
    FOREIGN KEY (payment_id) REFERENCES payments(payment_id)
);
INSERT INTO payment_parts (payment_id, method, amount, paid_at)
    SELECT payment_id, method, amount, paid_at FROM payments
    WHERE method IN ('CASH','UPI') AND paid_at IS NOT NULL AND amount > 0;
