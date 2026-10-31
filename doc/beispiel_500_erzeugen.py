"""Erzeugt das Testbeispiel mit 500 Punkten aus einer simulierten Gleisgeometrie.

Ausgabe (im Zielordner):
  beispiel_500.txt         Leerzeichen getrennt, Aufbau P R H Z C, keine Kopfzeile (Standard)
  beispiel_500_csv.csv     Semikolon, Aufbau P C R H Z, 3 Kopfzeilen (Formattest)
  beispiel_500_soll.csv    Sollwerte (wahre Gleisachse, Überhöhung, Spurweite) je Paar

Aufruf: python3 beispiel_500_erzeugen.py <zielordner>
"""
import math
import random
import sys

random.seed(500)
ZIEL = sys.argv[1] if len(sys.argv) > 1 else "."

H_REFL = 0.130          # Reflektorhöhe
B = 1.500               # Bezugsbasis Überhöhung
SOLL = 1.458            # Sollspurweite
SIGMA = 0.0007          # Messrauschen je Koordinate [m]
E0, N0, Z0 = 456000.000, 5420000.000, 250.000
GON = math.pi / 200

# ---------------------------------------------------------------- Trassierung
# Krümmung k(s): + = Rechtsbogen, - = Linksbogen; Übergangsbögen linear
def kruemmung(s):
    if s < 150:
        return 0.0
    if s < 270:
        return (s - 150) / 120 * (1 / 500)
    if s < 420:
        return 1 / 500
    if s < 520:
        return (520 - s) / 100 * (1 / 500)
    if s < 620:
        return 0.0
    if s < 680:
        return -(s - 620) / 60 * (1 / 400)
    return -1 / 400

def ueberhoehung(s):           # u > 0: rechte Schiene höher
    if s < 150:
        return 0.0
    if s < 270:
        return (s - 150) / 120 * 0.120
    if s < 420:
        return 0.120
    if s < 520:
        return (520 - s) / 100 * 0.120
    if s < 620:
        return 0.0
    if s < 680:
        return -(s - 620) / 60 * 0.090
    return -0.090

# Achse numerisch integrieren (Schritt 0,1 m)
SCHRITT = 0.1
achse = [(0.0, E0, N0, 60 * GON)]   # s, E, N, Richtungswinkel
s, e, n, t = 0.0, E0, N0, 60 * GON
while s < 820:
    k = kruemmung(s + SCHRITT / 2)
    tm = t + k * SCHRITT / 2
    e += SCHRITT * math.sin(tm)
    n += SCHRITT * math.cos(tm)
    t += k * SCHRITT
    s = round(s + SCHRITT, 6)
    achse.append((s, e, n, t))

def lage(s):
    i = min(int(round(s / SCHRITT)), len(achse) - 1)
    _, e, n, t = achse[i]
    return e, n, t

def hoehe(s):
    return Z0 + 0.004 * s

def rausch(v):
    return v + random.gauss(0, SIGMA)

# ---------------------------------------------------------------- Punkte
zeilen = []        # (punkt, R, H, Z, code) oder ("#", text) oder ("!", rohzeile)
soll = []          # Sollwerte je Paar

def schienenpunkte(s, spur, u, prisma):
    """Linker/rechter Punkt auf SO (prisma=False) oder Prismenmitte (prisma=True)."""
    e, n, t = lage(s)
    z = hoehe(s)
    re = (math.cos(t), -math.sin(t))          # rechte Normale (E, N)
    sa = u / B
    ca = math.sqrt(1 - sa * sa)
    links = (e - spur / 2 * ca * re[0], n - spur / 2 * ca * re[1], z - spur / 2 * sa)
    rechts = (e + spur / 2 * ca * re[0], n + spur / 2 * ca * re[1], z + spur / 2 * sa)
    if prisma:
        nrm = (-sa * re[0], -sa * re[1], ca)
        links = tuple(a + H_REFL * b for a, b in zip(links, nrm))
        rechts = tuple(a + H_REFL * b for a, b in zip(rechts, nrm))
    return links, rechts, (e, n, z)

def punkt(nr, p, code):
    zeilen.append((str(nr), rausch(p[0]), rausch(p[1]), rausch(p[2]), code))

def paar(s, nr_l, nr_r, code_l, code_r, prisma=True, umgekehrt=False, spur=None, merken=True, u=None):
    spur = spur if spur is not None else SOLL + random.uniform(-0.003, 0.006)
    u = ueberhoehung(s) if u is None else u
    l, r, m = schienenpunkte(s, spur, u, prisma)
    folge = [(nr_r, r, code_r), (nr_l, l, code_l)] if umgekehrt else [(nr_l, l, code_l), (nr_r, r, code_r)]
    if merken:
        soll.append((s, min(nr_l, nr_r), m, u, spur, "GML" if prisma else "Code"))
    return folge

def sonstig(nr, s, versatz, code, dz=0.0):
    e, n, t = lage(s)
    re = (math.cos(t), -math.sin(t))
    p = (e + versatz * re[0], n + versatz * re[1], hoehe(s) + dz)
    punkt(nr, p, code)

