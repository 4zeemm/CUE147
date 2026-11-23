#!/bin/bash
# Starts the local MySQL server installed in ~/cue147-mysql (localhost only)
M=~/cue147-mysql/mysql-8.4.3-macos14-arm64
D=~/cue147-mysql/data
nohup $M/bin/mysqld --no-defaults --basedir=$M --datadir=$D --bind-address=127.0.0.1 --port=3306 \
  --socket=$HOME/cue147-mysql/mysql.sock --mysqlx=OFF > ~/cue147-mysql/mysql.log 2>&1 &
echo "MySQL starting... (log: ~/cue147-mysql/mysql.log)"
