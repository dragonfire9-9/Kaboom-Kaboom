"""GUI art: buttons, HUD icons and the mod icon."""
from __future__ import annotations

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

import icons
import tex
from tex import rgb as H, shade, lerp, fbm, ridged, hashf


def _img(c, a=None):
    a = np.ones(c.shape[:2]) if a is None else a
    return Image.fromarray((np.dstack([np.clip(c, 0, 1), np.clip(a, 0, 1)]) * 255).astype(np.uint8), "RGBA")


NINE_SLICE_BORDER = 5


def button(state, w=200, h=20, seed=1):
    """Engraved, riveted iron plate. Rivets and bevels sit inside the nine-slice border."""
    ys, xs = np.mgrid[0:h, 0:w]
    X, Y = xs.astype(float), ys.astype(float)
    base = {"normal": H(0x34262a), "hover": H(0x62121a), "disabled": H(0x1c1819)}[state]
    grime = fbm(X / 9, Y / 3, seed + 3)
    c = shade(base, 1.22 - 0.42 * (Y / (h - 1)) + 0.18 * (grime - 0.5))
    brush = fbm(X / 14, Y * 1.4, seed + 4, 2)
    c = shade(c, 0.94 + 0.12 * brush)
    stain = fbm(X / 5, Y / 5, seed + 5) > 0.68
    c = np.where(stain[..., None], shade(c, 0.85), c)
    b = NINE_SLICE_BORDER
    # engraved inner frame: dark line with a lit line below/right of it
    frame = ((Y == b - 1) | (X == b - 1)) & (X >= b - 1) & (X <= w - b) & (Y >= b - 1) & (Y <= h - b)
    lit = ((Y == h - b) | (X == w - b)) & (X >= b - 1) & (X <= w - b) & (Y >= b - 1) & (Y <= h - b)
    c = np.where(frame[..., None], shade(base, 0.5), c)
    c = np.where(lit[..., None], shade(base, 1.35), c)
    edge = {"normal": H(0x6a1a1e), "hover": H(0xff3a3a), "disabled": H(0x3a3232)}[state]
    c[0, :], c[-1, :], c[:, 0], c[:, -1] = H(0x070304), H(0x070304), H(0x070304), H(0x070304)
    c[1, 1:-1], c[1:-1, 1] = shade(edge, 1.25), shade(edge, 1.1)
    c[-2, 1:-1], c[1:-1, -2] = shade(edge, 0.6), shade(edge, 0.7)
    for rx, ry in ((2, 2), (w - 4, 2), (2, h - 4), (w - 4, h - 4)):
        c[ry, rx] = shade(H(0x8a8890), 1.3)
        c[ry, rx + 1] = shade(H(0x8a8890), 0.9)
        c[ry + 1, rx] = shade(H(0x8a8890), 0.8)
        c[ry + 1, rx + 1] = shade(H(0x8a8890), 0.5)
    if state == "hover":
        rng = np.random.default_rng(seed)
        for x in range(b, w - b, 3):
            if rng.random() < 0.45:
                L = rng.integers(1, 4)
                for i in range(L):
                    c[h - 3 - i, x] = lerp(H(0x9a0008), H(0x500004), i / 3)
    return _img(c)


def hud_icons():
    sheet = Image.new("RGBA", (36, 9), (0, 0, 0, 0))
    specs = [("heart9", (200, 24, 32), (255, 140, 140)), ("food9", (190, 110, 60), (255, 200, 120)),
             ("armor9", (170, 172, 184), (255, 255, 255)), ("mana9", (60, 120, 255), (160, 220, 255))]
    for i, (t, col, glow) in enumerate(specs):
        sheet.alpha_composite(icons.icon(t, col, glow=glow, handle=(110, 70, 40), size=9), (i * 9, 0))
    return sheet


