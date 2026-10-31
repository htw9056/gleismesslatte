package de.gleismessung;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Lesbares Protokoll zur berechneten Überhöhung (Textdatei).
 *
 * <p>Je Lattenlage: Achspunkt, Station (aufsummierter Abstand der Achspunkte),
 * Querneigung, Überhöhung u, hohe Seite und Rampe zur vorherigen Lattenlage.</p>
 *
 * <p>Vorzeichen: u &gt; 0 = rechte Schiene höher, bezogen auf die Messrichtung
 * (Reihenfolge der Lattenlagen in der Datei). Bei nur einer Lattenlage ist die
 * Messrichtung unbekannt; dann wird nur die hohe Schiene über den Punkt angegeben.</p>
 */
final class UeberhoehungsProtokoll {

    /** Unterhalb dieses Betrags gilt das Gleis als nicht überhöht [m]. */
    private static final double EBEN = 0.0005;

    private UeberhoehungsProtokoll() {
    }

    /** Eine Zeile des Protokolls. */
    record Zeile(LattenErgebnis e, double station, double uRechts, String hoheSeite, String hoherPunkt,
                 double ds, double du, boolean richtungBekannt) {
    }

    static Path dateiFuer(Path punktDatei) {
        return punktDatei.resolveSibling(Main.ohneEndung(punktDatei).getFileName() + "_ueberhoehung.txt");
    }

    static List<Zeile> berechne(List<LattenErgebnis> lagen) {
        int n = lagen.size();
        Zeile[] z = new Zeile[n];
        double station = 0;
        for (int i = 0; i < n; i++) {
            LattenErgebnis e = lagen.get(i);
            double ds = 0;
            if (i > 0) {
                ds = horizontal(lagen.get(i - 1).achse(), e.achse());
                station += ds;
            }
            // Messrichtung: zum nächsten Achspunkt, beim letzten vom vorherigen
            double[] t = null;
            if (i + 1 < n && horizontal(e.achse(), lagen.get(i + 1).achse()) > 0.05) {
                t = richtung(e.achse(), lagen.get(i + 1).achse());
            } else if (i > 0 && horizontal(lagen.get(i - 1).achse(), e.achse()) > 0.05) {
                t = richtung(lagen.get(i - 1).achse(), e.achse());
            }
            // Querrichtung Punkt 1 -> Punkt 2
            double qy = e.f2().y() - e.f1().y();
            double qx = e.f2().x() - e.f1().x();

            double u = e.ueberhoehung();               // > 0: Punkt 2 höher
            String hoherPunkt = Math.abs(u) < EBEN ? "-" : (u > 0 ? e.f2().id() : e.f1().id());
            double uRechts = Double.NaN;
            String seite;
            if (t == null) {
                seite = Math.abs(u) < EBEN ? "eben" : "?";
            } else {
                // rechte Normale zur Fahrtrichtung (y = Ost, x = Nord): (tx, -ty)
                double rechts = qy * t[1] + qx * -t[0];
                uRechts = rechts >= 0 ? u : -u;
                seite = Math.abs(u) < EBEN ? "eben" : (uRechts > 0 ? "rechts" : "links");
            }
            double du = (i > 0 && t != null && !Double.isNaN(z[i - 1].uRechts()))
                    ? uRechts - z[i - 1].uRechts() : Double.NaN;
            z[i] = new Zeile(e, station, uRechts, seite, hoherPunkt, ds, du, t != null);
        }
        return List.of(z);
    }

