package de.gleismessung;

/**
 * Auswertung einer Gleismesslatte mit zwei starr montierten Prismen.
 *
 * <p>Die Prismenstäbe stehen senkrecht zur Latte. Bei Überhöhung kippen beide
 * Prismen um den Querneigungswinkel alpha mit. Für beide Prismen gilt deshalb
 * derselbe Korrekturvektor:</p>
 *
 * <pre>
 *   y_F = y_P + h * sin(alpha) * e_y
 *   x_F = x_P + h * sin(alpha) * e_x
 *   z_F = z_P - h * cos(alpha)
 * </pre>
 *
 * <p>e = horizontaler Einheitsvektor quer zum Gleis von Prisma 1 nach Prisma 2,
 * sin(alpha) = dz / s (vorzeichenbehaftet). Der Lageanteil zeigt damit immer
 * zur tieferen Schiene.</p>
 *
 * <p>Annahme: Die Prismen sitzen symmetrisch zu den Spurmesspunkten, so dass der
 * Mittelpunkt der korrigierten Punkte die Gleismitte ist. Eine Längsneigung der
 * Latte (in Gleisrichtung) ist aus zwei Punkten nicht bestimmbar und bleibt
 * unberücksichtigt.</p>
 */
public final class GleismesslatteRechner {

    private static final double RHO_GON = 200.0 / Math.PI;

    /** Punktnummer der Gleisachse = kleinste Punktnummer + dieser Suffix. */
    public static final String ACHSE_SUFFIX = "Achse";
    public static final String ACHSE_CODE = "Achse";

    private final LattenParameter p;

    public GleismesslatteRechner(LattenParameter parameter) {
        this.p = parameter;
    }

    /** GML-Paar: Prismen auf SO korrigieren, Spurweite mit Lattenkonstante c. */
    public LattenErgebnis berechne(String messpunkt, Point3D p1, Point3D p2) {
        return berechne(messpunkt, p1, p2, true);
    }

    /**
     * @param korrektur true = GML-Paar (Reflektorhöhe/Überhöhung korrigieren, GML aus Code
     *                  entfernen, Spurweite = s + c); false = Achs-Code-Paar (Punkte und Codes
     *                  unverändert, Spurweite = s)
     */
    public LattenErgebnis berechne(String messpunkt, Point3D p1, Point3D p2, boolean korrektur) {
        return berechne(messpunkt, p1, p2, korrektur, p.h());
    }

    /**
     * Wie oben, mit eigener Reflektorhöhe h für dieses Paar (z. B. aus der Messdatei).
     * Die Koordinaten müssen sich auf die Prismenmitte beziehen.
     */
    public LattenErgebnis berechne(String messpunkt, Point3D p1, Point3D p2, boolean korrektur, double h) {
        double dy = p2.y() - p1.y();
        double dx = p2.x() - p1.x();
        double dz = p2.z() - p1.z();

        double d = Math.hypot(dy, dx);           // Horizontalstrecke
        double s = Math.hypot(d, dz);            // Schrägstrecke = Abstand entlang der Latte
        if (d < 1e-6) {
            throw new IllegalArgumentException(
                    messpunkt + ": Punkte des Paares liegen lotrecht übereinander – Messung ungültig.");
        }

        double sinA = dz / s;
        double cosA = d / s;
        double ey = dy / d;
        double ex = dx / d;

        Point3D f1;
        Point3D f2;
        if (korrektur) {
            // Korrekturvektor F = P - h * n mit n = (-sinA*e, cosA)
            double ky = h * sinA * ey;
            double kx = h * sinA * ex;
            double kz = -h * cosA;
            // korrigierte Punkte: GML-Kennung aus dem Code entfernen
            f1 = new Point3D(p1.id(), GmlCodierung.entferneKennung(p1.code()),
                    p1.y() + ky, p1.x() + kx, p1.z() + kz);
            f2 = new Point3D(p2.id(), GmlCodierung.entferneKennung(p2.code()),
                    p2.y() + ky, p2.x() + kx, p2.z() + kz);
        } else {
            // Achs-Code-Paar: keine Korrektur, Punkte bleiben wie gemessen
            f1 = p1;
            f2 = p2;
        }
        // Gleisachse: kleinste Punktnummer des Paares + Suffix "Achse"
        Point3D achse = f1.mitte(f2, kleinereNummer(p1.id(), p2.id()) + ACHSE_SUFFIX, ACHSE_CODE);

        double spurweite = korrektur ? s + p.c() : s;
        double ueberhoehung = p.bezugsbasis() * sinA;
        double zTief = Math.min(f1.z(), f2.z());

        double riwi = Math.atan2(dy, dx) * RHO_GON;
        if (riwi < 0) {
            riwi += 400.0;
        }

        return new LattenErgebnis(
                messpunkt, f1, f2, achse,
                s, spurweite, spurweite - p.sollSpurweite(),
                Math.asin(sinA) * RHO_GON,
                ueberhoehung, zTief, riwi, korrektur, korrektur ? h : Double.NaN);
    }

    /**
     * Kleinere der beiden Punktnummern in natürlicher Sortierung:
     * Ziffernfolgen werden als Zahl verglichen (99 vor 100, A5 vor A12),
     * der Rest alphabetisch ohne Groß-/Kleinschreibung.
     */
    static String kleinereNummer(String a, String b) {
        return natuerlich(a.strip(), b.strip()) <= 0 ? a : b;
    }

    static int natuerlich(String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i);
            char cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int si = i;
                int sj = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) {
                    i++;
                }
                while (j < b.length() && Character.isDigit(b.charAt(j))) {
                    j++;
                }
                int r = new java.math.BigInteger(a.substring(si, i)).compareTo(new java.math.BigInteger(b.substring(sj, j)));
                if (r != 0) {
                    return r;
                }
            } else {
                int r = Character.compare(Character.toLowerCase(ca), Character.toLowerCase(cb));
                if (r != 0) {
                    return r;
                }
                i++;
                j++;
            }
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }
}
