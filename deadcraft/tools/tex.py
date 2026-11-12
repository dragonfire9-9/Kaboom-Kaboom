"""DeadCraft texture core: coherent noise, hue-shifted shading and a material library.

Every material is a function of absolute texture coordinates, so neighbouring faces of a cube
share the same coherent pattern (a crack that crosses an edge continues on the next face).
Colours are float RGB in 0..1. Materials return (rgb, alpha, glow) arrays for one face.
"""
from __future__ import annotations

import colorsys
import math

import numpy as np

# =============================================================================================
# Noise
# =============================================================================================
_LATTICES: dict[int, np.ndarray] = {}


def _lattice(seed: int) -> np.ndarray:
    seed %= 1_000_003
    if seed not in _LATTICES:
        _LATTICES[seed] = np.random.default_rng(seed).random((256, 256))
    return _LATTICES[seed]


def vnoise(x, y, seed):
    """Smooth value noise in [0, 1]."""
    L = _lattice(seed)
    x0 = np.floor(x).astype(int)
    y0 = np.floor(y).astype(int)
    fx, fy = x - x0, y - y0
    sx, sy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a = L[y0 % 256, x0 % 256]
    b = L[y0 % 256, (x0 + 1) % 256]
    c = L[(y0 + 1) % 256, x0 % 256]
    d = L[(y0 + 1) % 256, (x0 + 1) % 256]
    return (a * (1 - sx) + b * sx) * (1 - sy) + (c * (1 - sx) + d * sx) * sy


def fbm(x, y, seed, octaves=4):
    v = np.zeros_like(np.asarray(x, dtype=float))
    amp, freq, total = 0.5, 1.0, 0.0
    for i in range(octaves):
        v = v + amp * vnoise(x * freq, y * freq, seed + i * 131)
        total += amp
        amp *= 0.5
        freq *= 2.0
    return v / total


def ridged(x, y, seed, octaves=3):
    """Thin bright ridges near 1: useful for cracks and veins."""
    return 1.0 - np.abs(fbm(x, y, seed, octaves) * 2 - 1)


def hashf(x, y, seed):
    """Per-pixel deterministic random in [0, 1)."""
    L = _lattice(seed + 7)
    return L[np.asarray(y, int) % 256, np.asarray(x, int) % 256]


def voronoi(x, y, seed, scale):
    """Returns (d1, d2, cell_random) for a jittered grid of cells `scale` pixels wide."""
    px, py = x / scale, y / scale
    cx, cy = np.floor(px).astype(int), np.floor(py).astype(int)
    L = _lattice(seed)
    L2 = _lattice(seed + 1)
    d1 = np.full(px.shape, 9.0)
    d2 = np.full(px.shape, 9.0)
    cid = np.zeros(px.shape)
    for oy in (-1, 0, 1):
        for ox in (-1, 0, 1):
            gx, gy = cx + ox, cy + oy
            jx = L[gy % 256, gx % 256]
            jy = L2[gy % 256, gx % 256]
            d = np.hypot(px - (gx + jx), py - (gy + jy))
            closer = d < d1
            d2 = np.where(closer, d1, np.minimum(d2, d))
            cid = np.where(closer, L[(gy * 7) % 256, (gx * 13) % 256], cid)
            d1 = np.where(closer, d, d1)
    return d1, d2, cid


# =============================================================================================
# Colour
# =============================================================================================
def rgb(h):
    return np.array([(h >> 16) & 255, (h >> 8) & 255, h & 255], dtype=float) / 255.0


def lerp(a, b, t):
    """Blend colours. Scalar fields are 2-D (h, w); colours carry a trailing RGB axis."""
    t = np.asarray(t, dtype=float)
    if t.ndim == 2:
        t = t[..., None]
    return np.asarray(a, dtype=float) + (np.asarray(b, dtype=float) - np.asarray(a, dtype=float)) * t


SHADOW_TINT = np.array([0.55, 0.5, 0.85])
LIGHT_TINT = np.array([1.0, 0.94, 0.78])


