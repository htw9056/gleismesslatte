package de.gleismessung;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Erkennt Gleismesslatten-Punkte an der Codierung und entfernt die Kennung
 * nach der Bearbeitung.
 *
 * <p>Ein Punkt gehört zur Gleismesslatte, wenn sein Code mit "GML" beginnt
 * (Präfix, z. B. "GML", "GML_LI", "GML01") oder endet (Suffix, z. B. "SO_GML",
 * "LI-GML"). Groß-/Kleinschreibung und umgebende Leerzeichen werden ignoriert.</p>
 *
 * <p>Nach der Korrektur wird die Kennung samt angrenzendem Trennzeichen
 * (_ - . / Leerzeichen) aus dem Code gelöscht: "LI_GML" → "LI", "GML-SO" → "SO",
 * "GML01" → "01", "GML" → "".</p>
 */
public final class GmlCodierung {

    public static final String KENNUNG = "GML";

    private static final String TRENNER = "[_\\-./ ]*";
    private static final Pattern PRAEFIX = Pattern.compile("^" + KENNUNG + TRENNER, Pattern.CASE_INSENSITIVE);
    private static final Pattern SUFFIX = Pattern.compile(TRENNER + KENNUNG + "$", Pattern.CASE_INSENSITIVE);

    private GmlCodierung() {
    }

    public static boolean istGleismesspunkt(String code) {
        if (code == null) {
            return false;
        }
        String c = code.strip().toUpperCase(Locale.ROOT);
        return c.startsWith(KENNUNG) || c.endsWith(KENNUNG);
    }

    /** Entfernt GML-Präfix und/oder -Suffix samt Trennzeichen aus dem Code. */
    public static String entferneKennung(String code) {
        if (code == null) {
            return "";
        }
        String c = code.strip();
        c = PRAEFIX.matcher(c).replaceFirst("");
        c = SUFFIX.matcher(c).replaceFirst("");
        return c.strip();
    }
}
