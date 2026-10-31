"""Testbeispiel Reflektorhöhe aus Messdateien.

Simuliert eine Messung, bei der die Reflektorhöhe im Instrument eingetragen war:
die exportierte Punktliste ist dann um hr LOTRECHT reduziert (Bodenpunkt),
die Reflektorhöhe steht in den Rohdaten.

  Lagen  1–15  hr = 0,130 m   (Leica GSI-16, teils Einheit 1/10 mm, teils ohne WI 87 → wird übernommen)
  Lagen 16–25  hr = 0,200 m   (Trimble JobXML)
  Lagen 26–27  hr = 0,200 m   (LandXML)
  Lagen 28–29  hr = 0,200 m   (Text-Tabelle ohne Begriffe, mit Störspalten)
  Lage  30     hr = 0,130 m   in KEINER Rohdatei → Feldwert 0,130 m (Warnung)
  Masten       hr = 1,500 m   (außerhalb 0–0,5 m → ignoriert)
  Messstab SOL/SOR hr = 1,300 m (Achs-Codes, keine Korrektur)
  Überhöhung: Rampe 0 → 150 mm (rechts hoch), Lagen alle 5 m

Aufruf: python3 beispiel_hr_erzeugen.py <zielordner>
"""
import math
import random
import sys

random.seed(14)
ZIEL = sys.argv[1] if len(sys.argv) > 1 else "."
B, SPUR = 1.500, 1.458
E0, N0, Z0 = 512000.000, 5410000.000, 180.000
T = 50 * math.pi / 200            # Richtungswinkel der Gleisachse
SIG = 0.0005

def rausch(v):
    return v + random.gauss(0, SIG)

te, tn = math.sin(T), math.cos(T)    # Gleisrichtung (E, N)
re, rn = math.cos(T), -math.sin(T)   # rechte Normale

punkte = []    # (nr, E, N, Z_export, code, hr_instrument, quelle)
soll = []
for k in range(1, 31):
    s = 5.0 * (k - 1)
    u = 0.150 * (k - 1) / 29
    hr = 0.130 if (k <= 15 or k == 30) else 0.200
    quelle = ("gsi" if k <= 15 else "jxl" if k <= 25 else "landxml" if k <= 27 else "text" if k <= 29
              else "keine")
    e, n, z = E0 + s * te, N0 + s * tn, Z0 + 0.002 * s
    sa = u / B
    ca = math.sqrt(1 - sa * sa)
    links = (e - SPUR / 2 * ca * re, n - SPUR / 2 * ca * rn, z - SPUR / 2 * sa)
    rechts = (e + SPUR / 2 * ca * re, n + SPUR / 2 * ca * rn, z + SPUR / 2 * sa)
    nrm = (-sa * re, -sa * rn, ca)
    nr_l, nr_r = 3000 + 2 * k - 1, 3000 + 2 * k
    for nr, f, code in ((nr_l, links, "LI_GML"), (nr_r, rechts, "RE_GML")):
        prisma = tuple(a + hr * b for a, b in zip(f, nrm))
        # Export aus dem Instrument: Höhe lotrecht um hr reduziert
        punkte.append((nr, rausch(prisma[0]), rausch(prisma[1]), rausch(prisma[2]) - hr, code, hr, quelle))
    soll.append((k, nr_l, e, n, z, u, SPUR, hr))
    if k % 6 == 3:   # Mast mit Lotstab 1,500 m
        nr = 9000 + k
        punkte.append((nr, e - 3.1 * re, n - 3.1 * rn, z + 0.30, "MAST", 1.500, "gsi" if k <= 15 else "jxl"))
    if k in (18, 19, 20):   # Gleismessstab SOL/SOR direkt auf der Fahrkante (keine Korrektur)
        for nr, f, code in ((5000 + 2 * k - 1, links, "SOL"), (5000 + 2 * k, rechts, "SOR")):
            punkte.append((nr, rausch(f[0]) + 2.5 * te, rausch(f[1]) + 2.5 * tn, rausch(f[2]), code, 1.300, "jxl"))

# --- Punktliste (Export, Standardaufbau P R H Z C, Leerzeichen) ---------------
with open(f"{ZIEL}/beispiel_hr_punkte.txt", "w", encoding="utf-8", newline="\r\n") as f:
    for nr, e, n, z, code, hr, q in punkte:
        f.write(f"{nr:<6} {e:13.4f} {n:14.4f} {z:9.4f}  {code}\n")

# --- Leica GSI-16 -------------------------------------------------------------
def gsi(wi, info, wert, ziffern=16):
    vz = "-" if str(wert).startswith("-") else "+"
    w = str(wert).lstrip("-")
    return f"{wi:02d}{info}{vz}{w.rjust(ziffern, '0')} "

