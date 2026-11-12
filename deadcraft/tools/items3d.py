"""3D item models (Blockbench "Java Block/Item" format) for melee weapons, staves and the storm rod.

Each weapon is designed upright (blade along +Y, centred on x = z = 8) and every element is then
rotated -45° about Z, so the vanilla 'handheld' display transforms hold it exactly like a sword.
"""
from __future__ import annotations

import numpy as np
from PIL import Image

import tex
from tex import rgb as H, shade, lerp, fbm, Face

# 8x8 swatches on a 32x32 atlas; UVs address it in 0..16 units (4x4 per swatch)
SW = {"blade": (0, 0), "edge": (4, 0), "dark": (8, 0), "guard": (12, 0),
      "grip": (0, 4), "gem": (4, 4), "bone": (8, 4), "accent": (12, 4),
      "wood": (0, 8), "metal": (4, 8), "glow2": (8, 8), "cloth": (12, 8)}


def atlas(colors, seed):
    img = np.zeros((32, 32, 3))
    for name, (sx, sy) in SW.items():
        c0 = H(colors.get(name, 0x808080))
        f = Face(sx * 2, sy * 2, 8, 8, "front", seed=seed)
        X, Y = f.X, f.Y
        if name in ("blade", "edge", "metal", "guard"):
            c = shade(c0, 0.85 + 0.3 * fbm(X / 6, Y * 1.2, seed, 2))
            c = np.where((((X + Y) % 7) == 0)[..., None], shade(c0, 1.35), c)
        elif name == "grip":
            c = np.where(((Y + X // 2) % 3 == 0)[..., None], shade(c0, 0.6), shade(c0, 1.0 + 0.1 * fbm(X, Y, seed)[..., None]))
        elif name == "wood":
            c = lerp(shade(c0, 0.75), shade(c0, 1.15), np.sin(Y * 1.4 + fbm(X / 2, Y / 3, seed) * 4) * 0.5 + 0.5)
        elif name in ("gem", "glow2"):
            r = np.hypot(X - f.x0 - 3.5, Y - f.y0 - 3.5) / 5
            c = lerp(np.ones(3), c0, np.clip(r * 1.3, 0, 1))
        elif name == "bone":
            c = shade(c0, 0.88 + 0.2 * fbm(X / 1.5, Y / 4, seed))
        else:
            c = shade(c0, 0.85 + 0.3 * fbm(X / 3, Y / 3, seed + 3))
        img[f.y0:f.y0 + 8, f.x0:f.x0 + 8] = c
    return Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8), "RGB").convert("RGBA")


def el(frm, to, mat):
    sx, sy = SW[mat]
    uv = [sx, sy, sx + 4, sy + 4]
    return {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to],
            "rotation": {"angle": -45, "axis": "z", "origin": [8, 8, 8]},
            "faces": {f: {"uv": uv, "texture": "#atlas"} for f in ("north", "south", "east", "west", "up", "down")}}


def sword(blade_w=3.0, blade_len=12.0, guard_w=8.0, grip_len=5.0, extra=()):
    y0 = 8 - (blade_len + grip_len + 3.5) / 2 + 1
    e = []
    e.append(el([7, y0, 7], [9, y0 + 2, 9], "guard"))                          # pommel
    e.append(el([7.5, y0 + 2, 7.5], [8.5, y0 + 2 + grip_len, 8.5], "grip"))     # grip
    g = y0 + 2 + grip_len
    e.append(el([8 - guard_w / 2, g, 7], [8 + guard_w / 2, g + 1.5, 9], "guard"))
    e.append(el([8 - guard_w / 2 - 0.5, g - 0.5, 7.5], [8 - guard_w / 2 + 0.5, g + 2, 8.5], "guard"))
    e.append(el([8 + guard_w / 2 - 0.5, g - 0.5, 7.5], [8 + guard_w / 2 + 0.5, g + 2, 8.5], "guard"))
    b = g + 1.5
    e.append(el([8 - blade_w / 2, b, 7.6], [8 + blade_w / 2, b + blade_len, 8.4], "blade"))
    e.append(el([8 - blade_w / 2 - 0.25, b, 7.75], [8 - blade_w / 2, b + blade_len - 0.5, 8.25], "edge"))
    e.append(el([8 + blade_w / 2, b, 7.75], [8 + blade_w / 2 + 0.25, b + blade_len - 0.5, 8.25], "edge"))
    e.append(el([7.75, b + 0.5, 7.5], [8.25, b + blade_len - 2, 8.5], "dark"))  # fuller
    e.append(el([8 - blade_w / 2 + 0.5, b + blade_len, 7.7], [8 + blade_w / 2 - 0.5, b + blade_len + 1.5, 8.3], "blade"))
    e.append(el([7.6, b + blade_len + 1.5, 7.8], [8.4, b + blade_len + 2.5, 8.2], "edge"))
    return e, b, g, y0


