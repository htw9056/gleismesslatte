#!/bin/sh
# Gleismesslatte – übersetzen, Selbsttest ausführen, dist/Gleismesslatte.jar erzeugen
# Voraussetzung: JDK 17 oder neuer
set -e
cd "$(dirname "$0")"
rm -rf build dist
mkdir -p build/klassen build/test dist
javac --release 17 -encoding UTF-8 -d build/klassen src/de/gleismessung/*.java
cp -r src/de/gleismessung/bild build/klassen/de/gleismessung/
javac --release 17 -encoding UTF-8 -cp build/klassen -d build/test test/de/gleismessung/*.java
java -Djava.awt.headless=true -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp build/klassen:build/test de.gleismessung.Selbsttest
VERSION=$(sed -n 's/.*NUMMER = "\(.*\)".*/\1/p' src/de/gleismessung/Version.java)
printf 'Main-Class: de.gleismessung.Main\nImplementation-Title: Gleismesslatte\nImplementation-Version: %s\nImplementation-Vendor: Dipl.-Ing. (FH) Stefan Klein\n' "$VERSION" > build/MANIFEST.MF
jar cfm dist/Gleismesslatte.jar build/MANIFEST.MF -C build/klassen .
echo "Fertig: dist/Gleismesslatte.jar (V$VERSION)"
