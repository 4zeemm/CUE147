-- =====================================================================
--  CUE 147 - databases for THIS computer (development and the automatic tests).
--  Run by ./setup-db.sh as the MySQL root user. The tables are created by the
--  app itself (database.SchemaMigrator, files in resources/db).
--  For the club server use sql/server-setup.sql instead.
-- =====================================================================
CREATE DATABASE IF NOT EXISTS cue147 CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS cue147_test CHARACTER SET utf8mb4;   -- used only by ./test.sh

CREATE USER IF NOT EXISTS 'cue147_app'@'localhost' IDENTIFIED BY 'cue147_pass';
GRANT ALL PRIVILEGES ON cue147.* TO 'cue147_app'@'localhost';
GRANT ALL PRIVILEGES ON cue147_test.* TO 'cue147_app'@'localhost';