def weapons():
    W = {}
    e, b, g, y0 = sword(3, 11, 7, 5)
    e = e[:5]  # cleaver keeps hilt only, then a slab blade
    e.append(el([5, b, 7.6], [11, b + 9, 8.4], "bone"))
    e.append(el([10.6, b, 7.7], [11.3, b + 9, 8.3], "edge"))
    e.append(el([5, b + 9, 7.6], [9, b + 10.5, 8.4], "bone"))
    for i in range(3):
        e.append(el([5.5 + i * 1.6, b + 2, 7.4], [6.3 + i * 1.6, b + 2.8, 8.6], "dark"))  # holes
    e.append(el([7, y0 - 0.5, 7], [9, y0 + 0.5, 9], "bone"))
    W["bone_cleaver"] = (e, {"blade": 0xd8d0bc, "edge": 0xf4f0e4, "dark": 0x3a2e24, "guard": 0x5a4a3a, "grip": 0x6a4a2a,
                             "bone": 0xd8ccb0, "gem": 0x7fffd4})

    e, b, g, y0 = sword(3, 12, 6, 5)
    for i in range(6):  # serrations
        y = b + 1 + i * 1.8
        e.append(el([5.8, y, 7.7], [6.5, y + 0.9, 8.3], "edge"))
        e.append(el([9.5, y + 0.9, 7.7], [10.2, y + 1.8, 8.3], "edge"))
    e.append(el([7.75, b + 0.5, 7.45], [8.25, b + 10, 8.55], "gem"))  # blood channel
    e.append(el([7.3, y0 + 0.3, 6.8], [8.7, y0 + 1.7, 9.2], "gem"))
    W["bloodreaver"] = (e, {"blade": 0x8a0f14, "edge": 0xd8c0c0, "dark": 0x2a0505, "guard": 0x2a0505, "grip": 0x3a1a14,
                            "gem": 0xff2030})

    e, b, g, y0 = sword(2, 15, 9, 5)
    e.append(el([7.3, g - 0.2, 6.6], [8.7, g + 1.7, 9.4], "gem"))
    W["voidsteel_longsword"] = (e, {"blade": 0x5a5480, "edge": 0xc8b8ff, "dark": 0x1a1428, "guard": 0x2a2440,
                                    "grip": 0x1a1420, "gem": 0xb388ff})

    e, b, g, y0 = sword(3, 1, 6, 5)
    e = e[:5]
    x = 8.0
    for i in range(7):  # a curved blade from offset segments
        x += 0.35 * i * 0.4
        e.append(el([x - 1.5, b + i * 2, 7.6], [x + 1.5, b + i * 2 + 2.2, 8.4], "blade"))
        e.append(el([x + 1.5, b + i * 2, 7.75], [x + 1.8, b + i * 2 + 2.2, 8.25], "edge"))
    e.append(el([x - 1, b + 14, 7.7], [x + 1.2, b + 15.5, 8.3], "edge"))
    e.append(el([7, g - 0.5, 6.7], [9, g + 2, 9.3], "gem"))  # star at the guard
    W["star_edge"] = (e, {"blade": 0xffe680, "edge": 0xffffff, "guard": 0x1a2a5a, "grip": 0x1a2240, "gem": 0xfff6b0})

    e, b, g, y0 = sword(2.5, 12, 4, 5)
    for frm, to in (([5, g - 2, 7.3], [11, g - 1.3, 8.7]), ([5, g + 2.5, 7.3], [11, g + 3.2, 8.7]),
                    ([5, g - 2, 7.3], [5.7, g + 3.2, 8.7]), ([10.3, g - 2, 7.3], [11, g + 3.2, 8.7])):
        e.append(el(frm, to, "guard"))  # clock-ring guard
    e.append(el([7.2, g - 0.6, 7.2], [8.8, g + 1.9, 8.8], "gem"))
    W["chrono_blade"] = (e, {"blade": 0xd4af37, "edge": 0xfff3b0, "dark": 0x5a4a1a, "guard": 0xb08a20, "grip": 0x3a2a10,
                             "gem": 0xfff3b0})

    e, b, g, y0 = sword(5, 14, 10, 5)
    for i, (dx, dy, s) in enumerate(((-3.5, 3, 1.5), (3, 6, 1.5), (-3.3, 9, 1.2), (3.2, 11, 1.0), (0, 15.5, 1.2))):
        e.append(el([8 + dx - s / 2, b + dy, 7.2], [8 + dx + s / 2, b + dy + s * 1.6, 8.8], "gem"))
    W["fracture_greatsword"] = (e, {"blade": 0x5ab8d0, "edge": 0xe0ffff, "dark": 0x1a2a3a, "guard": 0x2b2f3a,
                                    "grip": 0x1a1e26, "gem": 0x7df9ff})

    # Hollow scythe: long haft and a hooked blade
    e = [el([7.5, -3, 7.5], [8.5, 18, 8.5], "wood"), el([7.2, -3.5, 7.2], [8.8, -2, 8.8], "metal"),
         el([7.2, 4, 7.2], [8.8, 5, 8.8], "grip"), el([7.2, 15, 7.2], [8.8, 18.5, 8.8], "metal"),
         el([0, 16.5, 7.7], [8, 18.2, 8.3], "blade"), el([-1, 14.5, 7.7], [1.5, 16.5, 8.3], "blade"),
         el([-1.5, 12.5, 7.75], [0, 14.5, 8.25], "edge"), el([0, 16.2, 7.8], [8, 16.5, 8.2], "edge"),
         el([7, 18.5, 7], [9, 20, 9], "gem")]
    W["hollow_scythe"] = (e, {"blade": 0x9a9aa2, "edge": 0xffffff, "wood": 0x3a2a24, "metal": 0x4a4a52, "grip": 0x5b0f0f,
                              "gem": 0xf2f2f2})

    # ---- knives ----
    def knife(blade_w, blade_len, grip_len=4.0, guard_w=3.0):
        return sword(blade_w, blade_len, guard_w, grip_len)

    e, b, g, y0 = knife(2.0, 7)
    e = e[:3]
    e.append(el([7, b, 7.6], [9, b + 6, 8.4], "bone"))
    e.append(el([7.3, b + 6, 7.7], [8.7, b + 8, 8.3], "bone"))
    e.append(el([7.6, b + 8, 7.8], [8.4, b + 9, 8.2], "edge"))
    e.append(el([7.2, y0 + 2.5, 7.2], [8.8, y0 + 3.3, 8.8], "cloth"))  # rag wrap
    e.append(el([7.2, y0 + 4.2, 7.2], [8.8, y0 + 5.0, 8.8], "cloth"))
    W["bone_shiv"] = (e, {"blade": 0xd8ccb0, "edge": 0xf4ecd8, "guard": 0x5a4a3a, "grip": 0x4a3a2a, "bone": 0xd8ccb0,
                          "cloth": 0x8a7a5a, "dark": 0x3a2e24})

    e, b, g, y0 = knife(2.2, 8, 4.5, 5)
    e.append(el([7.3, g - 0.3, 6.7], [8.7, g + 1.8, 9.3], "gem"))
    e.append(el([7.6, y0 - 1.2, 7.6], [8.4, y0, 8.4], "gem"))
    for i in range(3):
        e.append(el([7.75, b + 1 + i * 2.2, 7.45], [8.25, b + 2 + i * 2.2, 8.55], "accent"))  # runes
    W["ritual_dagger"] = (e, {"blade": 0x3a3a44, "edge": 0xd8d8e0, "dark": 0x1a1a20, "guard": 0xc8a030, "grip": 0x5a0a14,
                              "gem": 0xff2030, "accent": 0xff6060})

    e, b, g, y0 = knife(4, 8, 5, 3)
    e = e[:3]
    e.append(el([6, b, 7.6], [10.5, b + 7, 8.4], "blade"))
    e.append(el([10.5, b, 7.75], [10.9, b + 7, 8.25], "edge"))
    e.append(el([6, b + 7, 7.6], [9.5, b + 8.5, 8.4], "blade"))
    e.append(el([6.5, b + 1, 7.4], [8, b + 3, 8.6], "accent"))   # blood
    e.append(el([7, y0 + 1.5, 7.3], [9, y0 + 2.0, 8.7], "metal"))   # rivets
    e.append(el([7, y0 + 4.0, 7.3], [9, y0 + 4.5, 8.7], "metal"))
    W["butcher_knife"] = (e, {"blade": 0x9a9aa2, "edge": 0xe0e0e8, "guard": 0x3a2a1a, "grip": 0x4a2e1a, "accent": 0x7a0a10,
                              "metal": 0xc8c8d0, "dark": 0x2a2a30})

    e, b, g, y0 = knife(1.8, 1, 4.5, 4)
    e = e[:5]
    for i in range(6):  # a wavy blade
        x = 8 + (0.9 if i % 2 else -0.9)
        e.append(el([x - 1, b + i * 1.6, 7.6], [x + 1, b + i * 1.6 + 1.8, 8.4], "blade"))
        e.append(el([x - 0.4, b + i * 1.6, 7.45], [x + 0.4, b + i * 1.6 + 1.8, 8.55], "gem"))
    e.append(el([7.5, b + 9.6, 7.7], [8.5, b + 11, 8.3], "edge"))
    W["voidglass_kris"] = (e, {"blade": 0x3a1a6a, "edge": 0xd8c8ff, "guard": 0x1a1428, "grip": 0x2a2040, "gem": 0x9a6aff,
                               "dark": 0x0a0814})

    # ---- swords ----
    e, b, g, y0 = sword(3, 13, 9, 5)
    e.append(el([6.8, g - 0.5, 6.6], [9.2, g + 2.2, 9.4], "bone"))     # skull guard
    e.append(el([7.3, g + 0.4, 6.5], [7.8, g + 0.9, 6.6], "dark"))
    e.append(el([8.2, g + 0.4, 6.5], [8.7, g + 0.9, 6.6], "dark"))
    W["grave_warden_blade"] = (e, {"blade": 0x6a6a72, "edge": 0xd0d0d8, "dark": 0x1a1a20, "guard": 0x3a3a40,
                                   "grip": 0x3a2a1a, "bone": 0xd8ccb0})

    e, b, g, y0 = sword(2, 1, 3, 6)
    e = e[:5]
    x = 8.0
    for i in range(8):  # a long, gently curved katana blade
        x += 0.18 * i * 0.3
        e.append(el([x - 1, b + i * 2, 7.65], [x + 1, b + i * 2 + 2.2, 8.35], "blade"))
        e.append(el([x + 1, b + i * 2, 7.8], [x + 1.25, b + i * 2 + 2.2, 8.2], "edge"))
    e.append(el([x - 0.8, b + 16, 7.7], [x + 0.9, b + 17.4, 8.3], "edge"))
    e.append(el([6.8, g - 0.2, 6.8], [9.2, g + 0.5, 9.2], "gem"))       # tsuba
    for i in range(3):
        e.append(el([7.3, y0 + 2.4 + i * 1.6, 7.3], [8.7, y0 + 3.0 + i * 1.6, 8.7], "accent"))  # wrap
    W["bloodmoon_katana"] = (e, {"blade": 0xd8d8e0, "edge": 0xff6070, "dark": 0x2a0a10, "guard": 0x2a0a10,
                                 "grip": 0x1a1014, "gem": 0x8b0f14, "accent": 0xb0101a})

    e, b, g, y0 = sword(3, 12, 6, 5)
    for i in range(5):  # sinew strands wrapped round the blade
        y = b + 1 + i * 2.3
        e.append(el([6.2, y, 7.4], [9.8, y + 0.7, 8.6], "accent"))
    W["sinew_saber"] = (e, {"blade": 0x9a3a3a, "edge": 0xe0a0a0, "dark": 0x3a0a0a, "guard": 0x5a2a20,
                            "grip": 0x3a1a14, "accent": 0xd86a5a})

    e, b, g, y0 = sword(4.5, 17, 11, 7)
    e.append(el([7.0, g - 0.6, 6.5], [9.0, g + 2.4, 9.5], "gem"))
    e.append(el([5.5, b + 2, 7.5], [10.5, b + 3, 8.5], "accent"))
    W["skystone_claymore"] = (e, {"blade": 0xb8c4d4, "edge": 0xf4f8ff, "dark": 0x5a6478, "guard": 0xd8d8e0,
                                  "grip": 0x4a5a7a, "gem": 0x7ab8ff, "accent": 0xe8f0ff})

    e, b, g, y0 = sword(1, 15, 6, 5)
    e.append(el([6, g - 2.5, 7.5], [6.6, g + 1.5, 8.5], "guard"))       # basket hilt
    e.append(el([9.4, g - 2.5, 7.5], [10, g + 1.5, 8.5], "guard"))
    e.append(el([6, y0 + 1, 7.5], [10, y0 + 1.6, 8.5], "guard"))
    for i in range(4):
        e.append(el([7.8, b + 2 + i * 3.5, 7.4], [8.2, b + 3.2 + i * 3.5, 8.6], "gem"))  # crackling sparks
    W["static_rapier"] = (e, {"blade": 0x7a8a9a, "edge": 0xe0ffff, "dark": 0x1a2028, "guard": 0xb87333,
                              "grip": 0x2a1a10, "gem": 0x7df9ff})

    return W


