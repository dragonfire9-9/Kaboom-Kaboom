"""Textures for the terrain of DeadCraft's own dimensions: every stone, soil, plant and the blood
fluid. Everything here tiles seamlessly (noise is sampled on a torus), because these blocks cover
whole landscapes.
"""
from __future__ import annotations

import math

import numpy as np
from PIL import Image

from tex import rgb as H
from tex import shade, lerp, fbm, hashf

N = 16


def to_img(c, a=None):
    if a is None:
        a = np.ones(c.shape[:2])
    rgba = np.dstack([np.clip(c, 0, 1), np.clip(a, 0, 1)])
    return Image.fromarray((rgba * 255).astype(np.uint8), "RGBA")


def grid(n=N):
    ys, xs = np.mgrid[0:n, 0:n]
    return xs.astype(float), ys.astype(float)


def tnoise(seed, freq=1.0, octaves=3, n=N, sx=1.0, sy=1.0):
    """Seamless noise in [0, 1]: fbm sampled on a 4-D torus (two circles)."""
    X, Y = grid(n)
    a, b = X / n * 2 * math.pi, Y / n * 2 * math.pi
    r = 1.6 * freq
    u = np.cos(a) * r * sx + np.sin(b) * r * sy * 0.37 + 11
    v = np.sin(a) * r * sx + np.cos(b) * r * sy + 23
    w = fbm(u, v, seed, octaves)
    w2 = fbm(np.cos(b) * r * sy + 31, np.sin(a) * r * sx * 0.5 + np.sin(b) * r * sy + 7, seed + 99, octaves)
    out = 0.5 * w + 0.5 * w2
    lo, hi = out.min(), out.max()
    return (out - lo) / max(1e-6, hi - lo)


def speck(seed, p, n=N):
    X, Y = grid(n)
    return hashf(X, Y, seed) > p


def rock(seed, base, dark, light, cracks=0.0, layers=0):
    X, Y = grid()
    n = tnoise(seed, 1.2)
    c = lerp(H(dark), H(base), np.clip(n * 1.4 - 0.1, 0, 1))
    c = np.where((tnoise(seed + 1, 2.2) > 0.72)[..., None], shade(H(base), 1.15), c)
    if layers:
        band = ((Y + np.round(tnoise(seed + 2, 1.0) * 3)) % layers) < 1
        c = np.where(band[..., None], shade(c, 0.78), c)
    if cracks:
        cr = np.abs(tnoise(seed + 3, 1.6) - 0.5) < cracks
        c = np.where(cr[..., None], shade(H(dark), 0.6), c)
    c = np.where(speck(seed + 4, 0.94)[..., None], H(light), c)
    return c


def grass_side(top_col, soil, seed, fringe=3):
    """Soil texture with a ragged band of the top material hanging over the edge."""
    X, Y = grid()
    depth = fringe + np.round(hashf(X, np.zeros_like(X), seed) * 2 - 0.4)
    over = Y < depth
    c = np.where(over[..., None], shade(top_col, 0.85 + 0.25 * tnoise(seed + 1, 2.0)[..., None]), soil)
    c = np.where((Y == depth)[..., None] & ~over[..., None], shade(soil, 0.7), c)
    return c


# =============================================================================================
# Hell's Paradise
# =============================================================================================
def charnel_stone():
    """Dark meat-red rock packed with bone fragments."""
    c = rock(101, 0x5a2422, 0x220a0c, 0x8a4a40, cracks=0.03)
    X, Y = grid()
    frag = (tnoise(102, 2.6) > 0.82) & (hashf(X, Y, 103) > 0.3)
    c = np.where(frag[..., None], lerp(H(0xb8a888), H(0xe0d4b8), hashf(X, Y, 104)), c)
    return to_img(c)


def ash_soil():
    X, Y = grid()
    c = lerp(H(0x2a2626), H(0x6a625e), tnoise(111, 1.6))
    c = np.where(speck(112, 0.93)[..., None], H(0x9a928a), c)
    ember = speck(113, 0.975)
    c = np.where(ember[..., None], lerp(H(0xff5a10), H(0xffc040), hashf(X, Y, 114)), c)
    return to_img(c)


