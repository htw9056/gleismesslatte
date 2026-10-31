# Änderungen

Versionsformat **V0.xx**, bei jeder Änderung um 0,01 erhöht.

## V0.16
- Veröffentlichung als Open Source auf GitHub unter der MIT-Lizenz.
- Neue Projektstruktur:
  - Quellen in `src/` und Selbsttest in `test/`
  - Beispieldaten in `beispiele/`
  - Windows-Paket in `windows/`
  - Startkonfigurationen in `eclipse/`
- Selbsttest prüft Rechenkern, GML-Codierung, Eingabeformat, Spaltenanalyse und beide
  Beispiele gegen ihre Sollwerte.
- Neue Build-Skripte `bauen.sh` / `bauen.bat`, die ohne Eclipse funktionieren.
- `exe_bauen.bat` läuft jetzt sowohl im Repository als auch im Release-Paket.
- jpackage-Version enthält jetzt das Modul `java.xml` (nötig für JobXML/LandXML).
- GitHub-Actions-Workflow: Build und Selbsttest bei jedem Push, Release bei Tag `v*`.

## V0.15
- Text-Messdateien ohne feste Begriffe:
  - automatische Spaltenanalyse
  - Vorschlagsfenster mit Tabellenvorschau, Begründung und Sofortprüfung
  - Einheit m/mm
  - Spaltenwahl wird je Datei gespeichert

## V0.14
- Alle Eingaben werden automatisch gespeichert und beim Start geladen.
- Reflektorhöhe je Punkt aus Messdateien:
  - Formate: Leica GSI, Trimble JobXML, LandXML/XML, Text
  - Wertebereich 0–0,5 m
  - Knopf „Höhe suchen“
- Höhenbezug Prismenmitte/reduziert.
- Spalte Reflektorhoehe in der Spur-CSV.

## V0.13
- Programmfenster: 3D-Ansicht und Detail Prisma stehen untereinander.
  - Sie füllen die linke Fensterhälfte und wachsen beim Vergrößern mit (HiDPI-tauglich).
  - Die Beschriftung ist deutlich größer.
- Das Fenster öffnet passend zur Bildschirmgröße.

## V0.12
- Herstellervermerk „© Dipl.-Ing. (FH) Stefan Klein“ an folgenden Stellen:
  - Fenstertitel und Fußzeile
  - Konsole und Protokolle
  - Parameterdatei und Bild
  - Windows-Dateieigenschaften der .exe

## V0.11
- Die Ergebnis-Punktliste hat das Format der Eingabe und ist wieder einlesbar.
- Option „CSV mit Statusspalte“.
- Bild und Icon: Die Prismen liegen an den inneren Fahrkanten (fester Anschlag / Spannfeder).

## V0.10
- Grenzwert Überhöhung nach EBO § 6 Abs. 3 (Standard 180 mm, einstellbar):
  - Prüfung je Lage
  - Warnung
  - Kennzeichnung in Spur-CSV und Überhöhungsprotokoll

## V0.09
- Eingabe auch als Leerzeichen-/Tab-getrennter Text.
- Neu einstellbar:
  - Trennzeichen
  - Spaltenaufbau (Standard P R H Z C)
  - Kopfzeilen (0–5)
- ANSI-Dateien werden erkannt.
- Neues Beispiel mit 500 Punkten.

## V0.08
- Achs-Codes: Punkte ohne GML werden ohne Korrektur paarweise zur Gleisachse gerechnet.
- Neue Spalte „Art“ (GML/Code).

## V0.07
- Paarbildung `frei`:
  - GML-Punkte in beliebiger Reihenfolge
  - Paarbildung nach bester Übereinstimmung mit der Sollspurweite

## V0.06
- Zusätzliches Überhöhungsprotokoll mit Station, Querneigung, Überhöhung links/rechts und Rampe.

## V0.05
- Nachkommastellen der Ausgabe wählbar (3 oder 4, Standard 3).
- Klare Fehlermeldungen bei ungültigen Parametern.

## V0.04
- Programmfenster mit Bild und Eingabefeldern für alle Parameter.
- Gleisachse ja/nein (kleinste Nr. + „Achse“).
- GML-Kennung wird nach der Korrektur gelöscht.
- Programm-Icon.

## V0.03
- Windows-.exe, Dateiauswahl und Drag & Drop.
- Ergebnis automatisch als `<name>_ergebnis`.
- `exe_bauen.bat` erstellt eine Version mit eingebauter Java-Laufzeit.

## V0.02
- Paarbildung über einstellbare Sollspurweite ± Toleranz (1,458 m ± 0,10 m).
- Parameterdatei.

## V0.01
- Erste Version:
  - Korrektur bei Überhöhung
  - Spurweite, Gleismitte
  - GML-Präfix/-Suffix im Code
  - 3D-Illustration
