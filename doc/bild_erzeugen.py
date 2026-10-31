"""Erzeugt die Bilder der Gleismesslatte für Programm und Doku.

Aufruf: python3 bild_erzeugen.py <zielordner> <version>
Ergebnis: szene.svg (3D-Ansicht) und detail.svg (Detail Prisma, Formeln)
"""
import math
import sys

ZIEL = sys.argv[1]
VERSION = sys.argv[2] if len(sys.argv) > 2 else "0.13"

# --- Geometrie (Meter) -------------------------------------------------------
U = 0.150          # Überhöhung auf 1,500 m
B = 1.500          # Bezugsbasis / Schienenmittenabstand
ALPHA = math.asin(U / B)
H = 0.130          # Prismenhöhe über SO, senkrecht zur Latte
CA, SA = math.cos(ALPHA), math.sin(ALPHA)

def cant(x, z):
    """Lokale Querschnittskoordinaten (x quer, z senkrecht zur Gleisebene) -> global."""
    return x * CA - z * SA, x * SA + z * CA

# --- Kamera ------------------------------------------------------------------
W, HH = 900, 610
CAM = (-0.45, -3.05, 1.02)    # Kameraposition
TGT = (0.06, 0.8, -0.16)     # Blickziel
F = 900                      # Brennweite in Pixel

def sub(a, b): return (a[0]-b[0], a[1]-b[1], a[2]-b[2])
def cross(a, b): return (a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0])
def norm(a):
    l = math.sqrt(sum(c*c for c in a)); return (a[0]/l, a[1]/l, a[2]/l)
def dot(a, b): return sum(x*y for x, y in zip(a, b))

FWD = norm(sub(TGT, CAM))
RIGHT = norm(cross(FWD, (0, 0, 1)))
UP = cross(RIGHT, FWD)

def proj(p):
    d = sub(p, CAM)
    cx, cy, cz = dot(d, RIGHT), dot(d, UP), dot(d, FWD)
    return (452 + F*cx/cz, HH/2 + 5 - F*cy/cz, cz)

def P(x, y, z):
    gx, gz = cant(x, z)
    return (gx, y, gz)

svg = []
def poly(pts3, fill, stroke="none", sw=0.8, extra=""):
    pts = [proj(p) for p in pts3]
    s = " ".join(f"{p[0]:.1f},{p[1]:.1f}" for p in pts)
    svg.append(f'<polygon points="{s}" fill="{fill}" stroke="{stroke}" stroke-width="{sw}" '
               f'stroke-linejoin="round" {extra}/>')

def screen_area(pts3):
    pts = [proj(p) for p in pts3]
    a = 0
    for i in range(len(pts)):
        x1, y1 = pts[i][:2]; x2, y2 = pts[(i+1) % len(pts)][:2]
        a += x1*y2 - x2*y1
    return a / 2

def shade(base, n):
    """Einfache Lambert-Schattierung, base als (r,g,b)."""
    light = norm((-0.4, -0.6, 1.0))
    k = 0.55 + 0.45 * max(0.0, dot(n, light))
    return "#%02x%02x%02x" % tuple(min(255, int(c*k)) for c in base)

def extrude(profile, y0, y1, base, stroke="#00000040", caps=True):
    """Profil (Liste lokaler (x,z), gegen den Uhrzeigersinn) entlang Y von y0..y1 extrudieren."""
    faces = []
    n = len(profile)
    for i in range(n):
        x1, z1 = profile[i]; x2, z2 = profile[(i+1) % n]
        q = [P(x1, y0, z1), P(x2, y0, z2), P(x2, y1, z2), P(x1, y1, z1)]
        # Aussennormale im Querschnitt
        ex, ez = x2-x1, z2-z1
        nx, nz = cant(ez, -ex)
        l = math.hypot(nx, nz) or 1
        faces.append((q, (nx/l, 0, nz/l)))
    if caps:
        faces.append(([P(x, y0, z) for x, z in reversed(profile)], (0, -1, 0)))
    drawn = []
    for q, nrm in faces:
        if screen_area(q) < 0:       # Rückseite ausblenden
            continue
        depth = sum(proj(p)[2] for p in q) / len(q)
        drawn.append((depth, q, nrm))
    for _, q, nrm in sorted(drawn, key=lambda t: -t[0]):
        poly(q, shade(base, nrm), stroke, 0.6)

def box(x0, x1, z0, z1, y0, y1, base, stroke="#00000055"):
    extrude([(x0, z0), (x1, z0), (x1, z1), (x0, z1)], y0, y1, base, stroke)