    static void schreibe(Path ziel, Path eingabe, Path punktDatei, List<LattenErgebnis> lagen,
                         LattenParameter lp) throws IOException {
        List<Zeile> zeilen = berechne(lagen);
        DecimalFormat m = format(lp.nachkommastellen());
        DecimalFormat mm = format(1);
        DecimalFormat gon = format(4);
        DecimalFormat st = format(2);

        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(ziel, StandardCharsets.UTF_8))) {
            String titel = Version.text() + " – Protokoll Überhöhung";
            w.println(titel);
            w.println("=".repeat(titel.length()));
            w.println(Version.copyright());
            w.println();
            w.println("Erstellt:          " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
            w.println("Eingabedatei:      " + eingabe.toAbsolutePath().normalize());
            w.println("Ergebnisdatei:     " + punktDatei.toAbsolutePath().normalize());
            w.println("Reflektorhöhe h:   " + format(3).format(lp.h()) + " m");
            w.println("Lattenkonstante c: " + format(3).format(lp.c()) + " m");
            w.println("Bezugsbasis:       " + format(3).format(lp.bezugsbasis()) + " m   (u = Bezugsbasis · sin α)");
            w.println("Grenzwert:         |u| ≤ " + format(0).format(lp.maxUeberhoehung() * 1000)
                    + " mm   (EBO § 6 Abs. 3, einschließlich Abweichungen im Betrieb)");
            w.println("Vorzeichen:        u, α > 0 = rechte Schiene höher, in Messrichtung (Reihenfolge der Lattenlagen)");
            w.println("Rampe:             Überhöhungsänderung Δu je m Station in mm/m (= ‰), 1:n = Δs / Δu");
            w.println("Station:           aufsummierter Horizontalabstand der Achspunkte ab erster Lattenlage");
            w.println("Art:               GML = Gleismesslatte (korrigiert), Code = Achs-Code-Paar (ohne Korrektur,");
            w.println("                   u aus den gemessenen Punkten)");
            w.println();

            if (zeilen.isEmpty()) {
                w.println("Keine Lattenlagen ausgewertet.");
                return;
            }

            String kopf = String.format("%3s  %-14s %-4s %-12s %13s %13s %10s %10s %9s %-2s %-6s %-8s %9s %10s %8s",
                    "Nr", "Lattenlage", "Art", "Achspunkt", "y Achse", "x Achse", "Station[m]", "α [gon]",
                    "u [mm]", "", "hoch", "Punkt", "Δu [mm]", "Rampe[‰]", "1:n");
            w.println(kopf);
            w.println("-".repeat(kopf.length()));
            int nr = 0;
            for (Zeile z : zeilen) {
                nr++;
                LattenErgebnis e = z.e();
                double uAnzeige = z.richtungBekannt() ? z.uRechts() : Math.abs(e.ueberhoehung());
                // Querneigung mit demselben Vorzeichen wie u (unabhängig von der Punktreihenfolge)
                double alphaGon = Math.asin(Math.max(-1, Math.min(1, uAnzeige / lp.bezugsbasis()))) * 200 / Math.PI;
                String rampe = "";
                String einsZu = "";
                String du = "";
                if (!Double.isNaN(z.du()) && z.ds() > 0.01) {
                    du = vz(mm, z.du() * 1000);
                    double promille = z.du() / z.ds() * 1000;      // mm/m
                    rampe = mm.format(Math.abs(promille));
                    einsZu = Math.abs(z.du()) < 1e-6 ? "–" : "1:" + Math.round(z.ds() / Math.abs(z.du()));
                }
                w.println(String.format("%3d  %-14s %-4s %-12s %13s %13s %10s %10s %9s %-2s %-6s %-8s %9s %10s %8s",
                        nr, kurz(e.messpunkt(), 14), e.art(), kurz(e.achse().id(), 12),
                        m.format(e.achse().y()), m.format(e.achse().x()), st.format(z.station()),
                        vz(gon, alphaGon), vz(mm, uAnzeige * 1000),
                        lp.ueberschritten(e.ueberhoehung()) ? "!!" : "",
                        z.hoheSeite(), kurz(z.hoherPunkt(), 8), du, rampe, einsZu));
            }
            w.println();

            // Statistik
            double max = 0;
            double min = Double.MAX_VALUE;
            double summe = 0;
            Zeile zMax = null;
            Zeile zRampe = null;
            double maxRampe = -1;
            for (Zeile z : zeilen) {
                double a = Math.abs(z.e().ueberhoehung());
                summe += a;
                if (a > max) {
                    max = a;
                    zMax = z;
                }
                min = Math.min(min, a);
                if (!Double.isNaN(z.du()) && z.ds() > 0.01) {
                    double r = Math.abs(z.du() / z.ds() * 1000);
                    if (r > maxRampe) {
                        maxRampe = r;
                        zRampe = z;
                    }
                }
            }
            w.println("Zusammenfassung");
            w.println("---------------");
            long code = zeilen.stream().filter(z -> !z.e().korrigiert()).count();
            w.println("Lagen gesamt:             " + zeilen.size()
                    + (code > 0 ? "   (davon " + (zeilen.size() - code) + " GML, " + code + " Achs-Codes)" : ""));
            if (zeilen.size() > 1) {
                w.println("Länge (Station):          " + st.format(zeilen.get(zeilen.size() - 1).station()) + " m");
            }
            w.println("Überhöhung |u| min:       " + mm.format(min * 1000) + " mm");
            w.println("Überhöhung |u| max:       " + mm.format(max * 1000) + " mm"
                    + (zMax != null ? "   bei " + zMax.e().messpunkt() + " (" + zMax.e().achse().id() + ")" : ""));
            w.println("Überhöhung |u| Mittel:    " + mm.format(summe / zeilen.size() * 1000) + " mm");
            List<Zeile> ueber = zeilen.stream().filter(z -> lp.ueberschritten(z.e().ueberhoehung())).toList();
            String gw = format(0).format(lp.maxUeberhoehung() * 1000) + " mm";
            if (ueber.isEmpty()) {
                w.println("Grenzwert " + gw + ":         eingehalten (EBO § 6 Abs. 3)");
            } else {
                w.println("Grenzwert " + gw + ":         ÜBERSCHRITTEN bei " + ueber.size() + " Lage(n)  (in der Tabelle mit !! markiert)");
                for (Zeile z : ueber) {
                    w.println("                            " + z.e().messpunkt() + " (" + z.e().achse().id() + "): |u| = "
                            + mm.format(Math.abs(z.e().ueberhoehung()) * 1000) + " mm, Station "
                            + st.format(z.station()) + " m");
                }
            }
            if (zRampe != null) {
                w.println("größte Rampe:             " + mm.format(maxRampe) + " mm/m   bei " + zRampe.e().messpunkt()
                        + " (Δu " + vz(mm, zRampe.du() * 1000) + " mm auf " + st.format(zRampe.ds()) + " m)");
            }
            if (zeilen.stream().anyMatch(z -> !z.richtungBekannt())) {
                w.println();
                w.println("Hinweis: Bei nur einer Lattenlage ist die Messrichtung unbekannt –");
                w.println("         u ist dann als Betrag angegeben, die hohe Schiene über die Punktnummer.");
            }
            w.println();
            w.println("Hinweis: Station und Rampe setzen voraus, dass die Lattenlagen in Gleisrichtung");
            w.println("         nacheinander gemessen wurden (Reihenfolge in der Eingabedatei).");
        }
    }

    private static double horizontal(Point3D a, Point3D b) {
        return Math.hypot(b.y() - a.y(), b.x() - a.x());
    }

    private static double[] richtung(Point3D a, Point3D b) {
        double d = horizontal(a, b);
        return new double[] {(b.y() - a.y()) / d, (b.x() - a.x()) / d};
    }

    private static DecimalFormat format(int stellen) {
        DecimalFormat f = new DecimalFormat(stellen == 0 ? "0" : "0." + "0".repeat(stellen),
                DecimalFormatSymbols.getInstance(Locale.GERMANY));
        f.setGroupingUsed(false);
        return f;
    }

    private static String vz(DecimalFormat f, double v) {
        String s = f.format(v);
        return v > 0 && !s.matches("0[,0]*") ? "+" + s : s.replaceFirst("^-(0[,0]*)$", "$1");
    }

    private static String kurz(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
