package de.gleismessung;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Schreibt die Ergebnis-Punktliste im Format der Eingabedatei: gleiche Spaltenfolge,
 * gleiches Trennzeichen, gleiches Dezimalzeichen, gleiche Kopfzeilen. Zusätzliche
 * Spalten der Eingabe (hinter dem Aufbau oder als "-" übersprungen) bleiben erhalten.
 * Die Datei kann damit mit denselben Einstellungen wieder eingelesen werden.
 */
final class PunktAusgabe {

    /** Eingelesene Datei: Punkte, Originalzeilen und erkanntes Format. */
    record Eingabe(List<Point3D> punkte, List<String[]> felder, List<String> kopfzeilen,
                   boolean semikolon, boolean komma) {
    }

    /** Ein Ausgabepunkt; quelle = Index in der Eingabe oder -1 für neue Punkte (Achse). */
    record Punkt(Point3D punkt, String status, int quelle) {
    }

    private static final String PLATZHALTER = "-";

    private PunktAusgabe() {
    }

    static void schreibe(Path ziel, List<Punkt> liste, Eingabe ein, LattenParameter lp,
                         Consumer<String> warnung) throws IOException {
        List<EingabeFormat.Spalte> aufbau = EingabeFormat.spalten(lp.aufbau());
        int letzte = aufbau.size() - 1;
        boolean codeAmEnde = aufbau.get(letzte) == EingabeFormat.Spalte.C;
        boolean leer = !ein.semikolon();
        boolean platzhalter = false;
        boolean unterstrich = false;

        List<String[]> zeilen = new ArrayList<>();
        for (Punkt a : liste) {
            Point3D p = a.punkt();
            String[] orig = a.quelle() >= 0 ? ein.felder().get(a.quelle()) : new String[0];
            // bei Leerzeichen + Code am Ende gehören alle restlichen Felder zum Code
            int breite = (leer && codeAmEnde) ? aufbau.size() : Math.max(aufbau.size(), orig.length);
            String[] f = new String[breite];
            for (int i = 0; i < breite; i++) {
                f[i] = i < orig.length ? orig[i] : "";
            }
            for (int i = 0; i < aufbau.size(); i++) {
                switch (aufbau.get(i)) {
                    case P -> f[i] = p.id();
                    case R -> f[i] = zahl(lp.m(p.y()), ein.komma());
                    case H -> f[i] = zahl(lp.m(p.x()), ein.komma());
                    case Z -> f[i] = zahl(lp.m(p.z()), ein.komma());
                    case C -> f[i] = p.code();
                    case LEER -> { }
                }
            }
            if (leer) {
                for (int i = 0; i < f.length; i++) {
                    boolean istLetzterCode = i == letzte && codeAmEnde;
                    if (f[i].isEmpty() && !istLetzterCode) {
                        f[i] = PLATZHALTER;          // leeres Feld würde die Spalten verschieben
                        platzhalter = true;
                    } else if (!istLetzterCode && f[i].matches(".*\\s.*")) {
                        f[i] = f[i].replaceAll("\\s+", "_");
                        unterstrich = true;
                    }
                }
            }
            zeilen.add(f);
        }

        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(ziel, StandardCharsets.UTF_8))) {
            for (String k : ein.kopfzeilen()) {
                w.println(k);
            }
            if (ein.semikolon()) {
                for (String[] f : zeilen) {
                    w.println(String.join(";", f));
                }
            } else {
                schreibeSpalten(w, zeilen, aufbau);
            }
        }
        if (platzhalter) {
            warnung.accept("Ausgabe Leerzeichen-Format: leere Felder (z. B. Code nach Entfernen von GML) "
                    + "als \"" + PLATZHALTER + "\" geschrieben, damit die Spalten erhalten bleiben.");
        }
        if (unterstrich) {
            warnung.accept("Ausgabe Leerzeichen-Format: Leerzeichen innerhalb eines Feldes durch _ ersetzt.");
        }
    }

    /** Leerzeichen-Format: Spalten bündig, Zahlen rechtsbündig, Text linksbündig. */
    private static void schreibeSpalten(PrintWriter w, List<String[]> zeilen, List<EingabeFormat.Spalte> aufbau) {
        int n = 0;
        for (String[] f : zeilen) {
            n = Math.max(n, f.length);
        }
        int[] breite = new int[n];
        for (String[] f : zeilen) {
            for (int i = 0; i < f.length; i++) {
                breite[i] = Math.max(breite[i], f[i].length());
            }
        }
        for (String[] f : zeilen) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < f.length; i++) {
                if (i > 0) {
                    sb.append("  ");
                }
                boolean zahl = i < aufbau.size() && (aufbau.get(i) == EingabeFormat.Spalte.R
                        || aufbau.get(i) == EingabeFormat.Spalte.H || aufbau.get(i) == EingabeFormat.Spalte.Z);
                boolean letztes = i == f.length - 1;
                if (zahl) {
                    sb.append(" ".repeat(breite[i] - f[i].length())).append(f[i]);
                } else if (letztes) {
                    sb.append(f[i]);
                } else {
                    sb.append(f[i]).append(" ".repeat(breite[i] - f[i].length()));
                }
            }
            w.println(sb.toString().stripTrailing());
        }
    }

    private static String zahl(String s, boolean komma) {
        return komma ? s.replace('.', ',') : s;
    }

    /** Schreibt die Punktliste als CSV mit Statusspalte (Punkt;Code;R;H;Z;Status). */
    static void schreibeStatus(Path ziel, List<Punkt> liste, LattenParameter lp) throws IOException {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(ziel, StandardCharsets.UTF_8))) {
            w.println("# " + Version.textMitHersteller());
            w.println("Punkt;Code;R;H;Z;Status");
            for (Punkt a : liste) {
                Point3D p = a.punkt();
                w.println(String.join(";", p.id(), p.code(), lp.m(p.y()), lp.m(p.x()), lp.m(p.z()), a.status()));
            }
        }
    }
}
