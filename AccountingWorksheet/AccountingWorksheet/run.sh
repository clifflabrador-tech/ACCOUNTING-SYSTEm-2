#!/usr/bin/env bash
# LedgerBuddy launcher.  ./run.sh = MySQL (config/db.properties)   ./run.sh --demo = no database needed
cd "$(dirname "$0")"
if command -v javac >/dev/null 2>&1; then
  mkdir -p out
  javac -encoding UTF-8 -cp "lib/*" -d out Main.java bridges/*.java || exit 1
  exec java -cp "out:lib/*" Main "$@"
else
  exec java -jar AccountingWorksheet.jar "$@"
fi
