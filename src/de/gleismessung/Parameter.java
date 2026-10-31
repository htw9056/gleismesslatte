package de.gleismessung;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/**
 * Liest und speichert die einstellbaren Parameter.
 *
 * <p>Reihenfolge beim Lesen (später überschreibt früher):</p>
 * <ol>
 *   <li>Standardwerte (Reflektorhöhe 0,130, c 0,000, Sollspurweite 1,458,
 *       Toleranz 0,100, Achse ja, Nachkommastellen 3)</li>
 *   <li>gleismesslatte.properties im Programmordner (neben .exe/.jar)</li>
 *   <li>gleismesslatte.properties im Arbeitsverzeichnis</li>
 *   <li>gleismesslatte.properties neben der Eingabedatei</li>
 *   <li>Programmargumente: 3. und 4. Argument als Zahl = h und c,
 *       oder benannt als h=.., c=.., spurweite=.., toleranz=.., achse=ja|nein,
 *       nachkommastellen=3|4, ueberhoehungsprotokoll=ja|nein, paarbildung=folge|frei,
 *       achscodes=SOL,SOR, trennzeichen=auto|semikolon|leerzeichen, aufbau="P R H Z C",
 *       kopfzeilen=0..5, maxueberhoehung=180 (mm), ausgabeformat=eingabe|status,
 *       hdateien=a.gsi;b.jxl, hbezug=prisma|reduziert</li>
 * </ol>
 * Dezimalkomma ist erlaubt.
 */
public final class Parameter {

    private Parameter() {
    }

    /** Parameter für eine Eingabedatei inklusive Programmargumenten (ab Index 2). */
    public static LattenParameter lese(Path eingabe, String[] args) throws IOException {
        Properties p = ladeDateien(eingabe);

        int position = 0;
        for (int i = 2; i < args.length; i++) {
            String a = args[i].strip();
            int gl = a.indexOf('=');
            if (gl > 0) {
                p.setProperty(a.substring(0, gl).strip().toLowerCase(Locale.ROOT), a.substring(gl + 1).strip());
            } else if (position == 0) {
                p.setProperty("h", a);
                position++;
            } else if (position == 1) {
                p.setProperty("c", a);
                position++;
            } else {
                System.err.println("Unbekanntes Argument ignoriert: " + a);
            }
        }
        return aus(p);
    }

    /**
     * Startwerte für das Programmfenster: Standard, Parameterdateien und zuletzt die
     * gespeicherte Sitzung (letzte Eingaben).
     */
    public static LattenParameter leseMitSitzung(Properties sitzung) throws IOException {
        Properties p = ladeDateien(null);
        sitzung.forEach((k, v) -> p.setProperty(k.toString(), v.toString()));
        return aus(p);
    }

    /** Parameter ohne Eingabedatei (Startwerte für das Programmfenster). */
    public static LattenParameter leseStandard() throws IOException {
        return aus(ladeDateien(null));
    }

    private static Properties ladeDateien(Path eingabe) throws IOException {
        Properties p = new Properties();
        Set<Path> gelesen = new LinkedHashSet<>();
        for (Path ordner : programmOrdner()) {
            ladeFallsVorhanden(p, ordner.resolve(Main.PARAMETER_DATEI), gelesen);
        }
        ladeFallsVorhanden(p, Path.of(Main.PARAMETER_DATEI), gelesen);
        if (eingabe != null) {
            Path neben = eingabe.toAbsolutePath().normalize().getParent();
            if (neben != null) {
                ladeFallsVorhanden(p, neben.resolve(Main.PARAMETER_DATEI), gelesen);
            }
        }
        return p;
    }

    /**
     * Parameter nur aus Standardwerten und den angegebenen Schlüssel=Wert-Paaren
     * (ohne Parameterdateien) – für Tests und reproduzierbare Auswertungen.
     */
    static LattenParameter ausWerten(String... schluesselWert) {
        Properties p = new Properties();
        for (String kv : schluesselWert) {
            int i = kv.indexOf('=');
            p.setProperty(kv.substring(0, i).strip().toLowerCase(Locale.ROOT), kv.substring(i + 1).strip());
        }
        return aus(p);
    }

