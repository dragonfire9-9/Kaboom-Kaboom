"""Clean, hand-painted-style materials for entity skins.

The original materials (tex.py) were built from per-pixel noise, which reads as speckle at
Minecraft scale. These paint the way a Blockbench artist would: a few flat, posterised tones per
material, crisp structural lines (plates, seams, scales, feather rows, stitches), deliberate
highlights on upper edges, and sparse, intentional details. Same names and signature as
tex.MATERIALS, so skins.py can switch over without other changes.
"""
from __future__ import annotations

import numpy as np

import tex
from tex import shade, lerp, fbm, ridged, hashf, voronoi, ramp, _out, _glow


def poster(n, levels=3):
    """Quantise a 0..1 field to a few flat steps (the core of the clean look)."""
    return np.clip(np.floor(np.asarray(n) * levels) / (levels - 1), 0, 1)


def tones(base, n, lo=0.86, hi=1.1, levels=3):
    return shade(base, lo + (hi - lo) * poster(n, levels))


def local(f):
    return f.X - f.x0, f.Y - f.y0


# ---------------------------------------------------------------------------------------------
def m_flesh(f, P, s):
    n = fbm(f.X / 14, f.Y / 14, s, 2)
    c = tones(P["base"], n, 0.9, 1.06)
    blot = fbm(f.X / 9, f.Y / 9, s + 4, 2) > 0.62
    c = np.where(blot[..., None], lerp(c, P["accent"], 0.35), c)
    vein = (np.abs(np.sin(f.X * 0.25 + fbm(f.X / 14, f.Y / 14, s + 9, 2) * 5)) < 0.05) & (fbm(f.X / 16, f.Y / 16, s + 2, 2) > 0.68)
    c = np.where(vein[..., None], shade(P.get("vein", P["base"] * np.array([0.7, 0.3, 0.3])), 0.85), c)
    return _out(f, c)


def m_rot(f, P, s):
    c, a, g = m_flesh(f, P, s)
    rot = fbm(f.X / 8, f.Y / 8, s + 21, 2) > 0.6
    c = np.where(rot[..., None], lerp(c, np.array([0.24, 0.3, 0.14]), 0.55), c)
    w = fbm(f.X / 7, f.Y / 7, s + 33, 2)
    wound = w > 0.7
    rim = (w > 0.66) & ~wound
    c = np.where(rim[..., None], np.array([0.62, 0.22, 0.2]), c)
    c = np.where(wound[..., None], np.array([0.36, 0.04, 0.05]), c)
    return c, a, g


