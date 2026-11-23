-- =====================================================================
--  CUE 147 database version 3: membership plans and food & beverages.
--  * membership_plans: Monthly / Quarterly / Yearly; the owner sets price, months and discount.
--    The price starts at 0: a plan cannot be sold until the owner has set its price.
--  * memberships: which plan was sold, for how much, when, and who billed it
--  * payments: a payment now belongs to a bill OR to a membership sale (membership revenue)
--  * menu_items: the food & drinks menu; order_items: what a table ordered during a game
--  * bills.items_amount: total of the food & drinks on the bill
--  Existing data is kept: old memberships stay as they are (no plan recorded).
-- =====================================================================

CREATE TABLE IF NOT EXISTS membership_plans (
    plan_id          INT AUTO_INCREMENT PRIMARY KEY,
    name             VARCHAR(30) NOT NULL UNIQUE,
    duration_months  INT NOT NULL CHECK (duration_months BETWEEN 1 AND 36),
    price            DECIMAL(10,2) NOT NULL DEFAULT 0 CHECK (price >= 0),
    discount_percent DECIMAL(5,2) NOT NULL DEFAULT 0 CHECK (discount_percent BETWEEN 0 AND 100),
    sort_order       INT NOT NULL
);
-- months and discounts are the ones the old Membership screen used
INSERT IGNORE INTO membership_plans (name, duration_months, price, discount_percent, sort_order) VALUES
    ('Monthly', 1, 0, 5, 1), ('Quarterly', 3, 0, 10, 2), ('Yearly', 12, 0, 15, 3);

ALTER TABLE memberships
    ADD COLUMN plan_id            INT NULL,
    ADD COLUMN price_paid         DECIMAL(10,2) NULL,
    ADD COLUMN sold_at            DATETIME NULL,
    ADD COLUMN billed_by_staff_id INT NULL,
    ADD FOREIGN KEY (plan_id) REFERENCES membership_plans(plan_id),
    ADD FOREIGN KEY (billed_by_staff_id) REFERENCES staff_members(staff_id);

ALTER TABLE payments
    MODIFY bill_id INT NULL,                                   -- NULL for a membership sale
    ADD COLUMN membership_id INT NULL UNIQUE,
    ADD FOREIGN KEY (membership_id) REFERENCES memberships(membership_id);

CREATE TABLE IF NOT EXISTS menu_items (
    item_id      INT AUTO_INCREMENT PRIMARY KEY,
    name         VARCHAR(50) NOT NULL UNIQUE,
    category     VARCHAR(30) NOT NULL,
    price        DECIMAL(8,2) NOT NULL CHECK (price > 0),
    is_available BOOLEAN NOT NULL DEFAULT TRUE              -- items are switched off, never deleted
);

CREATE TABLE IF NOT EXISTS order_items (
    order_item_id INT AUTO_INCREMENT PRIMARY KEY,
    session_id    INT NOT NULL,                              -- the game (table) that ordered it
    item_id       INT NOT NULL,
    item_name     VARCHAR(50) NOT NULL,                      -- copied: a later rename or price change
    category      VARCHAR(30) NOT NULL,                      -- does not change old bills
    unit_price    DECIMAL(8,2) NOT NULL,
    quantity      INT NOT NULL CHECK (quantity BETWEEN 1 AND 99),
    added_at      DATETIME NOT NULL,
    FOREIGN KEY (session_id) REFERENCES sessions(session_id),
    FOREIGN KEY (item_id) REFERENCES menu_items(item_id)
);

ALTER TABLE bills ADD COLUMN items_amount DECIMAL(10,2) NOT NULL DEFAULT 0;
