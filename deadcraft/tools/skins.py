"""Creature, vehicle and armour skins painted onto the model UV layouts."""
from __future__ import annotations

import numpy as np
from PIL import Image

import models
import tex
from tex import Face, rgb, shade, lerp, fbm, hashf

H = rgb  # shorthand


# =============================================================================================
# Canvas
# =============================================================================================
class Canvas:
    """A texture painted at S× resolution. Materials paint per hi-res pixel; faces and decorators
    address texels (px fills an S×S block), so detail is fine but features stay crisp pixel art."""

    def __init__(self, w, h, S=2):
        self.w, self.h, self.S = w, h, S
        self.rgb = np.zeros((h * S, w * S, 3))
        self.a = np.zeros((h * S, w * S))
        self.glow = np.zeros((h * S, w * S, 4))
        self.faces: list[Face] = []

    def put(self, f: Face, c, alpha, glow):
        ys, xs = slice(f.y0, f.y0 + f.h), slice(f.x0, f.x0 + f.w)
        self.rgb[ys, xs] = c
        self.a[ys, xs] = alpha
        g = glow[..., 3:4]
        self.glow[ys, xs] = np.where(g > 0, glow, self.glow[ys, xs])

    def get(self, x, y):
        S = self.S
        return self.rgb[int(y) * S + S // 2, int(x) * S + S // 2].copy()

    def px(self, x, y, c, glow=None, alpha=1.0):
        S = self.S
        x, y = int(x), int(y)
        if 0 <= x < self.w and 0 <= y < self.h:
            ys, xs = slice(y * S, y * S + S), slice(x * S, x * S + S)
            self.rgb[ys, xs] = c
            self.a[ys, xs] = np.maximum(self.a[ys, xs], alpha)
            if glow is not None:
                self.glow[ys, xs, :3] = glow
                self.glow[ys, xs, 3] = 1.0

    def images(self):
        rgba = np.dstack([self.rgb, self.a])
        img = Image.fromarray((np.clip(rgba, 0, 1) * 255).astype(np.uint8), "RGBA")
        g = self.glow.copy()
        g[..., :3] *= g[..., 3:4] ** 0.5
        glow = Image.fromarray((np.clip(g, 0, 1) * 255).astype(np.uint8), "RGBA")
        return img, glow


def cube_faces(c, part="", role="", seed=0, S=1):
    u, v, w, h, d = c.u, c.v, c.w, c.h, c.d
    rects = {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
             "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}
    return {k: Face(int(x * S), int(y * S), int(ww * S), int(hh * S), k, part, role, seed)
            for k, (x, y, ww, hh) in rects.items() if ww > 0 and hh > 0}


def paint_cube(cv: Canvas, c, part, role, material, P, seed, skip=()):
    fn = tex.MATERIALS[material]
    hi = cube_faces(c, part, role, seed, cv.S)
    lo = cube_faces(c, part, role, seed, 1)
    for name, f in hi.items():
        if name in skip:
            continue
        col, alpha, glow = fn(f, P, seed)
        col = tex.light(f, col)
        cv.put(f, col, alpha, glow)
        cv.faces.append(lo[name])
    return lo


# =============================================================================================
# Faces (drawn on the front face of the head)
# =============================================================================================
def draw_face(cv: Canvas, f: Face, style, P):
    x0, y0, w, h = f.x0, f.y0, f.w, f.h
    eye = P.get("eye", np.ones(3))
    base = cv.get(x0 + w // 2, y0 + h // 2)
    dark = shade(base, 0.3)
    ey = y0 + max(1, int(h * 0.38))
    lx, rx = x0 + max(1, int(w * 0.27)), x0 + w - 1 - max(1, int(w * 0.27))
    if w <= 4:
        lx, rx = x0, x0 + w - 1

    def socket(cx, cy, rw=1, rh=1, col=None):
        for dx in range(-rw, rw + 1):
            for dy in range(-rh + 1, rh + 1):
                cv.px(cx + dx, cy + dy, col if col is not None else dark)

    if style in ("skull", "hollow", "stone", "burnt", "demon", "zombie"):
        big = style in ("skull", "hollow")
        for cx in (lx, rx):
            socket(cx, ey, 1 if w >= 6 else 0, 1, np.array([0.02, 0.01, 0.01]))
            if style == "hollow":
                cv.px(cx, ey, eye, eye)
            elif style == "demon":
                cv.px(cx, ey, eye, eye)
                cv.px(cx + (1 if cx == lx else -1), ey, eye, eye)
                for dx in range(-1, 2):
                    cv.px(cx + dx, ey - 1, shade(base, 0.45))  # heavy brow
            else:
                cv.px(cx, ey, eye, eye)
                if big:
                    cv.px(cx, ey + 1, shade(eye, 0.6), shade(eye, 0.6))
        # nose cavity
        if style in ("skull", "hollow", "zombie") and h >= 6:
            cv.px(x0 + w // 2, ey + 2, dark)
            if w % 2 == 0:
                cv.px(x0 + w // 2 - 1, ey + 2, dark)
        # mouth / teeth
        my = y0 + h - 2
        if h >= 5:
            for x in range(x0 + max(1, w // 5), x0 + w - max(1, w // 5)):
                cv.px(x, my, np.array([0.06, 0.02, 0.02]))
                if (x - x0) % 2 == 0 and style != "hollow":
                    cv.px(x, my - 1 if style != "demon" else my, np.array([0.88, 0.84, 0.72]))
            if style == "hollow":  # stitched-shut mouth
                for x in range(x0 + 2, x0 + w - 2, 2):
                    cv.px(x, my - 1, np.array([0.1, 0.05, 0.05]))
                    cv.px(x, my + 0, np.array([0.1, 0.05, 0.05]))
            if style == "demon":
                cv.px(x0 + 1 + w // 5, my + 1, np.array([0.95, 0.92, 0.85]))
                cv.px(x0 + w - 2 - w // 5, my + 1, np.array([0.95, 0.92, 0.85]))
        if style == "burnt":
            for x in range(x0 + 1, x0 + w - 1):
                if hashf(np.array(x), np.array(y0), 3) > 0.5:
                    cv.px(x, y0 + h - 2, P["glow"], P["glow"])
    elif style == "void":
        for cx in (lx, rx):
            cv.px(cx, ey, eye, eye)
            cv.px(cx, ey + 1, shade(eye, 0.5), shade(eye, 0.5))
    elif style == "mask":
        gold = P.get("trim", H(0xd4af37))
        for x in range(x0, x0 + w):
            for y in range(y0 + 1, y0 + h - 1):
                k = 1.25 - 0.5 * (y - y0) / h
                cv.px(x, y, shade(gold, k * (0.95 + 0.1 * ((x + y) % 2))))
        for cx in (lx, rx):
            for dx in (-1, 0, 1):
                cv.px(cx + dx, ey, eye, eye)
        cv.px(x0 + w // 2, ey + 2, shade(gold, 0.5))
    elif style in ("beast", "critter", "bird", "insect"):
        r = 0 if style in ("critter", "bird") or w < 6 else 1
        for cx in (lx, rx):
            if style == "insect":
                for dx in (0, 1 if cx == lx else -1):
                    for dy in (0, 1):
                        cv.px(cx + dx, ey + dy, shade(eye, 0.8 + 0.4 * ((dx + dy) % 2)), eye if P.get("glow_eyes") else None)
                continue
            cv.px(cx, ey, np.array([0.05, 0.04, 0.05]))
            if r:
                cv.px(cx + (1 if cx == lx else -1), ey, np.array([0.05, 0.04, 0.05]))
            if P.get("glow_eyes"):
                cv.px(cx, ey, eye, eye)
            else:  # dark, glossy animal eye with a catch-light
                cv.px(cx, ey, np.array([0.04, 0.03, 0.04]))
                cv.px(cx, ey + 1 if h > 4 else ey, shade(eye, 0.5))
                if w >= 6:
                    cv.px(cx, ey - 1, np.array([0.95, 0.95, 0.95]))


def draw_snout(cv: Canvas, f: Face, P):
    cx, cy = f.x0 + f.w // 2, f.y0 + f.h // 2
    nose = P.get("nose", np.array([0.15, 0.1, 0.1]))
    cv.px(cx - 1, cy, nose)
    cv.px(cx, cy, nose)
    if f.w >= 4:
        cv.px(cx - 2, cy - 1, shade(nose, 0.5))
        cv.px(cx + 1, cy - 1, shade(nose, 0.5))


# =============================================================================================
# Decorators
# =============================================================================================
def torso_part(cv):
    return "chest" if any(f.part == "chest" for f in cv.faces) else "body"


def faces_of(cv, part=None, role=None, kinds=None):
    return [f for f in cv.faces if (part is None or f.part == part) and (role is None or f.role in role)
            and (kinds is None or f.kind in kinds)]


def deco_blood(cv, P, seed):
    """Blood running down from the top of side faces."""
    rng = np.random.default_rng(seed)
    for f in faces_of(cv, role=("skin", "head", "cloth", "accent"), kinds=("front", "left", "right", "back")):
        for _ in range(max(1, f.w // 4)):
            if rng.random() > 0.55:
                continue
            x = f.x0 + rng.integers(0, f.w)
            length = rng.integers(1, max(2, f.h * 2 // 3))
            for i in range(length):
                c = H(0x7a0006) if i < length - 1 else H(0x4a0004)
                cv.px(x, f.y0 + i, shade(c, 1.1 - 0.3 * i / max(1, length)))


def deco_stitches(cv, P, seed):
    rng = np.random.default_rng(seed + 1)
    for f in faces_of(cv, role=("skin", "head"), kinds=("front", "left", "right", "top")):
        if f.w < 4 or f.h < 4 or rng.random() > 0.6:
            continue
        y = f.y0 + rng.integers(1, f.h - 1)
        for x in range(f.x0 + 1, f.x0 + f.w - 1):
            cv.px(x, y, H(0x3a1a14))
            if (x - f.x0) % 2:
                cv.px(x, y - 1, H(0xc8b8a0))
                cv.px(x, y + 1, H(0xc8b8a0))


def deco_bandages(cv, P, seed):
    for f in faces_of(cv, role=("skin", "head"), kinds=("front", "left", "right", "back")):
        lx = f.X - f.x0
        ly = f.Y - f.y0
        band = ((lx + ly + seed) % 5) < 2
        torn = fbm(f.X / 2, f.Y / 2, seed) > 0.62
        m = band & ~torn
        for (y, x) in zip(*np.nonzero(m)):
            k = 0.9 + 0.2 * ((x + y) % 2)
            cv.px(f.x0 + x, f.y0 + y, shade(H(0xd8cfb8), k))


def deco_hollow_chest(cv, P, seed):
    for f in faces_of(cv, part=torso_part(cv), kinds=("front",)):
        if f.w < 6:
            continue
        cx, cy = f.x0 + f.w / 2 - 0.5, f.y0 + f.h * 0.42
        for y in range(f.y0, f.y0 + f.h):
            for x in range(f.x0, f.x0 + f.w):
                if ((x - cx) / (f.w * 0.32)) ** 2 + ((y - cy) / (f.h * 0.3)) ** 2 < 1:
                    rib = (y - f.y0) % 2 == 0
                    cv.px(x, y, shade(P["bone"], 0.75) if rib and abs(x - cx) > 0.6 else np.array([0.02, 0.0, 0.01]))
        break


def deco_ribs(cv, P, seed):
    for f in faces_of(cv, part=torso_part(cv), kinds=("front",)):
        if f.w < 5:
            continue
        for y in range(f.y0 + 1, f.y0 + min(f.h - 1, 9)):
            for x in range(f.x0 + 1, f.x0 + f.w - 1):
                if (y - f.y0) % 2 == 1:
                    cv.px(x, y, shade(P["bone"], 0.95 + 0.1 * ((x + y) % 2)))
                elif x != f.x0 + f.w // 2:
                    cv.px(x, y, np.array([0.12, 0.02, 0.03]))
        break


def deco_clock(cv, P, seed):
    for f in faces_of(cv, part=torso_part(cv), kinds=("front",)):
        cx, cy = f.x0 + f.w / 2 - 0.5, f.y0 + f.h * 0.35
        r = min(f.w, f.h) * 0.32
        gold = P.get("trim", H(0xd4af37))
        for y in range(f.y0, f.y0 + f.h):
            for x in range(f.x0, f.x0 + f.w):
                d = np.hypot(x - cx, y - cy)
                if abs(d - r) < 0.6:
                    cv.px(x, y, gold, gold)
                elif d < r - 0.5:
                    cv.px(x, y, np.array([0.08, 0.06, 0.02]))
        for t in np.linspace(0, 1, 6):
            cv.px(int(round(cx)), int(round(cy - t * (r - 1))), H(0xfff3b0), H(0xfff3b0))
            cv.px(int(round(cx + t * (r - 1.5))), int(round(cy)), H(0xfff3b0), H(0xfff3b0))
        break


def deco_pustules(cv, P, seed):
    rng = np.random.default_rng(seed + 5)
    col = P.get("pus", H(0x9acd32))
    for f in faces_of(cv, role=("skin", "accent"), kinds=("front", "left", "right", "top", "back")):
        for _ in range(max(1, f.w * f.h // 18)):
            x, y = f.x0 + rng.integers(0, f.w), f.y0 + rng.integers(0, f.h)
            cv.px(x, y, col, col if P.get("glow_pus") else None)
            cv.px(x + 1, y, shade(col, 0.7))
            cv.px(x, y + 1, shade(col, 0.6))


def deco_glow_spots(cv, P, seed):
    for f in faces_of(cv, role=("skin", "shell", "accent"), kinds=("top", "left", "right", "back")):
        d1, _, cid = tex.voronoi(f.X, f.Y, seed + 9, 3.0)
        m = (d1 < 0.17) & (cid > 0.55)
        for (y, x) in zip(*np.nonzero(m)):
            cv.px(f.x0 + x, f.y0 + y, P["glow"], P["glow"])


def deco_glow_stripes(cv, P, seed):
    for f in faces_of(cv, role=("skin",), kinds=("left", "right", "top")):
        m = (np.abs(np.sin(f.X * 0.8 + fbm(f.X / 3, f.Y / 3, seed) * 5)) > 0.9)
        for (y, x) in zip(*np.nonzero(m)):
            cv.px(f.x0 + x, f.y0 + y, P["glow"], P["glow"])


def deco_runes(cv, P, seed):
    rng = np.random.default_rng(seed + 11)
    for f in faces_of(cv, role=("skin", "cloth", "accent", "head"), kinds=("front", "left", "right", "back")):
        if f.w < 5 or f.h < 5 or rng.random() > 0.7:
            continue
        x, y = f.x0 + rng.integers(1, f.w - 2), f.y0 + rng.integers(1, f.h - 3)
        for dx, dy in [(0, 0), (0, 1), (0, 2), (1, 1), (1, 0)][: rng.integers(3, 6)]:
            cv.px(x + dx, y + dy, P["glow"], P["glow"])


DECORATORS = {"blood": deco_blood, "stitches": deco_stitches, "bandages": deco_bandages, "hollow_chest": deco_hollow_chest,
              "ribs": deco_ribs, "clock": deco_clock, "pustules": deco_pustules, "glow_spots": deco_glow_spots,
              "glow_stripes": deco_glow_stripes, "runes": deco_runes}


# =============================================================================================
# Creature specifications
# =============================================================================================
ARCH_DEFAULT = {
    "gaunt": {"skin": "flesh", "head": "flesh", "bone": "bone", "cloth": "cloth"},
    "crawler": {"skin": "flesh", "head": "flesh", "bone": "bone", "accent": "flesh"},
    "wraith": {"head": "flesh", "cloth": "tattered", "skin": "flesh", "bone": "bone"},
    "brute": {"skin": "flesh", "head": "flesh", "accent": "stone", "horn": "horn"},
    "quadruped": {"skin": "fur", "head": "fur", "accent": "fur", "horn": "horn", "bone": "bone"},
    "critter": {"skin": "fur", "head": "fur", "accent": "fur", "shell": "shell"},
    "flyer": {"skin": "feather", "head": "feather", "accent": "feather", "wing": "wing", "bone": "horn"},
}
FACE_DEFAULT = {"gaunt": "skull", "crawler": "demon", "wraith": "void", "brute": "demon", "quadruped": "beast",
                "critter": "critter", "flyer": "bird"}

# role -> material overrides, palette, face style, decorators
SPEC = {
    # ---- hostile ----
    "skeleton": dict(mat={"skin": "bone", "head": "bone", "bone": "bone", "cloth": "tattered"}, face="skull",
                     pal={"base": H(0xe8e0c8), "accent": H(0xb8ac90), "vein": H(0x6a5a40), "bone": H(0xe8e0c8),
                          "cloth": H(0x3a0a14), "trim": H(0xd4af37), "glow": H(0x40ff60), "eye": H(0x40ff60),
                          "membrane": H(0x2a0a10), "claw": H(0xa89c80)},
                     deco=["blood"]),
    "the_hollow": dict(mat={"skin": "flesh", "head": "flesh", "cloth": "cloth"}, face="hollow",
                       pal={"base": H(0x6a6a72), "accent": H(0x3a3a44), "vein": H(0x22222a), "cloth": H(0x1a1a20)},
                       deco=["hollow_chest", "blood"]),
    "severed": dict(face="demon", pal={"base": H(0x9a5a4a), "accent": H(0x6a1a14), "vein": H(0x5a0a0a)},
                    mat={"accent": "flesh"}, deco=["stitches", "blood"]),
    "cracked": dict(mat={"skin": "fissure_stone", "head": "fissure_stone", "accent": "fissure_stone"}, face="stone",
                    pal={"stone": H(0x5c5a57), "glow": H(0xff8c1a)}),
    "depart": dict(mat={"head": "void", "skin": "flesh"}, face="void",
                   pal={"base": H(0x1c2333), "cloth": H(0x161c2a), "trim": H(0x3a4a6a), "accent": H(0x6c7a99)}),
    "scorched": dict(mat={"skin": "charred", "head": "charred", "cloth": "charred"}, face="burnt",
                     pal={"glow": H(0xff6a00), "base": H(0x2b1a12)}),
    "parasite": dict(mat={"skin": "chitin", "head": "chitin", "accent": "flesh"}, face="insect",
                     pal={"base": H(0x6b7d2a), "accent": H(0x5a2a6a), "pus": H(0xd7ff3d), "glow_pus": True},
                     deco=["pustules"]),
    "illusion": dict(mat={"head": "ethereal", "skin": "ethereal", "cloth": "ethereal", "bone": "ethereal"}, face="void",
                     pal={"base": H(0x9a7cff), "cloth": H(0x7a5cff), "iridescent": True}),
    "the_forgotten": dict(face="skull", pal={"base": H(0xb8b0a0), "accent": H(0x8a8478), "vein": H(0x6a7a8a),
                                             "cloth": H(0x3d3d3d)}, deco=["bandages"]),
    "wretched": dict(mat={"accent": "flesh"}, face="demon",
                     pal={"base": H(0x6a4058), "accent": H(0x8a5a2a), "vein": H(0x3a1030), "pus": H(0x9acd32),
                          "glow_pus": True}, deco=["pustules", "blood"]),
    "ghost": dict(mat={"head": "ethereal", "skin": "ethereal", "cloth": "ethereal", "bone": "ethereal"}, face="void",
                  pal={"base": H(0xd8ecf8), "cloth": H(0xb8d8f0)}),
    "blood_demon": dict(mat={"accent": "flesh"}, face="demon",
                        pal={"base": H(0x7a1010), "accent": H(0x3a0505), "vein": H(0x200202), "horn": H(0x2a1a14)},
                        deco=["blood"]),
    "star_fall": dict(mat={"head": "void", "cloth": "starsky", "skin": "starsky"}, face="void",
                      pal={"base": H(0x0d1b3d), "accent": H(0xffe680), "cloth": H(0x0d1b3d)}),
    "collapse": dict(mat={"skin": "rubble", "head": "rubble", "accent": "metal"}, face="stone",
                     pal={"stone": H(0x6a5a48), "metal": H(0x5a3a2a), "rust": H(0x8a4a20)}),
    # ---- bosses ----
    "fracture": dict(mat={"skin": "crystal", "head": "crystal", "accent": "crystal"}, face="stone",
                     pal={"stone": H(0x2b2f3a), "glow": H(0x7df9ff)}),
    "chronos": dict(mat={"head": "flesh", "cloth": "cloth", "skin": "flesh", "bone": "bone"}, face="mask",
                    pal={"base": H(0x3a3424), "cloth": H(0x2a2410), "trim": H(0xd4af37), "accent": H(0xd4af37)},
                    deco=["clock", "runes"]),
    "dim_light_demon": dict(mat={"skin": "void_runes", "head": "void_runes", "accent": "void_runes"}, face="demon",
                            pal={"base": H(0x24101a), "horn": H(0x1a0a20)}),
    "petrified": dict(mat={"skin": "mossy_stone", "head": "mossy_stone", "accent": "mossy_stone"}, face="stone",
                      pal={"stone": H(0x8a867c), "moss": H(0x3a5a34), "crack_glow": H(0x9bff7a)}),
    # ---- peaceful ----
    "ashgrazer": dict(pal={"base": H(0x6d655c), "accent": H(0x3a332c), "pattern": "back_stripe", "horn": H(0x8a7a60),
                           "belly": H(0xa89c8c)}),
    "gloomhog": dict(pal={"base": H(0x4d3f4a), "accent": H(0xc08a9a), "belly": H(0x8a7080), "nose": H(0xc08a9a)}),
    "cinderhen": dict(pal={"base": H(0xa8441a), "accent": H(0xffae42), "wing": H(0x8a3a14), "belly": H(0xffc070)},
                      mat={"head:accent": "horn"}),
    "marrow_ox": dict(pal={"base": H(0xd6ccb4), "accent": H(0x4a3a2a), "pattern": "patches", "horn": H(0x3a2a1a)}),
    "duskhare": dict(pal={"base": H(0x6a5f80), "accent": H(0xc8b8e0), "belly": H(0xd8c8f0)}),
    "lantern_moth": dict(mat={"skin": "fur", "head": "fur", "head:accent": "horn"},
                         pal={"base": H(0x8a7a5a), "accent": H(0xffe066), "wing": H(0x7a6a4f), "wingstyle": "membrane",
                              "eyespot": True, "glow": H(0xffe066), "glow_eyes": True}),
    "hush_owl": dict(pal={"base": H(0x5a5044), "accent": H(0xe8dcc0), "wing": H(0x4a4238), "belly": H(0xd9cbb0)},
                     mat={"head:accent": "horn"}),
    "gravecat": dict(pal={"base": H(0x1e1e22), "accent": H(0x3a3a40), "belly": H(0x3a3a40), "glow_eyes": True}),
    "wisplet": dict(mat={"skin": "ethereal", "head": "ethereal", "accent": "ethereal"},
                    pal={"base": H(0x9fdcff), "glow_eyes": True}),
    "shellmourn": dict(mat={"skin": "flesh", "head": "flesh", "shell": "shell"},
                       pal={"base": H(0x7a6a5a), "accent": H(0x5a4a3a), "shell": H(0xa89f91), "vein": H(0x5a4a3a)}),
    "candlewick_toad": dict(mat={"skin": "flesh", "head": "flesh", "accent": "flesh"},
                            pal={"base": H(0x4f5a2a), "accent": H(0x6a7a3a), "vein": H(0x3a4220), "glow": H(0xffd27f),
                                 "glow_eyes": True}, deco=["glow_spots"]),
    "mossback_tortoise": dict(mat={"skin": "flesh", "head": "flesh", "shell": "plates"},
                              pal={"base": H(0x5a6a4a), "accent": H(0x4a5a3a), "shell": H(0x5a4a32), "moss": H(0x5a8a3e),
                                   "vein": H(0x3a4a2a)}),
    "ember_fox": dict(pal={"base": H(0xc05010), "accent": H(0x2b1a12), "belly": H(0xf0e0d0), "nose": H(0x1a1010)}),
    "veilfawn": dict(pal={"base": H(0xb8a890), "accent": H(0xf4f0e8), "pattern": "spots", "belly": H(0xf4f0e8)}),
    "grimfinch": dict(pal={"base": H(0x262628), "accent": H(0x8b1010), "wing": H(0x1a1a1c), "belly": H(0xa01818)},
                      mat={"head:accent": "horn"}),
    "sorrowdove": dict(pal={"base": H(0xb8b8c4), "accent": H(0x6b6b7a), "wing": H(0x9a9aa8), "belly": H(0xd8d8e0)},
                       mat={"head:accent": "horn"}),
    "stormstag": dict(pal={"base": H(0x2f3a4a), "accent": H(0x1a2230), "glow": H(0x7df9ff), "horn": H(0x9ab0c0),
                           "glow_eyes": True, "belly": H(0x4a5a6a)}, deco=["glow_stripes"]),
    "cragstrider": dict(pal={"base": H(0x7a6a5a), "accent": H(0x4a3f34), "pattern": "patches", "horn": H(0x4a4038)}),
    "lumen_beetle": dict(mat={"skin": "chitin", "head": "chitin", "shell": "chitin", "accent": "chitin"}, face="insect",
                         pal={"base": H(0x1f3a2f), "glow": H(0x39ff88), "glow_eyes": True}, deco=["glow_spots"]),
    "hollowfleece": dict(mat={"skin": "wool", "head": "flesh", "accent": "flesh"},
                         pal={"base": H(0xe6e0d6), "accent": H(0x1e1e22), "vein": H(0x111111), "glow_eyes": True}),
}


def palette_for(mob, spec):
    base = rgb(mob["base"])
    accent = rgb(mob["accent"])
    P = {"base": base, "accent": accent, "eye": rgb(mob["eye"]) if mob["eye"] else np.ones(3),
         "bone": H(0xd8ccb0), "cloth": shade(base, 0.7), "leather": H(0x5c3a22), "stone": base, "glow": accent,
         "horn": H(0x3a302a), "shell": accent, "wing": shade(base, 0.85), "metal": H(0x5a5c62)}
    P.update(spec.get("pal", {}))
    return P


# Materials and colours for the part roles used by the rigs (see rigs.py).
def role_material(role, part, arch_mats, part_mats, P):
    key = f"{part}:{role}"
    if key in part_mats:
        return part_mats[key], P
    if role in arch_mats:
        mat = arch_mats[role]
        if role == "accent" and mat in ("fur", "flesh", "feather", "chitin"):
            q = {**P, "base": P["accent"]}
            q.pop("pattern", None)
            return mat, q
        return mat, P
    skin = arch_mats.get("skin", "flesh")
    table = {
        "claw": ("horn", {"horn": P.get("claw", H(0x2a2420))}),
        "tooth": ("bone", {"bone": H(0xe8e0c8)}),
        "spike": ("horn", {"horn": P.get("spike", P.get("horn", H(0x2a2420)))}),
        "rock": ("stone", {"stone": P.get("rock", P.get("stone", H(0x5c5a57)))}),
        "slab": ("stone", {"stone": P.get("slab", H(0x7a7672))}),
        "rebar": ("metal", {"metal": H(0x6a3a22), "rust": H(0x9a4a1a)}),
        "crystal": ("crystal", {"stone": P.get("stone", H(0x2b2f3a)), "glow": P.get("glow", H(0x7df9ff))}),
        "flame": ("flame", {"glow": P.get("glow", H(0xff6a00))}),
        "glow": ("emissive", {"glow": P.get("glow", P["accent"])}),
        "eye": ("emissive", {"glow": P.get("eye", H(0xffffff))}),
        "pustule": ("emissive" if P.get("glow_pus") else "flesh", {"glow": P.get("pus", H(0x9acd32)), "base": P.get("pus", H(0x9acd32))}),
        "mask": ("bone", {"bone": H(0xe8e4f0)}),
        "mirror": ("glass", {}),
        "star": ("emissive", {"glow": P.get("accent", H(0xffe680))}),
        "gold": ("metal", {"metal": H(0xc8a030)}),
        "gem": ("emissive", {"glow": P.get("eye", H(0x40ff60))}),
        "membrane": ("wing", {"wing": P.get("membrane", shade(P["base"], 0.7)), "wingstyle": "membrane"}),
        "bandage": ("cloth", {"cloth": H(0xc8bea4), "trim": H(0x9a8a70)}),
        "void": ("void", {}),
        "charred": ("charred", {}),
        "metal": ("metal", {}),
        "antler": ("horn", {"horn": P.get("horn", H(0x9ab0c0))}),
        "horn": ("horn", {}),
        "snout": (skin, {"base": P.get("snout", shade(P["base"], 0.85))}),
        "belly": (skin, {"base": P.get("belly", shade(P["base"], 1.3))}),
        "beak": ("horn", {"horn": P.get("beak", H(0xd8a030))}),
        "wool": ("wool", {}),
        "wax": ("wax", {}),
        "moss": ("fur", {"base": H(0x4a7a34)}),
        "mushroom": ("flesh", {"base": H(0xa82020), "accent": H(0xf0e0d0)}),
        "veil": ("ethereal", {"base": H(0xe8e4f0)}),
        "shell": ("shell", {}),
        "wing": ("wing", {}),
        "bone": ("bone", {}),
        "cloth": ("cloth", {}),
        "head": (skin, {}),
    }
    mat, extra = table.get(role, (skin, {}))
    return mat, {**P, **extra}


def paint_creature(mob):
    import rigs
    arch = rigs.ARCHETYPE_OF[mob["id"]]
    parts, w, h = models.build(mob["id"])
    spec = SPEC.get(mob["id"], {})
    P = palette_for(mob, spec)
    mats = dict(ARCH_DEFAULT[arch])
    mats.update({k: v for k, v in spec.get("mat", {}).items() if ":" not in k})
    part_mats = {k: v for k, v in spec.get("mat", {}).items() if ":" in k}
    seed = abs(hash(mob["id"])) % 100000
    cv = Canvas(w, h, S=2)
    head_front = snout_front = None
    for part in parts:
        for c in part.cubes:
            mat, cP = role_material(c.role, part.name, mats, part_mats, P)
            faces = paint_cube(cv, c, part.name, c.role, mat, cP, seed)
            if c.role == "head" and head_front is None:
                head_front = faces["front"]
            if c.role == "snout" and snout_front is None:
                snout_front = faces["front"]
    if head_front is not None:
        draw_face(cv, head_front, spec.get("face", FACE_DEFAULT[arch]), P)
    if snout_front is not None:
        draw_snout(cv, snout_front, P)
    for d in spec.get("deco", []):
        DECORATORS[d](cv, P, seed)
    return cv.images(), (w, h)


# =============================================================================================
# Vanilla-layout skins: bandits, the Rotwalker, the Skeleton boss
# =============================================================================================
def _layout_faces(cubes, role):
    for c in cubes:
        if c.role == role:
            return c, cube_faces(c, role, role)
    return None, {}


EXTRA_ROLES = {
    "hat": ("leather", lambda k: {"leather": H(0x5a3a22) if k == "bandit" else H(0x161414)}),
    "band": ("leather", lambda k: {"leather": H(0x2a1a10)}),
    "bandana": ("cloth", lambda k: {"cloth": H(0x8b1a1a)}),
    "leather": ("leather", lambda k: {"leather": H(0x3a2416)}),
    "brass": ("metal", lambda k: {"metal": H(0xc89a40)}),
    "coat": ("leather" if True else "cloth", lambda k: {"leather": H(0x6b4a2b) if k == "bandit" else H(0x7a1414)}),
    "gold": ("metal", lambda k: {"metal": H(0xd4af37)}),
    "feather": ("feather", lambda k: {"base": H(0xe8e0d0), "accent": H(0x8b1a1a)}),
    "metal": ("metal", lambda k: {"metal": H(0x6a6c74)}),
    "flesh": ("rot", lambda k: {"base": H(0x6a8050), "accent": H(0x4a1010), "vein": H(0x3a2020)}),
    "tooth": ("bone", lambda k: {"bone": H(0xd8cca8)}),
    "bone": ("bone", lambda k: {"bone": H(0xe8e0c8)}),
    "rag": ("tattered", lambda k: {"cloth": H(0x3a3a4a)}),
    "cape": ("tattered", lambda k: {"cloth": H(0x3a0a14), "trim": H(0xd4af37)}),
    "gem": ("emissive", lambda k: {"glow": H(0x40ff60)}),
}


def paint_extras(cv, parts, kind, seed):
    for part in parts:
        for c in part.cubes:
            if c.fixed:
                continue
            mat, pal = EXTRA_ROLES.get(c.role, ("leather", lambda k: {"leather": H(0x3a2416)}))
            P = {"base": H(0x777777), "accent": H(0x444444), "bone": H(0xe8e0c8), "leather": H(0x3a2416),
                 "cloth": H(0x3a3a3a), "metal": H(0x6a6c74), "glow": H(0xffffff)}
            P.update(pal(kind))
            paint_cube(cv, c, part.name, c.role, mat, P, seed)


def paint_humanoid(mob):
    """Player-layout skin (2x resolution) plus the rig's extra hats, coats and gear."""
    parts, tw, th = models.build(mob["id"])
    cv = Canvas(tw, th, S=2)
    cubes = [c for p in parts for c in p.cubes if c.fixed]
    seed = abs(hash(mob["id"])) % 100000
    kind = mob["id"]
    skin_col = {"bandit": H(0xb88a68), "bandit_captain": H(0xa07a5a), "rotwalker": H(0x6a8050)}[kind]
    coat = {"bandit": H(0x6b4a2b), "bandit_captain": H(0x7a1414), "rotwalker": H(0x4a5a6a)}[kind]
    pants = {"bandit": H(0x3a3a3a), "bandit_captain": H(0x2a2420), "rotwalker": H(0x3a3a4a)}[kind]
    skinP = {"base": skin_col, "accent": shade(skin_col, 0.8), "vein": shade(skin_col, 0.6)}
    skin_mat = "rot" if kind == "rotwalker" else "flesh"
    for c in cubes:
        if c.role in ("hat", "jacket"):
            continue
        part = {"head": "head", "cloth": "body", "sleeve": "arm", "pants": "leg"}[c.role]
        if part == "head":
            faces = paint_cube(cv, c, "head", "head", skin_mat, skinP, seed)
        elif part == "body":
            faces = paint_cube(cv, c, "body", "cloth", "leather" if kind != "rotwalker" else "tattered",
                               {"leather": coat, "cloth": coat}, seed)
        elif part == "arm":
            faces = paint_cube(cv, c, "arm", "cloth", "leather" if kind != "rotwalker" else "cloth",
                               {"leather": coat, "cloth": coat}, seed)
            for f in faces.values():  # hands
                if f.side:
                    for y in range(f.y0 + f.h - 3, f.y0 + f.h):
                        for x in range(f.x0, f.x0 + f.w):
                            cv.px(x, y, shade(skin_col, 0.95 + 0.1 * ((x + y) % 2)))
            if "bottom" in faces:
                fb = faces["bottom"]
                for y in range(fb.y0, fb.y0 + fb.h):
                    for x in range(fb.x0, fb.x0 + fb.w):
                        cv.px(x, y, shade(skin_col, 0.8))
        else:
            faces = paint_cube(cv, c, "leg", "cloth", "cloth", {"cloth": pants}, seed)
            for f in faces.values():  # boots
                if f.side:
                    for y in range(f.y0 + f.h - 4, f.y0 + f.h):
                        for x in range(f.x0, f.x0 + f.w):
                            cv.px(x, y, shade(H(0x2a1a10), 0.9 + 0.2 * ((x * 3 + y) % 3) / 2))
        if part == "body":
            for f in faces.values():
                if f.side:  # belt
                    for x in range(f.x0, f.x0 + f.w):
                        cv.px(x, f.y0 + 8, H(0x1a1210))
                        cv.px(x, f.y0 + 9, H(0x2a1a10))
            fr = faces["front"]
            cv.px(fr.x0 + 3, fr.y0 + 8, H(0xd4af37))
            cv.px(fr.x0 + 4, fr.y0 + 8, H(0xd4af37))
            if kind == "bandit":  # bandolier with brass rounds
                for i in range(8):
                    x, y = fr.x0 + i, fr.y0 + i
                    cv.px(x, y, H(0x2a1a10))
                    if i % 2 == 0:
                        cv.px(x, y + 1, H(0xc89a40))
            if kind == "bandit_captain":  # gold buttons and lapels
                for y in range(fr.y0 + 1, fr.y0 + 8, 2):
                    cv.px(fr.x0 + 2, y, H(0xffd060))
                    cv.px(fr.x0 + 5, y, H(0xffd060))
                for y in range(fr.y0, fr.y0 + 12):
                    cv.px(fr.x0 + 3, y, shade(coat, 0.6))
                    cv.px(fr.x0 + 4, y, shade(coat, 0.6))
            if kind == "rotwalker":
                for y in range(fr.y0 + 3, fr.y0 + 8):  # torn shirt, ribs showing
                    for x in range(fr.x0 + 2, fr.x0 + 6):
                        cv.px(x, y, H(0xc8b898) if (y % 2) else H(0x4a0a08))
    # Face
    _, hf = _layout_faces(cubes, "head")
    f = hf["front"]
    if kind == "rotwalker":
        draw_face(cv, f, "zombie", {"eye": rgb(mob["eye"])})
    else:
        for x in (f.x0 + 1, f.x0 + 2, f.x0 + 5, f.x0 + 6):
            cv.px(x, f.y0 + 4, H(0xeeeeee) if x in (f.x0 + 1, f.x0 + 6) else H(0x2a1a10))
        for x in range(f.x0 + 1, f.x0 + 7):
            cv.px(x, f.y0 + 3, shade(skin_col, 0.55))  # brow shadow under the hat
        if kind == "bandit":  # bandana
            for y in range(f.y0 + 5, f.y0 + 8):
                for x in range(f.x0, f.x0 + 8):
                    cv.px(x, y, shade(H(0x8b1a1a), 0.9 + 0.2 * ((x + y) % 2)))
            for side in ("left", "right", "back"):
                g = hf[side]
                for y in range(g.y0 + 5, g.y0 + 7):
                    for x in range(g.x0, g.x0 + g.w):
                        cv.px(x, y, H(0x7a1414))
        else:  # captain: beard and eyepatch
            for y in range(f.y0 + 5, f.y0 + 8):
                for x in range(f.x0 + 1, f.x0 + 7):
                    cv.px(x, y, shade(H(0x2a1a10), 0.9 + 0.2 * ((x + y) % 2)))
            cv.px(f.x0 + 6, f.y0 + 4, H(0x0a0a0a))
            cv.px(f.x0 + 5, f.y0 + 4, H(0x0a0a0a))
            for x in range(f.x0, f.x0 + 8):
                cv.px(x, f.y0 + 3 if x < f.x0 + 5 else f.y0 + 2, H(0x111111))
    for side in ("top",):
        g = hf[side]
        hair = H(0x2a1a10) if kind != "rotwalker" else H(0x3a3a2a)
        for y in range(g.y0, g.y0 + g.h):
            for x in range(g.x0, g.x0 + g.w):
                cv.px(x, y, shade(hair, 0.85 + 0.3 * hashf(np.array(x), np.array(y), seed)))
    # Hat layer: crown + brim band (transparent elsewhere)
    if kind != "rotwalker":
        hat_col = H(0x5a3a22) if kind == "bandit" else H(0x141414)
        _, hat = _layout_faces(cubes, "hat")
        for name, g in hat.items():
            if name == "top":
                for y in range(g.y0, g.y0 + g.h):
                    for x in range(g.x0, g.x0 + g.w):
                        cv.px(x, y, shade(hat_col, 0.9 + 0.2 * fbm(np.array(x / 2.0), np.array(y / 2.0), seed)))
            elif name != "bottom":
                rows = 3 if kind == "bandit" else 4
                for y in range(g.y0, g.y0 + rows):
                    for x in range(g.x0, g.x0 + g.w):
                        band = y == g.y0 + rows - 1
                        col = H(0x2a1a10) if band and kind == "bandit" else (H(0xd4af37) if band else hat_col)
                        cv.px(x, y, shade(col, 0.9 + 0.1 * ((x + y) % 2)))
    paint_extras(cv, parts, kind, seed)
    return cv.images(), (tw, th)


def paint_skeleton_boss(mob):
    parts, tw, th = models.build("skeleton")
    cv = Canvas(tw, th, S=2)
    cubes = [c for p in parts for c in p.cubes if c.fixed]
    seed = 4242
    P = {"bone": H(0xe8e0c8), "base": H(0xe8e0c8)}
    for c in cubes:
        if c.role == "hat":
            continue
        role = "head" if c.role == "head" else "bone"
        faces = paint_cube(cv, c, "body" if (c.u, c.v) == (16, 16) else role, role, "bone", P, seed)
        if (c.u, c.v) == (16, 16):  # ribcage with a tattered cape behind
            fr = faces["front"]
            for y in range(fr.y0, fr.y0 + fr.h):
                for x in range(fr.x0, fr.x0 + fr.w):
                    ly = y - fr.y0
                    if ly < 8 and ly % 2 == 1 and x != fr.x0 + 3 and x != fr.x0 + 4:
                        cv.px(x, y, np.array([0.06, 0.03, 0.04]))
            bk = faces["back"]
            for y in range(bk.y0, bk.y0 + bk.h):
                for x in range(bk.x0, bk.x0 + bk.w):
                    if y - bk.y0 < bk.h - 1 - int(3 * fbm(np.array(x * 0.9), np.array(2.0), seed)):
                        cv.px(x, y, shade(H(0x3a0a14), 0.8 + 0.3 * fbm(np.array(x / 1.5), np.array(y / 5.0), seed)))
        if c.role == "head":
            draw_face(cv, faces["front"], "skull", {"eye": rgb(mob["eye"])})
    # Crown on the hat layer
    _, hat = _layout_faces(cubes, "hat")
    gold, gem = H(0xd4af37), rgb(mob["eye"])
    for name, g in hat.items():
        if name in ("top", "bottom"):
            continue
        for x in range(g.x0, g.x0 + g.w):
            spike = (x - g.x0) % 3 == 1
            cv.px(x, g.y0 + 1, shade(gold, 1.0))
            cv.px(x, g.y0 + 2, shade(gold, 0.8))
            if spike:
                cv.px(x, g.y0, shade(gold, 1.25))
        cv.px(g.x0 + g.w // 2, g.y0 + 1, gem, gem)
    paint_extras(cv, parts, "skeleton", seed)
    return cv.images(), (tw, th)


# =============================================================================================
# Vehicles and armour
# =============================================================================================
def paint_vehicle(name, model):
    parts, w, h = models.build(name)
    cv = Canvas(w, h, S=2)
    if model == "buggy":
        P = {"metal": H(0x4a4c52), "paint": H(0x8b2a1a), "rust": H(0x8a4a20), "leather": H(0x4a2a1a), "glow": H(0xffd27f), "bone": H(0xd8ccb0)}
    else:
        P = {"metal": H(0x2a3240), "paint": H(0x3a4a5e), "leather": H(0x2a2a30), "glow": H(0x5ad8ff), "bone": H(0xd8ccb0)}
    mats = {"metal": "metal", "paint": "paint", "tire": "tire", "leather": "leather", "glass": "glass", "glow": "emissive",
            "bone": "bone"}
    for part in parts:
        for c in part.cubes:
            paint_cube(cv, c, part.name, c.role, mats.get(c.role, "metal"), P, 77)
    if model == "skiff":  # glowing panel lines along the hull
        for f in faces_of(cv, part="hull", role=("metal",), kinds=("left", "right")):
            y = f.y0 + f.h // 2
            for x in range(f.x0, f.x0 + f.w):
                cv.px(x, y, P["glow"], P["glow"])
    return cv.images(), (w, h)


ARMOR = {
    "necrite": dict(base=H(0xd6ccb4), trim=H(0x4a3a2a), gem=H(0x7fffd4), mat="bone"),
    "bloodstone": dict(base=H(0x8b0f14), trim=H(0x2a0505), gem=H(0xff4040), mat="crystal"),
    "voidsteel": dict(base=H(0x2e2e3e), trim=H(0x7a5cff), gem=H(0xb388ff), mat="metal"),
    "stardust": dict(base=H(0x26386a), trim=H(0xffe680), gem=H(0xffffff), mat="starsky"),
}


def paint_armor(name, layer):
    A = ARMOR[name]
    cv = Canvas(64, 32, S=2)
    P = {"metal": A["base"], "bone": A["base"], "stone": A["base"], "base": A["base"], "accent": A["trim"],
         "glow": A["gem"]}
    for c in models.armor_layout(layer):
        faces = paint_cube(cv, c, c.role, c.role, A["mat"], P, 9 + layer)
        for f in faces.values():  # bevelled plate edges and trim
            if f.w < 3 or f.h < 3:
                continue
            for x in range(f.x0, f.x0 + f.w):
                cv.px(x, f.y0, shade(A["trim"], 1.2))
                cv.px(x, f.y0 + f.h - 1, shade(A["trim"], 0.7))
            for y in range(f.y0, f.y0 + f.h):
                cv.px(f.x0, y, shade(A["trim"], 1.0))
                cv.px(f.x0 + f.w - 1, y, shade(A["trim"], 0.75))
        if c.role == "head":
            fr = faces["front"]
            for x in range(fr.x0 + 1, fr.x0 + fr.w - 1):  # visor slit
                cv.px(x, fr.y0 + 3, np.array([0.03, 0.02, 0.03]))
                cv.px(x, fr.y0 + 4, np.array([0.06, 0.04, 0.06]))
            cv.px(fr.x0 + fr.w // 2, fr.y0 + 1, A["gem"])
            cv.px(fr.x0 + fr.w // 2 - 1, fr.y0 + 1, A["gem"])
        if c.role == "body":
            fr = faces["front"]
            cx, cy = fr.x0 + fr.w // 2, fr.y0 + 4
            for dx, dy in ((0, 0), (-1, 0), (0, -1), (-1, -1), (0, 1), (-1, 1), (1, 0), (-2, 0)):
                cv.px(cx + dx, cy + dy, A["gem"] if (dx, dy) in ((0, 0), (-1, 0)) else shade(A["trim"], 1.3))
    img, _ = cv.images()
    return img
