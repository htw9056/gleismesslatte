package de.gleismessung;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Fenster zur Wahl der Spalten einer Text-Messdatei: zeigt die Datei als Tabelle,
 * markiert Punktnummer (blau) und Reflektorhöhe (grün), nennt die Vorschläge mit
 * Begründung und prüft die aktuelle Wahl sofort.
 */
final class SpaltenDialog extends JDialog {

    private static final long serialVersionUID = 1L;
    private static final Color FARBE_PUNKT = new Color(0xCFE3FF);
    private static final Color FARBE_HOEHE = new Color(0xCDEFC9);
    private static final int MAX_ZEILEN = 40;

    private final transient SpaltenAnalyse.Tabelle tabelle;
    private final transient Set<String> gml;
    private final JComboBox<Integer> punktSpalte;
    private final JComboBox<Integer> hoeheSpalte;
    private final JComboBox<String> einheit = new JComboBox<>(new String[] {"m", "mm"});
    private final JLabel pruefung = new JLabel(" ");
    private final JTable tab;
    private transient SpaltenAnalyse.Wahl ergebnis;

    private SpaltenDialog(Window owner, Path datei, SpaltenAnalyse.Tabelle t, Set<String> bekannt,
                          Set<String> gml, SpaltenAnalyse.Wahl vorbelegt) {
        super(owner, "Spalte der Reflektorhöhe – " + datei.getFileName(), ModalityType.APPLICATION_MODAL);
        this.tabelle = t;
        this.gml = gml;

        Integer[] nummern = new Integer[t.spalten()];
        for (int i = 0; i < nummern.length; i++) {
            nummern[i] = i + 1;
        }
        punktSpalte = new JComboBox<>(nummern);
        hoeheSpalte = new JComboBox<>(nummern);

        int p = SpaltenAnalyse.punktSpalte(t, bekannt);
        List<SpaltenAnalyse.Vorschlag> vorschlaege = SpaltenAnalyse.vorschlaege(t, p, gml);

        // Kopf: Erkennung und Vorschläge
        StringBuilder sb = new StringBuilder();
        sb.append("Datei: ").append(datei).append('\n');
        sb.append("Erkannt: Trennzeichen ").append(t.trenner()).append(", ").append(t.spalten())
                .append(" Spalten, ").append(t.zeilen().size()).append(" Zeilen.\n");
        sb.append("Punktnummer vermutlich in Spalte ").append(p)
                .append(bekannt.isEmpty() ? " (keine Punktliste geladen)" : " (Abgleich mit der Punktliste)")
                .append(".\n\n");
        if (vorschlaege.isEmpty()) {
            sb.append("Keine Spalte mit Werten zwischen 0 und 0,5 m gefunden – bitte Spalte selbst wählen.");
        } else {
            sb.append("Vorschläge für die Reflektorhöhe (bester zuerst):\n");
            for (int i = 0; i < Math.min(4, vorschlaege.size()); i++) {
                sb.append(i == 0 ? "  ► " : "     ").append(vorschlaege.get(i).begruendung(!gml.isEmpty()))
                        .append('\n');
            }
        }
        JTextArea info = new JTextArea(sb.toString());
        info.setEditable(false);
        info.setOpaque(false);
        info.setFont(info.getFont().deriveFont(13f));

        // Tabelle
        tab = new JTable(new AbstractTableModel() {
            private static final long serialVersionUID = 1L;

            @Override
            public int getRowCount() {
                return Math.min(MAX_ZEILEN, t.zeilen().size());
            }

            @Override
            public int getColumnCount() {
                return t.spalten() + 1;
            }

            @Override
            public String getColumnName(int c) {
                return c == 0 ? "Zeile" : "Spalte " + c;
            }

            @Override
            public Object getValueAt(int r, int c) {
                return c == 0 ? t.zeilenNr().get(r) : SpaltenAnalyse.feld(t.zeilen().get(r), c);
            }
        });
        tab.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        tab.getColumnModel().getColumn(0).setPreferredWidth(50);
        for (int c = 1; c <= t.spalten(); c++) {
            tab.getColumnModel().getColumn(c).setPreferredWidth(95);
        }
        tab.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean sel, boolean focus,
                                                           int row, int col) {
                Component k = super.getTableCellRendererComponent(table, value, false, false, row, col);
                int spalte = table.convertColumnIndexToModel(col);
                k.setBackground(spalte == (Integer) hoeheSpalte.getSelectedItem() ? FARBE_HOEHE
                        : spalte == (Integer) punktSpalte.getSelectedItem() ? FARBE_PUNKT
                        : spalte == 0 ? new Color(0xF0F0F0) : Color.WHITE);
                return k;
            }
        });
        tab.getTableHeader().setToolTipText("Klick auf eine Spaltenüberschrift wählt sie als Reflektorhöhe");
        tab.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int spalte = tab.convertColumnIndexToModel(tab.columnAtPoint(e.getPoint()));
                if (spalte >= 1) {
                    hoeheSpalte.setSelectedItem(spalte);
                }
            }
        });
        JScrollPane sp = new JScrollPane(tab);
        sp.setPreferredSize(new Dimension(Math.min(1000, 60 + 95 * t.spalten()), 330));

        // Auswahl
        SpaltenAnalyse.Wahl start = vorbelegt != null ? vorbelegt
                : vorschlaege.isEmpty() ? new SpaltenAnalyse.Wahl(p, Math.min(2, t.spalten()), 1)
                : vorschlaege.get(0).wahl();
        punktSpalte.setSelectedItem(Math.min(start.punkt(), t.spalten()));
        hoeheSpalte.setSelectedItem(Math.min(start.hoehe(), t.spalten()));
        einheit.setSelectedItem(start.faktor() < 0.5 ? "mm" : "m");
        punktSpalte.addActionListener(e -> aktualisiere());
        hoeheSpalte.addActionListener(e -> aktualisiere());
        einheit.addActionListener(e -> aktualisiere());

        JPanel wahl = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 4, 4, 8);
        g.anchor = GridBagConstraints.WEST;
        g.gridx = 0;
        g.gridy = 0;
        wahl.add(farbig("Punktnummer-Spalte", FARBE_PUNKT), g);
        g.gridx = 1;
        wahl.add(punktSpalte, g);
        g.gridx = 2;
        wahl.add(farbig("Reflektorhöhe-Spalte", FARBE_HOEHE), g);
        g.gridx = 3;
        wahl.add(hoeheSpalte, g);
        g.gridx = 4;
        wahl.add(new JLabel("Einheit"), g);
        g.gridx = 5;
        wahl.add(einheit, g);
        g.gridx = 0;
        g.gridy = 1;
        g.gridwidth = 6;
        pruefung.setFont(pruefung.getFont().deriveFont(Font.BOLD));
        wahl.add(pruefung, g);

        JButton ok = new JButton("Übernehmen");
        ok.addActionListener(e -> {
            ergebnis = new SpaltenAnalyse.Wahl((Integer) punktSpalte.getSelectedItem(),
                    (Integer) hoeheSpalte.getSelectedItem(), "mm".equals(einheit.getSelectedItem()) ? 0.001 : 1);
            dispose();
        });
        JButton abbruch = new JButton("Abbrechen");
        abbruch.addActionListener(e -> dispose());
        JPanel knoepfe = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        knoepfe.add(ok);
        knoepfe.add(abbruch);

        JPanel unten = new JPanel(new BorderLayout());
        unten.add(wahl, BorderLayout.CENTER);
        unten.add(knoepfe, BorderLayout.SOUTH);

        JPanel inhalt = new JPanel(new BorderLayout(0, 8));
        inhalt.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        inhalt.add(info, BorderLayout.NORTH);
        inhalt.add(sp, BorderLayout.CENTER);
        inhalt.add(unten, BorderLayout.SOUTH);
        setContentPane(inhalt);
        getRootPane().setDefaultButton(ok);
        aktualisiere();
        pack();
        setLocationRelativeTo(owner);
    }

    private static JLabel farbig(String text, Color c) {
        JLabel l = new JLabel(" " + text + " ");
        l.setOpaque(true);
        l.setBackground(c);
        return l;
    }

    /** Prüft die aktuelle Wahl und zeigt das Ergebnis an. */
    private void aktualisiere() {
        int p = (Integer) punktSpalte.getSelectedItem();
        int h = (Integer) hoeheSpalte.getSelectedItem();
        double f = "mm".equals(einheit.getSelectedItem()) ? 0.001 : 1;
        List<Integer> rel = SpaltenAnalyse.relevanteZeilen(tabelle, p, gml);
        SpaltenAnalyse.Vorschlag v = h == p || rel.isEmpty() ? null
                : SpaltenAnalyse.pruefe(tabelle, p, h, f, rel, !gml.isEmpty());
        if (v == null) {
            pruefung.setForeground(new Color(0xB00020));
            pruefung.setText(rel.isEmpty() ? "In der Punktnummer-Spalte wurden keine GML-Punkte der Punktliste gefunden."
                    : "Mit dieser Wahl liegt kein Wert zwischen 0 und 0,5 m.");
        } else {
            pruefung.setForeground(new Color(0x1B5E20));
            pruefung.setText("Mit dieser Wahl: " + v.begruendung(!gml.isEmpty()));
        }
        tab.repaint();
    }

    /**
     * Zeigt das Fenster und liefert die gewählten Spalten oder null bei Abbruch.
     */
    static SpaltenAnalyse.Wahl zeige(Component owner, Path datei, SpaltenAnalyse.Tabelle t, Set<String> bekannt,
                                     Set<String> gml, SpaltenAnalyse.Wahl vorbelegt) {
        Window w = owner == null ? null : SwingUtilities.getWindowAncestor(owner);
        if (owner instanceof Window ow) {
            w = ow;
        }
        SpaltenDialog d = new SpaltenDialog(w, datei, t, bekannt, gml, vorbelegt);
        d.setVisible(true);
        return d.ergebnis;
    }
}