def ash_soil_glow():
    X, Y = grid()
    ember = speck(113, 0.975)
    c = lerp(H(0xff5a10), H(0xffc040), hashf(X, Y, 114))
    return to_img(c, ember.astype(float))


def sinew(top=True):
    """Raw striated muscle; the side has veins running down."""
    X, Y = grid()
    fib = tnoise(121, 1.0, sx=0.4, sy=3.0) if not top else tnoise(121, 1.4)
    c = lerp(H(0x5a0a10), H(0xb02a2a), fib)
    stri = (np.abs(np.sin((Y if not top else X + Y) * 1.6 + tnoise(122, 1.0) * 5)) > 0.85)
    c = np.where(stri[..., None], shade(c, 1.25), c)
    vein = np.abs(tnoise(123, 1.3) - 0.5) < 0.035
    c = np.where(vein[..., None], H(0x2a0010), c)
    c = np.where(speck(124, 0.97)[..., None], H(0xf0c0a8), c)  # fat
    return to_img(c)


# =============================================================================================
# Skyfall Torn
# =============================================================================================
def skystone():
    """Pale blue-grey layered stone with white quartz veins."""
    c = rock(201, 0x9aa4b4, 0x5a6478, 0xe8f0ff, layers=5)
    vein = np.abs(tnoise(202, 1.1) - 0.5) < 0.04
    return to_img(np.where(vein[..., None], H(0xf4f8ff), c))


def pale_moss_top():
    X, Y = grid()
    c = lerp(H(0x8aa69a), H(0xd0e4da), tnoise(211, 2.0))
    c = np.where(speck(212, 0.9)[..., None], H(0xf0fff8), c)
    c = np.where(speck(213, 0.97)[..., None], H(0x6a8aa8), c)
    return to_img(c)


def pale_moss_side():
    soil = np.asarray(skystone(), float)[..., :3] / 255
    return to_img(grass_side(H(0xb0ccc0), soil, 214))


def shroud_mold_top():
    X, Y = grid()
    c = lerp(H(0x4a4458), H(0x8a8098), tnoise(221, 1.8))
    spore = speck(222, 0.92)
    c = np.where(spore[..., None], H(0xc8bce0), c)
    web = np.abs(tnoise(223, 2.5) - 0.5) < 0.03
    return to_img(np.where(web[..., None], H(0xe8e0f0), c))


def shroud_mold_side():
    soil = np.asarray(skystone(), float)[..., :3] / 255
    return to_img(grass_side(H(0x6a6280), soil * 0.85, 224, fringe=4))


def ghostwood_log():
    X, Y = grid()
    bark = tnoise(231, 1.0, sx=3.0, sy=0.3)
    c = lerp(H(0x8a8a94), H(0xd8d8e0), bark)
    groove = np.abs(np.sin(X * 1.2 + tnoise(232, 0.8) * 4)) < 0.2
    c = np.where(groove[..., None], H(0x4a4a58), c)
    knot = np.hypot(X - 10, (Y - 6) * 0.6) < 1.5
    return to_img(np.where(knot[..., None], H(0x2a2a38), c))


def ghostwood_log_top():
    X, Y = grid()
    r = np.hypot(X - 7.5, Y - 7.5)
    c = lerp(H(0xb8b8c4), H(0xe8e8f0), np.sin(r * 1.5) * 0.5 + 0.5)
    c = np.where((r > 6.5)[..., None], H(0x8a8a94), c)
    return to_img(c)


def ghostwood_leaves():
    X, Y = grid()
    n = tnoise(241, 2.2)
    c = lerp(H(0x6a7a8a), H(0xc8d8e8), n)
    hole = tnoise(242, 2.8) < 0.28
    c = np.where(speck(243, 0.95)[..., None], H(0xf0f8ff), c)
    return to_img(c, (~hole).astype(float))


