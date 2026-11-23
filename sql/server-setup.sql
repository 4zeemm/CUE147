-- =====================================================================
--  CUE 147 - one-time setup of the CLUB SERVER (the computer that keeps the shared database).
--  Run it once as the MySQL root user, for example in MySQL Workbench:
--  File > Open SQL Script > this file, change the password below, then Execute (lightning icon).
--
--  After this, start CUE 147 on the server: it creates all tables by itself.
--  Every other computer then uses: Database settings > Server address = this computer's IP address.
-- =====================================================================
CREATE DATABASE IF NOT EXISTS cue147 CHARACTER SET utf8mb4;

-- The account every CUE 147 copy logs in with.
-- '%' = it may connect from any computer; the Windows firewall must allow port 3306 ONLY on the club network.
-- (Stricter: replace '%' with your network, for example '192.168.1.%', in BOTH lines.)
CREATE USER IF NOT EXISTS 'cue147_app'@'%' IDENTIFIED BY 'CHANGE_THIS_PASSWORD';
GRANT ALL PRIVILEGES ON cue147.* TO 'cue147_app'@'%';
