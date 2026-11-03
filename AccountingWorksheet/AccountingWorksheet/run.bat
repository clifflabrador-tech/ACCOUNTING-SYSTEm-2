@echo off
rem LedgerBuddy launcher.  run.bat           = use MySQL (config\db.properties)
rem                        run.bat --demo    = try it without a database
cd /d "%~dp0"
where javac >nul 2>nul
if %errorlevel%==0 (
  if not exist out mkdir out
  javac -encoding UTF-8 -cp "lib/*" -d out Main.java bridges\*.java
  if errorlevel 1 ( pause & exit /b 1 )
  java -cp "out;lib/*" Main %*
) else (
  java -jar AccountingWorksheet.jar %*
)
