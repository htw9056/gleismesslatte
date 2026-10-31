package de.gleismessung;

/**
 * Programmversion und Hersteller. Bei jeder Änderung um 0.01 hochzählen (0.01, 0.02, 0.03 ...).
 */
public final class Version {

    public static final String NAME = "Gleismesslatte";
    public static final String NUMMER = "0.16";
    public static final String HERSTELLER = "Dipl.-Ing. (FH) Stefan Klein";

    private Version() {
    }

    /** z. B. "Gleismesslatte V0.12" */
    public static String text() {
        return NAME + " V" + NUMMER;
    }

    /** z. B. "© Dipl.-Ing. (FH) Stefan Klein" */
    public static String copyright() {
        return "© " + HERSTELLER;
    }

    /** z. B. "Gleismesslatte V0.12 © Dipl.-Ing. (FH) Stefan Klein" */
    public static String textMitHersteller() {
        return text() + " " + copyright();
    }
}