def staves():
    W = {}
    base = [el([7.5, -4, 7.5], [8.5, 14, 8.5], "wood"), el([7.2, -4.5, 7.2], [8.8, -3, 8.8], "metal"),
            el([7.2, 3, 7.2], [8.8, 5, 8.8], "grip"), el([7, 13, 7], [9, 14.5, 9], "metal")]
    soul = base + [el([6.2, 14.5, 7.6], [6.8, 19, 8.4], "metal"), el([9.2, 14.5, 7.6], [9.8, 19, 8.4], "metal"),
                   el([7.6, 14.5, 6.2], [8.4, 19, 6.8], "metal"), el([7.6, 14.5, 9.2], [8.4, 19, 9.8], "metal"),
                   el([6.6, 19, 6.6], [9.4, 19.6, 9.4], "metal"), el([7, 15.3, 7], [9, 17.8, 9], "gem")]
    W["soul_staff"] = (soul, {"wood": 0x3a2a20, "metal": 0x8a8a90, "grip": 0x4a3020, "gem": 0x5ad8ff})
    ember = base + [el([7.3, 14.5, 7.3], [8.7, 16, 8.7], "accent"), el([6.8, 16, 6.8], [9.2, 18.5, 9.2], "gem"),
                    el([7.3, 18.5, 7.3], [8.7, 20.5, 8.7], "glow2"), el([6, 15, 7.6], [6.8, 17.5, 8.4], "accent"),
                    el([9.2, 15, 7.6], [10, 17.5, 8.4], "accent")]
    W["ember_staff"] = (ember, {"wood": 0x2a1a10, "metal": 0x6a3a2a, "grip": 0x2a1a10, "accent": 0xb0101a,
                                "gem": 0xff8020, "glow2": 0xffd060})
    rod = [el([7.5, -4, 7.5], [8.5, 14, 8.5], "metal"), el([7.3, -4.5, 7.3], [8.7, -3, 8.7], "accent")]
    for i in range(5):
        rod.append(el([7, 2 + i * 2.2, 7], [9, 2.6 + i * 2.2, 9], "accent"))  # copper coils
    rod += [el([7.2, 14, 7.2], [8.8, 17, 8.8], "gem"), el([7.6, 17, 7.6], [8.4, 19, 8.4], "glow2")]
    W["storm_rod"] = (rod, {"metal": 0x6a4a2a, "accent": 0xb87333, "gem": 0x7df9ff, "glow2": 0xe0ffff})
    return W


