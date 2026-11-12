"""3D item models (Blockbench "Java Block/Item" format) for melee weapons, staves and the storm rod.

Each weapon is designed upright (blade along +Y, centred on x = z = 8) and every element is then
rotated -45° about Z, so the vanilla 'handheld' display transforms hold it exactly like a sword.
"""
from __future__ import annotations

import numpy as np
from PIL import Image

import tex
from tex import rgb as H, shade, lerp, fbm, Face

# 16x16 swatches on a 64x64 atlas; UVs address it in 0..16 units (4x4 units per swatch)
SW = {"steel": (0, 0), "edge": (4, 0), "fuller": (8, 0), "steel_dark": (12, 0),
      "iron": (0, 4), "brass": (4, 4), "leather": (8, 4), "leather_dark": (12, 4),
      "wood": (0, 8), "bone": (4, 8), "gem": (8, 8), "glow": (12, 8),
      "cloth": (0, 12), "rust": (4, 12), "blood": (8, 12), "accent": (12, 12)}
# older material names still used by the staves and a few parts
ALIAS = {"blade": "steel", "dark": "steel_dark", "guard": "iron", "grip": "leather", "metal": "iron", "glow2": "glow"}

N = 16  # pixels per swatch


def _swatch(name, c0, seed):
    """A physically-ish shaded material swatch, lit from above and the viewer's side."""
    ys, xs = np.mgrid[0:N, 0:N].astype(float)
    u, v = xs / (N - 1), ys / (N - 1)
    if name in ("steel", "steel_dark", "edge"):
        # brushed metal: long streaks along the blade, a bright specular band and a darker falloff
        streak = fbm(xs / 0.7, ys / 9, seed, 3)
        spec = np.exp(-((u - 0.38) / 0.16) ** 2) * (0.55 if name != "steel_dark" else 0.25)
        tone = 0.72 + 0.28 * streak + spec - 0.18 * v
        if name == "edge":
            tone = 0.95 + 0.25 * np.exp(-((u - 0.5) / 0.3) ** 2) + 0.08 * streak
        c = shade(c0, tone)
        scratch = (tex.hashf(xs.astype(int), ys.astype(int), seed + 5) > 0.97)
        c = np.where(scratch[..., None], shade(c0, tone[..., None] * 1.35 if False else 1.4), c)
    elif name == "fuller":
        tone = 0.55 + 0.25 * np.exp(-((u - 0.5) / 0.18) ** 2) + 0.1 * fbm(xs / 0.7, ys / 9, seed + 1, 2)
        c = shade(c0, tone)
    elif name in ("iron", "brass", "rust"):
        tone = 0.7 + 0.35 * (1 - v) + 0.15 * fbm(xs / 3, ys / 3, seed + 2, 3)
        c = shade(c0, tone)
        if name == "brass":
            c = np.where(((xs + ys) % 9 == 0)[..., None], shade(c0, 1.45), c)
        if name == "rust":
            pit = fbm(xs / 2, ys / 2, seed + 3, 3) > 0.62
            c = np.where(pit[..., None], shade(c0, 0.55), c)
    elif name in ("leather", "leather_dark"):
        # diagonal wrap with stitched ridges
        band = ((ys + xs * 0.6) % 4) < 1.2
        c = shade(c0, np.where(band, 0.62, 0.95 + 0.12 * fbm(xs, ys, seed + 4, 2)))
    elif name == "wood":
        c = lerp(shade(c0, 0.7), shade(c0, 1.15), np.sin(xs * 0.9 + fbm(xs / 2, ys / 5, seed, 3) * 5) * 0.5 + 0.5)
    elif name == "bone":
        c = shade(c0, 0.8 + 0.25 * fbm(xs / 2, ys / 6, seed, 3) + 0.1 * (1 - v))
        c = np.where((tex.hashf(xs.astype(int), ys.astype(int), seed + 6) > 0.96)[..., None], shade(c0, 0.55), c)
    elif name in ("gem", "glow"):
        r = np.hypot(u - 0.4, v - 0.35) / 0.75
        c = lerp(np.ones(3), c0, np.clip(r * 1.4, 0, 1))
        c = shade(c, 1.0 + 0.2 * (name == "glow"))
    elif name == "blood":
        c = lerp(H(0x3a0006), c0, fbm(xs / 2, ys / 2, seed + 7, 3))
        c = np.where((fbm(xs / 1.5, ys / 4, seed + 8, 2) > 0.66)[..., None], shade(c0, 1.5), c)
    else:
        c = shade(c0, 0.8 + 0.3 * fbm(xs / 3, ys / 3, seed + 9, 3) + 0.1 * (1 - v))
    return np.clip(c, 0, 1)


def atlas(colors, seed):
    img = np.zeros((64, 64, 3))
    defaults = {"steel": 0x9aa0a8, "edge": 0xe8ecf2, "fuller": 0x6a7078, "steel_dark": 0x3a3e46, "iron": 0x5a5c62,
                "brass": 0xc8a040, "leather": 0x5a3a22, "leather_dark": 0x2a1a10, "wood": 0x6a4a2a, "bone": 0xd8ccb0,
                "gem": 0xff3040, "glow": 0x7df9ff, "cloth": 0x6a1a1a, "rust": 0x8a4a20, "blood": 0x8a0a14, "accent": 0xc0c0c0}
    for old, new in ALIAS.items():          # accept the old colour keys too
        if old in colors and new not in colors:
            colors = {**colors, new: colors[old]}
    for name, (sx, sy) in SW.items():
        c0 = H(colors.get(name, defaults[name]))
        img[sy * 4:sy * 4 + N, sx * 4:sx * 4 + N] = _swatch(name, c0, seed + sx * 7 + sy)
    return Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8), "RGB").convert("RGBA")


