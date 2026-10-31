package de.gleismessung;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Speichert alle Eingaben des Programmfensters (Dateien, Parameter, Fenstergröße) und
 * lädt sie beim nächsten Start wieder.
 *
 * <p>Ablage je Benutzer: Windows {@code %APPDATA%\Gleismesslatte\sitzung.properties},
 * sonst {@code ~/.gleismesslatte/sitzung.properties}. Mit der Java-Eigenschaft
 * {@code -Dgleismesslatte.sitzung=<datei>} lässt sich ein anderer Ort wählen.</p>
 */
final class Sitzung {

    private Sitzung() {
    }

    static Path datei() {
        String eigen = System.getProperty("gleismesslatte.sitzung");
        if (eigen != null && !eigen.isBlank()) {
            return Path.of(eigen);
        }
        String appdata = System.getenv("APPDATA");
        Path ordner = appdata != null && !appdata.isBlank()
                ? Path.of(appdata, "Gleismesslatte")
                : Path.of(System.getProperty("user.home"), ".gleismesslatte");
        return ordner.resolve("sitzung.properties");
    }

    /** Lädt die letzte Sitzung; leer, wenn keine vorhanden oder nicht lesbar. */
    static Properties lade() {
        Properties p = new Properties();
        Path d = datei();
        if (Files.isRegularFile(d)) {
            try (Reader r = Files.newBufferedReader(d, StandardCharsets.UTF_8)) {
                p.load(r);
            } catch (IOException | IllegalArgumentException e) {
                // beschädigte Datei – mit Standardwerten weiter
                p.clear();
            }
        }
        return p;
    }

    /** Speichert die Sitzung; Fehler werden ignoriert (z. B. kein Schreibrecht). */
    static boolean speichere(Properties p) {
        Path d = datei();
        try {
            Files.createDirectories(d.toAbsolutePath().getParent());
            try (Writer w = Files.newBufferedWriter(d, StandardCharsets.UTF_8)) {
                p.store(w, Version.textMitHersteller() + " – letzte Eingaben (wird automatisch geschrieben)");
            }
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
