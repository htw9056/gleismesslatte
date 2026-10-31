// Windows-Starter für Gleismesslatte.jar
//
// Aus derselben Quelle entstehen zwei Programme:
//
//	Gleismesslatte.exe         Fenster-Programm (ohne Konsole, startet javaw.exe)
//	                           Doppelklick -> Programmfenster
//	                           CSV auf die .exe ziehen -> Fenster mit vorgewählter Datei
//	GleismesslatteKonsole.exe  Kommandozeile (startet java.exe), z. B.
//	                           GleismesslatteKonsole.exe messung.csv ergebnis.csv achse=nein
//
// Suchreihenfolge für Java:
//  1. runtime\bin neben der .exe (mitgelieferte Laufzeit)
//  2. JAVA_HOME\bin
//  3. PATH
//  4. übliche Installationsorte (Adoptium, Oracle, Microsoft, Zulu, Corretto,
//     BellSoft) und die von Eclipse mitgebrachte Laufzeit (JustJ)
//
// Bauen (auch unter Linux, Icon kommt aus rsrc_windows_amd64.syso):
//
//	GOOS=windows GOARCH=amd64 go build -ldflags "-s -w -H windowsgui -X main.modus=gui" -o Gleismesslatte.exe
//	GOOS=windows GOARCH=amd64 go build -ldflags "-s -w" -o GleismesslatteKonsole.exe
package main

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"sort"
	"syscall"
	"unsafe"
)

const jarName = "Gleismesslatte.jar"

// modus wird beim Bauen gesetzt: "gui" oder leer (Konsole).
var modus = ""

func main() {
	gui := modus == "gui"
	javaExe := "java.exe"
	if gui {
		javaExe = "javaw.exe"
	}

	exe, err := os.Executable()
	if err != nil {
		fehler("Programmpfad nicht ermittelbar: " + err.Error())
	}
	dir := filepath.Dir(exe)

	jar := filepath.Join(dir, jarName)
	if !existiert(jar) {
		fehler(jarName + " fehlt.\n\nDie Datei muss im selben Ordner liegen wie die .exe:\n" + dir)
	}

	java := findeJava(dir, javaExe)
	if java == "" {
		fehler("Keine Java-Laufzeit gefunden (Java 17 oder neuer erforderlich).\n\n" +
			"Lösungen:\n" +
			"  • Java installieren, z. B. Eclipse Temurin 21 (adoptium.net)\n" +
			"  • oder JAVA_HOME auf ein vorhandenes JDK setzen\n" +
			"  • oder die Version mit eingebauter Laufzeit verwenden (exe_bauen.bat)")
	}

	args := append([]string{"-Dfile.encoding=UTF-8", "-jar", jar}, os.Args[1:]...)
	cmd := exec.Command(java, args...)
	cmd.Dir = dir
	if !gui {
		cmd.Stdin, cmd.Stdout, cmd.Stderr = os.Stdin, os.Stdout, os.Stderr
	}
	if err := cmd.Run(); err != nil {
		if ee, ok := err.(*exec.ExitError); ok {
			if gui && ee.ExitCode() != 0 {
				fehler(fmt.Sprintf("Gleismesslatte wurde mit Fehlercode %d beendet.\n\nJava: %s\n\n"+
					"Hinweis: Java 17 oder neuer ist erforderlich.", ee.ExitCode(), java))
			}
			os.Exit(ee.ExitCode())
		}
		fehler("Start von Java fehlgeschlagen:\n" + java + "\n\n" + err.Error())
	}
}

func findeJava(dir, javaExe string) string {
	kandidaten := []string{filepath.Join(dir, "runtime", "bin", javaExe)}
	if jh := os.Getenv("JAVA_HOME"); jh != "" {
		kandidaten = append(kandidaten, filepath.Join(jh, "bin", javaExe))
	}
	for _, k := range kandidaten {
		if existiert(k) {
			return k
		}
	}
	if p, err := exec.LookPath(javaExe); err == nil {
		return p
	}

	var muster []string
	for _, pf := range []string{os.Getenv("ProgramFiles"), os.Getenv("ProgramW6432")} {
		if pf == "" {
			continue
		}
		for _, hersteller := range []string{"Eclipse Adoptium", "Java", "Microsoft", "Zulu", "Amazon Corretto", "BellSoft"} {
			muster = append(muster, filepath.Join(pf, hersteller, "*", "bin", javaExe))
		}
	}
	if up := os.Getenv("USERPROFILE"); up != "" {
		// Von Eclipse mitgebrachte Laufzeit (JustJ)
		muster = append(muster, filepath.Join(up, ".p2", "pool", "plugins",
			"org.eclipse.justj.openjdk.hotspot.jre.*", "jre", "bin", javaExe))
	}
	var treffer []string
	for _, m := range muster {
		if t, _ := filepath.Glob(m); len(t) > 0 {
			treffer = append(treffer, t...)
		}
	}
	if len(treffer) == 0 {
		return ""
	}
	// neueste Version zuerst (Pfadname enthält die Versionsnummer)
	sort.Sort(sort.Reverse(sort.StringSlice(treffer)))
	return treffer[0]
}

func existiert(p string) bool {
	st, err := os.Stat(p)
	return err == nil && !st.IsDir()
}

// fehler zeigt eine Windows-Meldung und beendet das Programm.
func fehler(text string) {
	fmt.Fprintln(os.Stderr, "Fehler: "+text)
	user32 := syscall.NewLazyDLL("user32.dll")
	box := user32.NewProc("MessageBoxW")
	t, _ := syscall.UTF16PtrFromString(text)
	c, _ := syscall.UTF16PtrFromString("Gleismesslatte")
	const mbIconError = 0x10
	box.Call(0, uintptr(unsafe.Pointer(t)), uintptr(unsafe.Pointer(c)), mbIconError)
	os.Exit(1)
}
