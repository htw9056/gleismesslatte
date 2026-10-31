package de.gleismessung;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Startmodi:
 * <ul>
 *   <li>ohne Argumente (Doppelklick auf die .exe): Programmfenster</li>
 *   <li>ein Argument (CSV auf die .exe gezogen): Programmfenster mit vorgewählter Datei</li>
 *   <li>zwei oder mehr Argumente: Konsole
 *   <pre>  Gleismesslatte eingabe.csv ausgabe.csv [h] [c] [spurweite=1.458] [toleranz=0.100] [achse=ja|nein]
 *       [nachkommastellen=3|4] [ueberhoehungsprotokoll=ja|nein] [paarbildung=folge|frei] [achscodes=SOL,SOR]
 *       [trennzeichen=auto|semikolon|leerzeichen] [aufbau="P R H Z C"] [kopfzeilen=0..5]
 *       [maxueberhoehung=180] [ausgabeformat=eingabe|status]</pre></li>
 * </ul>
 *
 * <p>Eingabe ist eine Punktliste – Semikolon (CSV) oder Leerzeichen/Tab getrennt,
 * Dezimalpunkt oder -komma, Zeilen mit # werden ignoriert. Spaltenfolge und Anzahl
 * Kopfzeilen sind einstellbar (siehe {@link EingabeFormat}), Standard {@code P R H Z C}.</p>
 *
 * <p>Punkte mit "GML" als Präfix oder Suffix im Code sind Prismen der
 * Gleismesslatte. Zwei GML-Punkte bilden ein Paar (eine Lattenlage), wenn ihre
 * Spurweite in Sollspurweite ± Toleranz liegt – je nach Einstellung nur direkt
 * aufeinanderfolgende oder beliebige Punkte (siehe {@link Paarbildung}).
 * Nur diese werden korrigiert; danach wird die GML-Kennung aus dem Code
 * gelöscht. Alle anderen Punkte werden unverändert übernommen.</p>
 *
 * <p>Ausgabe:</p>
 * <ul>
 *   <li>ausgabe – Punktliste im Format der Eingabe (oder CSV mit Statusspalte):
 *       GML-Punkte auf SO korrigiert, optional Achspunkte</li>
 *   <li>ausgabe_spur.csv – je Lattenlage Spurweite, Überhöhung, Gleismitte</li>
 *   <li>ausgabe_ueberhoehung.txt – Protokoll zur Überhöhung mit Rampen (abschaltbar)</li>
 * </ul>
 */
public final class Main {

    /** Optionale Parameterdatei im Programmordner, Arbeitsverzeichnis oder neben der Eingabedatei. */
    public static final String PARAMETER_DATEI = "gleismesslatte.properties";