# --- Schienenprofil (vereinfachtes 60E1, Ursprung = Fahrkante Kopfmitte oben) --
def schiene(xm):
    return [(xm-0.075, -0.172), (xm+0.075, -0.172), (xm+0.075, -0.160), (xm+0.010, -0.140),
            (xm+0.008, -0.050), (xm+0.036, -0.035), (xm+0.036, -0.004), (xm+0.030, 0.0),
            (xm-0.030, 0.0), (xm-0.036, -0.004), (xm-0.036, -0.035), (xm-0.008, -0.050),
            (xm-0.010, -0.140), (xm-0.075, -0.160)]

Y0, Y1 = -0.95, 2.9
XL, XR = -0.75, 0.75

# Hintergrund
svg.append(f'<rect width="{W}" height="{HH}" fill="#f4f6f8"/>')

# Schotterbett (geneigte Fläche unter den Schwellen)
poly([P(-1.75, Y0, -0.40), P(1.75, Y0, -0.40), P(1.75, Y1, -0.40), P(-1.75, Y1, -0.40)],
     "#b9b4aa", "#a7a297", 1)
# Schotterkörnung
import random
random.seed(4)
for _ in range(900):
    x = random.uniform(-1.7, 1.7); y = random.uniform(Y0, Y1)
    sx, sy, sz = proj(P(x, y, -0.39))
    r = max(0.6, 26 / sz)
    g = random.randint(120, 175)
    svg.append(f'<circle cx="{sx:.1f}" cy="{sy:.1f}" r="{r:.1f}" fill="rgb({g},{g-6},{g-14})" opacity="0.55"/>')

# Schwellen (von hinten nach vorne)
for ys in sorted([y for y in [-0.75, -0.15, 0.45, 1.05, 1.65, 2.25, 2.80]], reverse=True):
    box(-1.30, 1.30, -0.392, -0.172, ys-0.13, ys+0.13, (150, 150, 146))

# Schienen: hinterer Teil, dann vorderer Teil
for (ya, yb) in [(0.04, Y1), (Y0, -0.04)]:
    for xm in (XL, XR):
        extrude(schiene(xm), ya, yb, (165, 162, 158), "#00000050", caps=(ya == Y0))
    # Fahrfläche blank
    for xm in (XL, XR):
        poly([P(xm-0.026, ya, 0.0005), P(xm+0.026, ya, 0.0005),
              P(xm+0.026, yb, 0.0005), P(xm-0.026, yb, 0.0005)], "#dfe3e6", "none")

# Schienenstück unter der Latte
for xm in (XL, XR):
    extrude(schiene(xm), -0.04, 0.04, (165, 162, 158), "#00000050", caps=False)

# --- Gleismesslatte ----------------------------------------------------------
LY0, LY1 = -0.025, 0.025
# Fahrkanten = Innenkante Schienenkopf
FKL, FKR = XL + 0.036, XR - 0.036
# Lattenkörper (gelb)
box(-0.86, 0.86, 0.0, 0.034, LY0, LY1, (242, 184, 30), "#6a4b00")
# links: fester Anschlag – Messkopf liegt an der Fahrkante an
box(FKL, FKL + 0.030, -0.040, 0.034, -0.030, 0.030, (70, 74, 80), "#222")
# rechts: verschiebbarer Messkopf, per Spannfeder gegen die Fahrkante gedrückt
box(FKR - 0.030, FKR, -0.040, 0.034, -0.030, 0.030, (70, 74, 80), "#222")
box(FKR - 0.150, FKR - 0.030, 0.034, 0.048, -0.020, 0.020, (150, 155, 160), "#333")   # Führung
# Griff in der Mitte
box(-0.09, 0.09, 0.034, 0.05, -0.012, 0.012, (40, 40, 40))
# Libelle
box(0.20, 0.30, 0.034, 0.046, -0.010, 0.010, (110, 190, 120), "#1e5b28")

# Prismenstäbe senkrecht zur Latte, genau über der Fahrkante + Prismen
prismen = []
for xm, aussen in ((FKL, 1), (FKR, -1)):
    box(xm - 0.007, xm + 0.007, 0.034, H - 0.032, -0.007, 0.007, (195, 198, 202), "#555")
    c = P(xm, -0.0, H)
    prismen.append((xm, c))

# Spannfeder (Zickzack) zwischen Führung und verschiebbarem Messkopf
fa = proj(P(FKR - 0.150, -0.032, 0.041)); fe = proj(P(FKR - 0.032, -0.032, 0.041))
pts = []
for k in range(13):
    t = k / 12
    xx = fa[0] + (fe[0] - fa[0]) * t
    yy = fa[1] + (fe[1] - fa[1]) * t + (0 if k in (0, 12) else (-5 if k % 2 else 5))
    pts.append(f"{xx:.1f},{yy:.1f}")
