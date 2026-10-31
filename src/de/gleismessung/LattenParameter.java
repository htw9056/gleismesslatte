package de.gleismessung;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Geräte- und Auswerteparameter der Gleismesslatte.
 *
 * @param h              Reflektorhöhe über Schienenoberkante, senkrecht zur Latte [m]
 * @param c              Lattenkonstante: Spurweite = Prismenabstand + c [m] (aus Kalibrierung)
 * @param bezugsbasis    Bezugsbasis für die Überhöhung [m] (Regelspur 1,500)
 * @param sollSpurweite  Sollspurweite [m]: Bezug für die Abweichung und Mitte des Paarbereichs
 * @param toleranz       Zulässige Abweichung (±) von der Sollspurweite für die Paarbildung [m]
 * @param achseBerechnen Gleisachspunkt je Lattenlage erzeugen
 * @param nachkommastellen Nachkommastellen der Meterwerte in der Ausgabe (3 oder 4)
 * @param ueberhoehungsProtokoll zusätzliches Protokoll zur Überhöhung schreiben
 * @param paarbildung    aufeinanderfolgende oder beliebige GML-Punkte paaren
 * @param achsCodes      zusätzliche Codes (ohne GML), deren Punkte ohne Korrektur zu
 *                       Achspaaren zusammengefasst werden (Großbuchstaben)
 * @param trennung       Trennzeichen der Eingabedatei (automatisch, Semikolon, Leerzeichen/Tab)
 * @param aufbau         Spaltenfolge der Eingabedatei, z. B. "P R H Z C"
 * @param kopfzeilen     Anzahl zu überspringender Kopfzeilen (0–5)
 * @param maxUeberhoehung Grenzwert der Überhöhung [m]; EBO § 6 Abs. 3: höchstens 180 mm
 *                       einschließlich der sich im Betrieb einstellenden Abweichungen
 * @param ausgabe        Format der Ergebnis-Punktliste: wie Eingabe oder CSV mit Statusspalte
 * @param hDateien       Messdateien, aus denen die Reflektorhöhe je Punkt gelesen wird (leer = nur Feldwert)
 * @param hBezug         Bezug der Höhen in der Punktliste: Prismenmitte oder um Reflektorhöhe reduziert
 * @param hSpalten       gewählte Spalten je Text-Messdatei, z. B. "C:\\a.txt=P1,H5,m|C:\\b.txt=P2,H7,mm"
 */
