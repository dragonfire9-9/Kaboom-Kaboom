"""Blocks, gun atlases, particles, projectiles and effect icons."""
from __future__ import annotations

import math

import numpy as np
from PIL import Image

import tex
from tex import rgb as H
from tex import Face, shade, lerp, fbm, hashf, voronoi, ridged


def to_img(c, a=None):
    if a is None:
        a = np.ones(c.shape[:2])
    rgba = np.dstack([np.clip(c, 0, 1), np.clip(a, 0, 1)])
    return Image.fromarray((rgba * 255).astype(np.uint8), "RGBA")


def grid(n=16):
    ys, xs = np.mgrid[0:n, 0:n]
    return xs.astype(float), ys.astype(float)


# =============================================================================================
# Block bases
# =============================================================================================
def stone(seed, base=0x7a7a7c, streaks=False, n=16):
    X, Y = grid(n)
    B = H(base)
    blot = fbm(X / 3, Y / 3, seed, 3)
    c = shade(B, 0.82 + 0.32 * blot)
    dark = fbm(X / 2.2 + 9, Y / 2.2, seed + 1) > 0.66
    c = np.where(dark[..., None], shade(B, 0.72), c)
    if streaks:
        band = ((Y + (fbm(X / 4, Y, seed + 2) * 3).astype(int)) % 4) == 0
        c = np.where(band[..., None], shade(c, 0.82), c)
    speck = hashf(X, Y, seed) > 0.93
    c = np.where(speck[..., None], shade(B, 1.18), c)
    return c


def void_stone(seed):
    X, Y = grid()
    c = shade(H(0x2a2438), 0.75 + 0.4 * fbm(X / 3, Y / 3, seed))
    star = hashf(X, Y, seed + 3) > 0.96
    return np.where(star[..., None], H(0x8a7aaa), c)


BLOBS = [
    [(0, 0), (1, 0), (0, 1), (1, 1), (2, 1)],
    [(1, 0), (0, 1), (1, 1), (2, 1), (1, 2)],
    [(0, 0), (1, 0), (2, 0), (1, 1)],
    [(0, 0), (1, 1), (2, 1), (1, 2)],
    [(0, 0), (1, 0), (0, 1)],
]


def ore(base, color, seed, style="gem", glow=False):
    rng = np.random.default_rng(seed)
    c = base.copy()
    C = H(color)
    spots = [(3, 3), (10, 2), (6, 8), (12, 10), (2, 12), (9, 13)]
    rng.shuffle(spots)
    occupied = np.zeros((16, 16), bool)
    for (sx, sy) in spots[:5]:
        blob = BLOBS[rng.integers(len(BLOBS))]
        sx += rng.integers(-1, 2)
        sy += rng.integers(-1, 2)
        pts = [(sx + dx, sy + dy) for dx, dy in blob if 0 <= sx + dx < 16 and 0 <= sy + dy < 16]
        for x, y in pts:
            occupied[y, x] = True
        for x, y in pts:
            up = (x, y - 1) not in pts and (x - 1, y) not in pts
            down = (x, y + 1) not in pts and (x + 1, y) not in pts
            k = 1.3 if up else 0.7 if down else 1.0
            if style == "vein":
                k *= 0.95 + 0.1 * ((x + y) % 2)
            c[y, x] = shade(C, k)
        if pts:
            x, y = pts[0]
            c[y, x] = shade(C, 1.7) if style != "vein" else shade(C, 1.25)
    # dark rim around each cluster so it reads at a distance
    rim = np.zeros_like(occupied)
    for y in range(16):
        for x in range(16):
            if not occupied[y, x] and any(0 <= y + dy < 16 and 0 <= x + dx < 16 and occupied[y + dy, x + dx]
                                          for dy, dx in ((1, 0), (0, 1))):
                rim[y, x] = True
    c = np.where(rim[..., None], shade(c, 0.6), c)
    return to_img(c)


