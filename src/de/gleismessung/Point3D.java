package de.gleismessung;

import java.util.Locale;

/**
 * 3D-Punkt im Landessystem: y = Rechtswert (Ost), x = Hochwert (Nord), z = Höhe [m].
 *
 * @param id   Punktnummer
 * @param code Codierung aus dem Messgerät (z. B. "GML", "SO_GML", "GML_LI")
 */
public record Point3D(String id, String code, double y, double x, double z) {

    public Point3D mitte(Point3D o, String neueId, String neuerCode) {
        return new Point3D(neueId, neuerCode, (y + o.y) / 2.0, (x + o.x) / 2.0, (z + o.z) / 2.0);
    }

    public double schraegstrecke(Point3D o) {
        double dy = o.y - y;
        double dx = o.x - x;
        double dz = o.z - z;
        return Math.sqrt(dy * dy + dx * dx + dz * dz);
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%s [%s] (y=%.4f, x=%.4f, z=%.4f)", id, code, y, x, z);
    }
}