def shade(c, f):
    """Pixel-art shading: shadows shift toward cool violet, highlights toward warm yellow."""
    f = np.asarray(f, dtype=float)
    if f.ndim == 2:
        f = f[..., None]
    dark = c * f * lerp(np.ones(3), SHADOW_TINT, np.clip(1 - f, 0, 1) * 0.6)
    light = c + (LIGHT_TINT - c) * np.clip(f - 1, 0, 1) * 0.9
    return np.clip(np.where(f < 1, dark, light), 0, 1)


def saturate(c, s):
    out = np.empty_like(c)
    flat = c.reshape(-1, 3)
    o = out.reshape(-1, 3)
    for i, (r, g, b) in enumerate(flat):
        h, l, sat = colorsys.rgb_to_hls(r, g, b)
        o[i] = colorsys.hls_to_rgb(h, l, min(1, sat * s))
    return out


def ramp(stops, t):
    """Piecewise-linear colour ramp. stops = [(pos, rgb), ...]."""
    t = np.clip(np.asarray(t, dtype=float), 0, 1)
    out = np.zeros(t.shape + (3,))
    for (p0, c0), (p1, c1) in zip(stops, stops[1:]):
        m = (t >= p0) & (t <= p1)
        k = ((t - p0) / max(1e-6, p1 - p0))[..., None]
        out = np.where(m[..., None], np.asarray(c0) + (np.asarray(c1) - np.asarray(c0)) * k, out)
    return out


# =============================================================================================
# Materials
# =============================================================================================
class Face:
    """One face of a cube in texture space."""

    def __init__(self, x0, y0, w, h, kind, part="", role="", seed=0):
        self.x0, self.y0, self.w, self.h = x0, y0, w, h
        self.kind, self.part, self.role, self.seed = kind, part, role, seed
        ys, xs = np.mgrid[0:h, 0:w]
        self.X = (xs + x0).astype(float)
        self.Y = (ys + y0).astype(float)
        self.u = (xs + 0.5) / max(1, w)
        self.v = (ys + 0.5) / max(1, h)
        self.side = kind in ("front", "back", "left", "right")


def _out(face, c, alpha=None, glow=None):
    a = np.ones((face.h, face.w)) if alpha is None else alpha
    g = np.zeros((face.h, face.w, 4)) if glow is None else glow
    return np.clip(c, 0, 1), a, g


def _glow(face, mask, color, strength=1.0):
    g = np.zeros((face.h, face.w, 4))
    m = np.clip(np.asarray(mask, dtype=float) * strength, 0, 1)
    g[..., :3] = np.asarray(color)
    g[..., 3] = m
    return g


def m_flesh(f, P, s):
    n = fbm(f.X / 3.5, f.Y / 3.5, s)
    c = lerp(shade(P["base"], 0.82), shade(P["base"], 1.12), n)
    vein = ridged(f.X / 5, f.Y / 5, s + 9) > 0.93
    c = np.where(vein[..., None], shade(P.get("vein", P["base"] * np.array([0.7, 0.3, 0.3])), 0.8), c)
    mottle = fbm(f.X / 2, f.Y / 2, s + 4) > 0.66
    c = np.where(mottle[..., None], lerp(c, P["accent"], 0.35), c)
    return _out(f, c)


def m_rot(f, P, s):
    c, a, g = m_flesh(f, P, s)
    rot = fbm(f.X / 3, f.Y / 3, s + 21) > 0.62
    c = np.where(rot[..., None], lerp(c, np.array([0.18, 0.22, 0.1]), 0.6), c)
    wound = fbm(f.X / 2.5, f.Y / 2.5, s + 33) > 0.74
    c = np.where(wound[..., None], np.array([0.45, 0.05, 0.06]) * (0.8 + 0.4 * hashf(f.X, f.Y, s))[..., None], c)
    return c, a, g