def el(frm, to, mat):
    mat = ALIAS.get(mat, mat)
    sx, sy = SW[mat]
    uv = [sx, sy, sx + 4, sy + 4]
    return {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to],
            "rotation": {"angle": -45, "axis": "z", "origin": [8, 8, 8]},
            "faces": {f: {"uv": uv, "texture": "#atlas"} for f in ("north", "south", "east", "west", "up", "down")}}


# =============================================================================================
# Realistic parts: every weapon is assembled from these
# =============================================================================================
def blade(y0, length, base_w, tip_len=3.0, tip_w=0.15, thick=0.7, curve=0.0, double=True, fuller=0.6,
          taper=0.25, seg=2.0, steel="steel", edge="edge", wave=0.0, serr=0):
    """A tapering blade from y0 upward: a thick core, bevelled honed edges, an optional fuller
    groove, a point, an optional curve (toward +x) or wave, and optional serrations."""
    e = []
    y = y0
    edge_w = min(0.45, base_w * 0.18)
    if curve or wave:
        seg = min(seg, 0.7)    # short segments keep curves smooth
    while y < y0 + length - 1e-6:
        h = min(seg if y < y0 + length - tip_len else seg * 0.5, y0 + length - y)
        t = (y + h / 2 - y0) / length
        tip_t = (y + h / 2 - (y0 + length - tip_len)) / tip_len
        w = base_w * (1 - taper * t)
        if tip_t > 0:
            w = max(tip_w, w * (1 - tip_t ** 1.3))
        cx = 8 + curve * (t ** 2) * length * 0.25 + wave * np.sin(t * np.pi * 4)
        hw = w / 2
        ew = min(edge_w, hw * 0.6)
        core_t = thick if tip_t <= 0 else thick * max(0.35, 1 - tip_t)
        # core (the flat of the blade)
        e.append(el([cx - hw + ew, y, 8 - core_t / 2], [cx + hw - ew, y + h, 8 + core_t / 2], steel))
        # bevelled edges: thinner, brighter
        e.append(el([cx + hw - ew, y, 8 - core_t / 4], [cx + hw, y + h, 8 + core_t / 4], edge))
        if double:
            e.append(el([cx - hw, y, 8 - core_t / 4], [cx - hw + ew, y + h, 8 + core_t / 4], edge))
        else:  # a thick spine instead of a second edge
            e.append(el([cx - hw - 0.1, y, 8 - core_t / 2 - 0.05], [cx - hw + ew, y + h, 8 + core_t / 2 + 0.05], "steel_dark"))
        if fuller and t < 0.7 and w > 1.2:
            fw = w * 0.22
            e.append(el([cx - fw / 2, y, 8 - core_t / 2 - 0.02], [cx + fw / 2, y + h, 8 + core_t / 2 + 0.02], "fuller"))
        if serr and tip_t <= 0 and int((y - y0) / h) % serr == 0:
            e.append(el([cx + hw, y, 8 - core_t / 4], [cx + hw + 0.5, y + h * 0.6, 8 + core_t / 4], edge))
        y += h
    return e, cx


def guard(y, width, depth=1.6, height=1.0, mat="iron", droop=0.0, finials=True, center_w=2.2):
    """A crossguard with tapering quillons, optional droop (negative) or rise, and end finials."""
    e = [el([8 - center_w / 2, y, 8 - depth / 2], [8 + center_w / 2, y + height * 1.4, 8 + depth / 2], mat)]
    half = width / 2
    steps = 3
    for side in (-1, 1):
        for k in range(steps):
            x0 = center_w / 2 + (half - center_w / 2) * k / steps
            x1 = center_w / 2 + (half - center_w / 2) * (k + 1) / steps
            dy = droop * ((k + 1) / steps) ** 2
            hh = height * (1 - 0.18 * k)
            dd = depth * (1 - 0.2 * k)
            xa, xb = sorted((8 + side * x0, 8 + side * x1))
            e.append(el([xa, y + dy + (height - hh) / 2, 8 - dd / 2], [xb, y + dy + (height + hh) / 2, 8 + dd / 2], mat))
        if finials:
            xf = 8 + side * half
            e.append(el([xf - 0.55, y + droop - 0.25, 8 - 0.55], [xf + 0.55, y + droop + height + 0.25, 8 + 0.55], mat))
    return e


