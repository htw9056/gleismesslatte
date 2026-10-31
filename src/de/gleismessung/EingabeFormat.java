package de.gleismessung;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Aufbau der Eingabedatei: Trennzeichen, Spaltenfolge und zu überspringende Kopfzeilen.
 *
 * <p>Spaltenkürzel im Aufbau (Leerzeichen, Komma oder Semikolon getrennt):</p>
 * <pre>
 *   P  Punktnummer   (auch PNR, NR, PUNKT)
 *   R  Rechtswert    (auch Y, RW, E, OST)
 *   H  Hochwert      (auch X, HW, N, NORD)
 *   Z  Höhe          (auch HOEHE, HÖHE)
 *   C  Code          (auch CODE, K)
 *   -  Spalte überspringen
 * </pre>
 * Standard: {@code P R H Z C}. P, R, H und Z sind Pflicht, C ist optional.
 * Steht C als letzte Spalte, gehören bei Leerzeichen-Trennung alle restlichen
 * Felder der Zeile zum Code (Codes mit Leerzeichen).
 */
final class EingabeFormat {

    enum Trennung {
        AUTO("auto", "automatisch"),
        SEMIKOLON("semikolon", "Semikolon ;"),
        LEERZEICHEN("leerzeichen", "Leerzeichen / Tab");

        final String schluessel;
        final String text;

        Trennung(String schluessel, String text) {
            this.schluessel = schluessel;
            this.text = text;
        }

        static Trennung von(String s) {
            if (s == null || s.isBlank()) {
                return AUTO;
            }
            return switch (s.strip().toLowerCase(Locale.ROOT)) {
                case "auto", "automatisch" -> AUTO;
                case "semikolon", ";", "csv" -> SEMIKOLON;
                case "leerzeichen", "leer", "space", "tab", "txt", "blank" -> LEERZEICHEN;
                default -> throw new IllegalArgumentException(
                        "trennzeichen: 'auto', 'semikolon' oder 'leerzeichen' erwartet, nicht '" + s + "'");
            };
        }

        @Override
        public String toString() {
            return text;
        }
    }

    /** Spaltenarten. */
    enum Spalte { P, R, H, Z, C, LEER }

    static final String STANDARD_AUFBAU = "P R H Z C";
    static final int MAX_KOPFZEILEN = 5;

    /** Vorschläge für das Auswahlfeld im Fenster. */
    static final String[] VORLAGEN = {"P R H Z C", "P C R H Z", "P H R Z C", "P R H Z", "P C H R Z"};

    private EingabeFormat() {
    }