def m_fur(f, P, s, belly=True):
    strand = fbm(f.X * 1.1, f.Y / 3.5, s, 3)
    c = lerp(shade(P["base"], 0.78), shade(P["base"], 1.15), strand)
    tips = hashf(f.X, f.Y, s) > 0.9
    c = np.where(tips[..., None], shade(c, 1.12), c)
    if belly and f.kind == "bottom":
        c = lerp(c, P.get("belly", shade(P["base"], 1.35)), 0.7)
    if "pattern" in P:
        pat = P["pattern"]
        if pat == "stripe":
            m = (np.abs(np.sin(f.X * 0.9 + fbm(f.X / 3, f.Y / 3, s) * 4)) > 0.82) & (f.kind != "bottom")
            c = np.where(m[..., None], shade(P["accent"], 0.9), c)
        elif pat == "spots":
            d1, _, cid = voronoi(f.X, f.Y, s + 3, 3.2)
            m = (d1 < 0.28) & (cid > 0.45) & (f.kind != "bottom")
            c = np.where(m[..., None], P["accent"], c)
        elif pat == "patches":
            m = fbm(f.X / 4, f.Y / 4, s + 8) > 0.58
            c = np.where(m[..., None], shade(P["accent"], 0.95 + 0.1 * strand[..., None]), c)
        elif pat == "back_stripe":
            m = f.kind == "top"
            c = np.where(np.full(c.shape[:2], m)[..., None], lerp(c, P["accent"], 0.55), c)
    return _out(f, c)


def m_wool(f, P, s):
    d1, d2, cid = voronoi(f.X, f.Y, s, 1.8)
    curl = np.clip(1 - d1 * 1.6, 0, 1)
    c = lerp(shade(P["base"], 0.78), shade(P["base"], 1.12), curl * 0.7 + cid * 0.3)
    return _out(f, c)


