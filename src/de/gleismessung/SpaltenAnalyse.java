package de.gleismessung;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Untersucht eine Messdatei im Textformat (Tabelle ohne feste Begriffe) und schlägt vor,
 * in welcher Spalte die Punktnummer und in welcher die Reflektorhöhe steht.
 *
 * <p>Punktnummer: Spalte mit den meisten Treffern in der Punktliste.<br>
 * Reflektorhöhe: Zahlenspalte, in der möglichst viele GML-Punkte einen Wert von 0 bis
 * 0,5 m haben und die nur wenige verschiedene Werte enthält (die Höhe der Latte ändert
 * sich selten). Werte in mm (20–500) werden ebenfalls erkannt.</p>
 */
final class SpaltenAnalyse {

    /** Zerlegte Datei. */
    record Tabelle(List<String[]> zeilen, List<Integer> zeilenNr, String trenner, int spalten) {
    }

    /** Spaltenwahl: Spalten ab 1, faktor 1 = m, 0,001 = mm. */
    record Wahl(int punkt, int hoehe, double faktor) {

        String alsText() {
            return "P" + punkt + ",H" + hoehe + (faktor < 0.5 ? ",mm" : ",m");
        }

        static Wahl aus(String t) {
            if (t == null || t.isBlank()) {
                return null;
            }
            int p = 0;
            int h = 0;
            double f = 1;
            for (String teil : t.split(",")) {
                String s = teil.strip().toUpperCase(Locale.ROOT);
                if (s.startsWith("P")) {
                    p = Integer.parseInt(s.substring(1));
                } else if (s.startsWith("H")) {
                    h = Integer.parseInt(s.substring(1));
                } else if (s.equals("MM")) {
                    f = 0.001;
                }
            }
            return p > 0 && h > 0 ? new Wahl(p, h, f) : null;
        }
    }

    /** Bewerteter Vorschlag für die Höhenspalte. */
    record Vorschlag(Wahl wahl, double bewertung, int imBereich, int relevant, String werte) {

        String begruendung(boolean gml) {
            return String.format(Locale.GERMANY, "Spalte %d%s: %d von %d %s mit Wert 0–0,5 m; %s",
                    wahl.hoehe(), wahl.faktor() < 0.5 ? " (in mm)" : "", imBereich, relevant,
                    gml ? "GML-Punkten" : "Zeilen", werte);
        }
    }

    private SpaltenAnalyse() {
    }

    // -------------------------------------------------------------- Zerlegen

    static Tabelle zerlege(String text) {
        List<String> roh = new ArrayList<>();
        List<Integer> nr = new ArrayList<>();
        int i = 0;
        for (String z : text.split("\\R")) {
            i++;
            String t = z.strip();
            if (!t.isEmpty() && t.charAt(0) == '\uFEFF') {
                t = t.substring(1).strip();
            }
            if (t.isEmpty() || t.startsWith("#") || t.startsWith("//")) {
                continue;
            }
            roh.add(t);
            nr.add(i);
        }
        String besterTrenner = "\\s+";
        int besteSpalten = 1;
        for (String tr : new String[] {";", "\t", "\\s+", ","}) {
            Map<Integer, Integer> anzahl = new HashMap<>();
            for (String z : roh) {
                anzahl.merge(z.split(tr, -1).length, 1, Integer::sum);
            }
            Map.Entry<Integer, Integer> modus = anzahl.entrySet().stream()
                    .max(Map.Entry.comparingByValue()).orElse(Map.entry(1, 0));
            if (modus.getKey() >= 3 && modus.getValue() >= 0.6 * roh.size()) {
                besterTrenner = tr;
                besteSpalten = modus.getKey();
                break;
            }
        }
        List<String[]> zeilen = new ArrayList<>();
        int max = besteSpalten;
        for (String z : roh) {
            String[] f = z.split(besterTrenner, -1);
            for (int k = 0; k < f.length; k++) {
                f[k] = f[k].strip();
            }
            zeilen.add(f);
            max = Math.max(max, f.length);
        }
        String name = switch (besterTrenner) {
            case ";" -> "Semikolon";
            case "\t" -> "Tabulator";
            case "," -> "Komma";
            default -> "Leerzeichen";
        };
        return new Tabelle(zeilen, nr, name, max);
    }

    static Double zahl(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        String t = s.replace(',', '.');
        if (!t.matches("[+-]?\\d+(\\.\\d+)?")) {
            return null;
        }
        return Double.parseDouble(t);
    }

    static String feld(String[] z, int spalte) {
        return spalte >= 1 && spalte <= z.length ? z[spalte - 1] : "";
    }

    // ------------------------------------------------------------- Analyse

