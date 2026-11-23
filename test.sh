#!/bin/bash
# Builds the project with Maven and runs all automatic tests (MySQL is started if needed)
cd "$(dirname "$0")"
./ensure-mysql.sh || exit 1
echo "=== JUnit tests and build (Maven) ==="
./mvnw -q -B package 2>&1 | grep -v "^WARNING"
[ "${PIPESTATUS[0]}" = "0" ] || { echo "FAIL  Maven build or JUnit tests"; exit 1; }
echo "PASS  Maven build and JUnit tests (details: target/surefire-reports)"
CP="target/classes:target/test-classes:target/lib/*"
# GUI tests need a switched-on display: on macOS keep it awake while they run (no system setting is changed)
AWAKE=""; command -v caffeinate >/dev/null && AWAKE="caffeinate -d"
java -Dcue147.db=cue147_test -cp "$CP" database.SchemaMigrator || exit 1
java -cp "$CP" BillingTest || exit 1
java -Dcue147.db=cue147_test -cp "$CP" BackendTest || exit 1
echo "=== Restart test (two separate programs) ==="
java -Dcue147.db=cue147_test -cp "$CP" RestartTest create || exit 1
java -Dcue147.db=cue147_test -cp "$CP" RestartTest check || exit 1
echo "=== Screen smoke test (opens every screen as owner and staff) ==="
$AWAKE java -Dcue147.db=cue147_test -cp "$CP" UiSmokeTest
echo "=== GUI click test (clicks the real buttons of the real screens) ==="
$AWAKE java -Dcue147.db=cue147_test -cp "$CP" GuiClickTest
echo "=== Appearance test (a new program reads the saved look) ==="
$AWAKE java -Dcue147.db=cue147_test -cp "$CP" AppearanceTest
echo "=== Deployment test (auto-start and backup) ==="
./stop-mysql.sh >/dev/null 2>&1; sleep 3
if ./ensure-mysql.sh >/dev/null && ~/cue147-mysql/mysql-8.4.3-macos14-arm64/bin/mysqladmin --no-defaults -uroot --socket=$HOME/cue147-mysql/mysql.sock ping >/dev/null 2>&1; then
  echo "PASS  MySQL was stopped, and ensure-mysql.sh started it again by itself"
else
  echo "FAIL  ensure-mysql.sh could not start MySQL"
fi
if ./backup-db.sh >/dev/null && grep -q "CREATE TABLE \`bills\`" "backups/cue147-$(date +%Y-%m-%d).sql"; then
  echo "PASS  backup-db.sh saved today's backup with all tables (backups/cue147-$(date +%Y-%m-%d).sql)"
else
  echo "FAIL  backup-db.sh did not make a correct backup"
fi