svg.append(f'<polyline points="{" ".join(pts)}" fill="none" stroke="#1d2430" stroke-width="1.6"/>')

for xm, c in prismen:
    sx, sy, sz = proj(c)
    r = 0.034 * F / sz
    svg.append(f'<circle cx="{sx:.1f}" cy="{sy:.1f}" r="{r*1.25:.1f}" fill="#d6531c" stroke="#7a2a08" stroke-width="1.2"/>')
    svg.append(f'<circle cx="{sx:.1f}" cy="{sy:.1f}" r="{r*0.95:.1f}" fill="url(#glas)" stroke="#333" stroke-width="0.8"/>')
    # Prismenfacetten
    for k in range(3):
        a = math.radians(90 + 120*k)
        svg.append(f'<line x1="{sx:.1f}" y1="{sy:.1f}" x2="{sx + r*0.95*math.cos(a):.1f}" '
                   f'y2="{sy - r*0.95*math.sin(a):.1f}" stroke="#ffffff60" stroke-width="0.9"/>')
    svg.append(f'<circle cx="{sx - r*0.3:.1f}" cy="{sy - r*0.35:.1f}" r="{r*0.22:.1f}" fill="#ffffffaa"/>')


# --- Beschriftung (groß, mit weißem Rand für Lesbarkeit) ----------------------
def txt(x, y, s, size=20, anchor="start", weight="normal", fill="#1d2430", ziel=None):
    (ziel if ziel is not None else svg).append(
        f'<text x="{x:.1f}" y="{y:.1f}" font-family="DejaVu Sans, Arial, sans-serif" font-size="{size}" '
        f'text-anchor="{anchor}" font-weight="{weight}" fill="{fill}" stroke="#ffffff" stroke-width="5" '
        f'stroke-linejoin="round" paint-order="stroke">{s}</text>')

def line(a, b, col="#1d2430", sw=1.6, dash=None, ziel=None):
    d = f' stroke-dasharray="{dash}"' if dash else ""
    (ziel if ziel is not None else svg).append(
        f'<line x1="{a[0]:.1f}" y1="{a[1]:.1f}" x2="{b[0]:.1f}" y2="{b[1]:.1f}" stroke="{col}" stroke-width="{sw}"{d}/>')

# Lotlinien (gestrichelt) vom Prisma nach unten
for xm, c in prismen:
    a = proj(c); b = proj((c[0], c[1], c[2] - 0.25))
    line(a, b, "#1565c0", 1.6, "6,4")

# Gleisachse
gmp = proj(P(0.0, 0.0, 0.0))
svg.append(f'<circle cx="{gmp[0]:.1f}" cy="{gmp[1]:.1f}" r="7" fill="#c62828" stroke="white" stroke-width="2"/>')

pl = proj(prismen[0][1]); pr = proj(prismen[1][1])
# Prisma 1 (links)
line((pl[0] - 6, pl[1] - 16), (pl[0] - 40, pl[1] - 92), "#333", 1.4)
txt(16, pl[1] - 128, "Prisma 1 (Code z. B. LI_GML)", 24, "start", "bold")
txt(16, pl[1] - 100, "an der Fahrkante, fester Anschlag", 20)
# Prisma 2 (rechts)
line((pr[0] + 4, pr[1] - 18), (pr[0] + 20, pr[1] - 92), "#333", 1.4)
txt(W - 16, pr[1] - 156, "Prisma 2 (Code z. B. RE_GML)", 24, "end", "bold")
txt(W - 16, pr[1] - 128, "an der Fahrkante, per Spannfeder angedrückt", 20, "end")
txt(W - 16, pr[1] - 102, "Stab ⊥ zur Latte, Reflektorhöhe h = 0,13 m", 20, "end")
# Gleisachse
line((gmp[0], gmp[1] + 8), (gmp[0] + 40, gmp[1] + 112), "#c62828", 1.6)
txt(gmp[0] + 46, gmp[1] + 132, "Gleisachse (z. B. 101Achse)", 24, "start", "bold", "#c62828")
txt(gmp[0] + 46, gmp[1] + 158, "Mitte der Fahrkanten auf SO-Höhe", 20, "start", "normal", "#c62828")
# Spurweite
lp = proj(P(-0.45, 0.0, 0.017))
line((lp[0], lp[1] + 10), (lp[0] - 40, lp[1] + 176), "#333", 1.4)
txt(16, lp[1] + 198, "Prismenabstand = Ist-Spurweite", 22, "start", "bold")
txt(16, lp[1] + 224, "(Fahrkante – Fahrkante)", 20)

