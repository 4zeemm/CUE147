-- =====================================================================
--  CUE 147 database version 4: history that is never overwritten, and backups.
--  * expenses: "Delete" now only marks an expense DELETED (it stays in the database);
--    a new category for food & drink stock; recorded_at = when it was typed in
--  * expense_changes: the OLD values every time an expense is edited
--  * backup_log: every backup and restore (shown in Settings > Backups)
-- =====================================================================

ALTER TABLE expenses
    MODIFY category ENUM('RENT','ELECTRICITY','SALARIES','MAINTENANCE','EQUIPMENT','FOOD_STOCK','MISCELLANEOUS') NOT NULL,
    ADD COLUMN status      ENUM('ACTIVE','DELETED') NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN deleted_by  INT NULL,
    ADD COLUMN deleted_at  DATETIME NULL,
    ADD COLUMN recorded_at DATETIME NULL,
    ADD FOREIGN KEY (deleted_by) REFERENCES users(user_id);

CREATE TABLE IF NOT EXISTS expense_changes (
    change_id       INT AUTO_INCREMENT PRIMARY KEY,
    expense_id      INT NOT NULL,
    changed_at      DATETIME NOT NULL,
    changed_by      INT NOT NULL,
    old_category    VARCHAR(20) NOT NULL,
    old_amount      DECIMAL(10,2) NOT NULL,
    old_date        DATE NOT NULL,
    old_description VARCHAR(200),
    old_method      VARCHAR(10) NOT NULL,
    FOREIGN KEY (expense_id) REFERENCES expenses(expense_id),
    FOREIGN KEY (changed_by) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS backup_log (
    backup_id  INT AUTO_INCREMENT PRIMARY KEY,
    made_at    DATETIME NOT NULL,
    computer   VARCHAR(60) NOT NULL,
    file_name  VARCHAR(250) NOT NULL,
    size_bytes BIGINT NOT NULL,
    kind       ENUM('AUTOMATIC','MANUAL','BEFORE_RESTORE','RESTORED') NOT NULL
);