sonst_nr = 9001
def sonstige_bis(s_bis, s_von):
    """Masten alle 25 m abwechselnd links/rechts, Festpunkte alle 100 m."""
    global sonst_nr
    for s in range(int(s_von), int(s_bis)):
        if s % 25 == 0:
            sonstig(sonst_nr, s, 3.2 if (s // 25) % 2 else -3.2, "MAST", 0.2)
            sonst_nr += 1
        if s % 100 == 40:
            sonstig(sonst_nr, s, -5.0, "FP", -0.6)
            sonst_nr += 1

# A: Gerade, u = 0, Code GML, aufeinanderfolgend
zeilen.append(("#", "A  km 0,000-0,150  Gerade, u = 0, Code GML (aufeinanderfolgend)"))
nr = 1001
for s in range(0, 151, 5):
    sonstige_bis(s + 5, s)
    for p in paar(s, nr, nr + 1, "GML", "GML"):
        punkt(*p)
    nr += 2

# B: Übergangsbogen rechts, Rampe 0 -> 120 mm, LI_GML/RE_GML, jede 3. Lage umgekehrt gemessen
zeilen.append(("#", "B  km 0,155-0,265  Übergang Rechtsbogen, Rampe 0->120 mm, LI_GML/RE_GML, teils umgekehrt"))
nr = 2001
for i, s in enumerate(range(155, 270, 5)):
    sonstige_bis(s + 5, s)
    for p in paar(s, nr, nr + 1, "LI_GML", "RE_GML", umgekehrt=(i % 3 == 1)):
        punkt(*p)
    nr += 2

# C: Rechtsbogen R = 500, u = 120 mm, gml01/GML02 (Groß/klein gemischt)
zeilen.append(("#", "C  km 0,270-0,415  Rechtsbogen R=500, u = 120 mm, gml01/GML02"))
nr = 3001
for i, s in enumerate(range(270, 420, 5)):
    sonstige_bis(s + 5, s)
    for p in paar(s, nr, nr + 1, "gml01" if i % 2 else "GML01", "GML02"):
        punkt(*p)
    nr += 2

# D: Übergang zurück, Rampe 120 -> 0, FREIE Reihenfolge: erst alle linken vorwärts, dann alle rechten rückwärts
zeilen.append(("#", "D  km 0,420-0,515  Übergang, Rampe 120->0 mm, GML-L/GML-R in FREIER Reihenfolge"))
zeilen.append(("#", "   (erst alle linken Punkte vorwärts, dann alle rechten rückwärts -> nur mit Paarbildung 'frei')"))
nr = 4001
linke, rechte = [], []
for s in range(420, 520, 5):
    l, r = paar(s, nr, nr + 1, "GML-L", "GML-R")
    linke.append(l)
    rechte.append(r)
    nr += 2
sonstige_bis(520, 420)
for p in linke:
    punkt(*p)
for p in reversed(rechte):
    punkt(*p)

# E: Gerade mit Bahnsteig, Achs-Codes SOL/SOR direkt auf SO (ohne Latte, KEINE Korrektur)
zeilen.append(("#", "E  km 0,520-0,615  Gerade, Achs-Codes SOL/SOR (direkt auf SO, ohne Korrektur), Bahnsteig BSK"))
nr = 5001
for i, s in enumerate(range(520, 620, 5)):
    sonstige_bis(s + 5, s)
    cl = "sol" if i % 4 == 2 else "SOL"
    cr = "Sor" if i % 5 == 3 else "SOR"
    for p in paar(s, nr, nr + 1, cl, cr, prisma=False):
        punkt(*p)
    nr += 2
    sonstig(sonst_nr, s + 2.5, -1.67, "BSK", 0.76)   # Bahnsteigkante 1,67 m links der Achse
    sonst_nr += 1

# F: Übergang + Linksbogen R = 400, Rampe 0 -> 90 mm links hoch, L.GML/R.GML (Suffix mit Punkt)
zeilen.append(("#", "F  km 0,620-0,755  Übergang + Linksbogen R=400, u 0->90 mm LINKS hoch, L.GML/R.GML"))
nr = 6001
for s in range(620, 760, 5):
    sonstige_bis(s + 5, s)
    for p in paar(s, nr, nr + 1, "L.GML", "R.GML"):
        punkt(*p)
    nr += 2

# G: dichte Lattenlagen alle 0,5 m -> bei Paarbildung 'frei' Warnung "nicht eindeutig"
zeilen.append(("#", "G  km 0,760-0,7695 dichte Lagen alle 0,5 m, Code GML (bei 'frei': Warnung nicht eindeutig)"))
nr = 7001
for k in range(20):
    s = 760 + 0.5 * k
    for p in paar(s, nr, nr + 1, "GML", "GML"):
        punkt(*p)
    nr += 2

# H: Sonderfälle
zeilen.append(("#", "H  km 0,775-0,800  Sonderfälle"))
zeilen.append(("#", "   8001/8002 Spurweite 1,600 m -> außerhalb Toleranz, nicht gepaart"))
for p in paar(775, 8001, 8002, "GML", "GML", spur=1.600, merken=False):
    punkt(*p)
zeilen.append(("#", "   8011/8012 Überhöhung 172 mm -> Grenzwert 180 mm (EBO § 6 Abs. 3) eingehalten"))
for p in paar(776, 8011, 8012, "GML", "GML", u=0.172):
    punkt(*p)
zeilen.append(("#", "   8013/8014 Überhöhung 195 mm rechts hoch -> GRENZWERT 180 mm ÜBERSCHRITTEN"))
for p in paar(777, 8013, 8014, "GML", "GML", u=0.195):
    punkt(*p)
zeilen.append(("#", "   8015/8016 Achs-Codes SOL/SOR, Überhöhung 188 mm links hoch -> ÜBERSCHRITTEN"))
for p in paar(778, 8015, 8016, "SOL", "SOR", prisma=False, u=-0.188):
    punkt(*p)
zeilen.append(("#", "   8003 einzelner GML-Punkt ohne Partner"))
l, _, _ = schienenpunkte(780, SOLL, ueberhoehung(780), True)
punkt(8003, l, "GML")
zeilen.append(("#", "   8004 einzelner Achs-Code-Punkt SOL ohne Partner"))
l, _, _ = schienenpunkte(785, SOLL, ueberhoehung(785), False)
punkt(8004, l, "SOL")
zeilen.append(("#", "   8005 fehlerhafte Zeile (Höhe fehlt) -> Warnung, wird übersprungen"))
e, n, _ = lage(788)
zeilen.append(("!", f"8005 {e:.3f} {n:.3f}"))
zeilen.append(("#", "   8006 Dezimalkomma, 8007 Code mit Leerzeichen"))
e, n, _ = lage(790)
zeilen.append(("!", f"8006 {e:.3f}".replace(".", ",") + f" {n:.3f}".replace(".", ",")
               + f" {hoehe(790) - 0.8:.3f}".replace(".", ",") + " KS"))
sonstig(8007, 795, 4.0, "SIGNAL ASIG 12", 1.5)

# Auffüllen mit Kabelschacht-/Kabelkanalpunkten bis genau 500 gültige Punkte
def gueltig():
    return sum(1 for z in zeilen if z[0] not in ("#", "!")) + 1   # +1 für 8006 (Dezimalkomma)
s = 0.0
zeilen.append(("#", "K  Kabelkanal KK links (Füllpunkte bis 500 Punkte)"))
while gueltig() < 500:
    sonstig(sonst_nr, s, -2.4, "KK", -0.3)
    sonst_nr += 1
    s += 7.0

# ---------------------------------------------------------------- schreiben
def fmt(v):
    return f"{v:.3f}"

with open(f"{ZIEL}/beispiel_500.txt", "w", encoding="utf-8", newline="\r\n") as f:
    f.write("# Gleismesslatte – Beispiel mit 500 Punkten (Aufbau P R H Z C, Leerzeichen, keine Kopfzeile)\n")
    f.write("# Abschnitte A-H siehe Kommentare; Sollwerte in beispiel_500_soll.csv\n")
    for z in zeilen:
        if z[0] == "#":
            f.write("# " + z[1] + "\n")
        elif z[0] == "!":
            f.write(z[1] + "\n")
        else:
            f.write(f"{z[0]:<6} {fmt(z[1]):>12} {fmt(z[2]):>13} {fmt(z[3]):>9}  {z[4]}\n")

with open(f"{ZIEL}/beispiel_500_csv.csv", "w", encoding="utf-8", newline="\r\n") as f:
    f.write("Gleismesslatte Beispiel 500 Punkte – CSV-Variante\n")
    f.write("Aufbau P C R H Z, 3 Kopfzeilen überspringen\n")
    f.write("Punkt;Code;Rechtswert;Hochwert;Hoehe\n")
    for z in zeilen:
        if z[0] == "#":
            continue
        if z[0] == "!":
            t = z[1].split()
            if len(t) == 5:      # 8006 Dezimalkomma
                f.write(f"{t[0]};{t[4]};{t[1]};{t[2]};{t[3]}\n")
            else:                # 8005 fehlerhaft
                f.write(f"{t[0]};;{t[1]};{t[2]}\n")
            continue
        f.write(f"{z[0]};{z[4]};{fmt(z[1]).replace('.', ',')};{fmt(z[2]).replace('.', ',')};"
                f"{fmt(z[3]).replace('.', ',')}\n")

with open(f"{ZIEL}/beispiel_500_soll.csv", "w", encoding="utf-8", newline="\r\n") as f:
    f.write("Station;Achspunkt;Art;R_Achse;H_Achse;Z_Achse;u_mm_rechts_hoch;Spurweite\n")
    for s, nr, m, u, spur, art in soll:
        f.write(f"{s:.1f};{nr}Achse;{art};{m[0]:.4f};{m[1]:.4f};{m[2]:.4f};{u * 1000:.1f};{spur:.4f}\n")

print("gültige Punkte:", gueltig(), " Paare (Soll):", len(soll))