def necrite_block():
    X, Y = grid()
    c = shade(H(0xd6ccb4), 0.85 + 0.2 * fbm(X / 3, Y / 3, 5))
    brick = ((Y % 8) == 0) | (((X + (Y // 8) * 4) % 8) == 0)
    c = np.where(brick[..., None], shade(H(0xd6ccb4), 0.6), c)
    skull = ["..oooo..", ".oaaaao.", "oaaaaaao", "oaooaooa", "oaooaooa", "oaaoaaao", ".oaaaao.", ".oaoaoo."]
    for j, row in enumerate(skull):
        for i, ch in enumerate(row):
            if ch == "o":
                c[4 + j, 4 + i] = shade(H(0xd6ccb4), 0.45)
            elif ch == "a":
                c[4 + j, 4 + i] = shade(H(0xd6ccb4), 1.1)
    return to_img(c)


def bloodstone_block():
    X, Y = grid()
    d1, d2, cid = voronoi(X, Y, 8, 4.0)
    c = shade(H(0x9a1018), 0.7 + 0.6 * cid)
    edge = (d2 - d1) < 0.12
    c = np.where(edge[..., None], shade(H(0x9a1018), 0.4), c)
    glint = (hashf(X, Y, 3) > 0.94) & ~edge
    c = np.where(glint[..., None], H(0xffb0b0), c)
    c[0, :], c[:, 0] = shade(H(0xd02028), 1.2), shade(H(0xd02028), 1.1)
    c[15, :], c[:, 15] = shade(H(0x5a0508), 0.8), shade(H(0x5a0508), 0.8)
    return to_img(c)


def voidsteel_block():
    X, Y = grid()
    c = shade(H(0x34324a), 0.8 + 0.3 * fbm(X / 7, Y * 1.5, 4, 2))
    seam = (X == 7) | (Y == 7) | (X == 8) & False
    c = np.where(seam[..., None], H(0x9a7cff), c)
    for (x, y) in ((2, 2), (12, 2), (2, 12), (12, 12), (5, 5), (10, 10), (5, 10), (10, 5)):
        c[y, x] = shade(H(0x6a6890), 1.3)
        c[y + 1, x + 1] = shade(H(0x34324a), 0.5)
    c[0, :], c[:, 0] = shade(H(0x5a5878), 1.1), shade(H(0x5a5878), 1.0)
    c[15, :], c[:, 15] = H(0x15141e), H(0x15141e)
    return to_img(c)


def stardust_block():
    X, Y = grid()
    c = lerp(H(0x101c40), H(0x24346a), fbm(X / 4, Y / 4, 6))
    star = [(7, 4), (7, 5), (7, 6), (7, 7), (7, 8), (7, 9), (7, 10), (4, 7), (5, 7), (6, 7), (8, 7), (9, 7), (10, 7),
            (6, 6), (8, 6), (6, 8), (8, 8)]
    for x, y in star:
        c[y, x] = H(0xfff0a0) if (x, y) != (7, 7) else np.ones(3)
    sp = hashf(X, Y, 9) > 0.95
    c = np.where(sp[..., None], H(0xffe680), c)
    c[0, :], c[:, 0] = H(0xd4af37), H(0xd4af37)
    c[15, :], c[:, 15] = H(0x8a6a1a), H(0x8a6a1a)
    return to_img(c)


def charred_side():
    X, Y = grid()
    bark = fbm(X * 0.9, Y / 6, 11, 3)
    c = shade(H(0x2a221e), 0.6 + 0.6 * bark)
    crack = (fbm(X * 1.3, Y / 8, 12, 2) < 0.32)
    c = np.where(crack[..., None], H(0x0c0808), c)
    ember = crack & (hashf(X, Y, 13) > 0.8)
    c = np.where(ember[..., None], lerp(H(0xff5a10), H(0xffb040), hashf(X, Y, 14)), c)
    ash = hashf(X, Y, 15) > 0.95
    c = np.where(ash[..., None], H(0x6a625c), c)
    return to_img(c)


def charred_top():
    X, Y = grid()
    r = np.hypot(X - 7.5, Y - 7.5)
    rings = np.sin(r * 1.7 + fbm(X / 3, Y / 3, 3) * 2) * 0.5 + 0.5
    c = lerp(H(0x3a2a20), H(0x5a4030), rings)
    c = np.where((r < 1.6)[..., None], H(0xff6a20), c)
    c = np.where((r > 6.6)[..., None], shade(H(0x1a1410), 0.8 + 0.4 * hashf(X, Y, 2)[..., None]), c)
    return to_img(c)


def gravestone():
    X, Y = grid()
    c = stone(21, 0x8a8a90)
    moss = (fbm(X / 3, Y / 3, 22) > 0.68) & (Y > 9)
    c = np.where(moss[..., None], shade(H(0x4a6a3a), 0.8 + 0.4 * hashf(X, Y, 1)[..., None]), c)
    crack = ridged(X / 4, Y / 4, 23) > 0.96
    return to_img(np.where(crack[..., None], shade(H(0x8a8a90), 0.45), c))


def blood_puddle():
    X, Y = grid()
    c = lerp(H(0x3a0003), H(0x8a0008), fbm(X / 3, Y / 3, 31))
    gloss = (ridged(X / 4 + Y / 8, Y / 4, 32) > 0.92)
    c = np.where(gloss[..., None], H(0xd84050), c)
    hi = hashf(X, Y, 33) > 0.97
    return to_img(np.where(hi[..., None], H(0xffb0b8), c))


def lantern_frame():
    X, Y = grid()
    c = shade(H(0x2a2a30), 0.8 + 0.4 * fbm(X / 6, Y * 1.2, 41, 2))
    rivet = ((X % 5) == 2) & ((Y % 5) == 2)
    c = np.where(rivet[..., None], H(0x6a6a74), c)
    return to_img(c)


def lantern_core():
    X, Y = grid()
    r = np.hypot(X - 7.5, Y - 7.5) / 8
    c = lerp(H(0xeaffff), H(0x5a3ad0), np.clip(r * 1.4, 0, 1))
    a = np.clip(1.15 - r, 0, 1)
    return to_img(c, a)


def portal_frames(n=16):
    frames = Image.new("RGBA", (16, 16 * n))
    X, Y = grid()
    for f in range(n):
        t = f / n * math.tau
        dx, dy = X - 7.5, Y - 7.5
        r = np.hypot(dx, dy)
        a = np.arctan2(dy, dx)
        swirl = 0.5 + 0.5 * np.sin(a * 3 + r * 0.9 - t * 2 + fbm(X / 4, Y / 4, f % 4) * 2)
        pulse = 0.5 + 0.5 * np.sin(r * 1.4 - t * 3)
        c = lerp(H(0x1a0030), H(0xb030ff), swirl * 0.85)
        c = lerp(c, H(0xff3a6a), pulse * 0.3)
        star = hashf(X + f * 3, Y + f * 5, 77) > 0.97
        c = np.where(star[..., None], np.ones(3), c)
        frames.paste(to_img(c, np.full((16, 16), 0.82)), (0, 16 * f))
    return frames


def blocks():
    st = stone(1)
    deep = stone(2, 0x46464e, streaks=True)
    vs = void_stone(3)
    return {
        "necrite_ore": ore(st, 0xe6dcc4, 10, "vein"),
        "deepslate_necrite_ore": ore(deep, 0xe6dcc4, 11, "vein"),
        "bloodstone_ore": ore(st, 0xc0141e, 12),
        "deepslate_bloodstone_ore": ore(deep, 0xc0141e, 13),
        "voidsteel_ore": ore(vs, 0x9a7cff, 14),
        "stardust_ore": ore(st, 0xffd860, 15),
        "necrite_block": necrite_block(), "bloodstone_block": bloodstone_block(),
        "voidsteel_block": voidsteel_block(), "stardust_block": stardust_block(),
        "charred_log": charred_side(), "charred_log_top": charred_top(), "gravestone": gravestone(),
        "blood_puddle": blood_puddle(), "dread_lantern": lantern_frame(), "dread_lantern_core": lantern_core(),
    }


# =============================================================================================
# Gun atlas: 32x32, 8x8 swatches (model UVs still address it in 0..16 units)
# =============================================================================================
SWATCH = {"metal": (0, 0), "steel": (4, 0), "wood": (8, 0), "rust": (12, 0), "black": (0, 4), "gold": (4, 4),
          "glow": (8, 4), "leather": (12, 4), "brass": (0, 8), "glass": (4, 8)}


def gun_atlas(seed, tint):
    img = np.zeros((32, 32, 3))
    T = np.array(tint, float) / 255
    for name, (sx, sy) in SWATCH.items():
        f = Face(sx * 2, sy * 2, 8, 8, "front", seed=seed)
        X, Y = f.X, f.Y
        if name in ("metal", "steel", "black"):
            base = {"metal": lerp(H(0x3a3c42), T, 0.15), "steel": H(0x8a8e98), "black": H(0x1c1c20)}[name]
            c, _, _ = tex.m_metal(f, {"metal": base}, seed, rivets=False)
            c = np.where(((Y - f.y0) == 0)[..., None], shade(base, 1.4), c)  # bevel highlight
        elif name == "wood":
            grain = np.sin(Y * 1.3 + fbm(X / 3, Y / 2, seed) * 5) * 0.5 + 0.5
            c = lerp(H(0x5a3018), H(0x8a5028), grain)
        elif name == "rust":
            c = lerp(H(0x6a3014), H(0xa05a28), fbm(X / 2, Y / 2, seed + 1))
        elif name == "gold" or name == "brass":
            base = H(0xd4af37) if name == "gold" else H(0xb88a40)
            c = shade(base, 0.8 + 0.4 * fbm(X / 4, Y / 2, seed + 2))
            c = np.where((((X + Y) % 6) == 0)[..., None], H(0xfff0b0), c)
        elif name == "glow":
            c = lerp(H(0xffd080), H(0xff5020), np.hypot(X - f.x0 - 3.5, Y - f.y0 - 3.5) / 5)
        elif name == "leather":
            c, _, _ = tex.m_leather(f, {"leather": H(0x4a2e1e)}, seed)
        else:
            c = lerp(H(0x5a8aa8), H(0xc0e4f8), (X - f.x0 + Y - f.y0) / 14)
        img[f.y0:f.y0 + 8, f.x0:f.x0 + 8] = c
    return to_img(img)


# =============================================================================================
# Particles and projectiles (soft, layered glows)
# =============================================================================================
def radial(n, core, edge, power=1.6, hot=0.25):
    X, Y = grid(n)
    c0 = (n - 1) / 2
    r = np.hypot(X - c0, Y - c0) / (n / 2)
    col = lerp(np.asarray(core, float), np.asarray(edge, float), np.clip(r / 0.9, 0, 1))
    col = lerp(col, np.ones(3), np.clip(hot - r, 0, 1) / max(hot, 1e-3))
    a = np.clip(1 - r, 0, 1) ** power
    return col, a


def star_shape(n, points, inner=0.35):
    X, Y = grid(n)
    c0 = (n - 1) / 2
    r = np.hypot(X - c0, Y - c0) / (n / 2)
    ang = np.arctan2(Y - c0, X - c0)
    k = inner + (1 - inner) * np.abs(np.cos(ang * points / 2)) ** 6
    return np.clip(1 - r / k, 0, 1)


def particle_frames(kind, count=6, n=8):
    frames = []
    for i in range(count):
        t = i / (count - 1)
        if kind == "blood":
            X, Y = grid(n)
            c0 = (n - 1) / 2
            r = np.hypot((X - c0) / (1 - 0.3 * t), (Y - c0 - 0.5) / (1 + 0.3 * t)) / (n / 2)
            a = (r < 0.85 - 0.25 * t).astype(float)
            col = lerp(H(0xa00810), H(0x4a0004), np.clip((Y - 1) / n, 0, 1))
            col = np.where(((X == c0 - 1) & (Y == c0 - 1))[..., None], H(0xff7080), col)
            frames.append(to_img(col, a))
        elif kind in ("ember", "soul", "frost", "spark", "star"):
            # Crisp, stepped pixel-art glow: white-hot core, saturated body, faint halo.
            pal = {"ember": (H(0xfff4b0), H(0xffa020), H(0xff3a00)), "soul": (H(0xf0ffff), H(0x40c8ff), H(0x1a50ff)),
                   "frost": (H(0xffffff), H(0xb8ecff), H(0x60b0ff)), "spark": (H(0xffffff), H(0xfff0a0), H(0xffb030)),
                   "star": (H(0xffffff), H(0xfff0a0), H(0xffc030))}[kind]
            X, Y = grid(n)
            c0 = (n - 1) / 2
            r = np.hypot(X - c0, Y - c0)
            if kind in ("star", "spark", "frost"):
                arms = {"star": 4, "spark": 4, "frost": 6}[kind]
                ang = np.arctan2(Y - c0, X - c0)
                r = r / (0.45 + 0.55 * np.abs(np.cos(ang * arms / 2)) ** 8)
            size = (3.6 * (1 - 0.45 * t)) if kind in ("ember", "soul") else (4.6 * (1 - 0.3 * t))
            core, body, halo = r < size * 0.35, r < size * 0.7, r < size
            col = np.where(core[..., None], pal[0], np.where(body[..., None], pal[1], pal[2]))
            a = np.where(core, 1.0, np.where(body, 0.95, np.where(halo, 0.45, 0.0))) * (1 - 0.35 * t)
            frames.append(to_img(col, a))
        elif kind == "ash":
            X, Y = grid(n)
            a = ((np.abs(X - 3.5 - (i % 3 - 1)) < 1.6 - 0.2 * (i % 2)) & (np.abs(Y - 3.5) < 0.8 + 0.4 * (i % 2))).astype(float)
            frames.append(to_img(lerp(H(0x5a5550), H(0x8a847c), hashf(X, Y, i)), a))
        elif kind == "rune":
            img = np.zeros((n, n, 3))
            a = np.zeros((n, n))
            glyphs = ["..o..o..|.ooo.o..|..o..o..|..o.ooo.|..o..o..",
                      ".oooo...|.o..o...|.oooo.o.|....o.o.|....ooo.",
                      "o.....o.|.o...o..|..o.o...|...o....|..ooo...",
                      "..ooo...|.o...o..|.o.o.o..|.o...o..|..ooo..."]
            rows = glyphs[i % 4].split("|")
            for y, row in enumerate(rows):
                for x, ch in enumerate(row):
                    if ch == "o":
                        a[y + 1, x] = 1
            glow = np.array(Image.fromarray((a * 255).astype(np.uint8)).resize((n, n)))
            img[:] = H(0xd070ff)
            img = np.where((a > 0)[..., None], H(0xffd8ff), img)
            soft, sa = radial(n, H(0xc060ff), H(0x6020a0), 2.0, 0)
            alpha = np.maximum(a, sa * 0.35) * (1 - 0.5 * t)
            frames.append(to_img(np.where((a > 0)[..., None], img, soft), alpha))
    return frames


def slash_frames(count=6, n=32):
    out = []
    X, Y = grid(n)
    c0 = (n - 1) / 2
    r = np.hypot(X - c0, Y - c0) / (n / 2)
    ang = (np.degrees(np.arctan2(Y - c0, X - c0)) + 360) % 360
    for i in range(count):
        t = i / (count - 1)
        start, sweep = 200 + 60 * t, 160 - 60 * t
        inarc = ((ang - start) % 360) < sweep
        pos = (((ang - start) % 360) / sweep)
        width = 0.22 * (0.35 + 0.65 * pos)
        band = np.clip(1 - np.abs(r - 0.74) / width, 0, 1)
        a = np.clip(band * 1.6, 0, 1) * inarc * (1 - t * 0.75) * (0.4 + 0.6 * pos)
        col = lerp(H(0xff1a1a), np.ones(3), np.clip(band * 1.8 - 0.6, 0, 1))
        out.append(to_img(col, a))
    return out


def muzzle_flash(n=16):
    a = star_shape(n, 8, 0.18)
    col, ra = radial(n, H(0xffffff), H(0xff8a20), 1.2, 0.4)
    return to_img(col, np.maximum(a, ra))


def shockwave(n=64):
    X, Y = grid(n)
    c0 = (n - 1) / 2
    r = np.hypot(X - c0, Y - c0) / (n / 2)
    ring = np.clip(1 - np.abs(r - 0.82) / 0.12, 0, 1)
    inner = np.clip(1 - np.abs(r - 0.82) / 0.35, 0, 1) * 0.35
    col = lerp(H(0xfff4e0), np.ones(3), ring)
    return to_img(col, np.maximum(ring, inner * (r < 0.82)))


def projectile(kind, n=16):
    X, Y = grid(n)
    c0 = (n - 1) / 2
    if kind in ("bullet", "pellet"):
        h = 2.2 if kind == "bullet" else 1.8
        streak = np.clip(1 - np.abs(Y - c0) / h, 0, 1) * np.clip(1 - np.abs(X - c0) / 7.5, 0, 1)
        col = lerp(H(0xffa030), H(0xfffff0), np.clip(streak * 1.6, 0, 1))
        return to_img(col, np.clip(streak * 2.2, 0, 1))
    if kind == "shard":
        a = (np.abs(X - c0) / 3 + np.abs(Y - c0) / 7 < 1).astype(float)
        col = lerp(H(0x2ac0f0), H(0xe0ffff), np.clip(1 - (X - c0 + 3) / 6, 0, 1))
        _, glow = radial(n, H(0xffffff), H(0x2ac0f0), 2.0)
        return to_img(np.where((a > 0)[..., None], col, H(0x7df9ff)), np.maximum(a, glow * 0.5))
    if kind == "bone":
        a = ((np.abs(X - Y) < 1.6) & (np.abs(X + Y - 2 * c0) < 9)).astype(float)
        for ex, ey in ((3, 3), (12, 12)):
            a = np.maximum(a, (np.hypot(X - ex, Y - ey) < 2.2).astype(float))
        return to_img(shade(H(0xeae2cc), 0.85 + 0.25 * (1 - Y / n)[..., None]), a)
    if kind == "rock":
        d1, d2, cid = voronoi(X, Y, 3, 3)
        a = (np.hypot(X - c0, Y - c0) < 6.5 + fbm(X / 2, Y / 2, 2) * 1.5).astype(float)
        col = shade(H(0x7a7268), 0.7 + 0.5 * cid[..., None] - 0.3 * ((d2 - d1) < 0.12)[..., None])
        return to_img(col, a)
    if kind == "acid":
        col, a = radial(n, H(0xeaff9a), H(0x4a9a10), 0.9, 0.2)
        bub = (hashf(X, Y, 5) > 0.9) & (a > 0.3)
        return to_img(np.where(bub[..., None], H(0xffffff), col), np.clip(a * 1.5, 0, 1))
    spec = {"star": (H(0xffffff), H(0xffc840), 4), "soul": (H(0xe8ffff), H(0x2a8aff), 0),
            "ember": (H(0xfff4c0), H(0xff3a00), 0), "chrono": (H(0xfff8d0), H(0xd4af37), 0)}[kind]
    col, a = radial(n, spec[0], spec[1], 1.1, 0.3)
    if spec[2]:
        a = np.maximum(a, star_shape(n, spec[2], 0.15))
    if kind == "ember":
        flame = fbm(X / 2, Y / 2, 9)
        col = lerp(col, H(0xff6a00), np.clip(flame - 0.5, 0, 1))
    if kind == "chrono":
        r = np.hypot(X - c0, Y - c0)
        ring = np.abs(r - 5.5) < 0.8
        col = np.where(ring[..., None], H(0xffe080), col)
        a = np.maximum(a, ring.astype(float))
    if kind == "soul":
        face = ((np.abs(X - 5.5) < 0.6) | (np.abs(X - 9.5) < 0.6)) & (np.abs(Y - 6.5) < 1.1)
        col = np.where(face[..., None], H(0x0a2050), col)
    return to_img(col, np.clip(a * 1.4, 0, 1))


def effect_icon(kind):
    n = 18
    X, Y = grid(n)
    c0 = (n - 1) / 2
    r = np.hypot(X - c0, Y - c0)
    base = {"bleeding": H(0xb01020), "infested": H(0x6b7d2a), "dread": H(0x3a3a5a)}[kind]
    col = lerp(shade(base, 1.25), shade(base, 0.55), np.clip(r / 8.5, 0, 1))
    a = (r < 8.5).astype(float)
    col = np.where((np.abs(r - 8.0) < 0.7)[..., None], H(0x140a0c), col)
    if kind == "bleeding":
        drop = (np.abs(X - c0) < (Y - 3) * 0.45) & (np.hypot(X - c0, Y - 10) < 3.6) | ((np.abs(X - c0) < (Y - 3) * 0.45) & (Y < 10) & (Y > 3))
        col = np.where(drop[..., None], H(0xff3040), col)
        col[8, 7] = H(0xffc0c8)
    elif kind == "infested":
        bug = np.hypot((X - c0) / 3, (Y - c0 - 1) / 4) < 1
        col = np.where(bug[..., None], H(0xd7ff3d), col)
        legs = (np.isin(Y, (7, 9, 11))) & (np.abs(X - c0) < 5) & ~bug
        col = np.where(legs[..., None], H(0x2a3010), col)
    else:
        eye = np.hypot((X - c0) / 5, (Y - c0) / 2.6) < 1
        col = np.where(eye[..., None], H(0xe8e8ff), col)
        col = np.where((r < 1.8)[..., None], H(0x0a0a14), col)
    return to_img(col, a)
