package de.gleismessung;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Bildet Paare aus einer Punktgruppe: GML-Punkte (Lattenlagen) oder Punkte mit
 * Achs-Codes. Beide Gruppen werden getrennt gepaart.
 *
 * <ul>
 *   <li>{@link Modus#FOLGE}: zwei GML-Punkte, die in der Datei direkt aufeinander
 *       folgen (andere Punkte dazwischen stören nicht), bilden ein Paar.</li>
 *   <li>{@link Modus#FREI}: alle GML-Punkte werden gegeneinander geprüft. Mögliche
 *       Paare sind alle Kombinationen, deren Spurweite in Sollspurweite ± Toleranz
 *       liegt und deren Höhenunterschied plausibel ist. Vergeben wird nach kleinster
 *       Abweichung von der Sollspurweite zuerst; jeder Punkt höchstens einmal.</li>
 * </ul>
 *
 * Innerhalb eines Paares ist Punkt 1 der in der Datei frühere Punkt. Die Paare
 * werden nach dem früheren Punkt sortiert zurückgegeben (Reihenfolge der Lattenlagen).
 */
final class Paarbildung {

    enum Modus {
        FOLGE("folge", "aufeinanderfolgend"),
        FREI("frei", "frei (beliebige Reihenfolge)");

        final String schluessel;
        final String text;

        Modus(String schluessel, String text) {
            this.schluessel = schluessel;
            this.text = text;
        }

        static Modus von(String s) {
            if (s == null || s.isBlank()) {
                return FOLGE;
            }
            String v = s.strip().toLowerCase(Locale.ROOT);
            return switch (v) {
                case "folge", "aufeinanderfolgend", "fortlaufend", "reihenfolge" -> FOLGE;
                case "frei", "beliebig", "alle" -> FREI;
                default -> throw new IllegalArgumentException(
                        "paarbildung: 'folge' oder 'frei' erwartet, nicht '" + s + "'");
            };
        }

        @Override
        public String toString() {
            return text;
        }
    }

    /** Größter plausibler Höhenunterschied der Prismen eines Paares [m] (≈ 250 mm Überhöhung). */
    static final double MAX_DZ = 0.25;

    /** Paar aus zwei Indizes in der Punktliste, i &lt; j. */
    record Paar(int i, int j) {
    }

    private Paarbildung() {
    }

    /** Paare der GML-Punkte (Spurweite = Abstand + Lattenkonstante c). */
    static List<Paar> bilde(List<Point3D> punkte, LattenParameter lp, Consumer<String> warnung) {
        return bilde(punkte, lp, warnung, p -> GmlCodierung.istGleismesspunkt(p.code()), lp.c(), "GML-Punkt");
    }

    /** Paare der Punkte mit Achs-Codes (Spurweite = Abstand, ohne c). */
    static List<Paar> bildeAchsCodes(List<Point3D> punkte, LattenParameter lp, Consumer<String> warnung) {
        if (lp.achsCodes().isEmpty()) {
            return new ArrayList<>();
        }
        return bilde(punkte, lp, warnung,
                p -> !GmlCodierung.istGleismesspunkt(p.code()) && lp.istAchsCode(p.code()), 0.0, "Achs-Code-Punkt");
    }

    private static List<Paar> bilde(List<Point3D> punkte, LattenParameter lp, Consumer<String> warnung,
                                    Predicate<Point3D> gruppe, double c, String bezeichnung) {
        List<Integer> gml = new ArrayList<>();
        for (int k = 0; k < punkte.size(); k++) {
            if (gruppe.test(punkte.get(k))) {
                gml.add(k);
            }
        }
        List<Paar> paare = lp.paarbildung() == Modus.FREI
                ? frei(punkte, gml, lp, c, warnung)
                : folge(punkte, gml, lp, c, warnung);

        // GML-Punkte ohne Partner melden
        boolean[] belegt = new boolean[punkte.size()];
        for (Paar p : paare) {
            belegt[p.i()] = true;
            belegt[p.j()] = true;
        }
        for (int k : gml) {
            if (!belegt[k]) {
                warnung.accept(bezeichnung + " " + punkte.get(k).id() + " ohne Partner – "
                        + (bezeichnung.startsWith("GML") ? "nicht korrigiert." : "keine Achse."));
            }
        }
        paare.sort(Comparator.comparingInt(Paar::i));
        return paare;
    }

    private static boolean passt(LattenParameter lp, double s, double c) {
        return Math.abs(s + c - lp.sollSpurweite()) <= lp.toleranz();
    }

    private static List<Paar> folge(List<Point3D> punkte, List<Integer> gml, LattenParameter lp, double c,
                                    Consumer<String> warnung) {
        List<Paar> paare = new ArrayList<>();
        int offen = -1;
        for (int k : gml) {
            if (offen >= 0) {
                Point3D a = punkte.get(offen);
                Point3D b = punkte.get(k);
                double s = a.schraegstrecke(b);
                if (passt(lp, s, c)) {
                    paare.add(new Paar(offen, k));
                    offen = -1;
                    continue;
                }
                warnung.accept(String.format(Locale.ROOT,
                        "%s und %s nicht gepaart (Spurweite %.3f m ausserhalb %.3f-%.3f m)",
                        a.id(), b.id(), s + c, lp.paarMin(), lp.paarMax()));
            }
            offen = k;
        }
        return paare;
    }

    private record Kandidat(int i, int j, double abweichung) {
    }

    private static List<Paar> frei(List<Point3D> punkte, List<Integer> gml, LattenParameter lp, double c,
                                   Consumer<String> warnung) {
        List<Kandidat> kandidaten = new ArrayList<>();
        int[] anzahl = new int[punkte.size()];
        for (int a = 0; a < gml.size(); a++) {
            for (int b = a + 1; b < gml.size(); b++) {
                int i = gml.get(a);
                int j = gml.get(b);
                Point3D p = punkte.get(i);
                Point3D q = punkte.get(j);
                double s = p.schraegstrecke(q);
                if (passt(lp, s, c) && Math.abs(q.z() - p.z()) <= MAX_DZ) {
                    kandidaten.add(new Kandidat(i, j, Math.abs(s + c - lp.sollSpurweite())));
                    anzahl[i]++;
                    anzahl[j]++;
                }
            }
        }
        kandidaten.sort(Comparator.comparingDouble(Kandidat::abweichung));

        boolean[] belegt = new boolean[punkte.size()];
        List<Paar> paare = new ArrayList<>();
        for (Kandidat k : kandidaten) {
            if (belegt[k.i()] || belegt[k.j()]) {
                continue;
            }
            belegt[k.i()] = true;
            belegt[k.j()] = true;
            paare.add(new Paar(k.i(), k.j()));
            if (anzahl[k.i()] > 1 || anzahl[k.j()] > 1) {
                warnung.accept(String.format(Locale.ROOT,
                        "Paar %s-%s nicht eindeutig (%d bzw. %d mögliche Partner) – gewählt nach kleinster "
                      + "Abweichung zur Sollspurweite (%.1f mm). Bitte prüfen.",
                        punkte.get(k.i()).id(), punkte.get(k.j()).id(), anzahl[k.i()], anzahl[k.j()],
                        k.abweichung() * 1000));
            }
        }
        return paare;
    }
}