def m_feather(f, P, s):
    row = (f.Y + (f.X // 2) % 2) % 3
    k = np.where(row == 0, 1.14, np.where(row == 1, 0.98, 0.8))
    c = shade(P["base"], k * (0.92 + 0.16 * fbm(f.X / 3, f.Y / 3, s)))
    speck = hashf(f.X, f.Y, s + 1) > 0.9
    c = np.where(speck[..., None], shade(P["accent"], 1.0), c)
    if f.kind == "bottom" or (f.side and f.v.mean() > 0.6 and f.part == "body"):
        c = lerp(c, P.get("belly", shade(P["base"], 1.3)), 0.45)
    return _out(f, c)


def m_cloth(f, P, s, tattered=False):
    folds = fbm(f.X / 1.6, f.Y / 7, s, 3)
    weave = ((f.X + f.Y) % 2) * 0.05
    c = shade(P["cloth"], 0.72 + 0.45 * folds - weave)
    hem = (f.v > 0.85) & f.side
    c = np.where(hem[..., None], shade(P.get("trim", P["cloth"]), 1.1), c)
    alpha = np.ones((f.h, f.w))
    if tattered and f.side:
        tear = f.v > 1.0 - 0.15 - 0.45 * fbm(f.X * 0.9, 3, s + 5, 2)
        alpha = np.where(tear, 0.0, 1.0)
    return _out(f, c, alpha)


def m_leather(f, P, s):
    n = fbm(f.X / 2.5, f.Y / 2.5, s)
    c = shade(P["leather"], 0.8 + 0.35 * n)
    stitch = f.side & ((f.u < 1.5 / max(f.w, 1)) | (f.u > 1 - 1.5 / max(f.w, 1))) & ((f.Y % 2) == 0)
    c = np.where(stitch[..., None], shade(P["leather"], 1.5), c)
    return _out(f, c)


def m_bone(f, P, s):
    grain = fbm(f.X / 1.2, f.Y / 5, s, 3)
    c = shade(P["bone"], 0.85 + 0.25 * grain)
    edge = (f.u < 0.18) | (f.u > 0.82)
    c = np.where((edge & f.side)[..., None], shade(c, 0.88) * np.array([1.02, 0.97, 0.85]), c)
    crack = ridged(f.X / 4, f.Y / 4, s + 2) > 0.985
    c = np.where(crack[..., None], shade(P["bone"], 0.55), c)
    return _out(f, c)


def m_stone(f, P, s, scale=2.6, fissure=None, moss=None):
    d1, d2, cid = voronoi(f.X, f.Y, s, scale)
    mortar = (d2 - d1) < 0.16
    rel = 0.82 + 0.3 * cid + 0.12 * fbm(f.X / 2, f.Y / 2, s + 3)
    c = shade(P["stone"], rel)
    c = np.where(mortar[..., None], shade(P["stone"], 0.5), c)
    glow = None
    if fissure is not None:
        crack = (ridged(f.X / 5.5, f.Y / 5.5, s + 11) > 0.955) | (mortar & (fbm(f.X / 3, f.Y / 3, s + 12) > 0.74))
        hot = np.where(crack[..., None], fissure, c)
        c = hot
        glow = _glow(f, crack, fissure)
    if moss is not None:
        m = (fbm(f.X / 3, f.Y / 3, s + 40) + (0.2 if f.kind == "top" else -0.08)) > 0.66
        c = np.where(m[..., None], shade(moss, 0.65 + 0.35 * hashf(f.X, f.Y, s + 41)[..., None]), c)
    return _out(f, c, None, glow)


def m_crystal(f, P, s):
    d1, d2, cid = voronoi(f.X, f.Y, s, 3.4)
    edge = (d2 - d1) < 0.12
    lit = edge & (fbm(f.X / 3, f.Y / 3, s + 2) > 0.5)
    c = shade(P["stone"], 0.7 + 0.55 * cid)
    c = np.where(edge[..., None], shade(P["stone"], 1.5), c)
    c = np.where(lit[..., None], P["glow"], c)
    edge = lit
    sparkle = hashf(f.X, f.Y, s) > 0.97
    c = np.where(sparkle[..., None], np.ones(3), c)
    return _out(f, c, None, _glow(f, edge | sparkle, P["glow"]))


def m_chitin(f, P, s):
    plate = (f.Y % 3)
    k = np.where(plate == 0, 1.18, np.where(plate == 1, 1.0, 0.72))
    c = shade(P["base"], k * (0.9 + 0.2 * fbm(f.X / 3, f.Y / 3, s)))
    gloss = (plate == 0) & (hashf(f.X, f.Y, s) > 0.6)
    c = np.where(gloss[..., None], shade(c, 1.25), c)
    return _out(f, c)


def m_metal(f, P, s, rivets=True):
    brush = fbm(f.X / 7, f.Y * 1.5, s, 2)
    c = shade(P["metal"], 0.8 + 0.35 * brush)
    scratch = ridged(f.X / 2, f.Y / 6, s + 4) > 0.985
    c = np.where(scratch[..., None], shade(P["metal"], 1.4), c)
    if rivets and f.w >= 4 and f.h >= 4:
        lx, ly = f.X - f.x0, f.Y - f.y0
        r = ((lx == 1) | (lx == f.w - 2)) & ((ly == 1) | (ly == f.h - 2))
        c = np.where(r[..., None], shade(P["metal"], 1.45), c)
    rust = fbm(f.X / 4, f.Y / 4, s + 9) > 0.7
    if "rust" in P:
        c = np.where(rust[..., None], lerp(c, P["rust"], 0.5), c)
    return _out(f, c)


def m_paint(f, P, s):
    c, a, g = m_metal(f, {**P, "metal": P["paint"]}, s, rivets=False)
    chip = fbm(f.X / 2, f.Y / 2, s + 5) > 0.7
    c = np.where(chip[..., None], shade(P["metal"], 0.9), c)
    return c, a, g


def m_tire(f, P, s):
    tread = ((f.Y + (f.X % 2)) % 2 == 0)
    c = np.where(tread[..., None], np.array([0.09, 0.09, 0.1]), np.array([0.16, 0.16, 0.17]))
    if f.kind in ("left", "right"):
        r = np.hypot(f.u - 0.5, f.v - 0.5)
        hub = r < 0.22
        c = np.where(hub[..., None], shade(P["metal"], 1.2), c)
        c = np.where(((r > 0.22) & (r < 0.3))[..., None], np.array([0.25, 0.25, 0.27]), c)
    return _out(f, c)


def m_glass(f, P, s):
    c = lerp(np.array([0.35, 0.55, 0.7]), np.array([0.7, 0.85, 0.95]), f.v)
    streak = ((f.X - f.Y) % 5) == 0
    c = np.where(streak[..., None], np.ones(3), c)
    return _out(f, c)


def m_void(f, P, s, runes=False):
    swirl = fbm(f.X / 4 + 3 * fbm(f.X / 6, f.Y / 6, s + 2), f.Y / 4, s, 4)
    c = lerp(np.array([0.02, 0.0, 0.03]), P["base"] * 1.6, swirl ** 2)
    star = hashf(f.X, f.Y, s) > 0.975
    c = np.where(star[..., None], np.array([0.85, 0.75, 1.0]), c)
    glow_mask = star.astype(float) * 0.7
    if runes:
        lx, ly = (f.X - f.x0), (f.Y - f.y0)
        rune = ((lx % 4 == 1) & (ly % 3 != 0) | (ly % 4 == 2) & (lx % 3 == 1)) & (fbm(f.X / 3, f.Y / 3, s + 7) > 0.55)
        c = np.where(rune[..., None], P["glow"], c)
        glow_mask = np.maximum(glow_mask, rune.astype(float))
    return _out(f, c, None, _glow(f, glow_mask, P["glow"]))


def m_ethereal(f, P, s):
    wisp = fbm(f.X / 1.5, f.Y / 5 - 0.0, s, 3)
    c = lerp(shade(P["base"], 0.85), shade(P["base"], 1.25), wisp)
    if P.get("iridescent"):
        hue = fbm(f.X / 4, f.Y / 4, s + 9)
        rainbow = ramp([(0, np.array([0.55, 0.3, 1.0])), (0.4, np.array([0.3, 0.8, 1.0])),
                        (0.7, np.array([1.0, 0.45, 0.85])), (1, np.array([0.55, 0.3, 1.0]))], hue)
        c = lerp(c, rainbow, 0.55)
    alpha = np.clip(0.45 + 0.4 * wisp - (f.v * 0.25 if f.side else 0), 0.25, 0.9)
    return _out(f, c, alpha, _glow(f, wisp > 0.7, shade(P["base"], 1.3), 0.4))


def m_starsky(f, P, s):
    neb = fbm(f.X / 5, f.Y / 5, s, 4)
    c = lerp(shade(P["base"], 0.7), shade(P["base"], 1.3), neb)
    c = lerp(c, P["accent"] * 0.6, np.clip(fbm(f.X / 4, f.Y / 4, s + 3) - 0.55, 0, 1) * 1.5)
    star = hashf(f.X, f.Y, s) > 0.93
    big = hashf(f.X, f.Y, s + 1) > 0.985
    c = np.where(star[..., None], np.array([1.0, 0.97, 0.8]), c)
    c = np.where(big[..., None], P["accent"], c)
    return _out(f, c, None, _glow(f, star | big, np.array([1.0, 0.95, 0.75])))


def m_horn(f, P, s):
    t = f.v if f.side else np.full((f.h, f.w), 0.5)
    c = ramp([(0, shade(P["horn"], 1.35)), (0.5, P["horn"]), (1, shade(P["horn"], 0.55))], t)
    ring = (f.Y % 2 == 0) & f.side
    c = np.where(ring[..., None], shade(c, 0.85), c)
    return _out(f, c)


def m_shell(f, P, s, plates=False):
    if plates:
        d1, d2, cid = voronoi(f.X, f.Y, s, 2.4)
        c = shade(P["shell"], 0.8 + 0.35 * cid)
        c = np.where(((d2 - d1) < 0.18)[..., None], shade(P["shell"], 0.45), c)
    else:
        r = np.hypot(f.u - 0.5, f.v - 0.5) * 8 + np.arctan2(f.v - 0.5, f.u - 0.5) * 0.6
        band = (np.floor(r) % 2) == 0
        c = np.where(band[..., None], shade(P["shell"], 1.1), shade(P["shell"], 0.8))
    if P.get("moss") is not None:
        m = (fbm(f.X / 3, f.Y / 3, s + 5) + (0.2 if f.kind == "top" else 0)) > 0.58
        c = np.where(m[..., None], shade(P["moss"], 0.8 + 0.4 * hashf(f.X, f.Y, s)[..., None]), c)
    return _out(f, c)


def m_wing(f, P, s):
    if P.get("wingstyle") == "membrane":
        vein = ridged(f.X / 2.5, f.Y / 1.2, s) > 0.88
        c = np.where(vein[..., None], shade(P["base"], 0.6), shade(P["wing"], 0.9 + 0.2 * fbm(f.X / 3, f.Y / 3, s)[..., None]))
        g = None
        if P.get("eyespot") and f.kind in ("top", "bottom"):
            r = np.hypot((f.u - 0.6) * f.w, (f.v - 0.5) * f.h)
            spot = r < 1.6
            c = np.where(spot[..., None], P["glow"], c)
            c = np.where((r < 0.7)[..., None], np.array([0.05, 0.03, 0.02]), c)
            g = _glow(f, spot & (r >= 0.7), P["glow"])
        return _out(f, c, None, g)
    out = m_feather(f, {**P, "base": P["wing"]}, s)
    c = out[0]
    tip = f.u > 0.75
    c = np.where(tip[..., None], shade(c, 0.7), c)
    return c, out[1], out[2]


def m_emissive(f, P, s):
    c = lerp(P["glow"], np.ones(3), 0.25 * fbm(f.X, f.Y, s))
    return _out(f, c, None, _glow(f, np.ones((f.h, f.w)), P["glow"]))


MATERIALS = {
    "flesh": m_flesh, "rot": m_rot, "fur": m_fur, "wool": m_wool, "feather": m_feather,
    "cloth": m_cloth, "tattered": lambda f, P, s: m_cloth(f, P, s, True), "leather": m_leather, "bone": m_bone,
    "stone": m_stone, "crystal": m_crystal, "chitin": m_chitin, "metal": m_metal, "paint": m_paint,
    "tire": m_tire, "glass": m_glass, "void": m_void, "void_runes": lambda f, P, s: m_void(f, P, s, True),
    "ethereal": m_ethereal, "starsky": m_starsky, "horn": m_horn, "shell": m_shell,
    "plates": lambda f, P, s: m_shell(f, P, s, True), "wing": m_wing, "emissive": m_emissive,
    "fissure_stone": lambda f, P, s: m_stone(f, P, s, fissure=P["glow"]),
    "mossy_stone": lambda f, P, s: m_stone(f, P, s, scale=3.0, fissure=P.get("crack_glow"), moss=P.get("moss")),
    "rubble": lambda f, P, s: m_stone(f, P, s, scale=1.8),
    "charred": lambda f, P, s: m_stone(f, {**P, "stone": np.array([0.11, 0.08, 0.07])}, s, 2.2, fissure=P["glow"]),
}

FACE_LIGHT = {"top": 1.14, "front": 1.0, "left": 0.9, "right": 0.9, "back": 0.84, "bottom": 0.68}


def light(face, c):
    """Directional light + soft ambient occlusion toward the bottom of side faces + edge definition."""
    k = FACE_LIGHT[face.kind]
    if face.side:
        k = k * (1.06 - 0.22 * face.v ** 1.4)
    lx, ly = face.X - face.x0, face.Y - face.y0
    edge = np.zeros((face.h, face.w))
    if face.h >= 3:
        edge = np.where(ly == face.h - 1, -0.14, edge)
        if face.side:
            edge = np.where(ly == 0, 0.08, edge)
    if face.w >= 3:
        edge = np.where((lx == 0) | (lx == face.w - 1), edge - 0.06, edge)
    return shade(c, k + edge)
