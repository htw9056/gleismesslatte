@echo off
rem ============================================================================
rem  Gleismesslatte (c) Dipl.-Ing. (FH) Stefan Klein  -  MIT-Lizenz
rem  Eigenstaendige Windows-Version MIT eingebauter Java-Laufzeit (jpackage)
rem  Ergebnis: dist\Gleismesslatte\Gleismesslatte.exe         (Fenster)
rem            dist\Gleismesslatte\GleismesslatteKonsole.exe  (Kommandozeile)
rem  Den ganzen Ordner dist\Gleismesslatte weitergeben - keine Installation noetig.
rem  Voraussetzung auf DIESEM PC: JDK 17 oder neuer (enthaelt jpackage)
rem
rem  Funktioniert im GitHub-Projekt (Ordner windows\, baut vorher mit bauen.bat)
rem  und im fertigen Windows-Paket (Gleismesslatte.jar liegt daneben).
rem ============================================================================
chcp 65001 >nul
setlocal
set "HIER=%~dp0"
if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"
where jpackage >nul 2>&1 || (echo jpackage nicht gefunden - bitte JDK 17+ installieren und JAVA_HOME setzen. & pause & exit /b 1)

if exist "%HIER%Gleismesslatte.jar" (
  rem --- fertiges Windows-Paket
  set "BASIS=%HIER%"
  set "JAR=%HIER%Gleismesslatte.jar"
  set "ICON=%HIER%gleismesslatte.ico"
  set "BEISPIELE=%HIER%"
) else (
  rem --- GitHub-Projekt: zuerst aus dem Quellcode bauen
  call "%HIER%..\bauen.bat" || (pause & exit /b 1)
  set "BASIS=%HIER%..\"
  set "JAR=%HIER%..\dist\Gleismesslatte.jar"
  set "ICON=%HIER%..\doc\gleismesslatte.ico"
  set "BEISPIELE=%HIER%..\beispiele\"
)
cd /d "%BASIS%"

rem Version aus der jar lesen (Implementation-Version)
set "VERSION=0.0"
for /f "tokens=2" %%V in ('jar xf "%JAR%" META-INF/MANIFEST.MF ^&^& findstr /b "Implementation-Version" META-INF\MANIFEST.MF') do set "VERSION=%%V"
if exist META-INF rmdir /s /q META-INF

if exist build_jpackage rmdir /s /q build_jpackage
if exist dist\Gleismesslatte rmdir /s /q dist\Gleismesslatte
mkdir build_jpackage
copy /y "%JAR%" build_jpackage\Gleismesslatte.jar >nul

echo Baue Gleismesslatte V%VERSION% mit eingebauter Java-Laufzeit ...
jpackage --type app-image ^
  --name Gleismesslatte ^
  --app-version %VERSION% ^
  --copyright "(c) Dipl.-Ing. (FH) Stefan Klein" ^
  --description "Gleismesslatte - Auswertung Spurweite, Ueberhoehung, Gleisachse" ^
  --vendor "Dipl.-Ing. (FH) Stefan Klein" ^
  --input build_jpackage ^
  --main-jar Gleismesslatte.jar ^
  --main-class de.gleismessung.Main ^
  --add-modules java.base,java.desktop,java.xml ^
  --jlink-options "--strip-debug --no-man-pages --no-header-files" ^
  --java-options "-Dfile.encoding=UTF-8" ^
  --icon "%ICON%" ^
  --add-launcher GleismesslatteKonsole="%HIER%konsole.properties" ^
  --dest dist
if errorlevel 1 (
  echo jpackage ist fehlgeschlagen.
  rmdir /s /q build_jpackage
  pause
  exit /b 1
)
rmdir /s /q build_jpackage

copy /y gleismesslatte.properties dist\Gleismesslatte\ >nul 2>&1
for %%B in ("%BEISPIELE%beispiel_*.*") do copy /y "%%B" dist\Gleismesslatte\ >nul
if exist "%HIER%LIESMICH.txt" copy /y "%HIER%LIESMICH.txt" dist\Gleismesslatte\ >nul

echo.
echo Fertig: %CD%\dist\Gleismesslatte\Gleismesslatte.exe
echo Den ganzen Ordner dist\Gleismesslatte weitergeben ^(nicht nur die .exe^).
pause