# =============================================================================================
# Light's Descent
# =============================================================================================
def umbral_stone():
    return to_img(rock(301, 0x343844, 0x14161e, 0x5a6074, cracks=0.025))


def grave_soil():
    X, Y = grid()
    c = lerp(H(0x1e1612), H(0x4a3628), tnoise(311, 1.7))
    bone = speck(312, 0.95)
    c = np.where(bone[..., None], H(0xcfc4a8), c)
    root = np.abs(tnoise(313, 1.2) - 0.5) < 0.025
    return to_img(np.where(root[..., None], H(0x120c0a), c))


def gloom_grass_top():
    X, Y = grid()
    c = lerp(H(0x1a2a28), H(0x3e5650), tnoise(321, 2.2))
    blade = speck(322, 0.85)
    c = np.where(blade[..., None], H(0x5a7a6e), c)
    return to_img(c)


def gloom_grass_side():
    soil = np.asarray(grave_soil(), float)[..., :3] / 255
    return to_img(grass_side(H(0x2e4440), soil, 323))


def bone_sand():
    X, Y = grid()
    c = lerp(H(0xb8ac90), H(0xe0d6bc), tnoise(331, 1.9))
    c = np.where(speck(332, 0.9)[..., None], H(0xf4ecd8), c)
    c = np.where(speck(333, 0.96)[..., None], H(0x8a7c64), c)
    return to_img(c)


