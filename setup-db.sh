#!/bin/bash
# Creates the cue147 database and tables (MySQL must be running: ./start-mysql.sh)
MYSQL=~/cue147-mysql/mysql-8.4.3-macos14-arm64/bin/mysql
SOCK=$HOME/cue147-mysql/mysql.sock
cd "$(dirname "$0")"
$MYSQL --no-defaults -uroot --socket=$SOCK < sql/schema.sql && echo "cue147 database ready"
# same tables in the test database
sed 's/USE cue147;/USE cue147_test;/' sql/schema.sql | $MYSQL --no-defaults -uroot --socket=$SOCK && echo "cue147_test database ready"
./upgrade-db.sh