def grip(y0, length, w=1.1, wrap="leather", ring="leather_dark", ferrule="iron"):
    e = [el([8 - w / 2, y0, 8 - w / 2], [8 + w / 2, y0 + length, 8 + w / 2], wrap)]
    k = 0.45
    while k < length - 0.3:
        e.append(el([8 - w / 2 - 0.08, y0 + k, 8 - w / 2 - 0.08], [8 + w / 2 + 0.08, y0 + k + 0.22, 8 + w / 2 + 0.08], ring))
        k += 0.7
    e.append(el([8 - w / 2 - 0.15, y0 + length - 0.5, 8 - w / 2 - 0.15], [8 + w / 2 + 0.15, y0 + length, 8 + w / 2 + 0.15], ferrule))
    e.append(el([8 - w / 2 - 0.15, y0, 8 - w / 2 - 0.15], [8 + w / 2 + 0.15, y0 + 0.4, 8 + w / 2 + 0.15], ferrule))
    return e


def pommel(y_top, size=1.8, mat="iron", gem=None):
    s = size
    e = [el([8 - s / 2, y_top - s * 0.7, 8 - s / 2], [8 + s / 2, y_top, 8 + s / 2], mat),
         el([8 - s * 0.35, y_top - s, 8 - s * 0.35], [8 + s * 0.35, y_top - s * 0.7, 8 + s * 0.35], mat),
         el([8 - s * 0.62, y_top - s * 0.55, 8 - s * 0.3], [8 + s * 0.62, y_top - s * 0.15, 8 + s * 0.3], mat)]
    if gem:
        e.append(el([8 - s * 0.22, y_top - s * 0.55, 8 - s / 2 - 0.08], [8 + s * 0.22, y_top - s * 0.15, 8 + s / 2 + 0.08], gem))
    return e