    private static LattenParameter aus(Properties p) {
        LattenParameter s = LattenParameter.standard();
        return new LattenParameter(
                wert(p, s.h(), "h", "reflektorhoehe"),
                wert(p, s.c(), "c"),
                wert(p, s.bezugsbasis(), "bezugsbasis"),
                wert(p, s.sollSpurweite(), "sollspurweite", "spurweite"),
                wert(p, s.toleranz(), "toleranz"),
                jaNein(p.getProperty("achse"), s.achseBerechnen()),
                ganzzahl(wert(p, s.nachkommastellen(), "nachkommastellen", "stellen"), "Nachkommastellen"),
                jaNein(p.getProperty("ueberhoehungsprotokoll"), s.ueberhoehungsProtokoll()),
                p.getProperty("paarbildung") == null ? s.paarbildung()
                        : Paarbildung.Modus.von(p.getProperty("paarbildung")),
                p.getProperty("achscodes") == null ? s.achsCodes()
                        : LattenParameter.codesAus(p.getProperty("achscodes")),
                p.getProperty("trennzeichen") == null ? s.trennung()
                        : EingabeFormat.Trennung.von(p.getProperty("trennzeichen")),
                p.getProperty("aufbau") == null || p.getProperty("aufbau").isBlank() ? s.aufbau()
                        : p.getProperty("aufbau"),
                ganzzahl(wert(p, s.kopfzeilen(), "kopfzeilen"), "Kopfzeilen"),
                wert(p, s.maxUeberhoehung() * 1000, "maxueberhoehung", "grenzwertueberhoehung") / 1000,
                p.getProperty("ausgabeformat") == null ? s.ausgabe()
                        : EingabeFormat.Ausgabe.von(p.getProperty("ausgabeformat")),
                p.getProperty("hdateien") == null ? s.hDateien()
                        : LattenParameter.dateienAus(p.getProperty("hdateien")),
                p.getProperty("hbezug") == null ? s.hBezug()
                        : ReflektorhoeheLeser.Bezug.von(p.getProperty("hbezug")),
                p.getProperty("hspalten", s.hSpalten())).pruefe();
    }

    /** Speichert die Parameter als gleismesslatte.properties im Programmordner. */
    public static Path speichere(LattenParameter lp) throws IOException {
        List<Path> ordner = programmOrdner();
        // erster Eintrag = Hauptordner (neben der .exe bzw. Eclipse-Projekt)
        Path ziel = (ordner.isEmpty() ? Path.of("") : ordner.get(0)).resolve(Main.PARAMETER_DATEI);
        try (Writer w = Files.newBufferedWriter(ziel, StandardCharsets.UTF_8)) {
            w.write(String.format(Locale.ROOT,
                    "# %s – Parameter (Dezimalpunkt oder -komma)%n"
                  + "# Reflektorhöhe über SO, senkrecht zur Latte [m]%n"
                  + "h = %.4f%n"
                  + "# Lattenkonstante: Spurweite = Prismenabstand + c [m]%n"
                  + "c = %.4f%n"
                  + "# Sollspurweite [m]: Bezug der Abweichung und Mitte des Paarbereichs%n"
                  + "sollspurweite = %.4f%n"
                  + "# zwei GML-Punkte sind ein Paar, wenn die Spurweite in Sollspurweite ± Toleranz liegt [m]%n"
                  + "toleranz = %.4f%n"
                  + "# Gleisachspunkt (kleinste Punktnummer + \"Achse\") berechnen: ja | nein%n"
                  + "achse = %s%n"
                  + "# Bezugsbasis der Überhöhung [m]%n"
                  + "bezugsbasis = %.4f%n"
                  + "# Nachkommastellen der Meterwerte in der Ausgabe: 3 oder 4%n"
                  + "nachkommastellen = %d%n"
                  + "# zusätzliches Protokoll zur Überhöhung (<name>_ergebnis_ueberhoehung.txt): ja | nein%n"
                  + "ueberhoehungsprotokoll = %s%n"
                  + "# Paarbildung: folge = nur aufeinanderfolgende GML-Punkte,%n"
                  + "#              frei  = beliebige GML-Punkte (nach bester Übereinstimmung mit der Sollspurweite)%n"
                  + "paarbildung = %s%n"
                  + "# zusätzliche Codes (ohne GML), deren Punkte paarweise zur Gleisachse gerechnet werden –%n"
                  + "# OHNE Korrektur; mehrere Codes mit Komma oder Semikolon trennen, z. B. SOL,SOR%n"
                  + "achscodes = %s%n"
                  + "# Eingabedatei – Trennzeichen: auto | semikolon | leerzeichen%n"
                  + "trennzeichen = %s%n"
                  + "# Spaltenfolge: P=Punktnummer R=Rechtswert H=Hochwert Z=Höhe C=Code -=überspringen%n"
                  + "aufbau = %s%n"
                  + "# Anzahl Kopfzeilen, die übersprungen werden (0 bis 5)%n"
                  + "kopfzeilen = %d%n"
                  + "# Grenzwert der Überhöhung [mm] – EBO § 6 Abs. 3: höchstens 180 mm%n"
                  + "maxueberhoehung = %.0f%n"
                  + "# Ergebnis-Punktliste: eingabe = gleicher Aufbau/Trennzeichen/Kopfzeilen wie die Eingabe,%n"
                  + "#                      status  = CSV Punkt;Code;R;H;Z;Status%n"
                  + "ausgabeformat = %s%n"
                  + "# Messdateien mit Reflektorhöhe je Punkt (GSI, XML, Text), mit ; getrennt – leer = Feldwert h%n"
                  + "hdateien = %s%n"
                  + "# Höhen der Punktliste: prisma = Prismenmitte (hr = 0 im Instrument),%n"
                  + "#                       reduziert = im Instrument um die Reflektorhöhe lotrecht reduziert%n"
                  + "hbezug = %s%n"
                  + "# gewählte Spalten je Text-Messdatei (wird über \"Höhe suchen\" gesetzt)%n"
                  + "hspalten = %s%n",
                    Version.textMitHersteller(), lp.h(), lp.c(), lp.sollSpurweite(), lp.toleranz(),
                    lp.achseBerechnen() ? "ja" : "nein", lp.bezugsbasis(), lp.nachkommastellen(),
                    lp.ueberhoehungsProtokoll() ? "ja" : "nein", lp.paarbildung().schluessel,
                    lp.achsCodesText(), lp.trennung().schluessel, lp.aufbau(), lp.kopfzeilen(),
                    lp.maxUeberhoehung() * 1000, lp.ausgabe().schluessel,
                    lp.hDateienText().replace("\\", "\\\\"), lp.hBezug().schluessel,
                    lp.hSpalten().replace("\\", "\\\\")));
        }
        return ziel.toAbsolutePath().normalize();
    }

