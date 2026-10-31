"""Erzeugt rsrc_windows_amd64.syso (COFF-Objekt mit Icon- und Versions-Ressource).

Der Go-Linker bindet *.syso im Paketordner automatisch ein, so dass die .exe
das Icon im Explorer und in der Taskleiste zeigt.

Die Versions-Ressource erscheint in Windows unter Eigenschaften -> Details
(Dateiversion, Produktname, Copyright, Firma).

Aufruf: python3 syso_erzeugen.py ../doc/gleismesslatte.ico rsrc_windows_amd64.syso 0.12 "Dipl.-Ing. (FH) Stefan Klein"
"""
import struct
import sys

RT_ICON = 3
RT_GROUP_ICON = 14
RT_VERSION = 16
LANG = 0x0407  # Deutsch (Deutschland)


def lese_ico(pfad):
    d = open(pfad, "rb").read()
    reserved, typ, anzahl = struct.unpack_from("<HHH", d, 0)
    assert reserved == 0 and typ == 1, "keine ICO-Datei"
    bilder = []
    for i in range(anzahl):
        w, h, farben, res, planes, bits, groesse, offset = struct.unpack_from("<BBBBHHII", d, 6 + 16 * i)
        bilder.append(dict(w=w, h=h, farben=farben, planes=planes or 1, bits=bits or 32,
                           daten=d[offset:offset + groesse]))
    return bilder


def _block(key, wert, text, kinder=b""):
    """Ein VS_VERSIONINFO-Knoten: Länge, Wertlänge, Typ, Schlüssel, Wert, Kinder (32-bit ausgerichtet)."""
    k = (key + "\0").encode("utf-16-le")
    kopf_len = 6 + len(k)
    pad1 = (4 - kopf_len % 4) % 4
    if text:
        w = (wert + "\0").encode("utf-16-le") if wert is not None else b""
        wlen = len(w) // 2
    else:
        w = wert or b""
        wlen = len(w)
    pad2 = (4 - (kopf_len + pad1 + len(w)) % 4) % 4 if kinder else 0
    gesamt = kopf_len + pad1 + len(w) + pad2 + len(kinder)
    daten = struct.pack("<HHH", gesamt, wlen, 1 if text else 0) + k + b"\0" * pad1 + w + b"\0" * pad2 + kinder
    return daten + b"\0" * ((4 - len(daten) % 4) % 4)


def versionsinfo(version, hersteller):
    teile = [int(x) for x in version.split(".")] + [0, 0, 0, 0]
    ms = (teile[0] << 16) | teile[1]
    ls = (teile[2] << 16) | teile[3]
    fest = struct.pack("<13I", 0xFEEF04BD, 0x00010000, ms, ls, ms, ls, 0x3F, 0, 0x00040004, 1, 0, 0, 0)
    strings = [
        ("CompanyName", hersteller),
        ("FileDescription", "Gleismesslatte – Auswertung Spurweite, Überhöhung, Gleisachse"),
        ("FileVersion", version),
        ("InternalName", "Gleismesslatte"),
        ("LegalCopyright", "© " + hersteller),
        ("OriginalFilename", "Gleismesslatte.exe"),
        ("ProductName", "Gleismesslatte"),
        ("ProductVersion", version),
    ]
    tabelle = b"".join(_block(k, v, True) for k, v in strings)
    sfi = _block("StringFileInfo", None, True, _block("040704B0", None, True, tabelle))
    vfi = _block("VarFileInfo", None, True, _block("Translation", struct.pack("<HH", LANG, 1200), False))
    return _block("VS_VERSION_INFO", fest, False, sfi + vfi)