public record LattenParameter(double h, double c, double bezugsbasis, double sollSpurweite,
                              double toleranz, boolean achseBerechnen, int nachkommastellen,
                              boolean ueberhoehungsProtokoll, Paarbildung.Modus paarbildung,
                              List<String> achsCodes, EingabeFormat.Trennung trennung, String aufbau,
                              int kopfzeilen, double maxUeberhoehung, EingabeFormat.Ausgabe ausgabe,
                              List<String> hDateien, ReflektorhoeheLeser.Bezug hBezug, String hSpalten) {

    public LattenParameter {
        achsCodes = achsCodes == null ? List.of() : List.copyOf(achsCodes);
        trennung = trennung == null ? EingabeFormat.Trennung.AUTO : trennung;
        aufbau = EingabeFormat.normiere(aufbau);
        ausgabe = ausgabe == null ? EingabeFormat.Ausgabe.EINGABE : ausgabe;
        hDateien = hDateien == null ? List.of() : List.copyOf(hDateien);
        hBezug = hBezug == null ? ReflektorhoeheLeser.Bezug.PRISMA : hBezug;
        hSpalten = hSpalten == null ? "" : hSpalten.strip();
    }

    /** Spaltenwahl je Datei als Map (Schlüssel = absoluter Pfad). */
    public java.util.Map<String, String> hSpaltenMap() {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        for (String e : hSpalten.split("\\|")) {
            int i = e.lastIndexOf('=');
            if (i > 0) {
                m.put(e.substring(0, i).strip(), e.substring(i + 1).strip());
            }
        }
        return m;
    }

    public static String hSpaltenText(java.util.Map<String, String> m) {
        StringBuilder sb = new StringBuilder();
        m.forEach((k, v) -> sb.append(sb.length() > 0 ? "|" : "").append(k).append('=').append(v));
        return sb.toString();
    }

    public static final double STANDARD_H = 0.130;
    public static final double STANDARD_C = 0.000;
    public static final double STANDARD_BEZUGSBASIS = 1.500;
    public static final double STANDARD_SOLLSPURWEITE = 1.458;
    public static final double STANDARD_TOLERANZ = 0.100;
    public static final boolean STANDARD_ACHSE = true;
    public static final int STANDARD_NACHKOMMASTELLEN = 3;
    public static final boolean STANDARD_UEBERHOEHUNGSPROTOKOLL = true;
    public static final Paarbildung.Modus STANDARD_PAARBILDUNG = Paarbildung.Modus.FOLGE;
    /** EBO § 6 Abs. 3: Überhöhung höchstens 180 mm [m]. */
    public static final double STANDARD_MAX_UEBERHOEHUNG = 0.180;

    public static LattenParameter standard() {
        return new LattenParameter(STANDARD_H, STANDARD_C, STANDARD_BEZUGSBASIS,
                STANDARD_SOLLSPURWEITE, STANDARD_TOLERANZ, STANDARD_ACHSE, STANDARD_NACHKOMMASTELLEN,
                STANDARD_UEBERHOEHUNGSPROTOKOLL, STANDARD_PAARBILDUNG, List.of(),
                EingabeFormat.Trennung.AUTO, EingabeFormat.STANDARD_AUFBAU, 0, STANDARD_MAX_UEBERHOEHUNG,
                EingabeFormat.Ausgabe.EINGABE, List.of(), ReflektorhoeheLeser.Bezug.PRISMA, "");
    }

    /** Prüft, ob zwei GML-Punkte mit Prismenabstand s ein gültiges Paar bilden. */
    public boolean istPaar(double prismenabstand) {
        return Math.abs(prismenabstand + c - sollSpurweite) <= toleranz;
    }

    public double paarMin() {
        return sollSpurweite - toleranz;
    }

    public double paarMax() {
        return sollSpurweite + toleranz;
    }

    /** Prüft die Werte auf Plausibilität, wirft IllegalArgumentException mit Klartext. */
    public LattenParameter pruefe() {
        if (!(h >= 0 && h <= ReflektorhoeheLeser.MAX + 1e-9)) {
            throw new IllegalArgumentException("Reflektorhöhe muss zwischen 0 und "
                    + ReflektorhoeheLeser.fmt(ReflektorhoeheLeser.MAX) + " m liegen.");
        }
        if (!(sollSpurweite > 0.5 && sollSpurweite < 2.5)) {
            throw new IllegalArgumentException("Sollspurweite muss zwischen 0,5 und 2,5 m liegen.");
        }
        if (!(toleranz > 0 && toleranz < 1)) {
            throw new IllegalArgumentException("Toleranz muss zwischen 0 und 1 m liegen.");
        }
        if (!(Math.abs(c) < 0.5)) {
            throw new IllegalArgumentException("Lattenkonstante muss kleiner als ±0,5 m sein.");
        }
        for (String a : achsCodes) {
            if (GmlCodierung.istGleismesspunkt(a)) {
                throw new IllegalArgumentException("Achs-Code \"" + a + "\" enthält GML – GML-Punkte werden "
                        + "bereits als Gleismesslatte ausgewertet.");
            }
        }
        if (!(maxUeberhoehung > 0 && maxUeberhoehung <= 0.300)) {
            throw new IllegalArgumentException("Grenzwert Überhöhung muss zwischen 0 und 300 mm liegen.");
        }
        if (kopfzeilen < 0 || kopfzeilen > EingabeFormat.MAX_KOPFZEILEN) {
            throw new IllegalArgumentException("Kopfzeilen: 0 bis " + EingabeFormat.MAX_KOPFZEILEN
                    + " erwartet, nicht " + kopfzeilen + ".");
        }
        if (paarbildung == null) {
            throw new IllegalArgumentException("Paarbildung fehlt.");
        }
        if (nachkommastellen != 3 && nachkommastellen != 4) {
            throw new IllegalArgumentException("Nachkommastellen: 3 oder 4 erwartet, nicht " + nachkommastellen + ".");
        }
        if (!(bezugsbasis > 0.5 && bezugsbasis < 2.5)) {
            throw new IllegalArgumentException("Bezugsbasis muss zwischen 0,5 und 2,5 m liegen.");
        }
        return this;
    }

    /**
     * Zerlegt eine Code-Liste wie "SOL, SOR; SO" (Komma oder Semikolon getrennt)
     * in einzelne Codes in Großbuchstaben, ohne Leereinträge und Doppelte.
     */
    public static List<String> codesAus(String text) {
        List<String> liste = new ArrayList<>();
        if (text != null) {
            for (String t : text.split("[,;]")) {
                String c = t.strip().toUpperCase(Locale.ROOT);
                if (!c.isEmpty() && !liste.contains(c)) {
                    liste.add(c);
                }
            }
        }
        return liste;
    }

    /** Messdateien als Text, mit Semikolon getrennt. */
    public String hDateienText() {
        return String.join("; ", hDateien);
    }

    /** Zerlegt eine Dateiliste (Semikolon oder Zeilenumbruch getrennt). */
    public static List<String> dateienAus(String text) {
        List<String> liste = new ArrayList<>();
        if (text != null) {
            for (String t : text.split("[;\\n|]")) {
                String d = t.strip();
                if (d.length() > 1 && d.startsWith("\"") && d.endsWith("\"")) {
                    d = d.substring(1, d.length() - 1).strip();
                }
                if (!d.isEmpty() && !liste.contains(d)) {
                    liste.add(d);
                }
            }
        }
        return liste;
    }

    /** Code-Liste als Text, z. B. "SOL,SOR". */
    public String achsCodesText() {
        return String.join(",", achsCodes);
    }

    public boolean istAchsCode(String code) {
        return code != null && achsCodes.contains(code.strip().toUpperCase(Locale.ROOT));
    }

    /** true, wenn der Betrag der Überhöhung den Grenzwert überschreitet (Vergleich auf 0,1 mm). */
    public boolean ueberschritten(double ueberhoehung) {
        return Math.round(Math.abs(ueberhoehung) * 10000) > Math.round(maxUeberhoehung * 10000);
    }

    /** Formatiert einen Meterwert mit der eingestellten Anzahl Nachkommastellen (Dezimalpunkt). */
    public String m(double wert) {
        return String.format(Locale.ROOT, "%." + nachkommastellen + "f", wert);
    }

    /** Punkt für Protokoll/Konsole mit der eingestellten Anzahl Nachkommastellen. */
    public String punkt(Point3D p) {
        return p.id() + " [" + p.code() + "] (y=" + m(p.y()) + ", x=" + m(p.x()) + ", z=" + m(p.z()) + ")";
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT,
                "Reflektorhöhe h = %.4f m, c = %+.4f m, Sollspurweite %.3f m ± %.3f m (%.3f–%.3f m), "
              + "Achse berechnen: %s, Nachkommastellen: %d, Überhöhungsprotokoll: %s, Paarbildung: %s, "
              + "Achs-Codes: %s, Grenzwert Überhöhung %.0f mm%nEingabe: Aufbau %s, Trennung %s, Kopfzeilen %d; Ausgabe: %s%nReflektorhöhe: Höhen der Punktliste = %s; Messdateien: %s",
                h, c, sollSpurweite, toleranz, paarMin(), paarMax(), achseBerechnen ? "ja" : "nein",
                nachkommastellen, ueberhoehungsProtokoll ? "ja" : "nein", paarbildung,
                achsCodes.isEmpty() ? "keine" : achsCodesText(), maxUeberhoehung * 1000, aufbau, trennung,
                kopfzeilen, ausgabe, hBezug, hDateien.isEmpty() ? "keine (Feldwert für alle GML-Punkte)" : hDateien.size() + "");
    }
}