    /**
     * Programmordner, Hauptordner zuerst. Beim Starter liegen .exe und .jar
     * zusammen. Bei jpackage liegt die .jar in "app" und die .exe eine Ebene
     * höher, in Eclipse liegen die Klassen in "bin" unter dem Projektordner.
     */
    static List<Path> programmOrdner() {
        List<Path> liste = new ArrayList<>();
        try {
            Path ort = Path.of(Parameter.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path ordner = Files.isDirectory(ort) ? ort : ort.getParent();
            if (ordner != null) {
                String name = ordner.getFileName() == null ? "" : ordner.getFileName().toString();
                if ((name.equalsIgnoreCase("app") || name.equalsIgnoreCase("bin")) && ordner.getParent() != null) {
                    liste.add(ordner.getParent());
                }
                liste.add(ordner);
            }
        } catch (URISyntaxException | RuntimeException e) {
            // Programmordner nicht ermittelbar – ignorieren
        }
        return liste;
    }

    private static void ladeFallsVorhanden(Properties p, Path datei, Set<Path> gelesen) throws IOException {
        datei = datei.toAbsolutePath().normalize();
        if (Files.isRegularFile(datei) && gelesen.add(datei)) {
            try (Reader r = Files.newBufferedReader(datei, StandardCharsets.UTF_8)) {
                Properties neu = new Properties();
                neu.load(r);
                neu.forEach((k, v) -> p.setProperty(k.toString().strip().toLowerCase(Locale.ROOT), v.toString()));
            }
            System.out.println("Parameter gelesen aus " + datei);
        }
    }

    private static double wert(Properties p, double standard, String... keys) {
        for (String key : keys) {
            String v = p.getProperty(key);
            if (v != null && !v.isBlank()) {
                return zahl(v);
            }
        }
        return standard;
    }

    private static int ganzzahl(double v, String name) {
        if (v != Math.rint(v)) {
            throw new IllegalArgumentException(name + ": ganze Zahl erwartet, nicht " + v);
        }
        return (int) v;
    }

    static double zahl(String s) {
        return Double.parseDouble(s.strip().replace(',', '.'));
    }

    static boolean jaNein(String v, boolean standard) {
        if (v == null || v.isBlank()) {
            return standard;
        }
        return switch (v.strip().toLowerCase(Locale.ROOT)) {
            case "ja", "j", "yes", "y", "true", "1", "an", "ein" -> true;
            case "nein", "n", "no", "false", "0", "aus" -> false;
            default -> throw new IllegalArgumentException("achse: 'ja' oder 'nein' erwartet, nicht '" + v + "'");
        };
    }
}