def baue(bilder, version=None):
    # Gruppen-Icon (GRPICONDIR)
    grp = struct.pack("<HHH", 0, 1, len(bilder))
    for nr, b in enumerate(bilder, 1):
        grp += struct.pack("<BBBBHHIH", b["w"], b["h"], b["farben"], 0, b["planes"], b["bits"],
                           len(b["daten"]), nr)

    # Baumstruktur: Typ -> Name/ID -> Sprache -> Datenblock
    typen = [(RT_ICON, [(nr, b["daten"]) for nr, b in enumerate(bilder, 1)]),
             (RT_GROUP_ICON, [(1, grp)])]
    if version is not None:
        typen.append((RT_VERSION, [(1, version)]))

    def verz_groesse(n):
        return 16 + 8 * n

    # Offsets berechnen
    off = verz_groesse(len(typen))
    typ_off = []
    for _, eintraege in typen:
        typ_off.append(off)
        off += verz_groesse(len(eintraege))
    sprach_off = []
    for _, eintraege in typen:
        lst = []
        for _ in eintraege:
            lst.append(off)
            off += verz_groesse(1)
        sprach_off.append(lst)
    daten_eintrag_off = []
    for _, eintraege in typen:
        lst = []
        for _ in eintraege:
            lst.append(off)
            off += 16
        daten_eintrag_off.append(lst)
    blob_off = []
    for _, eintraege in typen:
        lst = []
        for _, daten in eintraege:
            off = (off + 7) & ~7
            lst.append(off)
            off += len(daten)
        blob_off.append(lst)
    gesamt = (off + 7) & ~7

    buf = bytearray(gesamt)
    relocs = []

    def verz(at, n):
        struct.pack_into("<IIHHHH", buf, at, 0, 0, 0, 0, 0, n)

    verz(0, len(typen))
    for i, (typ, eintraege) in enumerate(typen):
        struct.pack_into("<II", buf, 16 + 8 * i, typ, 0x80000000 | typ_off[i])
        verz(typ_off[i], len(eintraege))
        for j, (name, daten) in enumerate(eintraege):
            struct.pack_into("<II", buf, typ_off[i] + 16 + 8 * j, name, 0x80000000 | sprach_off[i][j])
            verz(sprach_off[i][j], 1)
            struct.pack_into("<II", buf, sprach_off[i][j] + 16, LANG, daten_eintrag_off[i][j])
            # Datenblock-Eintrag: OffsetToData ist RVA -> Relocation ADDR32NB
            struct.pack_into("<IIII", buf, daten_eintrag_off[i][j], blob_off[i][j], len(daten), 0, 0)
            relocs.append(daten_eintrag_off[i][j])
            buf[blob_off[i][j]:blob_off[i][j] + len(daten)] = daten
    return bytes(buf), relocs


def coff(rsrc, relocs):
    IMAGE_FILE_MACHINE_AMD64 = 0x8664
    IMAGE_REL_AMD64_ADDR32NB = 3
    kopf = 20
    sektion = 40
    roh = kopf + sektion
    reloc_pos = roh + len(rsrc)
    sym_pos = reloc_pos + 10 * len(relocs)
    out = bytearray()
    out += struct.pack("<HHIIIHH", IMAGE_FILE_MACHINE_AMD64, 1, 0, sym_pos, 1, 0, 0)
    out += struct.pack("<8sIIIIIIHHI", b".rsrc", 0, 0, len(rsrc), roh, reloc_pos, 0,
                       len(relocs), 0, 0x40000040)
    out += rsrc
    for r in relocs:
        out += struct.pack("<IIH", r, 0, IMAGE_REL_AMD64_ADDR32NB)
    out += struct.pack("<8sIhHBB", b".rsrc", 0, 1, 0, 3, 0)   # Symbol der Sektion
    out += struct.pack("<I", 4)                               # leere String-Tabelle
    return bytes(out)


if __name__ == "__main__":
    bilder = lese_ico(sys.argv[1])
    ver = versionsinfo(sys.argv[3], sys.argv[4]) if len(sys.argv) > 4 else None
    rsrc, relocs = baue(bilder, ver)
    open(sys.argv[2], "wb").write(coff(rsrc, relocs))
    print(f"{sys.argv[2]}: {len(bilder)} Icon-Bilder, {'mit' if ver else 'ohne'} Versionsinfo, {len(rsrc)} Byte Ressourcen")
