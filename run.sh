#!/bin/bash
# Starts CUE 147 on this computer (developer use). MySQL is started automatically if needed,
# and a backup is made once a day.
cd "$(dirname "$0")"
./ensure-mysql.sh || exit 1
[ -f "backups/cue147-$(date +%Y-%m-%d).sql" ] || ./backup-db.sh
./mvnw -q -B -DskipTests package 2>&1 | grep -v "^WARNING"
java -jar target/cue147-*.jar