    /** Punktnummern-Spalte (ab 1) mit den meisten Treffern; 1, wenn keine Punktliste bekannt. */
    static int punktSpalte(Tabelle t, Set<String> bekannt) {
        if (bekannt.isEmpty()) {
            return 1;
        }
        int beste = 1;
        int besteTreffer = -1;
        for (int s = 1; s <= t.spalten(); s++) {
            int treffer = 0;
            for (String[] z : t.zeilen()) {
                String f = feld(z, s);
                if (!f.isEmpty() && bekannt.contains(ReflektorhoeheLeser.normiere(f))) {
                    treffer++;
                }
            }
            if (treffer > besteTreffer) {
                besteTreffer = treffer;
                beste = s;
            }
        }
        return beste;
    }

    /**
     * Bewertet alle Spalten als mögliche Reflektorhöhe.
     *
     * @param gml normierte Punktnummern der GML-Punkte (leer = alle Zeilen zählen)
     */
    static List<Vorschlag> vorschlaege(Tabelle t, int punktSpalte, Set<String> gml) {
        List<Integer> relevant = new ArrayList<>();
        for (int i = 0; i < t.zeilen().size(); i++) {
            String p = feld(t.zeilen().get(i), punktSpalte);
            if (gml.isEmpty() || (!p.isEmpty() && gml.contains(ReflektorhoeheLeser.normiere(p)))) {
                relevant.add(i);
            }
        }
        List<Vorschlag> liste = new ArrayList<>();
        if (relevant.isEmpty()) {
            return liste;
        }
        for (int s = 1; s <= t.spalten(); s++) {
            if (s == punktSpalte) {
                continue;
            }
            for (double faktor : new double[] {1.0, 0.001}) {
                Vorschlag v = pruefe(t, punktSpalte, s, faktor, relevant, !gml.isEmpty());
                if (v != null) {
                    liste.add(v);
                }
            }
        }
        liste.sort(Comparator.comparingDouble(Vorschlag::bewertung).reversed());
        return liste;
    }

    /** Prüft eine bestimmte Spaltenwahl (auch für die Anzeige im Fenster). */
    static Vorschlag pruefe(Tabelle t, int punktSpalte, int spalte, double faktor, List<Integer> relevant,
                            boolean gml) {
        int imBereich = 0;
        int zahlen = 0;
        TreeMap<Double, Integer> werte = new TreeMap<>();
        for (int i : relevant) {
            Double v = zahl(feld(t.zeilen().get(i), spalte));
            if (v == null) {
                continue;
            }
            zahlen++;
            if (faktor < 0.5 && (v != Math.rint(v) || v < 20 || v > 500)) {
                continue;                       // mm nur für ganze Zahlen 20–500
            }
            double h = Math.round(v * faktor * 10000) / 10000.0;
            if (h > 0 && h <= ReflektorhoeheLeser.MAX + 1e-9) {
                imBereich++;
                werte.merge(h, 1, Integer::sum);
            }
        }
        if (imBereich == 0) {
            return null;
        }
        int verschieden = werte.size();
        double anteil = (double) imBereich / relevant.size();
        double bewertung = anteil
                - (verschieden > 8 ? 0.3 : 0.02 * verschieden / 8.0)
                - (faktor < 0.5 ? 0.01 : 0)
                - (zahlen < relevant.size() / 2 ? 0.2 : 0);
        StringBuilder sb = new StringBuilder();
        werte.entrySet().stream().sorted(Map.Entry.<Double, Integer>comparingByValue().reversed()).limit(4)
                .forEach(e -> sb.append(sb.length() > 0 ? ", " : "Werte ").append(ReflektorhoeheLeser.fmt(e.getKey()))
                        .append(" m (").append(e.getValue()).append("×)"));
        if (verschieden > 4) {
            sb.append(" … (").append(verschieden).append(" verschiedene)");
        }
        return new Vorschlag(new Wahl(punktSpalte, spalte, faktor), bewertung, imBereich, relevant.size(),
                sb.toString());
    }

    /** Relevante Zeilen (GML-Punkte) für eine Punktspalte. */
    static List<Integer> relevanteZeilen(Tabelle t, int punktSpalte, Set<String> gml) {
        List<Integer> r = new ArrayList<>();
        for (int i = 0; i < t.zeilen().size(); i++) {
            String p = feld(t.zeilen().get(i), punktSpalte);
            if (gml.isEmpty() || (!p.isEmpty() && gml.contains(ReflektorhoeheLeser.normiere(p)))) {
                r.add(i);
            }
        }
        return r;
    }

    /** Bester automatischer Vorschlag oder null. */
    static Wahl automatisch(Tabelle t, Set<String> bekannt, Set<String> gml) {
        int p = punktSpalte(t, bekannt);
        List<Vorschlag> v = vorschlaege(t, p, gml);
        return v.isEmpty() ? null : v.get(0).wahl();
    }

    /** Normierte Mengen aus Punktnummern. */
    static Set<String> normiert(Set<String> ids) {
        Set<String> s = new HashSet<>();
        for (String id : ids) {
            s.add(ReflektorhoeheLeser.normiere(id));
        }
        return s;
    }
}
