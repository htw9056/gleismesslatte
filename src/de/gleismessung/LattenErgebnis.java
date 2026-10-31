package de.gleismessung;

/**
 * Ergebnis einer Lattenlage (ein GML-Prismenpaar) oder eines Achs-Code-Paares.
 *
 * @param messpunkt        Bezeichnung / Station
 * @param f1               korrigierter Punkt auf Schienenoberkante, Schiene 1
 * @param f2               korrigierter Punkt auf Schienenoberkante, Schiene 2
 * @param achse            Gleismitte (Mittelpunkt von f1 und f2)
 * @param prismenabstand   3D-Abstand der Prismen s [m]
 * @param spurweite        Ist-Spurweite S = s + c [m]
 * @param spurfehler       S - Soll [m]
 * @param neigungGon       Querneigung der Latte [gon], positiv: Schiene 2 höher
 * @param ueberhoehung     Überhöhung u = Bezugsbasis * sin(alpha) [m], positiv: Schiene 2 höher
 * @param zTiefereSchiene  Höhe SO der tieferen Schiene (Bogeninnenseite) [m]
 * @param riwiQuer         Richtungswinkel der Lattenquerrichtung 1 -> 2 [gon]
 * @param korrigiert       true = GML-Paar (auf SO korrigiert), false = Achs-Code-Paar (unverändert)
 * @param h                verwendete Reflektorhöhe [m] (NaN bei Achs-Code-Paaren)
 */
public record LattenErgebnis(
        String messpunkt,
        Point3D f1,
        Point3D f2,
        Point3D achse,
        double prismenabstand,
        double spurweite,
        double spurfehler,
        double neigungGon,
        double ueberhoehung,
        double zTiefereSchiene,
        double riwiQuer,
        boolean korrigiert,
        double h) {

    /** Art für die Ausgabe: "GML" oder "Code". */
    public String art() {
        return korrigiert ? "GML" : "Code";
    }
}
