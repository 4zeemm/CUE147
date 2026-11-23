#!/bin/bash
# Saves a copy of the cue147 database in the "backups" folder: one file per day, the newest 7 are kept.
BIN=~/cue147-mysql/mysql-8.4.3-macos14-arm64/bin
SOCK=$HOME/cue147-mysql/mysql.sock
cd "$(dirname "$0")"
mkdir -p backups
FILE="backups/cue147-$(date +%Y-%m-%d).sql"
if $BIN/mysqldump --no-defaults -uroot --socket=$SOCK --single-transaction cue147 > "$FILE.tmp"; then
  mv "$FILE.tmp" "$FILE"
  echo "Backup saved: $FILE"
else
  rm -f "$FILE.tmp"
  echo "Backup FAILED"
  exit 1
fi
# keep only the newest 7 daily backups
ls -1t backups/cue147-*.sql | tail -n +8 | while read old; do rm -f "$old"; done