def hilt(y_pommel, grip_len, guard_w, guard_mat="iron", grip_w=1.1, pommel_size=1.8, pommel_gem=None, droop=0.0,
         wrap="leather", ring="leather_dark", guard_depth=1.6, finials=True):
    """Pommel at the bottom, then grip, then guard; returns (elements, y where the blade starts)."""
    e = pommel(y_pommel + pommel_size, pommel_size, guard_mat, pommel_gem)
    y = y_pommel + pommel_size
    e += grip(y, grip_len, grip_w, wrap, ring)
    y += grip_len
    e += guard(y, guard_w, guard_depth, 1.0, guard_mat, droop, finials)
    return e, y + 1.0


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
    """Every melee weapon, built from realistic parts. Each entry: (elements, material colours)."""
    W = {}

    # ---------------- swords ----------------
    # Voidsteel Longsword: a classic arming sword, diamond-section blade with a long fuller
    e, y = hilt(-6, 4.2, 7.5, "steel_dark", pommel_gem="gem", droop=-0.4)
    b, _ = blade(y, 16, 2.4, tip_len=3.5)
    W["voidsteel_longsword"] = (e + b, {"steel": 0x5e5a84, "edge": 0xd8ccff, "fuller": 0x2a2440, "steel_dark": 0x1a1428,
                                        "leather": 0x1a1420, "leather_dark": 0x0a0810, "gem": 0xb388ff})
    # Fracture Greatsword: two-handed, ricasso and parrying lugs, crystal shards along the spine
    e, y = hilt(-9, 6.0, 9.5, "iron", grip_w=1.25, pommel_size=2.2, droop=0.5)
    e.append(el([8 - 1.5, y, 7.6], [8 + 1.5, y + 2, 8.4], "steel_dark"))                 # ricasso
    e.append(el([8 - 2.4, y + 1.6, 7.7], [8 + 2.4, y + 2.3, 8.3], "iron"))                # parrying lugs
    b, cx = blade(y + 2, 18, 3.4, tip_len=4, thick=0.8, taper=0.2)
    for i in range(5):
        yy = y + 4 + i * 3
        b.append(el([8 - 0.6, yy, 8.4], [8 + 0.6, yy + 1.6, 8.9], "gem"))                 # crystal shards
    W["fracture_greatsword"] = (e + b, {"steel": 0x4a5a6a, "edge": 0xc8f8ff, "fuller": 0x2a3a4a, "steel_dark": 0x1a2028,
                                        "iron": 0x3a3e46, "leather": 0x22262e, "gem": 0x7df9ff})
    # Star Edge: a slender curved sabre of pale gold steel, star-set guard
    e, y = hilt(-6, 4.0, 5.0, "brass", pommel_gem="gem", droop=-0.6)
    b, _ = blade(y, 16, 2.0, tip_len=3.0, curve=1.2, double=False, fuller=0.0)
    e.append(el([8 - 0.7, y - 1.3, 7.2], [8 + 0.7, y + 0.1, 8.8], "gem"))
    W["star_edge"] = (e + b, {"steel": 0xe8d8a0, "edge": 0xffffff, "steel_dark": 0xb89a50, "brass": 0x1a2a5a,
                              "leather": 0x1a2240, "gem": 0xfff6b0})
    # Chrono Blade: a sword whose blade is cut with clockwork gaps
    e, y = hilt(-6, 4.2, 6.5, "brass", pommel_gem="glow", droop=0.3)
    b, _ = blade(y, 15, 2.6, tip_len=3.2, fuller=0.0)
    for i in range(4):
        yy = y + 2 + i * 3
        b.append(el([8 - 0.75, yy, 7.5], [8 + 0.75, yy + 1.5, 8.5], "brass"))              # gear inlays
        b.append(el([8 - 0.3, yy + 0.45, 7.45], [8 + 0.3, yy + 1.05, 8.55], "glow"))
    W["chrono_blade"] = (e + b, {"steel": 0xb8a878, "edge": 0xfff3c0, "steel_dark": 0x5a4a20, "brass": 0xd4af37,
                                 "leather": 0x3a2a10, "glow": 0xfff3b0})
    # Bloodreaver: a serrated broadsword with a blood channel
    e, y = hilt(-6, 4.0, 7.0, "steel_dark", droop=-0.8)
    b, _ = blade(y, 14, 3.0, tip_len=3.0, serr=2, steel="steel", fuller=0.6)
    b.append(el([8 - 0.25, y + 0.5, 7.5], [8 + 0.25, y + 9, 8.5], "blood"))
    W["bloodreaver"] = (e + b, {"steel": 0x7a1a20, "edge": 0xe0b8b8, "fuller": 0x3a0508, "steel_dark": 0x2a0505,
                                "leather": 0x3a1a14, "blood": 0xff2030})
    # Grave Warden Blade: a heavy cleaving sword with a skull guard
    e, y = hilt(-6, 4.4, 8.5, "iron", pommel_size=2.0, droop=-0.3)
    e.append(el([8 - 1.2, y - 1.4, 7.0], [8 + 1.2, y + 0.6, 9.0], "bone"))                # skull at the guard
    e.append(el([8 - 0.75, y - 0.9, 6.9], [8 - 0.25, y - 0.4, 7.0], "steel_dark"))
    e.append(el([8 + 0.25, y - 0.9, 6.9], [8 + 0.75, y - 0.4, 7.0], "steel_dark"))
    b, _ = blade(y, 15, 3.0, tip_len=2.8, taper=0.1)
    W["grave_warden_blade"] = (e + b, {"steel": 0x7a7c84, "edge": 0xe0e2e8, "fuller": 0x3a3c44, "iron": 0x3a3a40,
                                       "leather": 0x3a2a1a, "bone": 0xd8ccb0})
    # Bloodmoon Katana: long gently curved single-edged blade, round tsuba, wrapped tsuka
    e = pommel(-6.2 + 1.0, 1.1, "brass")
    e += grip(-6.2 + 1.0, 6.0, 1.0, "cloth", "leather_dark")
    y = -6.2 + 7.0
    e.append(el([8 - 1.6, y, 8 - 1.6], [8 + 1.6, y + 0.4, 8 + 1.6], "steel_dark"))       # tsuba
    e.append(el([8 - 0.7, y + 0.4, 8 - 0.5], [8 + 0.7, y + 1.2, 8 + 0.5], "brass"))     # habaki
    b, _ = blade(y + 1.2, 17, 1.7, tip_len=2.2, curve=1.6, double=False, fuller=0.0, taper=0.12, thick=0.6)
    W["bloodmoon_katana"] = (e + b, {"steel": 0xd4d8e0, "edge": 0xffe0e4, "steel_dark": 0x1a1014, "brass": 0x8b0f14,
                                     "cloth": 0x8b0f14, "leather_dark": 0x1a0a0c})
    # Sinew Saber: a curved cavalry sabre, its blade bound in sinew strands, knuckle-bow guard
    e, y = hilt(-6, 4.0, 4.0, "rust", droop=-0.5)
    e.append(el([8 + 2.0, y - 4.2, 7.6], [8 + 2.5, y + 0.6, 8.4], "rust"))              # knuckle bow
    b, _ = blade(y, 15, 2.2, tip_len=2.6, curve=1.4, double=False, fuller=0.5)
    for i in range(5):
        yy = y + 1.5 + i * 2.4
        b.append(el([7.0, yy, 7.55], [9.3, yy + 0.45, 8.45], "accent"))
    W["sinew_saber"] = (e + b, {"steel": 0x9a7070, "edge": 0xe8c8c8, "fuller": 0x5a2a2a, "steel_dark": 0x3a1a1a,
                                "rust": 0x6a3a20, "leather": 0x3a1a14, "accent": 0xc85a4a})
    # Skystone Claymore: a huge two-hander, drooping quatrefoil quillons, long ricasso
    e, y = hilt(-11, 6.5, 11, "iron", grip_w=1.3, pommel_size=2.4, droop=-1.4, guard_depth=1.8)
    e.append(el([8 - 1.4, y, 7.6], [8 + 1.4, y + 2.6, 8.4], "steel_dark"))
    b, _ = blade(y + 2.6, 19, 3.6, tip_len=4, taper=0.22, thick=0.85)
    b.append(el([8 - 0.5, y + 1, 7.4], [8 + 0.5, y + 2, 8.6], "gem"))
    W["skystone_claymore"] = (e + b, {"steel": 0xb8c4d4, "edge": 0xf4f8ff, "fuller": 0x6a7488, "steel_dark": 0x4a5468,
                                      "iron": 0xd0d4dc, "leather": 0x2a3448, "gem": 0x7ab8ff})
    # Static Rapier: a needle blade, swept basket hilt in copper
    e, y = hilt(-6, 3.8, 4.5, "brass", pommel_size=1.6, droop=-0.8)
    for side in (-1, 1):
        e.append(el([8 + side * 2.2 - 0.25, y - 3.6, 7.75], [8 + side * 2.2 + 0.25, y - 0.2, 8.25], "brass"))   # swept bars
        e.append(el([8 + side * 1.2 - 0.25, y - 4.1, 7.75], [8 + side * 1.2 + 0.25, y - 3.6, 8.25], "brass"))
    b, _ = blade(y, 18, 0.9, tip_len=5, tip_w=0.1, thick=0.6, fuller=0.0, taper=0.3)
    for i in range(4):
        b.append(el([8 - 0.15, y + 3 + i * 3.4, 7.55], [8 + 0.15, y + 3.8 + i * 3.4, 8.45], "glow"))
    W["static_rapier"] = (e + b, {"steel": 0x8a9aaa, "edge": 0xe8ffff, "steel_dark": 0x2a3038, "brass": 0xb87333,
                                  "leather": 0x2a1a10, "glow": 0x7df9ff})

    # ---------------- cleavers, scythe ----------------
    # Bone Cleaver: a heavy rectangular chopper with a bone spine
    e = pommel(-5.5 + 1.3, 1.3, "iron") + grip(-4.2, 5.0, 1.15, "leather", "leather_dark")
    y = 0.8
    e.append(el([8 - 1.0, y, 7.5], [8 + 1.0, y + 0.8, 8.5], "iron"))
    e.append(el([6.0, y + 0.8, 7.65], [11.5, y + 10, 8.35], "steel"))
    e.append(el([11.5, y + 0.8, 7.85], [11.9, y + 10, 8.15], "edge"))
    e.append(el([5.6, y + 0.8, 7.5], [6.2, y + 10.4, 8.5], "bone"))                      # bone spine
    e.append(el([6.0, y + 10, 7.65], [11.0, y + 10.6, 8.35], "steel"))
    e.append(el([7.0, y + 8.2, 7.6], [7.8, y + 9.0, 8.4], "steel_dark"))                   # hanging hole
    for i in range(3):
        e.append(el([8 + i * 1.3, y + 2 + i * 1.9, 7.62], [8.8 + i * 1.3, y + 2.6 + i * 1.9, 8.38], "blood"))
    W["bone_cleaver"] = (e, {"steel": 0x9a9488, "edge": 0xf0ece0, "steel_dark": 0x1a1410, "iron": 0x5a4a3a,
                             "leather": 0x6a4a2a, "bone": 0xd8ccb0, "blood": 0x7a0a10})
    # Hollow Scythe: a long snath with a curved blade sweeping out from the top
    e = [el([7.55, -8, 7.55], [8.45, 18, 8.45], "wood")]
    for yy in (-6, 1, 8):
        e.append(el([7.4, yy, 7.4], [8.6, yy + 1, 8.6], "iron"))                         # grips and collars
    e.append(el([8.45, 4, 7.6], [10.5, 4.8, 8.4], "wood"))                                 # nib (second handle)
    e.append(el([7.3, 17.4, 7.3], [8.7, 19, 8.7], "iron"))
    for i in range(10):                                                                    # the curved blade
        t = i / 9
        x0 = 8.6 - i * 1.15
        yy = 18.6 - (t ** 1.6) * 5
        w = 1.8 * (1 - t * 0.85) + 0.2
        e.append(el([x0 - 1.2, yy - w, 7.7], [x0, yy, 8.3], "steel"))
        e.append(el([x0 - 1.2, yy - w - 0.3, 7.85], [x0, yy - w, 8.15], "edge"))
    W["hollow_scythe"] = (e, {"steel": 0x5a5a62, "edge": 0xd8d8e0, "wood": 0x3a2a1e, "iron": 0x2a2a30})

    # ---------------- knives ----------------
    # Bone Shiv: a sharpened shard of bone, grip bound with rag
    e = grip(-3.5, 4.0, 1.0, "cloth", "leather_dark")
    b, _ = blade(0.5, 8, 1.6, tip_len=3.5, steel="bone", edge="bone", fuller=0.0, thick=0.7, seg=1.0)
    W["bone_shiv"] = (e + b, {"bone": 0xd8ccb0, "cloth": 0x8a7a5a, "leather_dark": 0x3a2e24})
    # Ritual Dagger: a double-edged dagger, runes inlaid along the fuller, a garnet pommel
    e, y = hilt(-4.5, 3.6, 4.2, "brass", grip_w=0.95, pommel_size=1.4, pommel_gem="gem", droop=0.6)
    b, _ = blade(y, 8.5, 1.7, tip_len=3.0, fuller=0.6)
    for i in range(3):
        b.append(el([8 - 0.2, y + 1 + i * 1.9, 7.55], [8 + 0.2, y + 1.8 + i * 1.9, 8.45], "glow"))
    W["ritual_dagger"] = (e + b, {"steel": 0x3a3a44, "edge": 0xd8d8e0, "fuller": 0x1a1a20, "brass": 0xc8a030,
                                  "leather": 0x5a0a14, "gem": 0xff2030, "glow": 0xff6060})
    # Butcher Knife: a broad, single-edged slaughter knife with riveted wooden scales
    e = pommel(-4.5 + 1.0, 1.0, "iron") + grip(-3.5, 4.6, 1.15, "wood", "leather_dark")
    for yy in (-2.6, -0.6):
        e.append(el([7.4, yy, 7.35], [8.6, yy + 0.4, 8.65], "iron"))                      # rivets
    b, _ = blade(1.1, 9, 3.4, tip_len=3.0, double=False, fuller=0.0, taper=0.1, thick=0.5, curve=0.4)
    b.append(el([7.0, 2.5, 7.73], [8.5, 4.5, 8.27], "blood"))
    W["butcher_knife"] = (e + b, {"steel": 0xa0a2a8, "edge": 0xe8eaf0, "steel_dark": 0x4a4c52, "iron": 0xc8c8d0,
                                  "wood": 0x5a3a20, "blood": 0x7a0a10})
    # Voidglass Kris: a wavy dagger of dark crystal with a pistol grip
    e, y = hilt(-4.5, 3.8, 3.0, "steel_dark", grip_w=1.0, pommel_size=1.4, droop=0.8)
    b, _ = blade(y, 10, 1.9, tip_len=3.0, wave=0.55, steel="steel", edge="glow", fuller=0.0, seg=0.8)
    W["voidglass_kris"] = (e + b, {"steel": 0x3a1a6a, "glow": 0xc8a8ff, "steel_dark": 0x1a1428, "leather": 0x2a2040})
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
        out[name] = (cull(elements), atlas(colors, abs(hash(name)) % 9999))
    return out


