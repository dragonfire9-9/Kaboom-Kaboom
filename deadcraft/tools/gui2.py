"""HUD, widget, menu-background, panorama, title-scene and container GUI art for DeadCraft."""
from __future__ import annotations

import glob
import io
import math
import os
import zipfile

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

import content
from tex import rgb as H, shade, lerp, fbm, ridged, hashf, voronoi


def img(c, a=None):
    a = np.ones(c.shape[:2]) if a is None else a
    return Image.fromarray((np.dstack([np.clip(c, 0, 1), np.clip(a, 0, 1)]) * 255).astype(np.uint8), "RGBA")


def grid(w, h):
    ys, xs = np.mgrid[0:h, 0:w]
    return xs.astype(float), ys.astype(float)


IRON = H(0x2e2628)
BONE = H(0xcfc2a8)
BLOOD = H(0x8a0a10)


def iron(w, h, seed, base=IRON):
    X, Y = grid(w, h)
    c = shade(base, 0.85 + 0.3 * fbm(X / 9, Y / 3, seed, 3))
    c = shade(c, 0.95 + 0.1 * fbm(X / 14, Y * 1.3, seed + 1, 2))
    stain = fbm(X / 5, Y / 5, seed + 2) > 0.68
    return np.where(stain[..., None], shade(c, 0.82), c)


def bevel(c, light=1.35, dark=0.55, outline=H(0x070304)):
    c = c.copy()
    c[0, :], c[-1, :], c[:, 0], c[:, -1] = outline, outline, outline, outline
    if c.shape[0] > 2 and c.shape[1] > 2:
        c[1, 1:-1] = shade(c[1, 1:-1], light)
        c[1:-1, 1] = shade(c[1:-1, 1], light * 0.92)
        c[-2, 1:-1] = shade(c[-2, 1:-1], dark)
        c[1:-1, -2] = shade(c[1:-1, -2], dark * 1.1)
    return c


def rivet(c, x, y):
    c[y, x] = H(0xa8a4ac)
    c[y, x + 1] = H(0x6a6670)
    c[y + 1, x] = H(0x5a5660)
    c[y + 1, x + 1] = H(0x2a2630)