def mod_icon(font_path, size=256):
    S = size
    ys, xs = np.mgrid[0:S, 0:S]
    X, Y = xs.astype(float), ys.astype(float)
    r = np.hypot(X - S / 2, Y - S * 0.44) / (S / 2)
    bg = lerp(H(0x7a0006), H(0x0c0608), np.clip(r, 0, 1) ** 0.8)
    bg = shade(bg, 0.85 + 0.25 * fbm(X / 18, Y / 18, 4))
    # Skull silhouette as a mask, then light it like a 3D surface from its blurred height
    m = Image.new("L", (S, S), 0)
    d = ImageDraw.Draw(m)
    s = S * 0.29
    cx, cy = S / 2, S * 0.41
    d.ellipse([cx - s, cy - s, cx + s, cy + s * 0.95], fill=255)
    d.rounded_rectangle([cx - s * 0.62, cy + s * 0.5, cx + s * 0.62, cy + s * 1.3], radius=int(s * 0.18), fill=255)
    holes = Image.new("L", (S, S), 0)
    hd = ImageDraw.Draw(holes)
    for ex in (-0.42, 0.42):
        hd.ellipse([cx + ex * s - s * 0.3, cy - s * 0.12, cx + ex * s + s * 0.3, cy + s * 0.42], fill=255)
    hd.polygon([(cx, cy + s * 0.45), (cx - s * 0.14, cy + s * 0.75), (cx + s * 0.14, cy + s * 0.75)], fill=255)
    for k in range(-3, 4):
        hd.rectangle([cx + k * s * 0.17 - 1.5, cy + s * 0.95, cx + k * s * 0.17 + 1.5, cy + s * 1.28], fill=255)
    mask = np.asarray(m, float) / 255
    hole = np.asarray(holes, float) / 255
    solid = np.clip(mask - hole, 0, 1)
    height = np.asarray(Image.fromarray((solid * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(S / 40)), float) / 255
    gy, gx = np.gradient(height)
    light = np.clip(0.75 - (gx + gy) * S * 0.09, 0.25, 1.4)
    bone = lerp(H(0xe8dcc0), H(0xb8a888), fbm(X / 10, Y / 10, 7))
    crack = (ridged(X / 22, Y / 22, 9) > 0.965) & (Y < cy)
    bone = np.where(crack[..., None], H(0x5a4a3a), bone)
    skull = shade(bone, light)
    c = np.where((solid > 0.5)[..., None], skull, bg)
    # sockets: deep black with glowing red eyes and a bloom
    c = np.where((hole > 0.5)[..., None], H(0x0a0405), c)
    eyes = Image.new("L", (S, S), 0)
    ed = ImageDraw.Draw(eyes)
    for ex in (-0.42, 0.42):
        ed.ellipse([cx + ex * s - s * 0.1, cy + s * 0.05, cx + ex * s + s * 0.1, cy + s * 0.25], fill=255)
    e = np.asarray(eyes, float) / 255
    bloom = np.asarray(eyes.filter(ImageFilter.GaussianBlur(S / 30)), float) / 255
    c = lerp(c, H(0xff2020), np.clip(bloom * 1.6, 0, 1) * 0.8)
    c = np.where((e > 0.5)[..., None], H(0xffd0c0), c)
    # blood running from the eyes
    for ex in (-0.42, 0.42):
        x0 = int(cx + ex * s)
        for yy in range(int(cy + s * 0.3), int(cy + s * (1.2 if ex < 0 else 1.0))):
            for dx in range(-2, 3):
                c[yy, x0 + dx + int((yy - cy) * 0.02)] = lerp(H(0xb00010), H(0x500004), (yy - cy) / (s * 1.2))
    vign = np.clip((np.hypot(X - S / 2, Y - S / 2) / (S * 0.72)) ** 3, 0, 1)
    c = shade(c, 1 - 0.75 * vign)
    img = _img(c)
    dr = ImageDraw.Draw(img)
    font = ImageFont.truetype(font_path, int(S * 0.21))
    tb = dr.textbbox((0, 0), "DEADCRAFT", font=font)
    tx, ty = (S - (tb[2] - tb[0])) / 2 - tb[0], S * 0.76
    dr.text((tx, ty), "DEADCRAFT", font=font, fill=(220, 26, 30, 255), stroke_width=max(2, S // 90), stroke_fill=(10, 0, 0, 255))
    return img
