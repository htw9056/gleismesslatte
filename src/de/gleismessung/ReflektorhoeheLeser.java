package de.gleismessung;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Liest die beim Messen im Instrument eingetragene Reflektorhöhe (Zielhöhe) je Punkt
 * aus einer oder mehreren Messdateien. Das Format wird automatisch erkannt:
 *
 * <ul>
 *   <li><b>Leica GSI-8 / GSI-16</b>: Wortindex 11 = Punktnummer, 87 = Reflektorhöhe
 *       (Einheit aus der Wortinfo: mm, 1/10 mm, 1/100 mm, ft). WI 88 (Instrumentenhöhe)
 *       wird nicht verwendet.</li>
 *   <li><b>XML</b>: Trimble JobXML (PointRecord/TargetID → TargetRecord/TargetHeight),
 *       LandXML (RawObservation@targetHeight + TargetPoint@pntRef) sowie allgemein jedes
 *       Element mit Attribut oder Unterelement targetHeight / reflectorHeight / prismHeight
 *       und einer Punktbezeichnung (pntRef, name, Name, PointID …).</li>
 *   <li><b>Text / Tabelle</b> (Exporte ohne feste Begriffe): Punktnummer- und Höhenspalte
 *       werden über {@link SpaltenAnalyse} vorgeschlagen bzw. vom Benutzer gewählt.</li>
 * </ul>
 *
 * Gültig sind Werte von 0 bis {@value #MAX} m. 0 bedeutet „im Instrument keine Höhe
 * eingetragen“; Werte darüber (z. B. Lotstab 1,500 m) gehören zu anderen Zielen und werden
 * ignoriert.
 */
final class ReflektorhoeheLeser {

    /** Größte zulässige Reflektorhöhe der Gleismesslatte [m]. */
    static final double MAX = 0.5;

    /** Bezug der Höhen in der Punktliste. */
    enum Bezug {
        PRISMA("prisma", "Prismenmitte (hr = 0 im Instrument)"),
        REDUZIERT("reduziert", "um Reflektorhöhe lotrecht reduziert");

        final String schluessel;
        final String text;

        Bezug(String schluessel, String text) {
            this.schluessel = schluessel;
            this.text = text;
        }

        static Bezug von(String s) {
            if (s == null || s.isBlank()) {
                return PRISMA;
            }
            return switch (s.strip().toLowerCase(Locale.ROOT)) {
                case "prisma", "prismenmitte", "prismamitte" -> PRISMA;
                case "reduziert", "boden", "bodenpunkt", "lotrecht" -> REDUZIERT;
                default -> throw new IllegalArgumentException(
                        "hbezug: 'prisma' oder 'reduziert' erwartet, nicht '" + s + "'");
            };
        }

        @Override
        public String toString() {
            return text;
        }
    }

    /** Ergebnis aller gelesenen Dateien. */
    static final class Ergebnis {
        /** Reflektorhöhe je Punkt (Schlüssel normiert), 0 ≤ h ≤ MAX. */
        final Map<String, Double> proPunkt = new HashMap<>();
        /** Häufigkeit der Werte &gt; 0 (auf 0,1 mm gerundet). */
        final TreeMap<Double, Integer> verteilung = new TreeMap<>();
        /** Zeilen für das Protokoll, je Datei. */
        final List<String> dateiInfo = new ArrayList<>();
        int ausserhalb;
        int null0;

        Double hoehe(String punkt) {
            return proPunkt.get(normiere(punkt));
        }

        /** Häufigster Wert &gt; 0 oder null. */
        Double haeufigster() {
            return verteilung.entrySet().stream().max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey).orElse(null);
        }

        String verteilungText() {
            if (verteilung.isEmpty()) {
                return "keine Werte zwischen 0 und " + fmt(MAX) + " m";
            }
            StringBuilder sb = new StringBuilder();
            verteilung.descendingMap().entrySet().stream()
                    .sorted(Map.Entry.<Double, Integer>comparingByValue().reversed())
                    .forEach(e -> sb.append(sb.length() > 0 ? ", " : "").append(fmt(e.getKey()))
                            .append(" m (").append(e.getValue()).append("×)"));
            return sb.toString();
        }

        private void merke(String punkt, double h) {
            double r = Math.round(h * 10000) / 10000.0;
            if (punkt != null && !punkt.isBlank()) {
                proPunkt.put(normiere(punkt), r);
            }
            if (r > 0) {
                verteilung.merge(r, 1, Integer::sum);
            } else {
                null0++;
            }
        }
    }

    private ReflektorhoeheLeser() {
    }

    /** Punktnummern vergleichbar machen: Leerzeichen weg, Großbuchstaben, führende Nullen weg. */
    static String normiere(String punkt) {
        String p = punkt.strip().toUpperCase(Locale.ROOT);
        int i = 0;
        while (i < p.length() - 1 && p.charAt(i) == '0') {
            i++;
        }
        return p.substring(i);
    }

    static String fmt(double v) {
        return String.format(Locale.GERMANY, "%.3f", v).replaceFirst("(,\\d{3})0+$", "$1");
    }

    /** Dateiformat einer Messdatei. */
    enum Format { GSI, XML, TEXT }

    static Format format(byte[] roh) {
        String text = alsText(roh);
        String anfang = text.stripLeading();
        if (anfang.startsWith("\uFEFF")) {
            anfang = anfang.substring(1).stripLeading();
        }
        if (anfang.startsWith("<")) {
            return Format.XML;
        }
        return istGsi(text) ? Format.GSI : Format.TEXT;
    }

    static String text(Path datei) throws IOException {
        return alsText(Files.readAllBytes(datei));
    }

    /** Schlüssel einer Datei für die gespeicherte Spaltenwahl. */
    static String schluessel(Path datei) {
        return datei.toAbsolutePath().normalize().toString();
    }

    /** Liest alle Dateien ohne gespeicherte Spaltenwahl (Textdateien automatisch). */
    static Ergebnis lies(List<Path> dateien, Set<String> bekanntePunkte) throws IOException {
        return lies(dateien, bekanntePunkte, Set.of(), Map.of());
    }

    /**
     * Liest alle Dateien.
     *
     * @param bekanntePunkte Punktnummern der Punktliste, darf leer sein
     * @param gmlPunkte      Punktnummern der GML-Punkte (für den Spaltenvorschlag), darf leer sein
     * @param spalten        gespeicherte Spaltenwahl je Datei (Schlüssel siehe {@link #schluessel})
     */
    static Ergebnis lies(List<Path> dateien, Set<String> bekanntePunkte, Set<String> gmlPunkte,
                         Map<String, String> spalten) throws IOException {
        Set<String> bekannt = SpaltenAnalyse.normiert(bekanntePunkte);
        Set<String> gml = SpaltenAnalyse.normiert(gmlPunkte);
        Ergebnis erg = new Ergebnis();
        for (Path d : dateien) {
            if (!Files.isRegularFile(d)) {
                erg.dateiInfo.add(d.getFileName() + ": nicht gefunden");
                continue;
            }
            byte[] roh = Files.readAllBytes(d);
            String text = alsText(roh);
            int vorher = erg.proPunkt.size();
            int vorherWerte = summe(erg);
            String format;
            switch (format(roh)) {
                case XML -> format = liesXml(roh, erg);
                case GSI -> {
                    format = "Leica GSI";
                    liesGsi(text, erg);
                }
                default -> format = liesTabelle(text, bekannt, gml,
                        SpaltenAnalyse.Wahl.aus(spalten.get(schluessel(d))), erg);
            }
            erg.dateiInfo.add(String.format(Locale.ROOT, "%s: %s, %d Werte, %d Punkte zugeordnet",
                    d.getFileName(), format, summe(erg) - vorherWerte, erg.proPunkt.size() - vorher));
        }
        return erg;
    }

    private static int summe(Ergebnis e) {
        int s = e.null0 + e.ausserhalb;
        for (int n : e.verteilung.values()) {
            s += n;
        }
        return s;
    }

    private static String alsText(byte[] roh) {
        try {
            return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(roh)).toString();
        } catch (java.nio.charset.CharacterCodingException e) {
            return new String(roh, Charset.forName("windows-1252"));
        }
    }

    /** Prüft einen Wert und legt ihn ab. */
    private static void wert(Ergebnis erg, String punkt, double h) {
        if (Double.isNaN(h) || h < 0 || h > MAX + 1e-9) {
            erg.ausserhalb++;
            return;
        }
        erg.merke(punkt, h);
    }

    // ------------------------------------------------------------------ GSI

    private static final Pattern GSI_WORT = Pattern.compile("^\\*?\\d{2}[0-9.]{4}[+-]\\S+");

    static boolean istGsi(String text) {
        int treffer = 0;
        int zeilen = 0;
        for (String z : text.split("\\R")) {
            String t = z.strip();
            if (t.isEmpty()) {
                continue;
            }
            zeilen++;
            if (GSI_WORT.matcher(t).find() && (t.startsWith("*11") || t.startsWith("11")
                    || t.startsWith("*41") || t.startsWith("41"))) {
                treffer++;
            }
            if (zeilen >= 20) {
                break;
            }
        }
        return treffer > 0 && treffer * 2 >= zeilen;
    }

    private static void liesGsi(String text, Ergebnis erg) {
        Double aktuell = null;                       // zuletzt gesehene Reflektorhöhe
        for (String z : text.split("\\R")) {
            String t = z.strip();
            if (t.startsWith("*")) {
                t = t.substring(1);
            }
            if (t.isEmpty()) {
                continue;
            }
            String punkt = null;
            Double hr = null;
            boolean station = false;
            boolean messung = false;
            for (String w : t.split("\\s+")) {
                if (w.length() < 8 || !Character.isDigit(w.charAt(0)) || !Character.isDigit(w.charAt(1))) {
                    continue;
                }
                int wi = Integer.parseInt(w.substring(0, 2));
                char einheit = w.charAt(5);
                char vorzeichen = w.charAt(6);
                String daten = w.substring(7);
                switch (wi) {
                    case 11 -> punkt = daten.replaceFirst("^0+(?=.)", "");
                    case 87 -> hr = gsiZahl(daten, einheit, vorzeichen);
                    case 84, 85, 86, 88 -> station = true;
                    case 21, 22, 31, 32, 33, 81, 82, 83 -> messung = true;
                    default -> { }
                }
            }
            if (hr != null) {
                aktuell = hr;
                wert(erg, punkt, hr);
            } else if (aktuell != null && punkt != null && messung && !station) {
                wert(erg, punkt, aktuell);
            }
        }
    }

    private static Double gsiZahl(String daten, char einheit, char vorzeichen) {
        try {
            double faktor = switch (einheit) {
                case '0' -> 0.001;              // mm
                case '1' -> 0.001 * 0.3048;     // 1/1000 ft
                case '6' -> 0.0001;             // 1/10 mm
                case '7' -> 0.0001 * 0.3048;    // 1/10000 ft
                case '8' -> 0.00001;            // 1/100 mm
                default -> 0.001;
            };
            double v = Long.parseLong(daten) * faktor;
            return vorzeichen == '-' ? -v : v;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    // ------------------------------------------------------------------ XML

    private static final Set<String> HOEHE = Set.of("targetheight", "reflectorheight", "prismheight",
            "reflektorhoehe", "zielhoehe", "prismenhoehe", "targethgt", "tgtht", "antennaheight_prism");
    private static final List<String> NAME_ATTR = List.of("pntRef", "pointRef", "targetPointRef", "pointName",
            "PointName", "pointID", "PointID", "name", "Name");
    private static final List<String> NAME_ELEM = List.of("Name", "PointName", "PointID", "pntRef");

    private static String liesXml(byte[] roh, Ergebnis erg) throws IOException {
        Document doc;
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setExpandEntityReferences(false);
            f.setNamespaceAware(false);
            DocumentBuilder b = f.newDocumentBuilder();
            b.setErrorHandler(null);
            doc = b.parse(new ByteArrayInputStream(roh));
        } catch (Exception e) {
            throw new IOException("XML nicht lesbar: " + e.getMessage(), e);
        }
        String wurzel = doc.getDocumentElement().getTagName();

        // Trimble JobXML: TargetRecord(ID) -> TargetHeight, PointRecord(Name, TargetID)
        NodeList targets = doc.getElementsByTagName("TargetRecord");
        if (targets.getLength() > 0) {
            Map<String, Double> ziel = new HashMap<>();
            for (int i = 0; i < targets.getLength(); i++) {
                Element t = (Element) targets.item(i);
                String th = kindText(t, "TargetHeight");
                if (th != null) {
                    ziel.put(t.getAttribute("ID"), zahl(th));
                }
            }
            NodeList pr = doc.getElementsByTagName("PointRecord");
            for (int i = 0; i < pr.getLength(); i++) {
                Element p = (Element) pr.item(i);
                if ("true".equalsIgnoreCase(kindText(p, "Deleted"))) {
                    continue;
                }
                String name = kindText(p, "Name");
                String tid = kindText(p, "TargetID");
                if (name != null && tid != null && ziel.containsKey(tid)) {
                    wert(erg, name, ziel.get(tid));
                }
            }
            return "Trimble JobXML";
        }

        // allgemein (LandXML, Leica-XML u. a.)
        allgemein(doc.getDocumentElement(), erg);
        return wurzel.toLowerCase(Locale.ROOT).contains("landxml") ? "LandXML" : "XML (" + wurzel + ")";
    }

    private static void allgemein(Element e, Ergebnis erg) {
        Double h = null;
        NamedNodeMap at = e.getAttributes();
        for (int i = 0; i < at.getLength(); i++) {
            Node a = at.item(i);
            if (HOEHE.contains(schluessel(a.getNodeName()))) {
                h = zahl(a.getNodeValue());
            }
        }
        if (h == null) {
            for (Node k = e.getFirstChild(); k != null; k = k.getNextSibling()) {
                if (k instanceof Element ke && HOEHE.contains(schluessel(ke.getTagName()))
                        && ke.getElementsByTagName("*").getLength() == 0) {
                    h = zahl(ke.getTextContent());
                }
            }
        }
        if (h != null) {
            wert(erg, punktName(e), h);
        }
        for (Node k = e.getFirstChild(); k != null; k = k.getNextSibling()) {
            if (k instanceof Element ke && !HOEHE.contains(schluessel(ke.getTagName()))) {
                allgemein(ke, erg);
            }
        }
    }

    private static String punktName(Element e) {
        for (String a : NAME_ATTR) {
            if (e.hasAttribute(a) && !e.getAttribute(a).isBlank()) {
                return e.getAttribute(a);
            }
        }
        for (String n : NAME_ELEM) {
            String t = kindText(e, n);
            if (t != null && !t.isBlank()) {
                return t;
            }
        }
        // Unterelemente (z. B. LandXML TargetPoint pntRef)
        NodeList alle = e.getElementsByTagName("*");
        for (int i = 0; i < alle.getLength(); i++) {
            Element k = (Element) alle.item(i);
            for (String a : NAME_ATTR) {
                if (k.hasAttribute(a) && !k.getAttribute(a).isBlank()) {
                    return k.getAttribute(a);
                }
            }
        }
        return null;
    }

    private static String kindText(Element e, String name) {
        for (Node k = e.getFirstChild(); k != null; k = k.getNextSibling()) {
            if (k instanceof Element ke && ke.getTagName().equals(name)) {
                return ke.getTextContent().strip();
            }
        }
        return null;
    }

    private static String schluessel(String name) {
        String n = name.contains(":") ? name.substring(name.indexOf(':') + 1) : name;
        return n.toLowerCase(Locale.ROOT).replace("ö", "oe").replace("_", "").replace("-", "");
    }

    private static double zahl(String s) {
        try {
            return Double.parseDouble(s.strip().replace(',', '.'));
        } catch (NumberFormatException | NullPointerException e) {
            return Double.NaN;
        }
    }

    // ----------------------------------------------------------------- Text

    private static String liesTabelle(String text, Set<String> bekannt, Set<String> gml,
                                      SpaltenAnalyse.Wahl gewaehlt, Ergebnis erg) {
        SpaltenAnalyse.Tabelle t = SpaltenAnalyse.zerlege(text);
        SpaltenAnalyse.Wahl w = gewaehlt;
        String herkunft = "gewählt";
        if (w == null) {
            w = SpaltenAnalyse.automatisch(t, bekannt, gml);
            herkunft = "automatisch";
        }
        if (w == null) {
            return "Text (" + t.trenner() + "), keine Spalte mit Reflektorhöhe 0–0,5 m gefunden";
        }
        for (String[] z : t.zeilen()) {
            String p = SpaltenAnalyse.feld(z, w.punkt());
            Double v = SpaltenAnalyse.zahl(SpaltenAnalyse.feld(z, w.hoehe()));
            if (p.isEmpty() || v == null) {
                continue;
            }
            if (!bekannt.isEmpty() && !bekannt.contains(normiere(p))) {
                continue;                           // Kopfzeilen, Stationen u. ä.
            }
            wert(erg, p, v * w.faktor());
        }
        return String.format(Locale.GERMANY, "Text (%s), Punktnummer Spalte %d, Reflektorhöhe Spalte %d%s (%s)",
                t.trenner(), w.punkt(), w.hoehe(), w.faktor() < 0.5 ? " in mm" : "", herkunft);
    }

    /** Für Tests und das Protokoll: Werte je Punkt in Dateireihenfolge. */
    static Map<String, Double> alsListe(Ergebnis e) {
        return new LinkedHashMap<>(e.proPunkt);
    }
}
