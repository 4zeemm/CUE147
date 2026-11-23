#!/bin/bash
# Creates the cue147 and cue147_test databases on THIS computer and all their tables.
cd "$(dirname "$0")"
./ensure-mysql.sh || exit 1
MYSQL=~/cue147-mysql/mysql-8.4.3-macos14-arm64/bin/mysql
SOCK=$HOME/cue147-mysql/mysql.sock
$MYSQL --no-defaults -uroot --socket=$SOCK < sql/local-setup.sql || exit 1
./upgrade-db.sh