    /** Zerlegt und prüft einen Aufbau; Ergebnis in normierter Schreibweise, z. B. "P R H Z C". */
    static String normiere(String aufbau) {
        List<Spalte> sp = spalten(aufbau);
        StringBuilder sb = new StringBuilder();
        for (Spalte s : sp) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(s == Spalte.LEER ? "-" : s.name());
        }
        return sb.toString();
    }

    static List<Spalte> spalten(String aufbau) {
        if (aufbau == null || aufbau.isBlank()) {
            aufbau = STANDARD_AUFBAU;
        }
        List<Spalte> liste = new ArrayList<>();
        for (String t : aufbau.strip().split("[\\s,;]+")) {
            if (t.isEmpty()) {
                continue;
            }
            liste.add(switch (t.toUpperCase(Locale.ROOT)) {
                case "P", "PNR", "NR", "PUNKT", "PUNKTNUMMER" -> Spalte.P;
                case "R", "Y", "RW", "E", "OST", "RECHTS", "RECHTSWERT" -> Spalte.R;
                case "H", "X", "HW", "N", "NORD", "HOCH", "HOCHWERT" -> Spalte.H;
                case "Z", "HOEHE", "HÖHE", "HOE" -> Spalte.Z;
                case "C", "CODE", "K", "KODE" -> Spalte.C;
                case "-", "_", "*", "LEER" -> Spalte.LEER;
                default -> throw new IllegalArgumentException("Aufbau: unbekannte Spalte '" + t
                        + "' (erlaubt: P R H Z C und - für überspringen)");
            });
        }
        for (Spalte pflicht : new Spalte[] {Spalte.P, Spalte.R, Spalte.H, Spalte.Z}) {
            long n = liste.stream().filter(s -> s == pflicht).count();
            if (n != 1) {
                throw new IllegalArgumentException("Aufbau '" + aufbau + "': Spalte " + pflicht
                        + (n == 0 ? " fehlt" : " ist mehrfach angegeben") + " (Pflicht: P R H Z, optional C).");
            }
        }
        if (liste.stream().filter(s -> s == Spalte.C).count() > 1) {
            throw new IllegalArgumentException("Aufbau '" + aufbau + "': Spalte C ist mehrfach angegeben.");
        }
        return liste;
    }

    /**
     * Ergebnis des Zerlegens einer Zeile: Punkt oder Fehlertext, dazu die Originalfelder,
     * das erkannte Trennzeichen und ob Dezimalkomma verwendet wurde (für die Ausgabe im
     * Eingabeformat).
     */
    record Zeile(Point3D punkt, String fehler, String[] felder, boolean semikolon, boolean komma) {
        Zeile(Point3D punkt, String fehler) {
            this(punkt, fehler, null, false, false);
        }
    }

    /** Ausgabeformat der Ergebnis-Punktliste. */
    enum Ausgabe {
        EINGABE("eingabe", "wie Eingabe"),
        STATUS("status", "CSV mit Statusspalte");

        final String schluessel;
        final String text;

        Ausgabe(String schluessel, String text) {
            this.schluessel = schluessel;
            this.text = text;
        }

        static Ausgabe von(String s) {
            if (s == null || s.isBlank()) {
                return EINGABE;
            }
            return switch (s.strip().toLowerCase(Locale.ROOT)) {
                case "eingabe", "wie eingabe", "original" -> EINGABE;
                case "status", "csv", "tabelle" -> STATUS;
                default -> throw new IllegalArgumentException(
                        "ausgabeformat: 'eingabe' oder 'status' erwartet, nicht '" + s + "'");
            };
        }

        @Override
        public String toString() {
            return text;
        }
    }

    /**
     * Zerlegt eine Datenzeile (bereits ohne Kopfzeilen, Kommentare und Leerzeilen).
     */
    static Zeile lies(String zeile, Trennung trennung, List<Spalte> aufbau) {
        boolean semikolon = trennung == Trennung.SEMIKOLON
                || (trennung == Trennung.AUTO && zeile.indexOf(';') >= 0);
        String[] t = semikolon ? zeile.split(";", -1) : zeile.strip().split("\\s+");
        for (int i = 0; i < t.length; i++) {
            t[i] = t[i].strip();
        }
        int letzte = aufbau.size() - 1;
        boolean codeAmEnde = aufbau.get(letzte) == Spalte.C;
        int benoetigt = codeAmEnde ? aufbau.size() - 1 : aufbau.size();
        if (t.length < benoetigt) {
            return new Zeile(null, t.length + " Felder, erwartet " + aufbau.size()
                    + " (Aufbau " + normiere(aufbauText(aufbau)) + ", Trennung "
                    + (semikolon ? "Semikolon" : "Leerzeichen") + ")");
        }
        String p = null;
        String c = "";
        double r = 0;
        double h = 0;
        double z = 0;
        try {
            for (int i = 0; i < aufbau.size(); i++) {
                String f = i < t.length ? t[i] : "";
                switch (aufbau.get(i)) {
                    case P -> p = f;
                    case R -> r = Parameter.zahl(f);
                    case H -> h = Parameter.zahl(f);
                    case Z -> z = Parameter.zahl(f);
                    case C -> c = (i == letzte && !semikolon && t.length > aufbau.size())
                            ? String.join(" ", java.util.Arrays.copyOfRange(t, i, t.length)) : f;
                    case LEER -> { }
                }
            }
        } catch (NumberFormatException e) {
            return new Zeile(null, "Zahlenfehler (Aufbau " + normiere(aufbauText(aufbau)) + ")");
        }
        if (p == null || p.isEmpty()) {
            return new Zeile(null, "Punktnummer fehlt");
        }
        boolean komma = false;
        for (int i = 0; i < aufbau.size() && i < t.length; i++) {
            Spalte s = aufbau.get(i);
            if ((s == Spalte.R || s == Spalte.H || s == Spalte.Z) && t[i].indexOf(',') >= 0) {
                komma = true;
            }
        }
        return new Zeile(new Point3D(p, c, r, h, z), null, t, semikolon, komma);
    }

    private static String aufbauText(List<Spalte> aufbau) {
        StringBuilder sb = new StringBuilder();
        for (Spalte s : aufbau) {
            sb.append(s == Spalte.LEER ? "-" : s.name()).append(' ');
        }
        return sb.toString();
    }
}