# =============================================================================================
# HUD
# =============================================================================================
def vial_frame(w=89, h=13):
    """A glass tube held by iron caps; the liquid is drawn inside x 5..w-5, y 3..h-3."""
    X, Y = grid(w, h)
    c = np.zeros((h, w, 3))
    a = np.zeros((h, w))
    glass = (X >= 4) & (X < w - 4) & (Y >= 2) & (Y < h - 2)
    tube_edge = glass & ((Y == 2) | (Y == h - 3))
    c[glass] = H(0x1a1214)
    a[glass] = 0.55
    c[tube_edge] = H(0x8a8090)
    a[tube_edge] = 0.9
    hl = glass & (Y == 3) & ((X // 6) % 4 != 3)
    c[hl] = H(0xe8e0f0)
    a[hl] = 0.35
    for x0 in (0, w - 5):
        cap = (X >= x0) & (X < x0 + 5)
        c[cap] = shade(IRON, 1.0 + 0.3 * (1 - Y / h))[cap]
        a[cap] = 1
        c[(cap & ((Y == 0) | (Y == h - 1)))] = H(0x070304)
        rx = x0 + 1
        c[5, rx:rx + 2] = H(0xa8a4ac)
        c[7, rx:rx + 2] = H(0x3a3640)
    a[0, 4:w - 4] = 0
    a[h - 1, 4:w - 4] = 0
    return img(c, a)


def vial_cracks(w=89, h=13):
    c = np.ones((h, w, 3))
    a = np.zeros((h, w))
    rng = np.random.default_rng(3)
    for _ in range(7):
        x, y = rng.integers(6, w - 6), rng.integers(3, h - 3)
        for _ in range(rng.integers(3, 8)):
            if 4 <= x < w - 4 and 2 <= y < h - 2:
                a[y, x] = 0.85
            x += rng.choice((-1, 1))
            y += rng.choice((-1, 0, 1))
    return img(c, a)


def liquid(kind, w=64, h=7):
    """Tileable liquid strip: a bright meniscus line, body gradient, bubbles."""
    X, Y = grid(w, h)
    base = {"blood": H(0xb0101a), "bile": H(0xd08a20), "soul": H(0x3a8aff), "ghost": H(0xf0d0d0)}[kind]
    t = Y / (h - 1)
    c = lerp(shade(base, 1.25), shade(base, 0.6), t)
    wave = 0.5 + 0.5 * np.sin(X / w * 2 * math.pi * 3)
    c = shade(c, 0.92 + 0.12 * wave + 0.08 * (fbm(X / 4, Y / 2, 9, 2) - 0.5))
    c[0] = shade(base, 1.7)
    bub = (hashf(X, Y, 4 + len(kind)) > 0.94) & (Y > 1)
    c[bub] = shade(base, 1.9)
    return img(c)


def skull_medallion(s=15):
    X, Y = grid(s, s)
    r = np.hypot(X - 7, Y - 7)
    c = np.zeros((s, s, 3))
    a = (r < 7.6).astype(float)
    c[:] = shade(IRON, 1.0)
    ring = (r > 6) & (r < 7.6)
    c[ring] = H(0x9a7a30)
    skull = ["..ooooo..", ".ooooooo.", "oo..o..oo", "oo..o..oo", "ooooooooo", ".oo.o.oo.", "..o.o.o.."]
    for j, row in enumerate(skull):
        for i, ch in enumerate(row):
            if ch == "o":
                c[3 + j, 3 + i] = shade(BONE, 1.15 - 0.05 * j)
    c[(r < 7.6) & (r > 7)] = H(0x070304)
    return img(c, a)


def skull_eyes(s=15):
    c = np.zeros((s, s, 3))
    a = np.zeros((s, s))
    for x, y in ((5, 5), (6, 5), (5, 6), (6, 6), (8, 5), (9, 5), (8, 6), (9, 6)):
        c[y, x] = H(0xff3030)
        a[y, x] = 1
    return img(c, a)


def shields():
    out = Image.new("RGBA", (27, 9), (0, 0, 0, 0))
    shape = [".ooooooo.", "oaaaaaabo", "oaaaaaabo", "oaaaaabbo", ".oaaabbo.", ".oaaabbo.", "..oabbo..", "...obo...", "....o...."]
    for k, state in enumerate(("full", "half", "empty")):
        for y, row in enumerate(shape):
            for x, ch in enumerate(row):
                if ch == ".":
                    continue
                if ch == "o":
                    col = H(0x0a0808)
                elif state == "empty" or (state == "half" and x >= 4):
                    col = H(0x2a2428)
                else:
                    col = shade(H(0xa8acb8), 1.2 if ch == "a" else 0.75)
                    if (x + y) % 5 == 0:
                        col = shade(col, 0.8)
                out.putpixel((k * 9 + x, y), tuple(int(v * 255) for v in col) + (255,))
    return out


def hotbar():
    w, h = 182, 22
    c = iron(w, h, 11)
    c = bevel(c)
    for i in range(9):
        x0 = 1 + i * 20
        slot = c[3:19, x0 + 2:x0 + 18]
        X, Y = grid(16, 16)
        rec = lerp(H(0x0e0a0b), H(0x1e1416), Y / 15)
        rec = np.where(((X == 0) | (Y == 0))[..., None], H(0x050303), rec)
        rec = np.where(((X == 15) | (Y == 15))[..., None], H(0x4a3a3c), rec)
        c[3:19, x0 + 2:x0 + 18] = rec
        if i:
            c[2:20, x0] = shade(BONE, 0.7)
            c[2:20, x0 + 1] = shade(BONE, 0.4)
    for x in (2, w - 4):
        rivet(c, x, 2)
        rivet(c, x, h - 4)
    return img(c)


def hotbar_selection():
    w, h = 24, 23
    c = np.zeros((h, w, 3))
    a = np.zeros((h, w))
    X, Y = grid(w, h)
    ring = (X <= 1) | (X >= w - 2) | (Y <= 1) | (Y >= h - 2)
    c[ring] = H(0xd01a20)
    c[(X == 0) | (X == w - 1) | (Y == 0) | (Y == h - 1)] = H(0x2a0004)
    a[ring] = 1
    glow = ((X == 2) | (X == w - 3) | (Y == 2) | (Y == h - 3)) & ~ring
    c[glow] = H(0xff6060)
    a[glow] = 0.45
    for x, y in ((0, 0), (w - 3, 0), (0, h - 3), (w - 3, h - 3)):
        c[y:y + 3, x:x + 3] = BONE
        a[y:y + 3, x:x + 3] = 1
    return img(c, a)


def offhand(left=True):
    w, h = 29, 24
    c = iron(w, h, 12)
    c = bevel(c)
    sx = 3 if left else 10
    X, Y = grid(16, 16)
    rec = lerp(H(0x0e0a0b), H(0x1e1416), Y / 15)
    c[4:20, sx + 1:sx + 17] = rec
    a = np.ones((h, w))
    if left:
        a[:, 22:] = 0
    else:
        a[:, :7] = 0
    return img(c, a)


def xp_bar(progress=False):
    w, h = 182, 5
    X, Y = grid(w, h)
    if not progress:
        c = lerp(H(0x0a0808), H(0x1a1214), Y / 4)
        c[0], c[-1] = H(0x050303), H(0x3a2c2c)
        tick = (X % 18 == 0)
        c[tick] = H(0x3a2c2c)
        return img(c)
    c = lerp(H(0xc8ff60), H(0x3a8a10), Y / 4)
    c = shade(c, 0.9 + 0.2 * fbm(X / 3, Y, 5, 2))
    c[0] = H(0xf0ffc0)
    return img(c)


def crosshair():
    c = np.ones((15, 15, 3))
    a = np.zeros((15, 15))
    for d in range(3, 7):
        for x, y in ((7 + d, 7), (7 - d, 7), (7, 7 + d), (7, 7 - d)):
            a[y, x] = 1
    a[7, 7] = 1
    return img(c, a)


BOSS = {"pink": 0xff60c0, "blue": 0x40a0ff, "red": 0xff2a2a, "green": 0x60ff60, "yellow": 0xffd040, "purple": 0xb060ff,
        "white": 0xf0f0f0}


def boss_bar(color, progress):
    w, h = 182, 5
    X, Y = grid(w, h)
    base = H(BOSS[color])
    if not progress:
        c = lerp(H(0x0c0808), H(0x1c1416), Y / 4)
        c[0], c[-1] = H(0x050303), H(0x2a2024)
        return img(c)
    c = lerp(shade(base, 1.3), shade(base, 0.5), Y / 4)
    c = shade(c, 0.88 + 0.24 * fbm(X / 4, Y, 7, 2))
    c[0] = shade(base, 1.8)
    return img(c)


def boss_notches(n, progress):
    w, h = 182, 5
    c = np.zeros((h, w, 3))
    a = np.zeros((h, w))
    for i in range(1, n):
        x = round(i * w / n)
        c[:, x] = H(0xffffff) if progress else BONE
        a[:, x] = 0.55 if progress else 0.9
        if not progress:
            a[0, x - 1:x + 2] = 1
            c[0, x - 1:x + 2] = BONE
    return img(c, a)


# =============================================================================================
# Widgets and menu backgrounds
# =============================================================================================
def slider(highlight=False):
    w, h = 200, 20
    c = iron(w, h, 21, H(0x1a1416))
    X, Y = grid(w, h)
    groove = (Y >= 8) & (Y <= 11) & (X > 3) & (X < w - 4)
    c[groove] = H(0x050303)
    c[8, 4:w - 4] = H(0x050303)
    c[12, 4:w - 4] = H(0x3a2a2c)
    c = bevel(c, 1.2, 0.7, H(0xd01a20) if highlight else H(0x070304))
    return img(c)


def slider_handle(highlight=False):
    w, h = 8, 20
    c = iron(w, h, 22, H(0x6a5a58) if not highlight else H(0x9a2a2a))
    c = bevel(c, 1.4, 0.5)
    c[4:16, 3:5] = shade(BONE, 0.9)
    return img(c)


def text_field(highlight=False):
    w, h = 200, 20
    X, Y = grid(w, h)
    c = lerp(H(0x050303), H(0x120c0d), Y / h)
    c[0, :], c[-1, :], c[:, 0], c[:, -1] = (H(0xd01a20) if highlight else H(0x6a5a58),) * 4
    return img(c)


def checkbox(selected=False, highlight=False):
    w = h = 20
    c = iron(w, h, 23)
    c = bevel(c, 1.3, 0.6, H(0xd01a20) if highlight else H(0x070304))
    X, Y = grid(w, h)
    inner = (X >= 4) & (X < 16) & (Y >= 4) & (Y < 16)
    c[inner] = H(0x0a0607)
    a = np.ones((h, w))
    if selected:  # a bloody X
        m = ((np.abs(X - Y) < 1.2) | (np.abs(X + Y - 19) < 1.2)) & inner
        c[m] = H(0xd01a20)
        c[m & (hashf(X, Y, 3) > 0.7)] = H(0xff6060)
    return img(c, a)


def scroller(background=False, size=(6, 32)):
    w, h = size
    if background:
        X, Y = grid(w, h)
        return img(lerp(H(0x050303), H(0x0e0a0b), X / max(1, w - 1)))
    c = iron(w, h, 24, H(0x5a4a48))
    return img(bevel(c, 1.3, 0.55))


def tab(selected=False, highlight=False):
    w, h = 130, 24
    base = H(0x3a2a2c) if selected else H(0x201618)
    c = iron(w, h, 25, base)
    c[0, :] = H(0x070304)
    c[:, 0] = H(0x070304)
    c[:, -1] = H(0x070304)
    c[1, 1:-1] = H(0xd01a20) if (highlight or selected) else shade(base, 1.4)
    if not selected:
        c[-1, :] = H(0x070304)
    return img(c)


def menu_tile(seed, inworld=False, list_bg=False):
    w = h = 16
    X, Y = grid(w, h)
    # seamless: sample noise on a torus
    ang_x, ang_y = X / w * 2 * math.pi, Y / h * 2 * math.pi
    n = fbm(np.cos(ang_x) * 3 + 10, np.sin(ang_x) * 3 + np.cos(ang_y) * 3 + np.sin(ang_y) * 3, seed, 3)
    n = 0.35 + 0.3 * n  # low contrast: a tile this small repeats visibly otherwise
    # the list well is a touch lighter than the header/footer so the two read as separate panels
    c = lerp(H(0x150709), H(0x36101a), n) if list_bg else lerp(H(0x0c0607), H(0x2a0a0e), n)
    alpha = 0.55 if inworld else 0.82
    if list_bg:
        alpha = 0.7 if inworld else 0.88
    return img(c, np.full((h, w), alpha))


def separator(header=True):
    c = np.zeros((2, 32, 3))
    a = np.ones((2, 32))
    c[0] = H(0xd01a20) if header else H(0x1a0a0c)
    c[1] = H(0x1a0a0c) if header else H(0xd01a20)
    return img(c, a)


# =============================================================================================
# Panorama (six cube faces, seamless by construction: everything is a function of view direction)
# =============================================================================================
def _directions(k, n):
    u, v = np.meshgrid((np.arange(n) + 0.5) / n, (np.arange(n) + 0.5) / n)
    a, b = -1 + 2 * u, -1 + 2 * v
    one = np.ones_like(a)
    x, y, z = {0: (a, b, one), 1: (one, b, -a), 2: (-a, b, -one), 3: (-one, b, a),
               4: (a, -one, b), 5: (a, one, -b)}[k]
    L = np.sqrt(x * x + y * y + z * z)
    return x / L, y / L, z / L


def _profile_castle(az, a0=-0.9):
    d = az - a0
    h = np.where(np.abs(d) < 0.32, 0.075, 0.0)
    for off, hh, wdt in ((-0.3, 0.16, 0.05), (-0.12, 0.2, 0.045), (0.0, 0.27, 0.05), (0.14, 0.18, 0.04), (0.3, 0.15, 0.05)):
        h = np.maximum(h, np.where(np.abs(d - off) < wdt, hh, 0.0))
        h = np.maximum(h, np.where(np.abs(d - off) < wdt * 0.35, hh + 0.05, 0.0))  # spires
    merlon = (np.floor(d * 160) % 2 == 0) & (np.abs(d) < 0.32)
    h = np.where(merlon & (h > 0), h + 0.008, h)
    return h


def panorama_face(k, n=1024):
    x, y, z = _directions(k, n)
    el = -y
    az = np.arctan2(x, z)
    # sky
    sky = lerp(H(0x3a080c), H(0x040205), np.clip(el * 2.2, 0, 1) ** 0.7)
    star = (hashf(np.floor((az + 4) * 600), np.floor((el + 2) * 600), 3) > 0.9965) & (el > 0.15)
    sky = np.where(star[..., None], H(0xe8d8d8), sky)
    clouds = fbm(az * 2.8 + 10, el * 9 + fbm(az * 4, el * 6, 8) * 1.5, 5, 5)
    cloud_a = np.clip((clouds - 0.48) * 3, 0, 1) * np.clip(el * 4, 0, 1) * (el > 0)
    cloud_col = lerp(H(0x1a0608), H(0x5a1418), np.clip(1 - el * 3, 0, 1))
    # blood moon
    m_az, m_el = 0.55, 0.32
    dm = np.sqrt((((az - m_az + math.pi) % (2 * math.pi)) - math.pi) ** 2 * np.cos(el) ** 2 + (el - m_el) ** 2)
    moon = dm < 0.075
    crater = fbm((az - m_az) * 70, (el - m_el) * 70, 12, 3)
    moon_col = lerp(H(0xff5a40), H(0x8a1810), np.clip(crater * 1.4 - 0.2 + (el - m_el) * 4, 0, 1))
    halo = np.exp(-np.maximum(dm - 0.075, 0) * 9) * 0.55
    sky = lerp(sky, H(0xc02020), halo)
    sky = np.where(moon[..., None], moon_col, sky)
    sky = lerp(sky, cloud_col, cloud_a * (1 - 0.6 * moon))
    c = sky
    # far mountains, lit red along the ridge
    mountains = 0.035 + 0.06 * fbm(az * 3 + 50, 1.0, 21, 4)
    mtn = (el < mountains) & (el > -0.02)
    ridge = mtn & (el > mountains - 0.006)
    c = np.where(mtn[..., None], lerp(H(0x1c0c12), H(0x0c0608), np.clip((mountains - el) * 20, 0, 1)), c)
    c = np.where(ridge[..., None], H(0x6a1a1a), c)
    # castle silhouette with lit windows
    cast = _profile_castle(az)
    castle = (el < cast) & (el > -0.01)
    c = np.where(castle[..., None], H(0x070405), c)
    win = castle & (hashf(np.floor(az * 300), np.floor(el * 300), 41) > 0.93) & (el > 0.03)
    c = np.where(win[..., None], H(0xffa040), c)
    # fog band at the horizon
    fog = np.exp(-((el - 0.005) / 0.035) ** 2) * (0.45 + 0.25 * fbm(az * 6, el * 30, 31))
    c = lerp(c, H(0x5a2a2e), fog * 0.75)
    # near hill with dead trees, crosses and gravestones
    hill = -0.01 + 0.02 * np.sin(az * 2.3) + 0.015 * fbm(az * 8, 2.0, 51, 3)
    near = el < hill
    rng = np.random.default_rng(7)
    sil = near.copy()
    for _ in range(14):  # crosses and headstones
        a0 = rng.uniform(-math.pi, math.pi)
        hb = 0.02 + 0.015 * np.sin(a0 * 2.3)
        d = (((az - a0 + math.pi) % (2 * math.pi)) - math.pi)
        if rng.random() < 0.5:
            cross = ((np.abs(d) < 0.004) & (el < hb + 0.06) & (el > hb - 0.02)) | \
                    ((np.abs(d) < 0.016) & (np.abs(el - (hb + 0.042)) < 0.004))
        else:
            cross = (np.abs(d) < 0.012) & (el < hb + 0.025 - (np.abs(d) > 0.008) * 0.004) & (el > hb - 0.02)
        sil |= cross
    for _ in range(7):  # dead trees: trunk and crooked branches
        a0 = rng.uniform(-math.pi, math.pi)
        base = 0.0
        d = (((az - a0 + math.pi) % (2 * math.pi)) - math.pi)
        tree = (np.abs(d) < 0.006 * (1.2 - el * 4)) & (el < 0.16) & (el > -0.03)
        for _ in range(5):
            e0 = rng.uniform(0.05, 0.14)
            slope = rng.choice((-1, 1)) * rng.uniform(0.6, 2.2)
            ln = rng.uniform(0.03, 0.07)
            t = (el - e0) / ln
            on = (t > 0) & (t < 1) & (np.abs(d - slope * (el - e0)) < 0.0035)
            tree |= on
        sil |= tree
    c = np.where(sil[..., None], H(0x050304), c)
    # ground below the horizon fades into black mist
    ground = el < -0.02
    c = np.where(ground[..., None], lerp(H(0x0c0608), H(0x020101), np.clip(-el * 3, 0, 1)), c)
    mist = np.exp(-((el + 0.06) / 0.08) ** 2) * fbm(az * 10, el * 20, 61, 3)
    c = lerp(c, H(0x3a1a1e), mist * 0.5)
    return img(c)


# =============================================================================================
# Title-screen scene layers (parallax)
# =============================================================================================
def title_sky(w=1024, h=576):
    X, Y = grid(w, h)
    t = Y / h
    c = lerp(H(0x040205), H(0x3a080c), t ** 1.6)
    mx, my, mr = w * 0.72, h * 0.3, h * 0.13
    dm = np.hypot(X - mx, Y - my)
    crater = fbm(X / 14, Y / 14, 5, 4)
    moon = dm < mr
    moon_col = lerp(H(0xff6a48), H(0x8a1810), np.clip(crater * 1.3 - 0.15 + (Y - my) / (mr * 3), 0, 1))
    halo = np.exp(-np.maximum(dm - mr, 0) / (mr * 0.9)) * 0.6
    c = lerp(c, H(0xb01818), halo)
    c = np.where(moon[..., None], moon_col, c)
    star = (hashf(X, Y, 9) > 0.9975) & (t < 0.6)
    c = np.where(star[..., None], H(0xf0e0e0), c)
    clouds = fbm(X / 160 + fbm(X / 300, Y / 120, 3) * 0.8, Y / 50, 12, 5)
    ca = np.clip((clouds - 0.5) * 3.2, 0, 1) * np.clip(1.2 - t * 1.4, 0, 1)
    lit = np.exp(-dm / (mr * 3))
    c = lerp(c, lerp(H(0x14060a), H(0x6a1a1e), lit), ca)
    return img(c)


def title_far(w=1024, h=320):
    X, Y = grid(w, h)
    az = X / w * 2 * math.pi
    ridge = h * (0.55 - 0.3 * fbm(X / 180, 1.0, 21, 4))
    castle = np.zeros(w)
    x0 = w * 0.3
    for off, hh, wd in ((-60, 110, 22), (-25, 150, 18), (0, 190, 22), (30, 130, 16), (62, 105, 20)):
        m = np.abs(X[0] - (x0 + off)) < wd
        castle = np.maximum(castle, np.where(m, hh, 0))
        castle = np.maximum(castle, np.where(np.abs(X[0] - (x0 + off)) < wd * 0.35, hh + 35, 0))
    castle = np.maximum(castle, np.where(np.abs(X[0] - x0) < 85, 70, 0))
    merl = (np.floor(X[0] / 5) % 2 == 0) & (castle > 0)
    castle = castle + merl * 4
    top = np.minimum(ridge, (h - castle)[None, :] - 0)
    solid = Y >= top
    c = lerp(H(0x1a0a10), H(0x080406), np.clip((Y - ridge) / 120, 0, 1))
    c = np.where((Y >= h - castle[None, :])[..., None], H(0x060304), c)
    rim = solid & (Y < top + 2) & (Y >= ridge)
    c = np.where(rim[..., None], H(0x7a2020), c)
    win = (Y >= h - castle[None, :]) & (hashf(np.floor(X / 3), np.floor(Y / 4), 41) > 0.95) & (Y < h - 20)
    c = np.where(win[..., None], H(0xffa040), c)
    return img(c, solid.astype(float))


def title_mid(w=1024, h=300, seed=3):
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    rng = np.random.default_rng(seed)
    col = (8, 4, 5, 255)
    pts = [(x, h * 0.62 + 18 * math.sin(x / 90) + 10 * math.sin(x / 33)) for x in range(0, w + 8, 8)]
    d.polygon(pts + [(w, h), (0, h)], fill=col)

    def branch(x, y, ang, ln, wdt, depth):
        if depth == 0 or ln < 4:
            return
        x2, y2 = x + math.cos(ang) * ln, y - math.sin(ang) * ln
        d.line([(x, y), (x2, y2)], fill=col, width=max(1, int(wdt)))
        for _ in range(2 if depth > 1 else 3):
            branch(x2, y2, ang + rng.uniform(-0.9, 0.9), ln * rng.uniform(0.55, 0.8), wdt * 0.65, depth - 1)

    for x in rng.uniform(40, w - 40, 5):
        base = h * 0.62 + 18 * math.sin(x / 90) + 10 * math.sin(x / 33)
        branch(x, base + 4, math.pi / 2 + rng.uniform(-0.15, 0.15), rng.uniform(55, 90), 7, 6)
    for x in rng.uniform(10, w - 10, 26):
        base = h * 0.62 + 18 * math.sin(x / 90) + 10 * math.sin(x / 33)
        if rng.random() < 0.5:
            d.rectangle([x - 2, base - 30, x + 2, base + 4], fill=col)
            d.rectangle([x - 10, base - 22, x + 10, base - 18], fill=col)
        else:
            d.rounded_rectangle([x - 8, base - 20, x + 8, base + 4], radius=6, fill=col)
    # gallows
    gx = w * 0.82
    gb = h * 0.62 + 18 * math.sin(gx / 90) + 10 * math.sin(gx / 33)
    d.rectangle([gx - 3, gb - 120, gx + 3, gb + 4], fill=col)
    d.rectangle([gx - 3, gb - 120, gx + 60, gb - 114], fill=col)
    d.line([(gx + 50, gb - 114), (gx + 50, gb - 70)], fill=col, width=2)
    d.ellipse([gx + 45, gb - 72, gx + 55, gb - 62], outline=col, width=2)
    return im


def title_fog(w=512, h=160, seed=5):
    X, Y = grid(w, h)
    ang = X / w * 2 * math.pi
    n = fbm(np.cos(ang) * 3 + 5, np.sin(ang) * 3 + Y / 40, seed, 4)
    a = np.clip((n - 0.35) * 1.6, 0, 1) * np.exp(-((Y - h * 0.55) / (h * 0.3)) ** 2)
    return img(np.broadcast_to(H(0x5a2a2e), (h, w, 3)), a * 0.85)


# =============================================================================================
# Container GUIs: repaint the vanilla layouts in bone, iron and blood
# =============================================================================================
VANILLA_GUI = ["inventory", "generic_54", "crafting_table", "furnace", "blast_furnace", "smoker", "dispenser", "hopper",
               "shulker_box", "anvil", "enchanting_table", "brewing_stand", "grindstone", "stonecutter", "smithing",
               "cartography_table", "loom", "beacon", "horse", "villager", "crafter"]


def _vanilla_jar():
    jars = glob.glob(os.path.join(content.ROOT, "build/moddev/artifacts/*client-extra*.jar"))
    return jars[0] if jars else None


def container(name, seed=0):
    jar = _vanilla_jar()
    if not jar:
        return None
    with zipfile.ZipFile(jar) as z:
        src = np.asarray(Image.open(io.BytesIO(z.read(f"assets/minecraft/textures/gui/container/{name}.png"))).convert("RGBA")).astype(float) / 255
    h, w = src.shape[:2]
    rgb_, a = src[..., :3], src[..., 3]
    X, Y = grid(w, h)
    lum = rgb_.mean(axis=2)
    panel_tex = shade(H(0x9a8c7c), 0.88 + 0.22 * fbm(X / 6, Y / 6, seed + 1, 4))
    stain = fbm(X / 10, Y / 10, seed + 2) > 0.64
    panel_tex = np.where(stain[..., None], lerp(panel_tex, H(0x6a2a20), 0.35), panel_tex)
    crack = ridged(X / 14, Y / 14, seed + 3) > 0.975
    panel_tex = np.where(crack[..., None], shade(H(0x9a8c7c), 0.6), panel_tex)

    def near(col, tol=0.02):
        return (np.abs(rgb_ - np.array(col) / 255).max(axis=2) < tol) & (a > 0.5)

    out = rgb_.copy()
    out = np.where(near((198, 198, 198))[..., None], panel_tex, out)
    out = np.where(near((255, 255, 255))[..., None], H(0xe2d6c0), out)
    out = np.where(near((85, 85, 85))[..., None], H(0x4a3a34), out)
    out = np.where(near((0, 0, 0))[..., None], H(0x140808), out)
    out = np.where(near((139, 139, 139))[..., None], lerp(H(0x2a1e1e), H(0x3a2a28), fbm(X / 3, Y / 3, seed + 4)), out)
    out = np.where(near((55, 55, 55))[..., None], H(0x120a0a), out)
    known = near((198, 198, 198)) | near((255, 255, 255)) | near((85, 85, 85)) | near((0, 0, 0)) | \
        near((139, 139, 139)) | near((55, 55, 55))
    other = (a > 0.05) & ~known
    ramp = lerp(H(0x1a0a0c), H(0xe8d4c0), lum)
    out = np.where(other[..., None], lerp(out, ramp, 0.75), out)
    # dried blood running from the top edge of the panel
    rng = np.random.default_rng(seed + 9)
    panel = near((198, 198, 198))
    for _ in range(18):
        x = rng.integers(4, w - 4)
        col_ys = np.nonzero(panel[:, x])[0]
        if len(col_ys) == 0:
            continue
        y0 = col_ys[0]
        L = rng.integers(2, 14)
        for i in range(L):
            if y0 + i < h and panel[y0 + i, x]:
                out[y0 + i, x] = lerp(H(0x6a0a0e), H(0x3a0408), i / max(1, L))
    return img(out, a)


# =============================================================================================
# Main menu (v3): a candle-lit crypt with an altar on the right, where the boss models stand
# =============================================================================================
def menu_crypt(W=1024, HT=576):
    """Gothic vaulted crypt. Altar centre-top at (0.70 W, 0.80 HT) (see DeadTitleScreen)."""
    import math as _m
    ys, xs = np.mgrid[0:HT, 0:W].astype(float)
    # stone wall: brick courses with mortar, per-brick tone and noise
    bh, bw = 18, 40
    row = (ys // bh).astype(int)
    off = np.where(row % 2 == 0, 0, bw // 2)
    bx = ((xs + off) // bw).astype(int)
    mortar = ((ys % bh) < 2) | (((xs + off) % bw) < 2)
    tone = 0.75 + 0.35 * hashf(bx, row, 9001)
    n = fbm(xs / 30, ys / 30, 9002, 4)
    stone = shade(H(0x3a3436), (tone * (0.8 + 0.4 * n)))
    stone = np.where(mortar[..., None], H(0x141012), stone)
    pic = stone
    # three pointed arches (dark recesses)
    def arch(cx, top, half, bottom):
        dx = np.abs(xs - cx)
        inside_rect = (dx < half) & (ys > top + half) & (ys < bottom)
        # pointed top: two circles of radius 2*half offset sideways
        r = half * 1.6
        c1 = np.hypot(xs - (cx - half + r - half * 0.0) + r - half, ys - (top + half)) < r
        pointed = (dx < half) & (ys <= top + half) & (ys > top) & (np.hypot(dx - half + r, ys - (top + half)) < r)
        return inside_rect | pointed
    for cx, half, top in ((0.18, 0.09, 0.12), (0.45, 0.11, 0.06), (0.72, 0.13, 0.02)):
        m = arch(cx * W, top * HT, half * W, HT * 0.86)
        depth = np.clip((ys - top * HT) / (HT * 0.8), 0, 1)
        recess = lerp(H(0x050304), H(0x120a0c), depth)
        # arch rim stones
        rim = arch(cx * W, top * HT - 8, half * W + 8, HT * 0.86) & ~m
        pic = np.where(rim[..., None], shade(H(0x5a5052), 0.8 + 0.3 * hashf((xs // 12).astype(int), (ys // 12).astype(int), 9003)[..., None]), pic)
        pic = np.where(m[..., None], recess, pic)
    # floor: flagstones in perspective
    floor_top = int(HT * 0.82)
    fl = ys >= floor_top
    fy = np.clip((ys - floor_top) / (HT - floor_top + 1), 0, 1)
    fx = (xs - W * 0.55) / (0.3 + fy * 0.7)
    tile = (((fx // 60).astype(int) + ((fy * 6).astype(int))) % 2 == 0)
    floor = shade(H(0x2a2426), np.where(tile, 0.9, 0.7) * (0.6 + 0.6 * fy))
    floor = np.where((np.abs((fy * 6) % 1) < 0.05)[..., None], H(0x0e0a0a), floor)
    pic = np.where(fl[..., None], floor, pic)
    # candle niches with glow
    glow = np.zeros((HT, W))
    candles = [(0.06, 0.62), (0.31, 0.58), (0.58, 0.6), (0.88, 0.55), (0.95, 0.72), (0.03, 0.78), (0.38, 0.79)]
    for cx, cy in candles:
        X, Y = cx * W, cy * HT
        niche = (np.abs(xs - X) < 9) & (ys > Y - 22) & (ys < Y + 4)
        pic = np.where(niche[..., None], H(0x0c0808), pic)
        stick = (np.abs(xs - X) < 2.5) & (ys > Y - 8) & (ys < Y + 4)
        pic = np.where(stick[..., None], H(0xd8c8a8), pic)
        flame = np.hypot((xs - X) / 2.2, (ys - (Y - 12)) / 4.5) < 1
        pic = np.where(flame[..., None], H(0xffd890), pic)
        glow += np.exp(-((xs - X) ** 2 + (ys - Y + 10) ** 2) / (2 * 70 ** 2)) * 0.55
    # the altar: a stepped dais with a carved block on top
    ax, ay = 0.70 * W, 0.80 * HT
    for i, (hw, h) in enumerate(((150, 18), (115, 16), (85, 34))):
        top = ay + (2 - i) * 0 - sum(hh for _, hh in ((150, 18), (115, 16), (85, 34))[:i]) - h + 0
        m = (np.abs(xs - ax) < hw) & (ys >= top) & (ys < top + h)
        face = shade(H(0x4a4246), 0.75 + 0.35 * fbm(xs / 8, ys / 8, 9010 + i, 3))
        face = np.where(((ys - top) < 3)[..., None], H(0x6a6064), face)
        pic = np.where(m[..., None], face, pic)
    rune = (np.abs(xs - ax) < 60) & (np.abs(ys - (ay - 50)) < 3)
    pic = np.where(rune[..., None], H(0x8a1010), pic)
    # red light rising from the altar, warm light from candles
    red = np.exp(-((xs - ax) ** 2 / (2 * 160 ** 2) + (ys - ay) ** 2 / (2 * 180 ** 2)))
    pic = pic * (0.55 + 0.45 * np.clip(glow, 0, 1))[..., None]
    pic = pic + (red * 0.22)[..., None] * np.array([1.0, 0.1, 0.12]) + (np.clip(glow, 0, 1) * 0.12)[..., None] * np.array([1.0, 0.6, 0.25])
    # vignette
    v = np.clip(1 - (np.hypot((xs - W / 2) / (W * 0.62), (ys - HT / 2) / (HT * 0.7))) ** 2.2, 0, 1)
    pic = pic * (0.25 + 0.75 * v)[..., None]
    return img(np.clip(pic, 0, 1))


def menu_panel(W=360, HT=576):
    """The dark gradient panel behind the menu column on the left."""
    ys, xs = np.mgrid[0:HT, 0:W].astype(float)
    a = np.clip(0.9 - xs / W * 0.9, 0, 0.9) ** 1.2
    c = np.zeros((HT, W, 3))
    c[..., 0] = 0.03
    return img(c, a)
