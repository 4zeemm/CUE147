#!/bin/bash
~/cue147-mysql/mysql-8.4.3-macos14-arm64/bin/mysqladmin --no-defaults -uroot --socket=$HOME/cue147-mysql/mysql.sock shutdown && echo "MySQL stopped"