# =============================================================================================
# Guns: realistic models (barrel along +X, centred on z = 8; no -45° rotation)
# =============================================================================================
def gl(frm, to, mat, rot=None):
    mat = ALIAS.get(mat, mat)
    sx, sy = SW[mat]
    e = {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to],
         "faces": {f: {"uv": [sx, sy, sx + 4, sy + 4], "texture": "#atlas"} for f in ("north", "south", "east", "west", "up", "down")}}
    if rot:
        e["rotation"] = rot
    return e


def tube(x0, x1, cy, cz, r, mat="steel_dark"):
    """A round-looking tube along X: two crossing boxes give an octagonal section."""
    return [gl([x0, cy - r * 0.62, cz - r], [x1, cy + r * 0.62, cz + r], mat),
            gl([x0, cy - r, cz - r * 0.62], [x1, cy + r, cz + r * 0.62], mat)]


def guns():
    G = {}
    # ---- Rusted Revolver ------------------------------------------------------------------
    e = []
    e += [gl([5, 7, 7.15], [11.2, 10, 8.85], "steel_dark"),                    # frame
          gl([5.6, 10, 7.4], [11.2, 10.6, 8.6], "steel_dark"),                 # top strap
          gl([10.8, 9.9, 7.75], [11.2, 10.9, 8.25], "steel")]                  # rear sight notch
    e += tube(11.2, 18.2, 9.0, 8.0, 0.75, "steel")                             # barrel
    e += [gl([17.4, 9.6, 7.85], [18.0, 10.3, 8.15], "steel"),                  # front sight blade
          gl([11.2, 7.75, 7.7], [16.5, 8.3, 8.3], "steel_dark"),               # ejector rod housing
          gl([16.3, 7.65, 7.6], [16.8, 8.4, 8.4], "steel")]
    e += tube(6.6, 10.0, 8.6, 8.0, 1.45, "steel")                              # cylinder
    for z in (6.5, 9.3):
        e.append(gl([7.0, 8.0, z], [9.6, 9.2, z + 0.2], "steel_dark"))         # flutes
    e += [gl([5.0, 10.0, 7.7], [5.8, 11.4, 8.3], "steel_dark"),                # hammer
          gl([4.4, 11.0, 7.75], [5.4, 11.5, 8.25], "steel"),                   # hammer spur
          gl([6.2, 5.6, 7.75], [9.4, 6.0, 8.25], "steel_dark"),                # trigger guard bottom
          gl([9.0, 5.6, 7.75], [9.4, 7.0, 8.25], "steel_dark"),
          gl([7.4, 6.0, 7.85], [7.8, 7.0, 8.15], "steel")]                     # trigger
    grip = {"angle": -22.5, "axis": "z", "origin": [5.5, 7, 8]}
    e += [gl([3.9, 2.3, 7.05], [6.6, 7.2, 8.95], "wood", grip),               # grip scales
          gl([4.1, 2.4, 6.95], [6.4, 7.0, 7.05], "wood", grip),
          gl([4.1, 2.4, 8.95], [6.4, 7.0, 9.05], "wood", grip),
          gl([3.8, 1.8, 7.1], [6.7, 2.4, 8.9], "brass", grip),                 # butt cap
          gl([5.0, 4.6, 6.9], [5.6, 5.2, 9.1], "brass", grip)]                 # grip screw
    G["rusted_revolver"] = (e, {"steel": 0x8a8c90, "steel_dark": 0x3a3a3e, "wood": 0x6a3e20, "brass": 0xb88a40,
                                "edge": 0xd0d0d8, "rust": 0x8a4a20})
    # ---- Doomsday Shotgun (double barrel) --------------------------------------------------
    e = [gl([1, 6.4, 7.1], [4, 9.2, 8.9], "wood"),                             # stock: butt
         gl([4, 6.9, 7.2], [7, 9.3, 8.8], "wood"),
         gl([7, 7.4, 7.3], [9.5, 9.4, 8.7], "wood"),                           # wrist
         gl([0.7, 6.2, 7.0], [1.2, 9.4, 9.0], "leather_dark"),                 # recoil pad
         gl([9.5, 7.6, 7.1], [13, 10, 8.9], "steel_dark"),                     # receiver
         gl([10.4, 10, 7.6], [11.4, 10.4, 8.4], "steel"),                      # top lever
         gl([12.4, 7.6, 7.0], [13.0, 8.3, 9.0], "steel")]                      # hinge pin
    for z in (7.3, 8.7):
        e += tube(13, 26, 9.25, z, 0.68, "steel")                              # barrels
    e += [gl([13, 9.9, 7.85], [26, 10.15, 8.15], "steel_dark"),                # rib
          gl([25.5, 10.15, 7.9], [25.8, 10.45, 8.1], "brass"),                 # bead
          gl([13, 7.9, 7.2], [19, 8.7, 8.8], "wood"),                          # forend
          gl([9.6, 6.6, 7.7], [12.2, 7.0, 8.3], "steel_dark"),                 # trigger guard
          gl([11.8, 6.6, 7.7], [12.2, 7.6, 8.3], "steel_dark"),
          gl([10.4, 7.0, 7.85], [10.8, 7.6, 8.15], "steel"),
          gl([11.0, 7.0, 7.85], [11.4, 7.6, 8.15], "steel")]                   # two triggers
    G["doomsday_shotgun"] = (e, {"steel": 0x5a5c62, "steel_dark": 0x2a2a2e, "wood": 0x5a3018, "brass": 0xc8a040,
                                 "leather_dark": 0x1a1410, "edge": 0xb0b0b8})
    # ---- Marksman Rifle (bolt action, scoped) -----------------------------------------------
    e = [gl([-2, 6.0, 7.15], [2, 9.2, 8.85], "wood"),                          # buttstock
         gl([2, 6.7, 7.25], [6, 9.3, 8.75], "wood"),
         gl([-0.5, 9.2, 7.3], [3.5, 9.8, 8.7], "wood"),                        # cheek rest
         gl([-2.3, 5.8, 7.1], [-1.8, 9.4, 8.9], "leather_dark"),               # butt pad
         gl([6, 7.4, 7.3], [12, 9.6, 8.7], "wood"),                            # wrist & stock body
         gl([12, 8.2, 7.4], [22, 9.4, 8.6], "wood"),                           # forestock
         gl([8.5, 9.0, 7.2], [13.5, 10.6, 8.8], "steel_dark")]                 # receiver
    e += tube(13.5, 30, 9.9, 8.0, 0.55, "steel_dark")                          # barrel
    e += [gl([29.3, 10.3, 7.9], [29.8, 11.0, 8.1], "steel"),                   # front sight
          gl([29.6, 9.25, 7.4], [30.4, 10.55, 8.6], "steel_dark"),             # muzzle crown
          gl([9.6, 10.0, 8.8], [10.2, 10.4, 10.0], "steel"),                   # bolt handle
          gl([9.4, 9.8, 9.9], [10.4, 10.6, 10.5], "steel"),                    # bolt knob
          gl([9.2, 6.6, 7.7], [12, 7.0, 8.3], "steel_dark"),                   # trigger guard
          gl([11.6, 6.6, 7.7], [12, 7.6, 8.3], "steel_dark"),
          gl([10.2, 7.0, 7.85], [10.6, 7.8, 8.15], "steel"),
          gl([10.6, 7.0, 7.5], [11.4, 7.5, 8.5], "steel_dark")]                # magazine floorplate
    e += tube(6.5, 15.5, 12.0, 8.0, 0.7, "steel_dark")                         # scope tube
    e += tube(5.5, 7.0, 12.0, 8.0, 1.0, "steel_dark")                          # eyepiece bell
    e += tube(14.5, 16.5, 12.0, 8.0, 1.1, "steel_dark")                        # objective bell
    e += [gl([16.45, 11.2, 7.2], [16.55, 12.8, 8.8], "gem"),                   # lens glint
          gl([8.5, 10.6, 7.6], [9.2, 11.4, 8.4], "steel"),                     # rings
          gl([12.6, 10.6, 7.6], [13.3, 11.4, 8.4], "steel"),
          gl([10.8, 12.7, 7.7], [11.4, 13.1, 8.3], "steel"),                   # turret
          gl([4, 6.9, 7.9], [4.4, 7.3, 8.1], "steel"), gl([20.5, 8.0, 7.9], [20.9, 8.2, 8.1], "steel")]  # swivels
    G["marksman_rifle"] = (e, {"steel": 0x8a8e98, "steel_dark": 0x24262c, "wood": 0x7a4a26, "leather_dark": 0x1a1410,
                               "gem": 0x7ab8ff, "edge": 0xd0d0d8})
    # ---- Scrap SMG ---------------------------------------------------------------------------
    e = [gl([7, 8.2, 7.2], [15, 10.6, 8.8], "steel_dark"),                     # receiver
         gl([15, 8.6, 7.4], [21, 10.2, 8.6], "steel")]                         # barrel shroud
    for x in (15.6, 17.0, 18.4, 19.8):
        e += [gl([x, 9.0, 7.35], [x + 0.6, 9.8, 7.4], "steel_dark"), gl([x, 9.0, 8.6], [x + 0.6, 9.8, 8.65], "steel_dark")]
    e += tube(21, 23, 9.4, 8.0, 0.5, "steel_dark")                             # muzzle
    e += [gl([22.2, 9.0, 7.4], [22.6, 9.8, 8.6], "steel"),                     # compensator slot
          gl([20.4, 10.2, 7.9], [20.8, 11.0, 8.1], "steel"),                   # front sight post
          gl([8, 10.6, 7.85], [8.6, 11.2, 8.15], "steel"),                     # rear sight
          gl([12, 9.6, 6.8], [13.4, 10.0, 7.2], "steel"),                      # cocking handle
          gl([11.0, 4.4, 7.55], [12.4, 8.2, 8.45], "steel_dark", {"angle": 22.5, "axis": "z", "origin": [11.7, 8.2, 8]}),  # magazine
          gl([10.8, 4.0, 7.5], [12.6, 4.5, 8.5], "rust", {"angle": 22.5, "axis": "z", "origin": [11.7, 8.2, 8]}),
          gl([7.6, 4.8, 7.4], [9.2, 8.2, 8.6], "leather_dark", {"angle": -22.5, "axis": "z", "origin": [8.4, 8.2, 8]}),  # pistol grip
          gl([9.4, 7.4, 7.75], [11, 7.8, 8.25], "steel_dark"),                 # trigger guard
          gl([9.9, 7.8, 7.85], [10.3, 8.4, 8.15], "steel"),
          gl([1, 8.6, 7.6], [7, 9.0, 7.9], "steel"), gl([1, 8.6, 8.1], [7, 9.0, 8.4], "steel"),   # wire stock rails
          gl([0.6, 6.6, 7.5], [1.1, 9.0, 8.5], "steel")]                       # butt plate
    G["scrap_smg"] = (e, {"steel": 0x7a6a5a, "steel_dark": 0x2e2a26, "rust": 0x8a4a20, "leather_dark": 0x2a2018,
                          "edge": 0xb0a090})
    return G


