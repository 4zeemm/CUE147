#!/bin/bash
# Makes sure MySQL is running. If it is not, starts it and waits until it answers (at most 30 seconds).
BIN=~/cue147-mysql/mysql-8.4.3-macos14-arm64/bin
SOCK=$HOME/cue147-mysql/mysql.sock
cd "$(dirname "$0")"
if $BIN/mysqladmin --no-defaults -uroot --socket=$SOCK ping >/dev/null 2>&1; then
  exit 0
fi
echo "MySQL is not running - starting it..."
./start-mysql.sh >/dev/null
for i in $(seq 1 30); do
  if $BIN/mysqladmin --no-defaults -uroot --socket=$SOCK ping >/dev/null 2>&1; then
    echo "MySQL is ready."
    exit 0
  fi
  sleep 1
done
echo "MySQL did not start. Look at ~/cue147-mysql/mysql.log"
exit 1
