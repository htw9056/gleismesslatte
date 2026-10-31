package de.gleismessung;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Selbsttest ohne externe Bibliotheken: prüft Rechenkern, Codierung, Ein-/Ausgabe,
 * Spaltenanalyse und die mitgelieferten Beispiele gegen ihre Sollwerte.
 *
 * <p>Start im Projektordner: {@code java -cp bin de.gleismessung.Selbsttest}
 * (in Eclipse: Startkonfiguration „Gleismesslatte Selbsttest“). Rückgabewert 0 = alles bestanden.</p>
 */
public final class Selbsttest {

    private static int bestanden;
    private static int fehlgeschlagen;
    private static final Path BEISPIELE = Path.of(System.getProperty("gleismesslatte.beispiele", "beispiele"));

    private Selbsttest() {
    }

    public static void main(String[] args) throws Exception {
        System.out.println(Version.textMitHersteller() + " – Selbsttest");
        rechenkern();
        codierung();
        sortierung();
        eingabeformat();
        spaltenAnalyse();
        beispiel500();
        beispielReflektorhoehe();
        System.out.printf("%n%d bestanden, %d fehlgeschlagen%n", bestanden, fehlgeschlagen);
        System.exit(fehlgeschlagen == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ Tests

    /** Lattenlage mit u = 150 mm: Rückrechnung auf die Fahrkanten. */
    private static void rechenkern() {
        abschnitt("Rechenkern");
        double spur = 1.458;
        double sa = 0.150 / 1.500;
        double ca = Math.sqrt(1 - sa * sa);
        double h = 0.130;
        // Fahrkanten F1, F2; Latte in y-Richtung, rechte Schiene höher
        double[] f1 = {1000, 5000, 100};
        double[] f2 = {1000 + spur * ca, 5000, 100 + spur * sa};
        Point3D p1 = new Point3D("1", "GML", f1[0] - h * sa, f1[1], f1[2] + h * ca);
        Point3D p2 = new Point3D("2", "GML", f2[0] - h * sa, f2[1], f2[2] + h * ca);
        LattenErgebnis e = new GleismesslatteRechner(LattenParameter.standard()).berechne("1-2", p1, p2);
        nahe(e.f1().y(), f1[0], 1e-6, "Fahrkante 1 Rechtswert");
        nahe(e.f1().z(), f1[2], 1e-6, "Fahrkante 1 Höhe");
        nahe(e.f2().y(), f2[0], 1e-6, "Fahrkante 2 Rechtswert");
        nahe(e.spurweite(), spur, 1e-6, "Spurweite");
        nahe(e.ueberhoehung(), 0.150, 1e-6, "Überhöhung");
        nahe(e.achse().y(), (f1[0] + f2[0]) / 2, 1e-6, "Gleisachse");
        pruefe("1Achse".equals(e.achse().id()), "Achspunkt = kleinste Nr. + Achse");
        pruefe(e.f1().code().isEmpty(), "GML aus Code entfernt");
    }

    private static void codierung() {
        abschnitt("GML-Codierung");
        String[][] faelle = {{"GML", ""}, {"LI_GML", "LI"}, {"GML-RE", "RE"}, {"gml01", "01"}, {"L.GML", "L"}};
        for (String[] f : faelle) {
            pruefe(GmlCodierung.istGleismesspunkt(f[0]), f[0] + " erkannt");
            pruefe(f[1].equals(GmlCodierung.entferneKennung(f[0])), f[0] + " → \"" + f[1] + "\"");
        }
        pruefe(!GmlCodierung.istGleismesspunkt("SIGNAL"), "SIGNAL kein GML-Punkt");
    }

    private static void sortierung() {
        abschnitt("Punktnummern");
        pruefe("99".equals(GleismesslatteRechner.kleinereNummer("100", "99")), "99 vor 100");
        pruefe("A5".equals(GleismesslatteRechner.kleinereNummer("A12", "A5")), "A5 vor A12");
    }

    private static void eingabeformat() {
        abschnitt("Eingabeformat");
        List<EingabeFormat.Spalte> std = EingabeFormat.spalten("P R H Z C");
        EingabeFormat.Zeile z = EingabeFormat.lies("8007 456729.225 5420273.279 254.680 SIGNAL ASIG 12",
                EingabeFormat.Trennung.AUTO, std);
        pruefe(z.punkt() != null && "SIGNAL ASIG 12".equals(z.punkt().code()), "Code mit Leerzeichen");
        z = EingabeFormat.lies("101;GML;1000,5;5000,25;100,125", EingabeFormat.Trennung.AUTO,
                EingabeFormat.spalten("P C R H Z"));
        pruefe(z.punkt() != null && Math.abs(z.punkt().y() - 1000.5) < 1e-9, "CSV mit Dezimalkomma");
        pruefe(EingabeFormat.lies("8005 1 2", EingabeFormat.Trennung.AUTO, std).punkt() == null,
                "unvollständige Zeile abgelehnt");
    }

    private static void spaltenAnalyse() throws IOException {
        abschnitt("Spaltenanalyse Text-Messdatei");
        Path datei = BEISPIELE.resolve("beispiel_hr_teil4.txt");
        SpaltenAnalyse.Tabelle t = SpaltenAnalyse.zerlege(ReflektorhoeheLeser.text(datei));
        Set<String> ids = Set.of("3055", "3056", "3057", "3058");
        SpaltenAnalyse.Wahl w = SpaltenAnalyse.automatisch(t, ids, ids);
        pruefe(w != null && w.punkt() == 1 && w.hoehe() == 6, "Höhenspalte 6 trotz Störspalten erkannt");
    }

    /** 500 Punkte, alle Varianten, Vergleich mit beispiel_500_soll.csv. */
    private static void beispiel500() throws IOException {
        abschnitt("Beispiel 500 Punkte");
        Path aus = Files.createTempFile("gml500_", ".txt");
        LattenParameter lp = Parameter.ausWerten("achscodes=SOL,SOR", "paarbildung=frei");
        Main.Bericht b = Main.auswerten(BEISPIELE.resolve("beispiel_500.txt"), aus, lp, t -> { });
        pruefe(b.lagen() == 154, "154 GML-Lagen (" + b.lagen() + ")");
        pruefe(b.codePaare() == 21, "21 Achs-Code-Paare (" + b.codePaare() + ")");
        pruefe(b.ueberschreitungen() == 2, "2 Überschreitungen EBO-Grenzwert (" + b.ueberschreitungen() + ")");
        vergleiche(aus, BEISPIELE.resolve("beispiel_500_soll.csv"), 175, 3.0);
        int wieder = Main.lesePunkteStill(aus, lp).size();
        pruefe(wieder == 675, "Ergebnis wieder einlesbar (675 Punkte, gelesen " + wieder + ")");
        loesche(b);
    }

    /** Reflektorhöhe aus GSI, JobXML, LandXML und Text-Tabelle, Höhen um hr reduziert. */
    private static void beispielReflektorhoehe() throws IOException {
        abschnitt("Reflektorhöhe aus Messdateien");
        Path aus = Files.createTempFile("gmlhr_", ".txt");
        String dateien = String.join(";",
                BEISPIELE.resolve("beispiel_hr_teil1.gsi").toAbsolutePath().toString(),
                BEISPIELE.resolve("beispiel_hr_teil2.jxl").toAbsolutePath().toString(),
                BEISPIELE.resolve("beispiel_hr_teil3.xml").toAbsolutePath().toString(),
                BEISPIELE.resolve("beispiel_hr_teil4.txt").toAbsolutePath().toString());
        LattenParameter lp = Parameter.ausWerten("achscodes=SOL,SOR", "hbezug=reduziert", "hdateien=" + dateien);
        Main.Bericht b = Main.auswerten(BEISPIELE.resolve("beispiel_hr_punkte.txt"), aus, lp, t -> { });
        pruefe(b.lagen() == 30, "30 Lattenlagen (" + b.lagen() + ")");
        vergleiche(aus, BEISPIELE.resolve("beispiel_hr_soll.csv"), 30, 1.5);
        loesche(b);
    }

    // ---------------------------------------------------------------- Hilfen

    /** Vergleicht die Achspunkte der Ergebnisdatei mit den Sollwerten (Lage und Höhe in mm). */
    private static void vergleiche(Path ergebnis, Path soll, int anzahl, double grenzeMm) throws IOException {
        Map<String, double[]> ist = new HashMap<>();
        for (String z : Files.readAllLines(ergebnis, StandardCharsets.UTF_8)) {
            String[] t = z.strip().split("\\s+");
            if (t.length >= 4 && t[0].endsWith("Achse")) {
                ist.put(t[0], new double[] {Double.parseDouble(t[1]), Double.parseDouble(t[2]),
                        Double.parseDouble(t[3])});
            }
        }
        List<String> sz = Files.readAllLines(soll, StandardCharsets.UTF_8);
        String[] kopf = sz.get(0).split(";");
        int iP = index(kopf, "Achspunkt");
        int iR = index(kopf, "R_Achse");
        int iH = index(kopf, "H_Achse");
        int iZ = index(kopf, "Z_Achse");
        int gefunden = 0;
        double maxLage = 0;
        double maxHoehe = 0;
        for (String z : sz.subList(1, sz.size())) {
            String[] t = z.split(";");
            double[] a = ist.get(t[iP]);
            if (a == null) {
                continue;
            }
            gefunden++;
            maxLage = Math.max(maxLage, Math.hypot(a[0] - Double.parseDouble(t[iR]), a[1] - Double.parseDouble(t[iH])));
            maxHoehe = Math.max(maxHoehe, Math.abs(a[2] - Double.parseDouble(t[iZ])));
        }
        pruefe(gefunden == anzahl, "alle " + anzahl + " Sollachsen gefunden (" + gefunden + ")");
        pruefe(maxLage * 1000 <= grenzeMm, String.format(Locale.GERMANY, "Achse Lage max %.1f mm ≤ %.1f mm",
                maxLage * 1000, grenzeMm));
        pruefe(maxHoehe * 1000 <= grenzeMm, String.format(Locale.GERMANY, "Achse Höhe max %.1f mm ≤ %.1f mm",
                maxHoehe * 1000, grenzeMm));
    }

    private static int index(String[] kopf, String name) {
        for (int i = 0; i < kopf.length; i++) {
            if (kopf[i].strip().equals(name)) {
                return i;
            }
        }
        throw new IllegalStateException("Spalte " + name + " fehlt");
    }

    private static void loesche(Main.Bericht b) throws IOException {
        Files.deleteIfExists(b.punktDatei());
        Files.deleteIfExists(b.spurDatei());
        if (b.ueberhoehungDatei() != null) {
            Files.deleteIfExists(b.ueberhoehungDatei());
        }
    }

    private static void abschnitt(String name) {
        System.out.println("\n" + name);
    }

    private static void pruefe(boolean ok, String text) {
        if (ok) {
            bestanden++;
            System.out.println("  ok      " + text);
        } else {
            fehlgeschlagen++;
            System.out.println("  FEHLER  " + text);
        }
    }

    private static void nahe(double ist, double soll, double toleranz, String text) {
        pruefe(Math.abs(ist - soll) <= toleranz, String.format(Locale.ROOT, "%s (%.6f / %.6f)", text, ist, soll));
    }
}
