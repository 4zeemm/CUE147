#!/bin/bash
# Adds the "correction" columns to an EXISTING database (keeps all data). Safe to run many times.
MYSQL=~/cue147-mysql/mysql-8.4.3-macos14-arm64/bin/mysql
SOCK=$HOME/cue147-mysql/mysql.sock
cd "$(dirname "$0")"
for DB in cue147 cue147_test; do
  DONE=$($MYSQL --no-defaults -uroot --socket=$SOCK -N -e "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='$DB' AND table_name='game_tables' AND column_name='is_under_maintenance'")
  if [ "$DONE" = "0" ]; then
    (echo "USE $DB;"; cat sql/upgrade_v2.sql) | $MYSQL --no-defaults -uroot --socket=$SOCK && echo "$DB upgraded"
  else
    echo "$DB already up to date"
  fi
done
