package de.gleismessung;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Programmfenster: Bild der Gleismesslatte, Eingabefelder für alle Parameter,
 * Auswertung und Protokoll. Eine CSV-Datei kann auch ins Fenster gezogen werden.
 */
final class Fenster extends JFrame {

    private static final long serialVersionUID = 1L;
    private static final String BILD_SZENE = "/de/gleismessung/bild/szene.png";
    private static final String BILD_DETAIL = "/de/gleismessung/bild/detail.png";
    private static final String[] ICONS = {"16", "32", "48", "256"};
    private static final DecimalFormat ZAHL =
            new DecimalFormat("0.000#", DecimalFormatSymbols.getInstance(Locale.GERMANY));

    private final JTextField eingabe = new JTextField(26);
    private final JTextField ausgabe = new JTextField(26);
    private final JTextField reflektorhoehe = zahlFeld();
    private final JTextField lattenkonstante = zahlFeld();
    private final JTextField sollspurweite = zahlFeld();
    private final JTextField toleranz = zahlFeld();
    private final JTextField bezugsbasis = zahlFeld();
    private final JTextField maxUeberhoehung = zahlFeld();
    private final JComboBox<Integer> nachkommastellen = new JComboBox<>(new Integer[] {3, 4});
    private final JTextField achsCodes = new JTextField(14);
    private final JComboBox<String> aufbau = new JComboBox<>(EingabeFormat.VORLAGEN);
    private final JComboBox<EingabeFormat.Trennung> trennung = new JComboBox<>(EingabeFormat.Trennung.values());
    private final JComboBox<Integer> kopfzeilen = new JComboBox<>(new Integer[] {0, 1, 2, 3, 4, 5});
    private final JComboBox<EingabeFormat.Ausgabe> ausgabeformat = new JComboBox<>(EingabeFormat.Ausgabe.values());
    private final JTextField hDateien = new JTextField(26);
    private final JComboBox<ReflektorhoeheLeser.Bezug> hBezug = new JComboBox<>(ReflektorhoeheLeser.Bezug.values());
    private final JComboBox<Paarbildung.Modus> paarbildung = new JComboBox<>(Paarbildung.Modus.values());
    private final JCheckBox ueProtokoll = new JCheckBox("Protokoll Überhöhung erzeugen (…_ueberhoehung.txt)");
    private final JCheckBox achse = new JCheckBox("Gleisachse berechnen (Punktnr. = kleinste Nr. + \"Achse\")");
    private final JLabel paarBereich = new JLabel(" ");
    private final JTextArea protokoll = new JTextArea(8, 80);
    private final JButton auswerten = new JButton("Auswerten");
    private final JButton oeffnen = new JButton("Ergebnis öffnen");
    private final JButton ordner = new JButton("Ordner öffnen");
    private final JButton ueOeffnen = new JButton("Überhöhung öffnen");
    private transient Path letzteAusgabe;
    /** gewählte Spalten je Text-Messdatei (siehe LattenParameter.hSpalten) */
    private String hSpalten = "";
    private transient Path letzteUeberhoehung;

