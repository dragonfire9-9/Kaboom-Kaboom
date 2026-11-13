"""Generates the title screen's silk background and sparkle particle.

Usage: python3 tools/gen_title_assets.py
"""
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
TEX = ROOT / "src/main/resources/assets/soulbound/textures/gui"


def smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def silk():
    W, H = 1600, 900
    y, x = np.mgrid[0:H, 0:W].astype(np.float32)
    u, v = x / W, y / H
    out = np.zeros((H, W), np.float32)
    # left and right curtains of flowing folds
    for side in (0, 1):
        uu = u if side == 0 else 1 - u
        bend = 0.10 * np.sin(v * 3.1 + 0.6 + side) + 0.05 * np.sin(v * 7.3 + side * 2)
        d = uu + bend * (1.2 - uu)
        folds = np.sin((d * 15 + np.sin(v * 2.2 + side) * 1.2) * np.pi)
        fine = np.sin((d * 41 + np.sin(v * 3.7 + side * 3) * 0.8) * np.pi)
        ridge = ((folds + 1) / 2) ** 2.4 * 0.85 + ((fine + 1) / 2) ** 4 * 0.12
        # fade the curtain toward the centre and soften it vertically
        edge = smooth(0.42, 0.02, d)
        out += ridge * edge
    out *= smooth(0.0, 0.25, v) * 0.35 + 0.65
    out = np.clip(out, 0, 1)
    alpha = (out * 255).astype(np.uint8)
    img = np.zeros((H, W, 4), np.uint8)
    img[..., :3] = 255
    img[..., 3] = alpha
    Image.fromarray(img, "RGBA").save(TEX / "title_silk.png", optimize=True)


def sparkle():
    N, SS = 64, 4
    img = Image.new("RGBA", (N * SS, N * SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = N * SS / 2
    long_r, short_r = c * 0.95, c * 0.16
    pts = []
    import math
    for i in range(8):
        a = i * math.pi / 4 - math.pi / 2
        r = long_r if i % 2 == 0 else short_r
        pts.append((c + r * math.cos(a), c + r * math.sin(a)))
    d.polygon(pts, fill=(255, 255, 255, 255))
    img.resize((N, N), Image.LANCZOS).save(TEX / "icon_sparkle.png")


if __name__ == "__main__":
    silk()
    sparkle()
    print("ok")
