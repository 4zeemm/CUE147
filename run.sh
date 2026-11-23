#!/bin/bash
# Starts CUE 147. MySQL is started automatically if needed, and a backup is made once a day.
cd "$(dirname "$0")"
./ensure-mysql.sh || exit 1
[ -f "backups/cue147-$(date +%Y-%m-%d).sql" ] || ./backup-db.sh
CP="out:lib/mysql-connector-j-8.4.0.jar"
mkdir -p out
javac -cp "$CP" -d out $(find src -name "*.java") && java -cp "$CP" app.Main
