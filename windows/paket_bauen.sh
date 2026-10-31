#!/bin/sh
# Baut das Windows-Paket dist/Gleismesslatte_V<version>_Windows.zip:
# Gleismesslatte.exe, GleismesslatteKonsole.exe (Go-Starter mit Icon), Gleismesslatte.jar,
# Beispiele, Parameterdatei, LIESMICH, exe_bauen.bat (Version mit eingebauter Java-Laufzeit).
# Voraussetzung: bauen.sh wurde ausgeführt, Go 1.21+ und Python 3 installiert.
set -e
cd "$(dirname "$0")/.."
VERSION=$(sed -n 's/.*NUMMER = "\(.*\)".*/\1/p' src/de/gleismessung/Version.java)
HERSTELLER=$(sed -n 's/.*HERSTELLER = "\(.*\)".*/\1/p' src/de/gleismessung/Version.java)
ZIEL="dist/Gleismesslatte_V${VERSION}_Windows"
rm -rf "$ZIEL" "$ZIEL.zip"
mkdir -p "$ZIEL"

(cd launcher
 python3 syso_erzeugen.py ../doc/gleismesslatte.ico rsrc_windows_amd64.syso "$VERSION" "$HERSTELLER"
 GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go build -trimpath -ldflags "-s -w -H windowsgui -X main.modus=gui" \
     -o "../$ZIEL/Gleismesslatte.exe" .
 GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go build -trimpath -ldflags "-s -w" \
     -o "../$ZIEL/GleismesslatteKonsole.exe" .)

cp dist/Gleismesslatte.jar doc/gleismesslatte.ico windows/exe_bauen.bat windows/konsole.properties LICENSE "$ZIEL/"
# Textdateien mit Windows-Zeilenenden
for f in windows/LIESMICH.txt gleismesslatte.properties beispiele/*; do
  sed 's/\r$//; s/$/\r/' "$f" > "$ZIEL/$(basename "$f")"
done
(cd dist && zip -qr "Gleismesslatte_V${VERSION}_Windows.zip" "Gleismesslatte_V${VERSION}_Windows")
echo "Fertig: $ZIEL.zip"