with open(f"{ZIEL}/beispiel_hr_teil1.gsi", "w", encoding="ascii", newline="\r\n") as f:
    blk = 1
    # Stationszeile: WI 88 = Instrumentenhöhe (darf NICHT als Reflektorhöhe gelesen werden)
    f.write("*" + gsi(11, f"{blk:04d}", "ST1") + gsi(84, "..10", 512010000) + gsi(85, "..10", 5409990000)
            + gsi(86, "..10", 181200) + gsi(88, "..10", 1612) + "\n")
    i = 0
    vorher = None
    for nr, e, n, z, code, hr, q in punkte:
        if q != "gsi":
            continue
        blk += 1
        i += 1
        zeile = ("*" + gsi(11, f"{blk:04d}", nr) + gsi(21, ".324", 12345678) + gsi(22, ".324", 9876543)
                 + gsi(31, "..00", 25432) + gsi(81, "..00", round(e * 1000)) + gsi(82, "..00", round(n * 1000))
                 + gsi(83, "..00", round(z * 1000)))
        if hr != vorher or i % 2 == 1:      # bei geänderter Höhe immer, sonst jede 2. Zeile
            if i % 4 == 1:
                zeile += gsi(87, "..16", round(hr * 10000))      # Einheit 1/10 mm
            else:
                zeile += gsi(87, "..10", round(hr * 1000))       # Einheit mm
        # sonst: kein WI 87 → zuletzt gültige Reflektorhöhe gilt
        vorher = hr
        f.write(zeile + gsi(71, "....", 0) + "\n")

# --- Trimble JobXML -----------------------------------------------------------
with open(f"{ZIEL}/beispiel_hr_teil2.jxl", "w", encoding="utf-8", newline="\r\n") as f:
    f.write('<?xml version="1.0" encoding="UTF-8"?>\n<JOBFile jobName="Gleis" product="Trimble Access" '
            'productVersion="2025.10" version="7.0">\n  <FieldBook>\n'
            '    <StationRecord ID="0001"><StationName>ST2</StationName><TheodoliteHeight>1.587</TheodoliteHeight>'
            '</StationRecord>\n')
    letzte = None
    tid = 100
    for nr, e, n, z, code, hr, q in punkte:
        if q != "jxl":
            continue
        if hr != letzte:
            tid += 1
            f.write(f'    <TargetRecord ID="{tid:08d}"><Prism><Type>Custom</Type></Prism>'
                    f'<MeasurementMethod>BottomOfMount</MeasurementMethod><TargetHeight>{hr:.3f}</TargetHeight>'
                    f'<PrismConstant>0</PrismConstant></TargetRecord>\n')
            letzte = hr
        f.write(f'    <PointRecord ID="P{nr}"><Name>{nr}</Name><Code>{code}</Code><Method>TargetFromStation</Method>'
                f'<Classification>Normal</Classification><Deleted>false</Deleted><TargetID>{tid:08d}</TargetID>'
                f'<StationID>0001</StationID></PointRecord>\n')
    f.write("  </FieldBook>\n</JOBFile>\n")

# --- LandXML ------------------------------------------------------------------
with open(f"{ZIEL}/beispiel_hr_teil3.xml", "w", encoding="utf-8", newline="\r\n") as f:
    f.write('<?xml version="1.0" encoding="UTF-8"?>\n<LandXML xmlns="http://www.landxml.org/schema/LandXML-1.2" '
            'version="1.2">\n  <Survey>\n    <InstrumentSetup id="S3" stationName="ST3" instrumentHeight="1.598">'
            '<InstrumentPoint pntRef="ST3"/></InstrumentSetup>\n    <ObservationGroup id="OG3" setupID="S3">\n')
    for nr, e, n, z, code, hr, q in punkte:
        if q == "landxml":
            f.write(f'      <RawObservation setupID="S3" targetHeight="{hr:.3f}" horizAngle="123.4567" '
                    f'zenithAngle="99.8765" slopeDistance="25.432"><TargetPoint pntRef="{nr}" code="{code}"/>'
                    f'</RawObservation>\n')
    f.write("    </ObservationGroup>\n  </Survey>\n</LandXML>\n")

# --- Text-Tabelle (Leerzeichen, Kopfzeile ohne feste Begriffe, Störspalten) ----
with open(f"{ZIEL}/beispiel_hr_teil4.txt", "w", encoding="utf-8", newline="\r\n") as f:
    f.write("Nr      Hz        V         S       IH     T      PK       DQ     Satz Code\n")
    f.write("ST4     0.0000    100.0000  0.000   1.598  0.000  0.0000   0.000  0    STATION\n")
    for nr, e, n, z, code, hr, q in punkte:
        if q != "text":
            continue
        for satz in (1, 2):
            f.write(f"{nr:<6}  {random.uniform(0, 400):8.4f}  {random.uniform(98, 102):8.4f}  "
                    f"{random.uniform(15, 40):6.3f}  1.598  {hr:.3f}  -0.0344  {random.uniform(0, 0.04):.3f}  "
                    f"{satz}    {code}\n")

# --- Sollwerte ----------------------------------------------------------------
with open(f"{ZIEL}/beispiel_hr_soll.csv", "w", encoding="utf-8", newline="\r\n") as f:
    f.write("Lage;Achspunkt;R_Achse;H_Achse;Z_Achse;u_mm;Spurweite;hr\n")
    for k, nr, e, n, z, u, sp, hr in soll:
        f.write(f"{k};{nr}Achse;{e:.4f};{n:.4f};{z:.4f};{u*1000:.1f};{sp:.4f};{hr:.3f}\n")
print(len(punkte), "Punkte")