def m_fur(f, P, s, belly=True):
    lx, ly = local(f)
    strands = poster(fbm(f.X * 0.45, f.Y / 7, s, 2), 3)
    c = shade(P["base"], 0.86 + 0.2 * strands)
    groove = ((f.X + (f.Y // 6) * 2) % 5 == 0) & f.side
    c = np.where(groove[..., None], shade(P["base"], 0.8), c)
    if f.side and f.h >= 4:
        c = np.where((ly < 2)[..., None], shade(c, 1.1), c)                 # sunlit tips on top edge
    if belly and f.kind == "bottom":
        c = shade(P.get("belly", shade(P["base"], 1.3)), 0.95 + 0.08 * strands[..., None])
    pat = P.get("pattern")
    if pat == "stripe":
        m = (np.abs(np.sin(f.X * 0.45 + fbm(f.X / 8, f.Y / 8, s, 2) * 2.5)) > 0.86) & (f.kind != "bottom")
        c = np.where(m[..., None], shade(P["accent"], 0.92), c)
    elif pat == "spots":
        d1, _, cid = voronoi(f.X, f.Y, s + 3, 6.0)
        m = (d1 < 0.3) & (cid > 0.45) & (f.kind != "bottom")
        c = np.where(m[..., None], P["accent"], c)
    elif pat == "patches":
        m = fbm(f.X / 9, f.Y / 9, s + 8, 2) > 0.56
        c = np.where(m[..., None], shade(P["accent"], 0.95 + 0.08 * strands[..., None]), c)
    elif pat == "back_stripe" and f.kind == "top":
        c = lerp(c, P["accent"], 0.55)
    return _out(f, c)


def m_wool(f, P, s):
    d1, d2, cid = voronoi(f.X, f.Y, s, 3.6)
    curl = poster(np.clip(1 - d1 * 1.5, 0, 1), 3)
    c = shade(P["base"], 0.84 + 0.22 * curl)
    c = np.where(((d2 - d1) < 0.1)[..., None], shade(P["base"], 0.78), c)
    return _out(f, c)


def m_feather(f, P, s):
    lx, ly = local(f)
    row = (ly // 3).astype(int)
    off = (row % 2) * 2
    col = ((lx + off) // 4).astype(int)
    v = (ly % 3) / 2.0
    c = shade(P["base"], np.where(v < 0.34, 1.12, np.where(v < 0.7, 1.0, 0.82)))
    shaft = ((lx + off) % 4 == 0) & f.side
    c = np.where(shaft[..., None], shade(P["base"], 0.75), c)
    alt = (hashf(col, row, s) > 0.82)
    c = np.where(alt[..., None], lerp(c, P["accent"], 0.5), c)
    if f.kind == "bottom" or (f.side and f.v.mean() > 0.6 and f.part == "body"):
        c = lerp(c, P.get("belly", shade(P["base"], 1.3)), 0.45)
    return _out(f, c)


def m_cloth(f, P, s, tattered=False):
    lx, ly = local(f)
    folds = poster(fbm(f.X / 3.5, f.Y / 14, s, 2), 3)
    c = shade(P["cloth"], 0.8 + 0.28 * folds)
    if f.side and f.h >= 6:
        hem = ly >= f.h - 3
        c = np.where(hem[..., None], shade(P.get("trim", P["cloth"]), 1.05), c)
        c = np.where((ly == f.h - 4)[..., None], shade(P["cloth"], 0.62), c)        # seam line
    alpha = np.ones((f.h, f.w))
    if tattered and f.side and f.h >= 6:
        tooth = (lx % 5)
        depth = np.where(tooth < 3, tooth, 5 - tooth) + (hashf(lx // 5, f.y0, s + 5) * 3).astype(int)
        alpha = np.where(ly >= f.h - depth, 0.0, 1.0)
    return _out(f, c, alpha)


def m_leather(f, P, s):
    lx, ly = local(f)
    c = tones(P["leather"], fbm(f.X / 7, f.Y / 7, s, 2), 0.86, 1.06)
    if f.w >= 6 and f.h >= 6:
        stitch = (((lx == 1) | (lx == f.w - 2)) & (ly % 3 == 0)) | (((ly == 1) | (ly == f.h - 2)) & (lx % 3 == 0))
        c = np.where(stitch[..., None], shade(P["leather"], 1.45), c)
    return _out(f, c)


def m_bone(f, P, s):
    grain = poster(fbm(f.X / 10, f.Y / 24, s, 2), 3)
    c = shade(P["bone"], 0.9 + 0.14 * grain)
    if f.side:
        lx, ly = local(f)
        c = np.where(((lx == 0) | (lx == f.w - 1))[..., None], shade(P["bone"], 0.8) * np.array([1.02, 0.97, 0.88]), c)
    crack = ridged(f.X / 16, f.Y / 16, s + 2, 2) > 0.985
    c = np.where(crack[..., None], shade(P["bone"], 0.55), c)
    return _out(f, c)


def m_stone(f, P, s, scale=15.0, fissure=None, moss=None):
    d1, d2, cid = voronoi(f.X, f.Y, s, scale)
    block = poster(cid, 4)
    c = shade(P["stone"], 0.82 + 0.26 * block)
    edge = (d2 - d1) < 0.07
    bevel = ((d2 - d1) < 0.14) & ~edge & (d1 < 0.5)
    c = np.where(bevel[..., None], shade(c, 1.1), c)
    c = np.where(edge[..., None], shade(P["stone"], 0.48), c)
    glow = None
    if fissure is not None:
        crack = (ridged(f.X / 16, f.Y / 16, s + 11, 2) > 0.975) | (edge & (fbm(f.X / 14, f.Y / 14, s + 12, 2) > 0.66))
        c = np.where(crack[..., None], fissure, c)
        glow = _glow(f, crack, fissure)
    if moss is not None:
        m = (fbm(f.X / 13, f.Y / 13, s + 40, 2) + (0.22 if f.kind == "top" else -0.06)) > 0.64
        c = np.where(m[..., None], shade(moss, 0.9), c)
    return _out(f, c, None, glow)


def m_crystal(f, P, s):
    d1, d2, cid = voronoi(f.X, f.Y, s, 15.0)
    c = shade(P["stone"], 0.72 + 0.5 * poster(cid, 4))
    edge = (d2 - d1) < 0.06
    c = np.where(edge[..., None], shade(P["stone"], 1.55), c)
    lit = edge & (cid > 0.45)
    c = np.where(lit[..., None], P["glow"], c)
    sparkle = hashf(f.X, f.Y, s) > 0.995
    c = np.where(sparkle[..., None], np.ones(3), c)
    return _out(f, c, None, _glow(f, lit | sparkle, P["glow"]))


def m_chitin(f, P, s):
    lx, ly = local(f)
    band = ly % 5
    k = np.where(band == 0, 1.2, np.where(band == 4, 0.68, 1.0))
    c = shade(P["base"], k * (0.95 + 0.06 * poster(fbm(f.X / 8, f.Y / 8, s, 2), 2)))
    gloss = (band == 1) & ((lx + ly) % 7 < 2)
    c = np.where(gloss[..., None], shade(c, 1.2), c)
    return _out(f, c)


def m_metal(f, P, s, rivets=True):
    lx, ly = local(f)
    c = shade(P["metal"], 0.88 + 0.16 * poster(fbm(f.X / 14, f.Y * 0.4, s, 2), 3))
    if f.h >= 4:
        c = np.where((ly == 0)[..., None], shade(P["metal"], 1.35), c)
        c = np.where((ly == f.h - 1)[..., None], shade(P["metal"], 0.6), c)
    if rivets and f.w >= 8 and f.h >= 8:
        for rx in (2, f.w - 4):
            for ry in (2, f.h - 4):
                r = (lx >= rx) & (lx < rx + 2) & (ly >= ry) & (ly < ry + 2)
                c = np.where(r[..., None], shade(P["metal"], 1.25), c)
                c = np.where((r & (lx == rx) & (ly == ry))[..., None], shade(P["metal"], 1.6), c)
    if "rust" in P:
        rust = fbm(f.X / 9, f.Y / 9, s + 9, 2) > 0.66
        c = np.where(rust[..., None], lerp(c, P["rust"], 0.55), c)
    return _out(f, c)


def m_horn(f, P, s):
    t = f.v if f.side else np.full((f.h, f.w), 0.5)
    c = ramp([(0, shade(P["horn"], 1.3)), (0.5, P["horn"]), (1, shade(P["horn"], 0.6))], poster(t, 4))
    lx, ly = local(f)
    ring = (ly % 4 == 0) & f.side
    c = np.where(ring[..., None], shade(c, 0.82), c)
    return _out(f, c)


def m_shell(f, P, s, plates=False):
    if plates:
        d1, d2, cid = voronoi(f.X, f.Y, s, 4.5)
        c = shade(P["shell"], 0.84 + 0.3 * poster(cid, 3))
        c = np.where(((d2 - d1) < 0.12)[..., None], shade(P["shell"], 0.45), c)
    else:
        r = np.hypot(f.u - 0.5, f.v - 0.5) * 6
        band = (np.floor(r) % 2) == 0
        c = np.where(band[..., None], shade(P["shell"], 1.08), shade(P["shell"], 0.84))
    if P.get("moss") is not None:
        m = (fbm(f.X / 7, f.Y / 7, s + 5, 2) + (0.22 if f.kind == "top" else 0)) > 0.58
        c = np.where(m[..., None], shade(P["moss"], 0.9), c)
    return _out(f, c)


def m_wing(f, P, s):
    if P.get("wingstyle") == "membrane":
        lx, ly = local(f)
        vein = (lx % 6 == 0) | (((lx + ly * 2) % 11) == 0)
        c = np.where(vein[..., None], shade(P["base"], 0.6), shade(P["wing"], 0.9 + 0.14 * poster(f.v, 3)[..., None]))
        g = None
        if P.get("eyespot") and f.kind in ("top", "bottom"):
            r = np.hypot((f.u - 0.6) * f.w, (f.v - 0.5) * f.h)
            spot = r < 3.2
            c = np.where(spot[..., None], P["glow"], c)
            c = np.where((r < 1.4)[..., None], np.array([0.05, 0.03, 0.02]), c)
            g = _glow(f, spot & (r >= 1.4), P["glow"])
        return _out(f, c, None, g)
    out = m_feather(f, {**P, "base": P["wing"]}, s)
    c = out[0]
    c = np.where((f.u > 0.78)[..., None], shade(c, 0.72), c)
    return c, out[1], out[2]


def m_emissive(f, P, s):
    r = np.hypot(f.u - 0.5, f.v - 0.5) * 2
    c = lerp(np.ones(3), P["glow"], poster(np.clip(r, 0, 1), 3) * 0.8 + 0.2)
    return _out(f, c, None, _glow(f, np.ones((f.h, f.w)), P["glow"]))


def m_flame(f, P, s):
    t = f.v if f.side else np.full((f.h, f.w), 0.3)
    c = ramp([(0, np.array([1.0, 0.98, 0.78])), (0.35, np.array([1.0, 0.75, 0.22])), (0.7, P.get("glow", np.array([1.0, 0.38, 0.06]))),
              (1, np.array([0.6, 0.1, 0.03]))], poster(1 - t if f.side else t, 4))
    return _out(f, c, None, _glow(f, np.ones((f.h, f.w)), c.mean(axis=(0, 1))))


def m_ethereal(f, P, s):
    wisp = poster(fbm(f.X / 4, f.Y / 12, s, 2), 3)
    c = shade(P["base"], 0.9 + 0.3 * wisp)
    if P.get("iridescent"):
        hue = poster(fbm(f.X / 9, f.Y / 9, s + 9, 2), 4)
        rainbow = ramp([(0, np.array([0.55, 0.3, 1.0])), (0.4, np.array([0.3, 0.8, 1.0])),
                        (0.7, np.array([1.0, 0.45, 0.85])), (1, np.array([0.55, 0.3, 1.0]))], hue)
        c = lerp(c, rainbow, 0.5)
    alpha = np.clip(0.5 + 0.3 * wisp - (poster(f.v, 3) * 0.25 if f.side else 0), 0.3, 0.9)
    return _out(f, c, alpha, _glow(f, wisp > 0.9, shade(P["base"], 1.3), 0.4))


def m_void(f, P, s, runes=False):
    c = lerp(np.array([0.03, 0.01, 0.05]), P["base"] * 1.6, poster(fbm(f.X / 9, f.Y / 9, s, 2), 3) * 0.6)
    star = hashf(f.X, f.Y, s) > 0.996
    c = np.where(star[..., None], np.array([0.85, 0.75, 1.0]), c)
    glow_mask = star.astype(float) * 0.7
    if runes:
        lx, ly = local(f)
        cell_x, cell_y = lx // 10, ly // 10
        ix, iy = lx % 10, ly % 10
        pick = hashf(cell_x + f.x0, cell_y + f.y0, s + 7)
        rune = (pick > 0.45) & (((ix == 1) & (iy >= 1) & (iy <= 4)) | ((iy == 1) & (ix >= 1) & (ix <= 3) & (pick > 0.6))
                                | ((iy == 4) & (ix >= 1) & (ix <= 3) & (pick < 0.75)) | ((ix == 3) & (iy == 2) & (pick > 0.7)))
        c = np.where(rune[..., None], P["glow"], c)
        glow_mask = np.maximum(glow_mask, rune.astype(float))
    return _out(f, c, None, _glow(f, glow_mask, P["glow"]))


def m_starsky(f, P, s):
    c = shade(P["base"], 0.8 + 0.4 * poster(fbm(f.X / 10, f.Y / 10, s, 2), 3))
    star = hashf(f.X, f.Y, s) > 0.965
    big = hashf(f.X // 2, f.Y // 2, s + 1) > 0.99
    c = np.where(star[..., None], np.array([1.0, 0.97, 0.8]), c)
    c = np.where(big[..., None], P["accent"], c)
    return _out(f, c, None, _glow(f, star | big, np.array([1.0, 0.95, 0.75])))


def m_wax(f, P, s):
    lx, ly = local(f)
    base = P.get("wax", np.array([0.9, 0.85, 0.68]))
    c = shade(base, 0.95 + 0.08 * poster(f.v, 3))
    drip = ((lx % 4) == 1) & (ly < (2 + (hashf(lx // 4 + f.x0, 1, s) * 6).astype(int))) & f.side
    return _out(f, np.where(drip[..., None], shade(base, 1.12), c))


MATERIALS = dict(tex.MATERIALS)
MATERIALS.update({
    "flesh": m_flesh, "rot": m_rot, "fur": m_fur, "wool": m_wool, "feather": m_feather,
    "cloth": m_cloth, "tattered": lambda f, P, s: m_cloth(f, P, s, True), "leather": m_leather, "bone": m_bone,
    "stone": m_stone, "crystal": m_crystal, "chitin": m_chitin, "metal": m_metal,
    "void": m_void, "void_runes": lambda f, P, s: m_void(f, P, s, True),
    "ethereal": m_ethereal, "starsky": m_starsky, "horn": m_horn, "shell": m_shell,
    "plates": lambda f, P, s: m_shell(f, P, s, True), "wing": m_wing, "emissive": m_emissive, "flame": m_flame, "wax": m_wax,
    "fissure_stone": lambda f, P, s: m_stone(f, P, s, fissure=P["glow"]),
    "mossy_stone": lambda f, P, s: m_stone(f, P, s, scale=16.0, fissure=P.get("crack_glow"), moss=P.get("moss")),
    "rubble": lambda f, P, s: m_stone(f, P, s, scale=12.0),
    "charred": lambda f, P, s: m_stone(f, {**P, "stone": np.array([0.13, 0.1, 0.09])}, s, 13.0, fissure=P["glow"]),
})