    static void oeffne(Path vorauswahl) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // Standard-Look
        }
        SwingUtilities.invokeLater(() -> {
            Fenster f = new Fenster();
            if (vorauswahl != null) {
                f.setzeEingabe(vorauswahl);
            }
            f.setVisible(true);
        });
    }

    private Fenster() {
        super(Version.textMitHersteller() + " – Auswertung Gleismesslatte");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setIconImages(ladeIcons());
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                Sitzung.speichere(sitzungAusFeldern());
            }
        });

        // rechts: Eingabefelder oben, Protokoll darunter
        JPanel oben = new JPanel(new BorderLayout());
        oben.setBorder(BorderFactory.createEmptyBorder(10, 0, 4, 10));
        oben.add(formular(), BorderLayout.CENTER);

        protokoll.setEditable(false);
        protokoll.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane sp = new JScrollPane(protokoll);
        sp.setBorder(BorderFactory.createTitledBorder("Protokoll"));

        JPanel unten = new JPanel(new BorderLayout());
        unten.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 10));
        unten.add(sp, BorderLayout.CENTER);
        JLabel fuss = new JLabel(Version.textMitHersteller(), JLabel.RIGHT);
        fuss.setForeground(Color.GRAY);
        fuss.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 2));
        unten.add(fuss, BorderLayout.SOUTH);

        JPanel rechts = new JPanel(new BorderLayout());
        rechts.add(oben, BorderLayout.NORTH);
        rechts.add(unten, BorderLayout.CENTER);

        // links: beide Bilder untereinander, füllen die ganze Fensterhöhe
        BildPanel bild = new BildPanel(BILD_SZENE, BILD_DETAIL);
        bild.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel bildRahmen = new JPanel(new BorderLayout());
        bildRahmen.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 12));
        bildRahmen.add(bild, BorderLayout.CENTER);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(bildRahmen, BorderLayout.CENTER);
        getContentPane().add(rechts, BorderLayout.EAST);

        Properties sitzung = Sitzung.lade();
        String startHinweis = setzeStart(sitzung);
        protokoll.setText("Punktliste (CSV oder Leerzeichen-Text) wählen oder ins Fenster ziehen, Format und "
                + "Parameter prüfen, \"Auswerten\" klicken.\n"
                + "Aufbau: P=Punktnummer R=Rechtswert H=Hochwert Z=Höhe C=Code -=Spalte überspringen  "
                + "(Standard P R H Z C)\n"
                + "Codes mit GML am Anfang oder Ende sind Lattenprismen.\n"
                + "Alle Eingaben werden automatisch gespeichert und beim nächsten Start geladen.\n"
                + startHinweis);

        TransferHandler drop = new DateiDrop();
        ((JComponent) getContentPane()).setTransferHandler(drop);
        oben.setTransferHandler(drop);
        bild.setTransferHandler(drop);
        protokoll.setTransferHandler(drop);

        pack();
        setMinimumSize(new java.awt.Dimension(rechts.getPreferredSize().width + 400,
                Math.min(getHeight(), rechts.getPreferredSize().height + 60)));
        // Fenster möglichst groß öffnen, damit die Bilder gut lesbar sind
        java.awt.Rectangle frei = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int hoehe = Math.min(frei.height - 20, Math.max(getHeight(), 980));
        int breite = Math.min(frei.width - 20, rechts.getPreferredSize().width
                + (int) ((hoehe - 60) / 1.21) + 40);
        setSize(Math.max(breite, getMinimumSize().width), Math.max(hoehe, getMinimumSize().height));
        setLocationRelativeTo(null);
        setzeFenster(sitzung, frei);
    }

    // ------------------------------------------------------------------ Aufbau

    private JComponent formular() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 4, 3, 4);
        g.anchor = GridBagConstraints.WEST;
        int z = 0;

        JButton wahlEin = new JButton("…");
        wahlEin.setToolTipText("Eingabedatei wählen");
        wahlEin.addActionListener(e -> waehleEingabe());
        JButton wahlAus = new JButton("…");
        wahlAus.setToolTipText("Ausgabedatei wählen");
        wahlAus.addActionListener(e -> waehleAusgabe());

        z = zeile(p, g, z, "Eingabedatei", eingabe, wahlEin);
        z = zeile(p, g, z, "Ausgabedatei", ausgabe, wahlAus);
        aufbau.setEditable(true);
        aufbau.setPrototypeDisplayValue("P R H Z C - -");
        aufbau.setToolTipText("<html>Spaltenfolge der Eingabedatei, frei editierbar:<br>"
                + "P = Punktnummer, R = Rechtswert, H = Hochwert, Z = Höhe, C = Code, - = Spalte überspringen<br>"
                + "Standard: P R H Z C</html>");
        z = zeile(p, g, z, "Spaltenaufbau", aufbau, hinweis("P Punkt · R Rechts · H Hoch · Z Höhe · C Code · - leer"));
        JPanel kopf = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        kopf.add(new JLabel("Kopfzeilen überspringen "));
        kopf.add(kopfzeilen);
        trennung.setToolTipText("automatisch: Zeilen mit ; als CSV, sonst Leerzeichen/Tab");
        z = zeile(p, g, z, "Trennzeichen", trennung, kopf);
        ausgabeformat.setToolTipText("<html>wie Eingabe: gleicher Aufbau, Trennzeichen, Dezimalzeichen und "
                + "Kopfzeilen – direkt wieder einlesbar<br>CSV mit Statusspalte: Punkt;Code;R;H;Z;Status</html>");
        z = zeile(p, g, z, "Ausgabeformat", ausgabeformat, hinweis("Punktliste wieder einlesbar"));
        z = trenner(p, g, z);
        z = zeile(p, g, z, "Reflektorhöhe h [m]", reflektorhoehe,
                hinweis("⊥ zur Latte, 0–0,5 m · Feld- bzw. Ersatzwert"));
        JButton wahlH = new JButton("…");
        wahlH.setToolTipText("Messdatei(en) mit Reflektorhöhe hinzufügen (GSI, XML, JXL, Text …)");
        wahlH.addActionListener(e -> waehleHDateien());
        JButton sucheH = new JButton("Höhe suchen");
        sucheH.setToolTipText("<html>Reflektorhöhen in den Messdateien suchen und anzeigen.<br>"
                + "Bei Text-Tabellen schlägt ein Fenster die passende Spalte vor.</html>");
        sucheH.addActionListener(e -> sucheHoehe());
        JPanel hKnoepfe = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        hKnoepfe.add(wahlH);
        hKnoepfe.add(sucheH);
        hDateien.setToolTipText("<html>Messdatei(en), aus denen die Reflektorhöhe je Punkt gelesen wird "
                + "(mehrere mit ; trennen).<br>Leica GSI-8/16, Trimble JobXML, LandXML/XML, Text-Tabellen "
                + "(Spalte wird vorgeschlagen). "
                + "Gültig 0–0,5 m.<br>Leer = Feldwert für alle GML-Punkte. Dateien können auch hierher gezogen werden.</html>");
        hDateien.setTransferHandler(new HDateiDrop());
        z = zeile(p, g, z, "Messdatei(en) Reflektorh.", hDateien, hKnoepfe);
        hBezug.setToolTipText("<html>Prismenmitte: im Instrument war hr = 0, die Z-Werte sind die Prismenmitte.<br>"
                + "reduziert: im Instrument war die Reflektorhöhe eingetragen – die Z-Werte sind bereits lotrecht "
                + "um hr reduziert<br>und werden vor der Korrektur auf die Prismenmitte zurückgerechnet.</html>");
        z = zeile(p, g, z, "Höhen der Punktliste", hBezug, hinweis("Bezug der Z-Werte"));
        z = zeile(p, g, z, "Lattenkonstante c [m]", lattenkonstante, hinweis("Spurweite = Prismenabstand + c"));
        z = zeile(p, g, z, "Sollspurweite [m]", sollspurweite, hinweis("Bezug für Abweichung"));
        z = zeile(p, g, z, "Toleranz ± [m]", toleranz, paarBereich);
        z = zeile(p, g, z, "Bezugsbasis Überhöhung [m]", bezugsbasis, hinweis("u bezogen auf 1,500 m (Regelspur)"));
        maxUeberhoehung.setToolTipText("<html>Grenzwert der Überhöhung in mm.<br>EBO § 6 Abs. 3: höchstens 180 mm "
                + "einschließlich der im Betrieb auftretenden Abweichungen.</html>");
        z = zeile(p, g, z, "Grenzwert Überhöhung [mm]", maxUeberhoehung, hinweis("EBO § 6 Abs. 3: max. 180 mm"));
        nachkommastellen.setToolTipText("Nachkommastellen für Koordinaten, Spurweite und Höhen in der Ausgabe");
        z = zeile(p, g, z, "Nachkommastellen Ausgabe", nachkommastellen, hinweis("Meterwerte: 3 = mm, 4 = 0,1 mm"));
        paarbildung.setToolTipText("<html>aufeinanderfolgend: zwei GML-Punkte direkt hintereinander bilden ein Paar<br>"
                + "frei: beliebige GML-Punkte, Paare nach bester Übereinstimmung mit der Sollspurweite</html>");
        z = zeile(p, g, z, "Paarbildung", paarbildung, hinweis("Mehrdeutiges wird gewarnt"));
        achsCodes.setToolTipText("<html>Zusätzliche Codes (ohne GML), deren Punkte paarweise zur Gleisachse<br>"
                + "gerechnet werden – OHNE Korrektur. Mehrere Codes mit Komma oder Semikolon trennen.</html>");
        z = zeile(p, g, z, "Achs-Codes (ohne Korrektur)", achsCodes, hinweis("z. B. SOL,SOR"));

        g.gridx = 1;
        g.gridy = z++;
        g.gridwidth = 2;
        p.add(achse, g);
        g.gridy = z++;
        p.add(ueProtokoll, g);
        g.gridwidth = 1;

        JButton speichern = new JButton("Als Standard speichern");
        speichern.setToolTipText("Werte in gleismesslatte.properties im Programmordner speichern");
        speichern.addActionListener(e -> speichereStandard());
        JButton zuruecksetzen = new JButton("Werkseinstellung");
        zuruecksetzen.setToolTipText("Reflektorhöhe 0,130 · c 0,000 · Sollspurweite 1,458 · Toleranz 0,100 · "
                + "Achse ja · 3 Nachkommastellen · Überhöhungsprotokoll ja · Paarbildung aufeinanderfolgend · "
                + "keine Achs-Codes · Aufbau P R H Z C · Trennung automatisch · 0 Kopfzeilen · "
                + "Grenzwert Überhöhung 180 mm · Ausgabe wie Eingabe · keine Messdateien · Höhen = Prismenmitte");
        zuruecksetzen.addActionListener(e -> setzeParameter(LattenParameter.standard()));

        auswerten.setFont(auswerten.getFont().deriveFont(Font.BOLD));
        auswerten.addActionListener(e -> starteAuswertung());
        oeffnen.setEnabled(false);
        oeffnen.addActionListener(e -> oeffneDatei(letzteAusgabe));
        ordner.setEnabled(false);
        ueOeffnen.setEnabled(false);
        ueOeffnen.addActionListener(e -> oeffneDatei(letzteUeberhoehung));
        ordner.addActionListener(e -> oeffneDatei(letzteAusgabe == null ? null : letzteAusgabe.getParent()));

        JPanel knoepfe = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        knoepfe.add(auswerten);
        knoepfe.add(oeffnen);
        knoepfe.add(ueOeffnen);
        knoepfe.add(ordner);
        JPanel knoepfe2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        knoepfe2.add(speichern);
        knoepfe2.add(zuruecksetzen);

        g.gridx = 0;
        g.gridy = z++;
        g.gridwidth = 3;
        g.insets = new Insets(12, 0, 3, 0);
        p.add(knoepfe, g);
        g.gridy = z;
        g.insets = new Insets(3, 0, 3, 0);
        p.add(knoepfe2, g);

        DocumentListener bereich = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { zeigePaarBereich(); }
            @Override public void removeUpdate(DocumentEvent e) { zeigePaarBereich(); }
            @Override public void changedUpdate(DocumentEvent e) { zeigePaarBereich(); }
        };
        sollspurweite.getDocument().addDocumentListener(bereich);
        toleranz.getDocument().addDocumentListener(bereich);
        lattenkonstante.getDocument().addDocumentListener(bereich);
        return p;
    }

    private static int zeile(JPanel p, GridBagConstraints g, int z, String text, JComponent feld, JComponent rechts) {
        g.gridy = z;
        g.gridx = 0;
        g.weightx = 0;
        g.fill = GridBagConstraints.NONE;
        p.add(new JLabel(text), g);
        g.gridx = 1;
        g.weightx = 1;
        if (feld instanceof JTextField t && t.getColumns() > 10) {
            // Dateifeld: Feld dehnt sich, Knopf rechts
            g.fill = GridBagConstraints.HORIZONTAL;
            p.add(feld, g);
            g.gridx = 2;
            g.weightx = 0;
            g.fill = GridBagConstraints.NONE;
            p.add(rechts, g);
        } else {
            // Zahlenfeld mit Hinweis direkt dahinter
            JPanel zelle = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            zelle.add(feld);
            zelle.add(javax.swing.Box.createHorizontalStrut(8));
            zelle.add(rechts);
            g.gridwidth = 2;
            g.fill = GridBagConstraints.HORIZONTAL;
            p.add(zelle, g);
            g.gridwidth = 1;
        }
        g.fill = GridBagConstraints.NONE;
        g.weightx = 0;
        return z + 1;
    }

    private static int trenner(JPanel p, GridBagConstraints g, int z) {
        g.gridx = 0;
        g.gridy = z;
        g.gridwidth = 3;
        g.fill = GridBagConstraints.HORIZONTAL;
        JLabel l = new JLabel("Parameter");
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        l.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        p.add(l, g);
        g.gridwidth = 1;
        g.fill = GridBagConstraints.NONE;
        return z + 1;
    }

    private static JLabel hinweis(String t) {
        JLabel l = new JLabel(t);
        l.setForeground(Color.GRAY);
        return l;
    }

    private static JTextField zahlFeld() {
        JTextField f = new JTextField(7);
        f.setHorizontalAlignment(JTextField.RIGHT);
        return f;
    }

    // --------------------------------------------------------------- Parameter

    /** Setzt Parameter und Dateien beim Start; liefert einen Hinweis für das Protokoll. */
    private String setzeStart(Properties sitzung) {
        String hinweis = "";
        LattenParameter lp;
        try {
            lp = Parameter.leseMitSitzung(sitzung);
            if (!sitzung.isEmpty()) {
                hinweis = "Letzte Eingaben geladen (" + Sitzung.datei() + ").\n";
            }
        } catch (IOException | RuntimeException e) {
            hinweis = "Gespeicherte Eingaben fehlerhaft (" + e.getMessage() + ") – Standardwerte geladen.\n";
            try {
                lp = Parameter.leseStandard();
            } catch (IOException | RuntimeException e2) {
                lp = LattenParameter.standard();
            }
        }
        setzeParameter(lp);
        // Rohtexte übernehmen, damit auch unvollständige Eingaben erhalten bleiben
        String ein = sitzung.getProperty("eingabedatei", "");
        String aus = sitzung.getProperty("ausgabedatei", "");
        eingabe.setText(ein);
        ausgabe.setText(aus);
        if (!ein.isBlank() && !Files.isRegularFile(Path.of(ein))) {
            hinweis += "Hinweis: letzte Eingabedatei nicht mehr vorhanden: " + ein + "\n";
        }
        return hinweis;
    }

    /** Fensterposition und -größe der letzten Sitzung, sofern sie auf den Bildschirm passen. */
    private void setzeFenster(Properties s, java.awt.Rectangle frei) {
        try {
            if ("ja".equals(s.getProperty("fenster.maximiert"))) {
                setExtendedState(MAXIMIZED_BOTH);
                return;
            }
            if (s.getProperty("fenster.breite") == null) {
                return;
            }
            int x = Integer.parseInt(s.getProperty("fenster.x"));
            int y = Integer.parseInt(s.getProperty("fenster.y"));
            int w = Integer.parseInt(s.getProperty("fenster.breite"));
            int h = Integer.parseInt(s.getProperty("fenster.hoehe"));
            java.awt.Rectangle r = new java.awt.Rectangle(x, y, w, h);
            if (w >= getMinimumSize().width && h >= getMinimumSize().height && frei.contains(r)) {
                setBounds(r);
            }
        } catch (RuntimeException e) {
            // ungültige Angaben – Standardgröße behalten
        }
    }

    /** Alle Eingaben als Rohtext für die Sitzung (auch wenn einzelne Werte ungültig sind). */
    private Properties sitzungAusFeldern() {
        Properties p = new Properties();
        p.setProperty("eingabedatei", eingabe.getText().strip());
        p.setProperty("ausgabedatei", ausgabe.getText().strip());
        p.setProperty("aufbau", String.valueOf(aufbau.getEditor().getItem()).strip());
        p.setProperty("trennzeichen", ((EingabeFormat.Trennung) trennung.getSelectedItem()).schluessel);
        p.setProperty("kopfzeilen", String.valueOf(kopfzeilen.getSelectedItem()));
        p.setProperty("ausgabeformat", ((EingabeFormat.Ausgabe) ausgabeformat.getSelectedItem()).schluessel);
        p.setProperty("h", reflektorhoehe.getText().strip());
        p.setProperty("hdateien", hDateien.getText().strip());
        p.setProperty("hbezug", ((ReflektorhoeheLeser.Bezug) hBezug.getSelectedItem()).schluessel);
        p.setProperty("hspalten", hSpalten);
        p.setProperty("c", lattenkonstante.getText().strip());
        p.setProperty("sollspurweite", sollspurweite.getText().strip());
        p.setProperty("toleranz", toleranz.getText().strip());
        p.setProperty("bezugsbasis", bezugsbasis.getText().strip());
        p.setProperty("maxueberhoehung", maxUeberhoehung.getText().strip());
        p.setProperty("nachkommastellen", String.valueOf(nachkommastellen.getSelectedItem()));
        p.setProperty("paarbildung", ((Paarbildung.Modus) paarbildung.getSelectedItem()).schluessel);
        p.setProperty("achscodes", achsCodes.getText().strip());
        p.setProperty("achse", achse.isSelected() ? "ja" : "nein");
        p.setProperty("ueberhoehungsprotokoll", ueProtokoll.isSelected() ? "ja" : "nein");
        boolean max = (getExtendedState() & MAXIMIZED_BOTH) == MAXIMIZED_BOTH;
        p.setProperty("fenster.maximiert", max ? "ja" : "nein");
        if (!max) {
            p.setProperty("fenster.x", String.valueOf(getX()));
            p.setProperty("fenster.y", String.valueOf(getY()));
            p.setProperty("fenster.breite", String.valueOf(getWidth()));
            p.setProperty("fenster.hoehe", String.valueOf(getHeight()));
        }
        return p;
    }

    private void setzeParameter(LattenParameter lp) {
        reflektorhoehe.setText(ZAHL.format(lp.h()));
        lattenkonstante.setText(ZAHL.format(lp.c()));
        sollspurweite.setText(ZAHL.format(lp.sollSpurweite()));
        toleranz.setText(ZAHL.format(lp.toleranz()));
        bezugsbasis.setText(ZAHL.format(lp.bezugsbasis()));
        maxUeberhoehung.setText(new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(Locale.GERMANY))
                .format(lp.maxUeberhoehung() * 1000));
        achse.setSelected(lp.achseBerechnen());
        nachkommastellen.setSelectedItem(lp.nachkommastellen());
        ueProtokoll.setSelected(lp.ueberhoehungsProtokoll());
        paarbildung.setSelectedItem(lp.paarbildung());
        achsCodes.setText(lp.achsCodesText());
        aufbau.setSelectedItem(lp.aufbau());
        trennung.setSelectedItem(lp.trennung());
        kopfzeilen.setSelectedItem(lp.kopfzeilen());
        ausgabeformat.setSelectedItem(lp.ausgabe());
        hDateien.setText(lp.hDateienText());
        hBezug.setSelectedItem(lp.hBezug());
        hSpalten = lp.hSpalten();
        zeigePaarBereich();
    }

    private LattenParameter liesParameter() {
        return new LattenParameter(
                feldWert(reflektorhoehe, "Reflektorhöhe"),
                feldWert(lattenkonstante, "Lattenkonstante"),
                feldWert(bezugsbasis, "Bezugsbasis"),
                feldWert(sollspurweite, "Sollspurweite"),
                feldWert(toleranz, "Toleranz"),
                achse.isSelected(),
                (Integer) nachkommastellen.getSelectedItem(),
                ueProtokoll.isSelected(),
                (Paarbildung.Modus) paarbildung.getSelectedItem(),
                LattenParameter.codesAus(achsCodes.getText()),
                (EingabeFormat.Trennung) trennung.getSelectedItem(),
                String.valueOf(aufbau.getEditor().getItem()),
                (Integer) kopfzeilen.getSelectedItem(),
                feldWert(maxUeberhoehung, "Grenzwert Überhöhung") / 1000,
                (EingabeFormat.Ausgabe) ausgabeformat.getSelectedItem(),
                LattenParameter.dateienAus(hDateien.getText()),
                (ReflektorhoeheLeser.Bezug) hBezug.getSelectedItem(),
                hSpalten).pruefe();
    }

    private static double feldWert(JTextField f, String name) {
        try {
            return Parameter.zahl(f.getText());
        } catch (NumberFormatException e) {
            f.requestFocus();
            throw new IllegalArgumentException(name + ": keine gültige Zahl \"" + f.getText() + "\"");
        }
    }

    private void zeigePaarBereich() {
        try {
            double s = Parameter.zahl(sollspurweite.getText());
            double t = Parameter.zahl(toleranz.getText());
            paarBereich.setForeground(Color.GRAY);
            paarBereich.setText("Paarbereich " + ZAHL.format(s - t) + " – " + ZAHL.format(s + t) + " m");
        } catch (NumberFormatException e) {
            paarBereich.setForeground(Color.RED);
            paarBereich.setText("ungültige Zahl");
        }
    }

    private void speichereStandard() {
        try {
            Path ziel = Parameter.speichere(liesParameter());
            protokoll.append("\nParameter gespeichert: " + ziel + "\n");
        } catch (IOException | RuntimeException e) {
            fehler("Speichern nicht möglich:\n" + e.getMessage());
        }
    }

    // ----------------------------------------------------------------- Dateien

    private void setzeEingabe(Path p) {
        Path abs = p.toAbsolutePath().normalize();
        eingabe.setText(abs.toString());
        ausgabe.setText(Main.standardAusgabe(abs).toString());
        // Parameterdatei neben der Messdatei hat Vorrang
        try {
            if (Files.isRegularFile(abs.resolveSibling(Main.PARAMETER_DATEI))) {
                setzeParameter(Parameter.lese(abs, new String[0]));
                protokoll.append("\nParameter aus " + abs.resolveSibling(Main.PARAMETER_DATEI) + " übernommen.\n");
            }
        } catch (IOException | RuntimeException e) {
            protokoll.append("\nParameterdatei neben der Messdatei fehlerhaft: " + e.getMessage() + "\n");
        }
    }

    private void waehleEingabe() {
        JFileChooser fc = new JFileChooser(startOrdner());
        fc.setDialogTitle("Punktliste wählen (Punkt;Code;y;x;z)");
        fc.setFileFilter(new FileNameExtensionFilter("Punktlisten (*.csv, *.txt, *.dat, *.asc, *.koo, *.xyz)",
                "csv", "txt", "dat", "asc", "koo", "xyz"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            setzeEingabe(fc.getSelectedFile().toPath());
        }
    }

    private void waehleHDateien() {
        JFileChooser fc = new JFileChooser(startOrdner());
        fc.setDialogTitle("Messdatei(en) mit Reflektorhöhe wählen");
        fc.setMultiSelectionEnabled(true);
        fc.addChoosableFileFilter(new FileNameExtensionFilter(
                "Messdateien (*.gsi, *.xml, *.jxl, *.job, *.sdr, *.raw, *.txt, *.csv, *.dat)",
                "gsi", "xml", "jxl", "job", "sdr", "raw", "txt", "csv", "dat"));
        fc.setFileFilter(fc.getChoosableFileFilters()[fc.getChoosableFileFilters().length - 1]);
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            fuegeHDateienHinzu(java.util.Arrays.asList(fc.getSelectedFiles()));
        }
    }

    private void fuegeHDateienHinzu(List<File> dateien) {
        List<String> liste = new ArrayList<>(LattenParameter.dateienAus(hDateien.getText()));
        for (File f : dateien) {
            String pfad = f.getAbsolutePath();
            if (!liste.contains(pfad)) {
                liste.add(pfad);
            }
        }
        hDateien.setText(String.join("; ", liste));
    }

    /** Sucht die Reflektorhöhen in den Messdateien und bietet den häufigsten Wert als Feldwert an. */
    private void sucheHoehe() {
        try {
            LattenParameter lp = liesParameter();
            if (lp.hDateien().isEmpty()) {
                fehler("Bitte zuerst eine oder mehrere Messdateien wählen (Knopf …).");
                return;
            }
            Path ein = eingabe.getText().isBlank() ? null : Path.of(eingabe.getText().strip());
            java.util.Set<String> ids = new java.util.HashSet<>();
            java.util.Set<String> gml = new java.util.HashSet<>();
            if (ein != null && Files.isRegularFile(ein)) {
                for (Point3D pk : Main.lesePunkteStill(ein, lp)) {
                    ids.add(pk.id());
                    if (GmlCodierung.istGleismesspunkt(pk.code())) {
                        gml.add(pk.id());
                    }
                }
            }
            // Text-Tabellen: Spalte vorschlagen und bestätigen lassen
            java.util.Map<String, String> spalten = new java.util.LinkedHashMap<>(lp.hSpaltenMap());
            for (Path d : Main.hDateien(lp, ein)) {
                if (!Files.isRegularFile(d)
                        || ReflektorhoeheLeser.format(Files.readAllBytes(d)) != ReflektorhoeheLeser.Format.TEXT) {
                    continue;
                }
                SpaltenAnalyse.Tabelle t = SpaltenAnalyse.zerlege(ReflektorhoeheLeser.text(d));
                String key = ReflektorhoeheLeser.schluessel(d);
                SpaltenAnalyse.Wahl w = SpaltenDialog.zeige(this, d, t, SpaltenAnalyse.normiert(ids),
                        SpaltenAnalyse.normiert(gml), SpaltenAnalyse.Wahl.aus(spalten.get(key)));
                if (w != null) {
                    spalten.put(key, w.alsText());
                }
            }
            hSpalten = LattenParameter.hSpaltenText(spalten);
            ReflektorhoeheLeser.Ergebnis rh = ReflektorhoeheLeser.lies(Main.hDateien(lp, ein), ids, gml, spalten);
            StringBuilder sb = new StringBuilder();
            for (String info : rh.dateiInfo) {
                sb.append(info).append('\n');
            }
            sb.append("\nGefundene Reflektorhöhen (0–0,5 m):\n  ").append(rh.verteilungText()).append('\n');
            if (rh.null0 > 0) {
                sb.append("  ").append(rh.null0).append(" × 0 (im Instrument keine Höhe eingetragen)\n");
            }
            if (rh.ausserhalb > 0) {
                sb.append("  ").append(rh.ausserhalb).append(" Werte außerhalb 0–0,5 m ignoriert (andere Ziele)\n");
            }
            if (!gml.isEmpty()) {
                long mit = gml.stream().filter(id -> {
                    Double h = rh.hoehe(id);
                    return h != null && h > 0;
                }).count();
                sb.append("\nGML-Punkte der Eingabedatei mit Reflektorhöhe: ").append(mit).append(" von ")
                        .append(gml.size()).append('\n');
            }
            protokoll.append("\n--- Reflektorhöhe suchen ---\n" + sb);
            Double best = rh.haeufigster();
            if (best == null) {
                JOptionPane.showMessageDialog(this, sb + "\nKeine Reflektorhöhe zwischen 0 und 0,5 m gefunden.",
                        Version.text(), JOptionPane.WARNING_MESSAGE);
                return;
            }
            int antwort = JOptionPane.showConfirmDialog(this, sb + "\nHäufigster Wert: "
                    + ReflektorhoeheLeser.fmt(best) + " m\n\nAls Feldwert h (Ersatzwert für Punkte ohne Angabe) "
                    + "übernehmen?", Version.text(), JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (antwort == JOptionPane.YES_OPTION) {
                reflektorhoehe.setText(ZAHL.format(best));
            }
            Sitzung.speichere(sitzungAusFeldern());
        } catch (IOException | RuntimeException e) {
            fehler("Reflektorhöhe suchen nicht möglich:\n" + e.getMessage());
        }
    }

    private void waehleAusgabe() {
        JFileChooser fc = new JFileChooser(startOrdner());
        fc.setDialogTitle("Ausgabedatei");
        if (!ausgabe.getText().isBlank()) {
            fc.setSelectedFile(new File(ausgabe.getText()));
        }
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            String n = fc.getSelectedFile().getAbsolutePath();
            ausgabe.setText(fc.getSelectedFile().getName().contains(".") ? n : n + ".csv");
        }
    }

    private File startOrdner() {
        if (!eingabe.getText().isBlank()) {
            File f = new File(eingabe.getText()).getParentFile();
            if (f != null && f.isDirectory()) {
                return f;
            }
        }
        return null;
    }

    private void oeffneDatei(Path p) {
        if (p == null) {
            return;
        }
        try {
            Desktop.getDesktop().open(p.toFile());
        } catch (IOException | RuntimeException e) {
            fehler("Kann nicht geöffnet werden:\n" + p + "\n" + e.getMessage());
        }
    }

    // ------------------------------------------------------------- Auswertung

    private void starteAuswertung() {
        final Path ein;
        final Path aus;
        final LattenParameter lp;
        try {
            if (eingabe.getText().isBlank()) {
                throw new IllegalArgumentException("Bitte zuerst eine Eingabedatei wählen.");
            }
            ein = Path.of(eingabe.getText().strip());
            if (!Files.isRegularFile(ein)) {
                throw new IllegalArgumentException("Eingabedatei nicht gefunden:\n" + ein);
            }
            if (ausgabe.getText().isBlank()) {
                ausgabe.setText(Main.standardAusgabe(ein).toString());
            }
            aus = Path.of(ausgabe.getText().strip());
            if (aus.toAbsolutePath().normalize().equals(ein.toAbsolutePath().normalize())) {
                throw new IllegalArgumentException("Ausgabedatei darf nicht die Eingabedatei sein.");
            }
            lp = liesParameter();
        } catch (RuntimeException e) {
            fehler(e.getMessage());
            return;
        }
        Sitzung.speichere(sitzungAusFeldern());

        auswerten.setEnabled(false);
        protokoll.setText("");
        new SwingWorker<Main.Bericht, String>() {
            @Override
            protected Main.Bericht doInBackground() throws Exception {
                return Main.auswerten(ein, aus, lp, this::publish);
            }

            @Override
            protected void process(List<String> zeilen) {
                for (String z : zeilen) {
                    protokoll.append(z + "\n");
                }
            }

            @Override
            protected void done() {
                auswerten.setEnabled(true);
                protokoll.setCaretPosition(protokoll.getDocument().getLength());
                try {
                    Main.Bericht b = get();
                    letzteAusgabe = b.punktDatei().toAbsolutePath();
                    oeffnen.setEnabled(true);
                    letzteUeberhoehung = b.ueberhoehungDatei();
                    ueOeffnen.setEnabled(letzteUeberhoehung != null);
                    ordner.setEnabled(true);
                    String text = b.lagen() + " Lattenlagen (GML) ausgewertet"
                            + (b.codePaare() > 0 ? ", " + b.codePaare() + " Achs-Code-Paare" : "")
                            + ", " + b.achsen() + " Achspunkte."
                            + (b.ueberschreitungen() > 0
                                    ? "\n\nACHTUNG: " + b.ueberschreitungen() + " Lage(n) mit Überhöhung über dem "
                                      + "Grenzwert (EBO § 6 Abs. 3)!" : "");
                    if (b.warnungen().isEmpty()) {
                        JOptionPane.showMessageDialog(Fenster.this, text, Version.text(),
                                JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(Fenster.this,
                                text + "\n" + b.warnungen().size() + " Warnung(en) – siehe Protokoll.",
                                Version.text(), JOptionPane.WARNING_MESSAGE);
                    }
                } catch (Exception e) {
                    Throwable t = e.getCause() != null ? e.getCause() : e;
                    protokoll.append("\nFEHLER: " + t.getMessage() + "\n");
                    fehler("Auswertung fehlgeschlagen:\n" + t.getMessage());
                }
            }
        }.execute();
    }

    private void fehler(String text) {
        JOptionPane.showMessageDialog(this, text, Version.text(), JOptionPane.ERROR_MESSAGE);
    }

    // ------------------------------------------------------------------ Bilder

    private static List<Image> ladeIcons() {
        List<Image> liste = new ArrayList<>();
        for (String g : ICONS) {
            try (InputStream in = Fenster.class.getResourceAsStream("/de/gleismessung/bild/icon" + g + ".png")) {
                if (in != null) {
                    liste.add(ImageIO.read(in));
                }
            } catch (IOException ignored) {
                // ohne Icon weiter
            }
        }
        return liste;
    }

    /** Nimmt per Maus auf das Feld gezogene Messdateien für die Reflektorhöhe an. */
    private final class HDateiDrop extends TransferHandler {
        private static final long serialVersionUID = 1L;
        private final transient TransferHandler text = hDateien.getTransferHandler();

        @Override
        public boolean canImport(TransferSupport s) {
            return s.isDataFlavorSupported(DataFlavor.javaFileListFlavor) || text.canImport(s);
        }

        @Override
        public boolean importData(TransferSupport s) {
            if (s.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                try {
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) s.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    fuegeHDateienHinzu(files);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
            return text.importData(s);
        }
    }

    /** Nimmt eine per Maus ins Fenster gezogene Datei als Eingabe. */
    private final class DateiDrop extends TransferHandler {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean canImport(TransferSupport s) {
            return s.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
        }

        @Override
        public boolean importData(TransferSupport s) {
            try {
                if (s.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) s.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (!files.isEmpty()) {
                        setzeEingabe(files.get(0).toPath());
                        return true;
                    }
                }
            } catch (Exception e) {
                fehler("Datei konnte nicht übernommen werden:\n" + e.getMessage());
            }
            return false;
        }
    }

}