# =============================================================================================
# Optimisation: drop faces that are fully covered by a touching element (stacked blade segments,
# grip rings, guard blocks...). Saves a third or more of the quads on every weapon.
# =============================================================================================
_AX = {"east": (0, 1), "west": (0, -1), "up": (1, 1), "down": (1, -1), "south": (2, 1), "north": (2, -1)}


def cull(elements):
    eps = 1e-3
    out = []
    for a in elements:
        faces = dict(a["faces"])
        for name, (ax, sgn) in _AX.items():
            plane = a["to"][ax] if sgn > 0 else a["from"][ax]
            o1, o2 = [i for i in range(3) if i != ax]
            for b in elements:
                if b is a or b.get("rotation") != a.get("rotation"):
                    continue
                bplane = b["from"][ax] if sgn > 0 else b["to"][ax]
                if abs(bplane - plane) > eps:
                    continue
                if all(b["from"][i] <= a["from"][i] + eps and b["to"][i] >= a["to"][i] - eps for i in (o1, o2)):
                    faces.pop(name, None)
                    break
        if faces:
            out.append({**a, "faces": faces})
    return out


def icon_from_model(elements, atlas_img, size=32, rot45=False):
    """A flat side-view icon rendered from a 3D model (used for the guns' inventory icons)."""
    import math as _m
    tex = np.asarray(atlas_img.convert("RGB")).astype(float) / 255
    quads = []
    c, s_ = _m.cos(_m.radians(-45)), _m.sin(_m.radians(-45))
    for e in elements:
        (x1, y1, z1), (x2, y2, z2) = e["from"], e["to"]
        f = e["faces"].get("north") or next(iter(e["faces"].values()))
        u0, v0 = int(f["uv"][0] * 4), int(f["uv"][1] * 4)
        col = tex[v0 + 4:v0 + 12, u0 + 4:u0 + 12].mean((0, 1))
        r = e.get("rotation")
        pts = [(x1, y1), (x2, y1), (x2, y2), (x1, y2)]
        if r and r.get("axis") == "z":
            a = _m.radians(r["angle"]); ox, oy = r["origin"][0], r["origin"][1]
            pts = [(ox + (x - ox) * _m.cos(a) - (y - oy) * _m.sin(a), oy + (x - ox) * _m.sin(a) + (y - oy) * _m.cos(a)) for x, y in pts]
        quads.append((z2, pts, col, y2 - y1))
    allp = np.array([p for q in quads for p in q[1]])
    lo, hi = allp.min(0), allp.max(0)
    sc = (size * 4 - 8) / max(hi - lo)
    from PIL import ImageDraw
    big = Image.new("RGBA", (size * 4, size * 4), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    off = ((size * 4) - (hi - lo) * sc) / 2
    for z, pts, col, h in sorted(quads, key=lambda q: q[0]):
        poly = [((x - lo[0]) * sc + off[0], (hi[1] - y) * sc + off[1]) for x, y in pts]
        d.polygon(poly, fill=tuple(int(v * 255) for v in np.clip(col, 0, 1)) + (255,))
    icon = big.resize((size, size), Image.LANCZOS)
    a = np.asarray(icon).astype(float)
    a[..., 3] = np.where(a[..., 3] > 90, 255, 0)
    return Image.fromarray(a.astype(np.uint8), "RGBA")
