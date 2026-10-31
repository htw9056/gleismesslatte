package de.gleismessung;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JComponent;

/**
 * Zeigt mehrere Bilder untereinander und skaliert sie so groß wie möglich auf die
 * verfügbare Fläche (Seitenverhältnis bleibt erhalten). Berücksichtigt die
 * Windows-Anzeigeskalierung (HiDPI) und verkleinert stufenweise für scharfe Schrift.
 */
final class BildPanel extends JComponent {

    private static final long serialVersionUID = 1L;
    private static final int ABSTAND = 10;

    private final transient List<BufferedImage> bilder = new ArrayList<>();
    private transient List<BufferedImage> cache = List.of();
    private int cacheBreite = -1;

    BildPanel(String... ressourcen) {
        for (String r : ressourcen) {
            try (InputStream in = BildPanel.class.getResourceAsStream(r)) {
                if (in != null) {
                    bilder.add(ImageIO.read(in));
                }
            } catch (IOException e) {
                // Bild fehlt – Fläche bleibt leer
            }
        }
        setOpaque(true);
        setBackground(new Color(0xF4F6F8));
    }

    /** Seitenverhältnis Höhe/Breite aller Bilder untereinander (ohne Abstand). */
    private double verhaeltnis() {
        double h = 0;
        for (BufferedImage b : bilder) {
            h += (double) b.getHeight() / b.getWidth();
        }
        return h;
    }

    @Override
    public Dimension getPreferredSize() {
        int breite = 620;
        return new Dimension(breite, (int) (breite * verhaeltnis()) + ABSTAND * bilder.size());
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(300, 300);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setColor(getBackground());
        g.fillRect(0, 0, getWidth(), getHeight());
        if (bilder.isEmpty()) {
            g.dispose();
            return;
        }
        // Breite so wählen, dass alle Bilder untereinander in die Fläche passen
        int abstaende = ABSTAND * (bilder.size() - 1);
        double breite = Math.min(getWidth(), (getHeight() - abstaende) / verhaeltnis());
        int x = (int) Math.round((getWidth() - breite) / 2);

        // in Gerätepixeln rechnen (Windows-Skalierung 125 %, 150 % …)
        AffineTransform t = g.getTransform();
        double sx = t.getScaleX();
        int pixelBreite = Math.max(1, (int) Math.round(breite * sx));
        if (pixelBreite != cacheBreite) {
            List<BufferedImage> neu = new ArrayList<>();
            for (BufferedImage b : bilder) {
                neu.add(verkleinere(b, pixelBreite));
            }
            cache = neu;
            cacheBreite = pixelBreite;
        }

        double y = 0;
        for (BufferedImage b : cache) {
            double h = b.getHeight() / sx;
            Graphics2D gb = (Graphics2D) g.create();
            gb.translate(x, y);
            gb.scale(1 / sx, 1 / t.getScaleY());
            gb.drawImage(b, 0, 0, null);
            gb.dispose();
            y += h + ABSTAND;
        }
        g.dispose();
    }

    /** Stufenweises Verkleinern (jeweils höchstens halbieren) für saubere Kanten und Schrift. */
    private static BufferedImage verkleinere(BufferedImage quelle, int zielBreite) {
        int zielHoehe = Math.max(1, (int) Math.round((double) quelle.getHeight() * zielBreite / quelle.getWidth()));
        BufferedImage aktuell = quelle;
        int w = quelle.getWidth();
        int h = quelle.getHeight();
        do {
            if (w > zielBreite) {
                w = Math.max(zielBreite, w / 2);
                h = Math.max(zielHoehe, h / 2);
            } else {
                w = zielBreite;
                h = zielHoehe;
            }
            BufferedImage naechstes = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = naechstes.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(aktuell, 0, 0, w, h, null);
            g.dispose();
            aktuell = naechstes;
        } while (w != zielBreite || h != zielHoehe);
        return aktuell;
    }
}