    /** Ergebnis eines Laufs für die Zusammenfassung. */
    record Bericht(int lagen, int codePaare, int achsen, int ueberschreitungen, long sonstige, List<String> warnungen,
                   List<LattenErgebnis> ergebnisse, Path punktDatei, Path spurDatei, Path ueberhoehungDatei) {
    }

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length <= 1) {
            if (GraphicsEnvironment.isHeadless()) {
                System.err.println("Aufruf: Gleismesslatte eingabe.csv ausgabe.csv [h] [c] "
                        + "[h=..] [c=..] [spurweite=..] [toleranz=..] [achse=ja|nein] [nachkommastellen=3|4] "
                        + "[ueberhoehungsprotokoll=ja|nein] [paarbildung=folge|frei] [achscodes=SOL,SOR] "
                        + "[trennzeichen=auto|semikolon|leerzeichen] [aufbau=P,R,H,Z,C] [kopfzeilen=0..5] "
                        + "[maxueberhoehung=180] [ausgabeformat=eingabe|status] "
                        + "[hdateien=a.gsi;b.jxl] [hbezug=prisma|reduziert] [hspalten=datei=P1,H5,m]");
                System.exit(1);
            }
            Fenster.oeffne(args.length == 1 ? Path.of(args[0]) : null);
            return;
        }
        try {
            Path ein = Path.of(args[0]);
            LattenParameter param = Parameter.lese(ein, args);
            auswerten(ein, Path.of(args[1]), param, System.out::println);
        } catch (IllegalArgumentException | IOException e) {
            System.err.println("Fehler: " + e.getMessage());
            System.exit(2);
        }
    }

    /** messung.txt -> messung_ergebnis.txt im selben Ordner (Endung bleibt erhalten). */
    static Path standardAusgabe(Path eingabe) {
        String n = eingabe.getFileName().toString();
        int punkt = n.lastIndexOf('.');
        String name = punkt > 0 ? n.substring(0, punkt) : n;
        String endung = punkt > 0 ? n.substring(punkt) : ".csv";
        return eingabe.toAbsolutePath().resolveSibling(name + "_ergebnis" + endung);
    }

    /** Dateiname ohne Endung, z. B. für messung_ergebnis_spur.csv. */
    static Path ohneEndung(Path datei) {
        String n = datei.getFileName().toString();
        int punkt = n.lastIndexOf('.');
        return datei.resolveSibling(punkt > 0 ? n.substring(0, punkt) : n);
    }

    /**
     * Führt die Auswertung aus.
     *
     * @param protokoll erhält alle Meldungen (Konsole oder Fenster)
     */
    static Bericht auswerten(Path eingabe, Path punktDatei, LattenParameter param,
                             Consumer<String> protokoll) throws IOException {
        List<String> warnungen = new ArrayList<>();
        Consumer<String> warnung = t -> {
            warnungen.add(t);
            protokoll.accept("Warnung: " + t);
        };

        protokoll.accept(Version.textMitHersteller());
        protokoll.accept("Eingabe: " + eingabe.toAbsolutePath().normalize());
        protokoll.accept(param.toString());

        PunktAusgabe.Eingabe ein = lesePunkte(eingabe, param, warnung);
        List<Point3D> punkte = ein.punkte();
        protokoll.accept(punkte.size() + " Punkte gelesen.");
        GleismesslatteRechner rechner = new GleismesslatteRechner(param);

        // Reflektorhöhe je GML-Punkt: aus Messdatei(en), sonst Feldwert; bei Bezug "reduziert"
        // die Höhe des Punktes auf die Prismenmitte zurückrechnen
        double[] hKorr = new double[punkte.size()];
        List<Point3D> rechenPunkte = reflektorhoehen(eingabe, punkte, param, hKorr, protokoll, warnung);

        // Paare bilden (aufeinanderfolgend oder frei): GML-Punkte und Achs-Code-Punkte getrennt
        List<Paarbildung.Paar> gmlPaare = Paarbildung.bilde(rechenPunkte, param, warnung);
        List<Paarbildung.Paar> codePaare = Paarbildung.bildeAchsCodes(rechenPunkte, param, warnung);

        record Lage(int i, LattenErgebnis e) {
        }
        List<Lage> alle = new ArrayList<>();
        LattenErgebnis[] alsPunkt1 = new LattenErgebnis[punkte.size()];
        LattenErgebnis[] alsPunkt2 = new LattenErgebnis[punkte.size()];
        for (int art = 0; art < 2; art++) {
            boolean gml = art == 0;
            for (Paarbildung.Paar pa : gml ? gmlPaare : codePaare) {
                Point3D p1 = rechenPunkte.get(pa.i());
                Point3D p2 = rechenPunkte.get(pa.j());
                double h = param.h();
                if (gml) {
                    h = (hKorr[pa.i()] + hKorr[pa.j()]) / 2;
                    if (Math.abs(hKorr[pa.i()] - hKorr[pa.j()]) > 0.0005) {
                        warnung.accept(String.format(Locale.ROOT,
                                "Reflektorhöhen von %s (%.3f m) und %s (%.3f m) verschieden – Mittel %.4f m verwendet.",
                                p1.id(), hKorr[pa.i()], p2.id(), hKorr[pa.j()], h));
                    }
                }
                LattenErgebnis e = rechner.berechne(p1.id() + "-" + p2.id(), p1, p2, gml, h);
                alle.add(new Lage(pa.i(), e));
                alsPunkt1[pa.i()] = e;
                alsPunkt2[pa.j()] = e;
            }
        }
        // Reihenfolge der Lagen = Reihenfolge des früheren Punktes in der Datei
        alle.sort(java.util.Comparator.comparingInt(Lage::i));
        List<LattenErgebnis> lagen = new ArrayList<>();
        for (Lage l : alle) {
            lagen.add(l.e());
        }

        // Grenzwert der Überhöhung (EBO § 6 Abs. 3: höchstens 180 mm)
        int ueberschreitungen = 0;
        for (LattenErgebnis e : lagen) {
            if (param.ueberschritten(e.ueberhoehung())) {
                ueberschreitungen++;
                warnung.accept(String.format(Locale.ROOT,
                        "Überhöhung %.1f mm bei %s (%s) überschreitet den Grenzwert %.0f mm (EBO § 6 Abs. 3).",
                        Math.abs(e.ueberhoehung()) * 1000, e.messpunkt(), e.achse().id(),
                        param.maxUeberhoehung() * 1000));
            }
        }

        // Ausgabe in Dateireihenfolge; Achspunkt hinter dem späteren Punkt des Paares
        List<PunktAusgabe.Punkt> ausgabe = new ArrayList<>();
        for (int k = 0; k < punkte.size(); k++) {
            Point3D p = punkte.get(k);
            if (alsPunkt1[k] != null) {
                LattenErgebnis e = alsPunkt1[k];
                ausgabe.add(new PunktAusgabe.Punkt(e.f1(), e.korrigiert() ? "GML korrigiert" : "Achs-Code unveraendert", k));
            } else if (alsPunkt2[k] != null) {
                LattenErgebnis e = alsPunkt2[k];
                ausgabe.add(new PunktAusgabe.Punkt(e.f2(), e.korrigiert() ? "GML korrigiert" : "Achs-Code unveraendert", k));
                // GML-Paare: Achse nach Einstellung; Achs-Code-Paare: Achse ist ihr Zweck
                if (!e.korrigiert() || param.achseBerechnen()) {
                    ausgabe.add(new PunktAusgabe.Punkt(e.achse(),
                            e.korrigiert() ? "Gleisachse" : "Gleisachse aus Achs-Codes", -1));
                }
            } else if (GmlCodierung.istGleismesspunkt(p.code())) {
                ausgabe.add(new PunktAusgabe.Punkt(p, "GML ohne Partner - NICHT korrigiert", k));
            } else if (param.istAchsCode(p.code())) {
                ausgabe.add(new PunktAusgabe.Punkt(p, "Achs-Code ohne Partner - keine Achse", k));
            } else {
                ausgabe.add(new PunktAusgabe.Punkt(p, "unveraendert", k));
            }
        }

        Path spurDatei = punktDatei.resolveSibling(ohneEndung(punktDatei).getFileName() + "_spur.csv");
        if (punktDatei.toAbsolutePath().normalize().equals(eingabe.toAbsolutePath().normalize())) {
            throw new IOException("Ausgabedatei darf nicht die Eingabedatei sein: " + eingabe);
        }
        if (param.ausgabe() == EingabeFormat.Ausgabe.STATUS) {
            PunktAusgabe.schreibeStatus(punktDatei, ausgabe, param);
        } else {
            PunktAusgabe.schreibe(punktDatei, ausgabe, ein, param, warnung);
        }
        schreibeSpur(spurDatei, lagen, param);
        Path ueDatei = null;
        if (param.ueberhoehungsProtokoll()) {
            ueDatei = UeberhoehungsProtokoll.dateiFuer(punktDatei);
            UeberhoehungsProtokoll.schreibe(ueDatei, eingabe, punktDatei, lagen, param);
        }

        for (LattenErgebnis e : lagen) {
            protokoll.accept(text(e, param));
        }
        long nichtGml = ausgabe.stream().filter(a -> a.status().equals("unveraendert")).count();
        int achsen = (param.achseBerechnen() ? gmlPaare.size() : 0) + codePaare.size();
        protokoll.accept("");
        protokoll.accept(String.format("%d Lattenlagen (GML) ausgewertet, %d Achs-Code-Paare, %d Achspunkte, "
                + "%d sonstige Punkte unverändert.", gmlPaare.size(), codePaare.size(), achsen, nichtGml));
        protokoll.accept("-> " + punktDatei.toAbsolutePath().normalize());
        protokoll.accept("-> " + spurDatei.toAbsolutePath().normalize());
        if (ueDatei != null) {
            protokoll.accept("-> " + ueDatei.toAbsolutePath().normalize());
        }
        if (ueberschreitungen > 0) {
            protokoll.accept(String.format(Locale.ROOT, "ACHTUNG: %d Lage(n) mit Überhöhung über %.0f mm (EBO § 6 Abs. 3).",
                    ueberschreitungen, param.maxUeberhoehung() * 1000));
        }
        return new Bericht(gmlPaare.size(), codePaare.size(), achsen, ueberschreitungen, nichtGml, List.copyOf(warnungen), List.copyOf(lagen),
                punktDatei, spurDatei, ueDatei);
    }

    /** Liest nur die Punkte (ohne Meldungen), z. B. für die Suche der Reflektorhöhe. */
    static List<Point3D> lesePunkteStill(Path eingabe, LattenParameter lp) throws IOException {
        return lesePunkte(eingabe, lp, t -> { }).punkte();
    }

    /** Messdateien der Reflektorhöhe als Pfade; relative Angaben bezogen auf den Ordner der Eingabedatei. */
    static List<Path> hDateien(LattenParameter lp, Path eingabe) {
        List<Path> liste = new ArrayList<>();
        for (String d : lp.hDateien()) {
            Path p = Path.of(d);
            if (!p.isAbsolute() && !Files.exists(p) && eingabe != null
                    && eingabe.toAbsolutePath().getParent() != null) {
                p = eingabe.toAbsolutePath().getParent().resolve(d);
            }
            liste.add(p);
        }
        return liste;
    }

    /**
     * Bestimmt die Reflektorhöhe je GML-Punkt (hKorr) und liefert die Punkte für die Rechnung.
     * Bei Bezug „reduziert“ wird die Höhe der GML-Punkte um die im Instrument verwendete
     * Reflektorhöhe angehoben (Prismenmitte), alle anderen Punkte bleiben unverändert.
     */
    private static List<Point3D> reflektorhoehen(Path eingabe, List<Point3D> punkte, LattenParameter lp,
                                                 double[] hKorr, Consumer<String> protokoll,
                                                 Consumer<String> warnung) throws IOException {
        ReflektorhoeheLeser.Ergebnis rh = null;
        if (!lp.hDateien().isEmpty()) {
            java.util.Set<String> ids = new java.util.HashSet<>();
            for (Point3D p : punkte) {
                ids.add(p.id());
            }
            java.util.Set<String> gmlIds = new java.util.HashSet<>();
            for (Point3D p : punkte) {
                if (GmlCodierung.istGleismesspunkt(p.code())) {
                    gmlIds.add(p.id());
                }
            }
            rh = ReflektorhoeheLeser.lies(hDateien(lp, eingabe), ids, gmlIds, lp.hSpaltenMap());
            for (String info : rh.dateiInfo) {
                protokoll.accept("Reflektorhöhe – " + info);
            }
            protokoll.accept("Gefundene Reflektorhöhen (0–" + ReflektorhoeheLeser.fmt(ReflektorhoeheLeser.MAX)
                    + " m): " + rh.verteilungText());
            if (rh.ausserhalb > 0) {
                protokoll.accept(rh.ausserhalb + " Werte außerhalb 0–" + ReflektorhoeheLeser.fmt(ReflektorhoeheLeser.MAX)
                        + " m ignoriert (andere Ziele, z. B. Lotstab).");
            }
        }
        List<Point3D> rechen = new ArrayList<>(punkte);
        int ausDatei = 0;
        List<String> ersatz = new ArrayList<>();
        for (int k = 0; k < punkte.size(); k++) {
            Point3D p = punkte.get(k);
            if (!GmlCodierung.istGleismesspunkt(p.code())) {
                hKorr[k] = Double.NaN;
                continue;
            }
            Double hd = rh == null ? null : rh.hoehe(p.id());
            if (hd != null && hd > 0) {
                hKorr[k] = hd;
                ausDatei++;
            } else {
                hKorr[k] = lp.h();
                if (rh != null) {
                    ersatz.add(p.id());
                }
            }
            if (lp.hBezug() == ReflektorhoeheLeser.Bezug.REDUZIERT) {
                double zurueck = hd != null ? hd : lp.h();
                rechen.set(k, new Point3D(p.id(), p.code(), p.y(), p.x(), p.z() + zurueck));
            }
        }
        if (rh != null) {
            protokoll.accept(String.format(Locale.ROOT, "Reflektorhöhe: %d GML-Punkte aus Messdatei, %d mit Feldwert h = %s m.",
                    ausDatei, ersatz.size(), ReflektorhoeheLeser.fmt(lp.h())));
            if (!ersatz.isEmpty()) {
                warnung.accept(ersatz.size() + " GML-Punkt(e) ohne Reflektorhöhe in den Messdateien – Feldwert "
                        + ReflektorhoeheLeser.fmt(lp.h()) + " m verwendet: "
                        + String.join(", ", ersatz.subList(0, Math.min(10, ersatz.size())))
                        + (ersatz.size() > 10 ? " …" : ""));
            }
        }
        if (rh != null && ausDatei > 0 && lp.hBezug() == ReflektorhoeheLeser.Bezug.PRISMA) {
            warnung.accept("Die Messdateien enthalten Reflektorhöhen > 0. Waren diese im Instrument eingetragen, "
                    + "sind die Höhen der Punktliste bereits lotrecht um hr reduziert – dann 'Höhen der Punktliste = "
                    + "um Reflektorhöhe lotrecht reduziert' wählen (sonst Höhenfehler in Größe von hr).");
        }
        if (lp.hBezug() == ReflektorhoeheLeser.Bezug.REDUZIERT) {
            protokoll.accept("Höhen der Punktliste sind um die Reflektorhöhe lotrecht reduziert – "
                    + "GML-Punkte für die Rechnung auf Prismenmitte zurückgerechnet.");
        }
        return rechen;
    }

    /**
     * Liest die Punktliste nach eingestelltem Format (Trennzeichen, Aufbau, Kopfzeilen).
     * Leerzeilen und Zeilen mit # werden ignoriert. Die Datei darf UTF-8 oder
     * Windows-1252 (ANSI) sein.
     */
    private static PunktAusgabe.Eingabe lesePunkte(Path datei, LattenParameter lp, Consumer<String> warnung)
            throws IOException {
        List<EingabeFormat.Spalte> aufbau = EingabeFormat.spalten(lp.aufbau());
        List<Point3D> liste = new ArrayList<>();
        List<String[]> felder = new ArrayList<>();
        List<String> kopf = new ArrayList<>();
        Boolean semikolon = null;
        Boolean komma = null;
        int nr = 0;
        boolean ersteDatenzeile = true;
        for (String zeile : liesZeilen(datei)) {
            nr++;
            if (!zeile.isEmpty() && zeile.charAt(0) == '\uFEFF') {
                zeile = zeile.substring(1); // BOM aus Excel
            }
            if (nr <= lp.kopfzeilen()) {
                kopf.add(zeile);
                continue;
            }
            zeile = zeile.strip();
            if (zeile.isEmpty() || zeile.startsWith("#")) {
                continue;
            }
            EingabeFormat.Zeile z = EingabeFormat.lies(zeile, lp.trennung(), aufbau);
            if (z.punkt() != null) {
                liste.add(z.punkt());
                felder.add(z.felder());
                if (semikolon == null) {
                    semikolon = z.semikolon();
                }
                if (komma == null) {
                    komma = z.komma();
                }
            } else {
                String hinweis = ersteDatenzeile && liste.isEmpty()
                        ? " – Kopfzeile? Dann 'Kopfzeilen überspringen' einstellen" : "";
                warnung.accept(String.format("Zeile %d übersprungen: %s%s: %s", nr, z.fehler(), hinweis, zeile));
            }
            ersteDatenzeile = false;
        }
        boolean sk = semikolon != null ? semikolon : lp.trennung() == EingabeFormat.Trennung.SEMIKOLON;
        return new PunktAusgabe.Eingabe(liste, felder, kopf, sk, komma != null && komma);
    }

    private static List<String> liesZeilen(Path datei) throws IOException {
        try {
            return Files.readAllLines(datei, StandardCharsets.UTF_8);
        } catch (java.nio.charset.MalformedInputException e) {
            return Files.readAllLines(datei, java.nio.charset.Charset.forName("windows-1252"));
        }
    }

    private static String text(LattenErgebnis e, LattenParameter lp) {
        String s = String.format(Locale.ROOT,
                "%n%s" + (e.korrigiert() ? "" : "  (Achs-Codes, ohne Korrektur)") + "%n  Spurweite   S = %s m  (Abw. %+.1f mm)%n"
              + "  Überhöhung  u = %+.1f mm  (Neigung %+.4f gon)%s%n"
              + "%s  SO 1        %s%n  SO 2        %s",
                e.messpunkt(), lp.m(e.spurweite()), e.spurfehler() * 1000,
                e.ueberhoehung() * 1000, e.neigungGon(),
                lp.ueberschritten(e.ueberhoehung())
                        ? String.format(Locale.ROOT, "  ! GRENZWERT %.0f mm ÜBERSCHRITTEN", lp.maxUeberhoehung() * 1000) : "",
                e.korrigiert() ? String.format(Locale.GERMANY, "  Reflektorh. h = %.4f m%n", e.h()) : "",
                lp.punkt(e.f1()), lp.punkt(e.f2()));
        return lp.achseBerechnen() || !e.korrigiert()
                ? s + String.format("%n  Gleisachse  %s", lp.punkt(e.achse())) : s;
    }

    private static void schreibeSpur(Path ziel, List<LattenErgebnis> liste, LattenParameter lp) throws IOException {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(ziel, StandardCharsets.UTF_8))) {
            w.println("# " + Version.textMitHersteller());
            w.println("Lattenlage;Art;Spurweite;Abw_mm;Ueberhoehung_mm;Grenzwert_u;Neigung_gon;RiWi_quer_gon;"
                    + "Achspunkt;y_Mitte;x_Mitte;z_Mitte;z_tief;Reflektorhoehe");
            for (LattenErgebnis e : liste) {
                w.println(String.format(Locale.ROOT,
                        "%s;%s;%s;%.1f;%.1f;%s;%.4f;%.4f;%s;%s;%s;%s;%s;%s",
                        e.messpunkt(), e.art(), lp.m(e.spurweite()), e.spurfehler() * 1000,
                        e.ueberhoehung() * 1000,
                        lp.ueberschritten(e.ueberhoehung())
                                ? String.format(Locale.ROOT, "UEBERSCHRITTEN >%.0f", lp.maxUeberhoehung() * 1000) : "ok",
                        e.neigungGon(), e.riwiQuer(),
                        lp.achseBerechnen() || !e.korrigiert() ? e.achse().id() : "",
                        lp.m(e.achse().y()), lp.m(e.achse().x()), lp.m(e.achse().z()),
                        lp.m(e.zTiefereSchiene()),
                        e.korrigiert() ? String.format(Locale.ROOT, "%.4f", e.h()) : ""));
            }
        }
    }
}