HANDHELD_DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "gui": {"rotation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
    "fixed": {"rotation": [0, 180, 0]},
}


def display_for(elements):
    """Handheld transforms scaled to the model's real (rotated) size, so long blades and staves
    fit the inventory slot and don't fill the screen in first person."""
    import math
    c = math.sqrt(0.5)
    xs, ys = [], []
    for e in elements:
        for x in (e["from"][0], e["to"][0]):
            for y in (e["from"][1], e["to"][1]):
                xs.append(8 + c * (x - 8) + c * (y - 8))
                ys.append(8 - c * (x - 8) + c * (y - 8))
    span = max(max(xs) - min(xs), max(ys) - min(ys))
    k = min(1.0, 15.5 / span)
    cx, cy = (max(xs) + min(xs)) / 2 - 8, (max(ys) + min(ys)) / 2 - 8
    d = {key: {kk: list(vv) for kk, vv in v.items()} for key, v in HANDHELD_DISPLAY.items()}
    d["gui"] = {"rotation": [0, 0, 0], "translation": [round(-cx * k, 2), round(-cy * k, 2), 0], "scale": [round(k, 3)] * 3}
    fk = round(0.68 * max(0.62, k), 3)
    for side in ("firstperson_righthand", "firstperson_lefthand"):
        d[side]["scale"] = [fk] * 3
        d[side]["translation"] = [1.13, round(3.2 - (1 - k) * 3, 2), 1.13]
    tk = round(0.85 * max(0.75, k), 3)
    for side in ("thirdperson_righthand", "thirdperson_lefthand"):
        d[side]["scale"] = [tk] * 3
    d["ground"]["scale"] = [round(0.5 * k, 3)] * 3
    return d


def all_models():
    out = {}
    for name, (elements, colors) in {**weapons(), **staves()}.items():
        out[name] = (elements, atlas(colors, abs(hash(name)) % 9999))
    return out