# Titel
txt(16, 38, "Gleismesslatte im überhöhten Gleis", 30, "start", "bold")
txt(16, 66, f"Gleismesslatte V{VERSION} © Dipl.-Ing. (FH) Stefan Klein · u = 150 mm, maßstäblich", 18,
    "start", "normal", "#4a5563")

defs = ('<defs><radialGradient id="glas" cx="40%" cy="35%" r="70%">'
        '<stop offset="0%" stop-color="#e8f4ff"/><stop offset="55%" stop-color="#6d8fb0"/>'
        '<stop offset="100%" stop-color="#25384d"/></radialGradient></defs>')

def schreibe(name, breite, hoehe, inhalt):
    with open(f"{ZIEL}/{name}", "w", encoding="utf-8") as f:
        f.write(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {breite} {hoehe}" '
                f'width="{breite}" height="{hoehe}">{defs}' + "\n".join(inhalt) + "</svg>")

schreibe("szene.svg", W, HH, svg)

# --- Detail Prisma: Korrektur bei Überhöhung ------------------------------------
DW, DH = 900, 470
d = [f'<rect width="{DW}" height="{DH}" fill="#ffffff"/>',
     f'<rect x="1" y="1" width="{DW-2}" height="{DH-2}" fill="none" stroke="#c9d0d8" stroke-width="2"/>']
txt(20, 42, "Detail Prisma – Korrektur bei Überhöhung", 28, "start", "bold", ziel=d)
txt(20, 70, "(Neigung zur Verdeutlichung vergrößert)", 18, "start", "normal", "#4a5563", ziel=d)
a = math.radians(22)
ox, oy, L = 200, 380, 230
# Schienenkopf (vereinfacht) an der Fahrkante
d.append(f'<polygon points="{ox-6:.0f},{oy+4:.0f} {ox+60:.0f},{oy-22:.0f} {ox+66:.0f},{oy+40:.0f} {ox:.0f},{oy+66:.0f}" '
         f'fill="#c9ccd0" stroke="#55595e" stroke-width="2"/>')
line((ox - 70*math.cos(a), oy + 70*math.sin(a)), (ox + 280*math.cos(a), oy - 280*math.sin(a)), "#d39b00", 12, ziel=d)
px, py = ox - L*math.sin(a), oy - L*math.cos(a)
line((ox, oy), (px, py), "#555", 6, ziel=d)
d.append(f'<circle cx="{px:.1f}" cy="{py:.1f}" r="20" fill="#d6531c" stroke="#7a2a08" stroke-width="2"/>')
d.append(f'<circle cx="{px:.1f}" cy="{py:.1f}" r="13" fill="url(#glas)"/>')
line((px, py + 20), (px, oy + 14), "#1565c0", 2.5, "8,6", ziel=d)
line((px, oy), (ox, oy), "#c62828", 4, ziel=d)
d.append(f'<circle cx="{ox:.1f}" cy="{oy:.1f}" r="6" fill="#1d2430"/>')
txt((px + ox) / 2, oy + 36, "h·sin α", 22, "middle", "bold", "#c62828", ziel=d)
txt(px - 12, (py + oy) / 2 + 10, "h·cos α", 22, "end", "bold", "#1565c0", ziel=d)
txt((ox + px) / 2 + 16, (oy + py) / 2, "h", 24, "start", "bold", ziel=d)
txt(ox + 16, oy + 24, "Fahrkante (SO)", 20, ziel=d)
txt(px + 30, py + 8, "Prisma", 20, ziel=d)
# Textspalte
tx = 470
txt(tx, 120, "Lagekorrektur zur tieferen Schiene:", 20, ziel=d)
txt(tx, 152, "y, x + h·sin α · e", 24, "start", "bold", ziel=d)
txt(tx, 184, "z − h·cos α", 24, "start", "bold", ziel=d)
txt(tx, 226, "u = Bezugsbasis · sin α", 22, "start", "bold", ziel=d)
txt(tx, 254, "α = arcsin(u / 1,500 m)", 20, ziel=d)
txt(tx, 296, "Beispiel u = 150 mm:", 20, ziel=d)
txt(tx, 324, "α ≈ 6,4 gon", 22, "start", "bold", ziel=d)
txt(tx, 352, "Lage ≈ 13 mm, Höhe ≈ 0,7 mm", 22, "start", "bold", ziel=d)
txt(tx, 394, "blau gestrichelt: Lotrichtung", 20, "start", "normal", "#1565c0", ziel=d)
txt(tx, 422, "Grenzwert EBO § 6 (3): 180 mm", 20, "start", "normal", "#c62828", ziel=d)
schreibe("detail.svg", DW, DH, d)
print("ok")