def dustrock():
    """Ochre and bone-white strata."""
    X, Y = grid()
    wob = np.round(tnoise(341, 0.9) * 2)
    band = ((Y + wob) // 3) % 3
    cols = np.stack([H(0xa08060), H(0xd0c0a0), H(0x7a5a40)])
    c = cols[band.astype(int)]
    c = shade(c, 0.85 + 0.3 * tnoise(342, 2.0))
    c = np.where(speck(343, 0.95)[..., None], H(0xf0e8d8), c)
    return to_img(c)


# =============================================================================================
# Disposed Skies
# =============================================================================================
def voidrock():
    X, Y = grid()
    c = lerp(H(0x1a1628), H(0x3e3460), tnoise(401, 1.4))
    c = np.where((tnoise(402, 2.4) > 0.8)[..., None], H(0x52467a), c)
    c = np.where(speck(403, 0.965)[..., None], lerp(H(0x9a8aff), H(0xffffff), hashf(X, Y, 404)), c)
    return to_img(c)


def static_crust(top=True):
    X, Y = grid()
    c = lerp(H(0x101820), H(0x283848), tnoise(411, 1.6))
    arc = np.abs(tnoise(412, 2.0) - 0.5) < 0.03
    c = np.where(arc[..., None], H(0x7df9ff), c)
    if not top:
        soil = np.asarray(voidrock(), float)[..., :3] / 255
        c = np.where((Y > 3 + np.round(hashf(X, 0 * X, 413) * 2))[..., None], soil, c)
    return to_img(c)


def static_crust_glow(top=True):
    X, Y = grid()
    arc = np.abs(tnoise(412, 2.0) - 0.5) < 0.03
    if not top:
        arc &= ~(Y > 3 + np.round(hashf(X, 0 * X, 413) * 2))
    return to_img(np.broadcast_to(H(0x7df9ff), (N, N, 3)).copy(), arc.astype(float))


def void_crystal():
    X, Y = grid()
    facet = tnoise(421, 1.3)
    c = lerp(H(0x3a1a8a), H(0x9a6aff), facet)
    edge = np.abs(np.sin((X + Y) * 0.9 + facet * 3)) > 0.93
    c = np.where(edge[..., None], H(0xe0d0ff), c)
    c = np.where(speck(422, 0.95)[..., None], H(0x7df9ff), c)
    return to_img(c, 0.82 + 0.18 * facet)


# =============================================================================================
# Plants (crossed sprites)
# =============================================================================================
def _stalks(seed, count, col_lo, col_hi, height=(8, 14), tip=None, curl=0.0, leaf=None):
    X, Y = grid()
    c = np.zeros((N, N, 3))
    a = np.zeros((N, N))
    rnd = np.random.default_rng(seed)
    for i in range(count):
        x0 = 2 + rnd.integers(0, 12)
        hgt = rnd.integers(*height)
        lean = rnd.uniform(-0.35, 0.35)
        for k in range(hgt):
            y = 15 - k
            x = int(round(x0 + lean * k + curl * math.sin(k * 0.6 + i)))
            if not 0 <= x < N:
                continue
            t = k / max(1, hgt - 1)
            c[y, x] = lerp(H(col_lo), H(col_hi), t)
            a[y, x] = 1
            if leaf and k > 2 and k % 3 == 0:
                for dx in (-1, 1):
                    if 0 <= x + dx < N:
                        c[y, x + dx] = H(leaf)
                        a[y, x + dx] = 1
        if tip:
            y, x = 15 - hgt, int(round(x0 + lean * hgt))
            for dx, dy in ((0, 0), (1, 0), (0, 1), (-1, 0), (0, -1)):
                if 0 <= x + dx < N and 0 <= y + dy < N and (dx == 0 or rnd.random() < 0.8):
                    c[y + dy, x + dx] = H(tip) if (dx, dy) == (0, 0) else shade(H(tip), 0.75)
                    a[y + dy, x + dx] = 1
    return c, a


def cinder_sprout():
    c, a = _stalks(501, 5, 0x1a1210, 0x4a2a1a, (5, 11), tip=0xff7a20)
    return to_img(c, a)


def cinder_sprout_glow():
    c, a = _stalks(501, 5, 0x1a1210, 0x4a2a1a, (5, 11), tip=0xff7a20)
    glow = (c[..., 0] > 0.9) & (a > 0)
    return to_img(c, glow.astype(float))


def gloom_fern():
    c, a = _stalks(511, 4, 0x142420, 0x3a6a5e, (9, 15), curl=0.6, leaf=0x2a5048)
    return to_img(c, a)


def pale_bloom():
    c, a = _stalks(521, 3, 0x6a8a7a, 0x9ab4a6, (7, 12), tip=0xf0f4ff)
    X, Y = grid()
    centre = (a > 0) & (c[..., 0] > 0.9)
    c = np.where(centre[..., None] & (hashf(X, Y, 522) > 0.6)[..., None], H(0x7ab8ff), c)
    return to_img(c, a)


def static_thorn():
    c, a = _stalks(531, 4, 0x0a0a12, 0x2a2a3a, (8, 15), tip=0x7df9ff, leaf=0x1a1a24)
    return to_img(c, a)


def static_thorn_glow():
    c, a = _stalks(531, 4, 0x0a0a12, 0x2a2a3a, (8, 15), tip=0x7df9ff, leaf=0x1a1a24)
    glow = (c[..., 2] > 0.9) & (a > 0)
    return to_img(c, glow.astype(float))


# =============================================================================================
# Blood (animated fluid)
# =============================================================================================
def blood(flowing=False, frames=32):
    n = 32 if flowing else 16
    sheet = Image.new("RGBA", (n, n * frames))
    X, Y = grid(n)
    for f in range(frames):
        t = f / frames * 2 * math.pi
        if flowing:
            v = (Y / n * 2 * math.pi + t)
            ripple = 0.5 + 0.5 * np.sin(v * 2 + np.sin(X / n * 2 * math.pi * 2) * 0.8)
        else:
            ripple = 0.5 + 0.25 * np.sin(X / n * 2 * math.pi + t) + 0.25 * np.sin((X + Y) / n * 2 * math.pi * 2 - t)
        base = tnoise(601, 1.2, n=n)
        k = np.clip(0.55 * ripple + 0.45 * base, 0, 1)
        c = lerp(H(0x3a0006), H(0x9a0a14), k)
        c = np.where((k > 0.86)[..., None], H(0xd84858), c)
        sheet.paste(to_img(c, np.full((n, n), 0.92)), (0, f * n))
    return sheet


TEXTURES = {
    "charnel_stone": charnel_stone, "ash_soil": ash_soil, "sinew_block_top": lambda: sinew(True),
    "sinew_block": lambda: sinew(False),
    "skystone": skystone, "pale_moss_top": pale_moss_top, "pale_moss_side": pale_moss_side,
    "shroud_mold_top": shroud_mold_top, "shroud_mold_side": shroud_mold_side,
    "ghostwood_log": ghostwood_log, "ghostwood_log_top": ghostwood_log_top, "ghostwood_leaves": ghostwood_leaves,
    "umbral_stone": umbral_stone, "grave_soil": grave_soil, "gloom_grass_top": gloom_grass_top,
    "gloom_grass_side": gloom_grass_side, "bone_sand": bone_sand, "dustrock": dustrock,
    "voidrock": voidrock, "static_crust_top": lambda: static_crust(True), "static_crust_side": lambda: static_crust(False),
    "void_crystal": void_crystal,
    "cinder_sprout": cinder_sprout, "gloom_fern": gloom_fern, "pale_bloom": pale_bloom, "static_thorn": static_thorn,
}


# =============================================================================================
# Sky bodies (drawn by DeadSkies)
# =============================================================================================
def _disc(n=128):
    ys, xs = np.mgrid[0:n, 0:n].astype(float)
    c0 = (n - 1) / 2
    return xs, ys, np.hypot(xs - c0, ys - c0) / (n / 2)


def blood_moon(n=128):
    X, Y, r = _disc(n)
    lit = np.clip(1.15 - 0.35 * ((X - n * 0.35) ** 2 + (Y - n * 0.35) ** 2) ** 0.5 / n * 2, 0.5, 1.1)
    craters = fbm(X / 9, Y / 9, 701, 4)
    c = lerp(H(0x5a0408), H(0xd8301c), craters)
    c = np.where((np.abs(fbm(X / 14, Y / 14, 702, 3) - 0.5) < 0.03)[..., None], H(0x3a0004), c)
    c = shade(c, lit)
    a = np.clip((1 - r) * n / 3, 0, 1)
    return to_img(c, a)


def moon_halo(n=128):
    X, Y, r = _disc(n)
    a = np.clip(1 - r, 0, 1) ** 2.4
    c = lerp(H(0xffe0d0), H(0xff3020), np.clip(r * 1.3, 0, 1))
    return to_img(c, a)


def shattered_moon(n=128):
    X, Y, r = _disc(n)
    c = lerp(H(0x9aa4b8), H(0xf0f4ff), fbm(X / 10, Y / 10, 711, 4))
    crack = np.abs(fbm(X / 18, Y / 18, 712, 3) - 0.5) < 0.035
    c = np.where(crack[..., None], H(0x1a2030), c)
    a = np.clip((1 - r) * n / 3, 0, 1)
    gap = np.abs(fbm(X / 26, Y / 26, 713, 2) - 0.5) < 0.05  # whole chunks broken away
    return to_img(c, a * (~gap))


def void_eye(n=128):
    X, Y, r = _disc(n)
    c0 = (n - 1) / 2
    ang = np.arctan2(Y - c0, X - c0)
    swirl = 0.5 + 0.5 * np.sin(ang * 3 + r * 14 + fbm(X / 8, Y / 8, 721, 3) * 4)
    ring = np.exp(-((r - 0.55) / 0.16) ** 2)
    c = lerp(H(0x2a0a5a), H(0xd8b8ff), swirl * ring)
    c = np.where((r < 0.34)[..., None], H(0x000000), c)
    c = np.where((np.abs(r - 0.36) < 0.025)[..., None], H(0xffffff), c)
    a = np.clip(np.where(r < 0.36, 1.0, ring * 1.3), 0, 1) * (r < 1)
    return to_img(c, a)


SKY = {"blood_moon": blood_moon, "moon_halo": moon_halo, "shattered_moon": shattered_moon, "void_eye": void_eye}
