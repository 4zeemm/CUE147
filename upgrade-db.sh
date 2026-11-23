#!/bin/bash
# Brings the tables of cue147 and cue147_test up to date (keeps all data). Safe to run many times.
# (The app does the same by itself when it starts.)
cd "$(dirname "$0")"
./mvnw -q -B -DskipTests package 2>&1 | grep -v "^WARNING" || true
CP="target/classes:target/lib/*"
java -cp "$CP" database.SchemaMigrator || exit 1
java -Dcue147.db=cue147_test -cp "$CP" database.SchemaMigrator || exit 1
