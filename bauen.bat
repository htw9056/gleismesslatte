@echo off
rem Gleismesslatte - uebersetzen, Selbsttest ausfuehren, dist\Gleismesslatte.jar erzeugen
rem Voraussetzung: JDK 17 oder neuer (javac, jar im PATH oder JAVA_HOME gesetzt)
setlocal
cd /d "%~dp0"
if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"
where javac >nul 2>&1 || (echo javac nicht gefunden - bitte JDK 17+ installieren & exit /b 1)

if exist build rmdir /s /q build
if exist dist rmdir /s /q dist
mkdir build\klassen build\test dist

javac --release 17 -encoding UTF-8 -d build\klassen src\de\gleismessung\*.java || exit /b 1
xcopy /e /i /q src\de\gleismessung\bild build\klassen\de\gleismessung\bild >nul
javac --release 17 -encoding UTF-8 -cp build\klassen -d build\test test\de\gleismessung\*.java || exit /b 1
java -Djava.awt.headless=true -Dfile.encoding=UTF-8 -cp build\klassen;build\test de.gleismessung.Selbsttest || exit /b 1

for /f "tokens=2 delims==" %%V in ('findstr /c:"NUMMER =" src\de\gleismessung\Version.java') do set "VZ=%%V"
set "VERSION=%VZ:~2,-2%"
(
echo Main-Class: de.gleismessung.Main
echo Implementation-Title: Gleismesslatte
echo Implementation-Version: %VERSION%
echo Implementation-Vendor: Dipl.-Ing. ^(FH^) Stefan Klein
) > build\MANIFEST.MF
jar cfm dist\Gleismesslatte.jar build\MANIFEST.MF -C build\klassen . || exit /b 1
echo Fertig: dist\Gleismesslatte.jar (V%VERSION%)
