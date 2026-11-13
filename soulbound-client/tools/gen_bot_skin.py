"""Generates the Sword PvP Bot skin (64x64, classic arms): a soul knight in dark steel.

Usage: python3 tools/gen_bot_skin.py
"""
from pathlib import Path
import random
from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/soulbound/textures/entity/sword_bot.png"
OUT.parent.mkdir(parents=True, exist_ok=True)
img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
px = img.load()
rnd = random.Random(7)

STEEL = (52, 58, 84)
STEEL_HI = (86, 96, 132)
STEEL_LO = (32, 36, 56)
CLOTH = (24, 22, 38)
CLOTH_HI = (40, 36, 62)
SOUL = (126, 196, 255)
SOUL_HI = (210, 240, 255)
SOUL_LO = (82, 112, 230)
GOLD = (150, 130, 255)


def jitter(c, amt=6):
    j = rnd.randint(-amt, amt)
    return tuple(max(0, min(255, v + j)) for v in c) + (255,)


def fill(x, y, w, h, c, amt=6):
    for i in range(x, x + w):
        for j in range(y, y + h):
            px[i, j] = jitter(c, amt)


def box(u, v, w, h, d, faces):
    """faces: dict(top, bottom, right, front, left, back) -> callable(x, y, w, h)."""
    rects = {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }
    for name, (x, y, ww, hh) in rects.items():
        faces.get(name, faces.get("default"))(x, y, ww, hh)


def plate(x, y, w, h):
    fill(x, y, w, h, STEEL)
    for i in range(x, x + w):
        px[i, y] = jitter(STEEL_HI, 4)
    for j in range(y, y + h):
        px[x, j] = jitter(STEEL_HI, 4)
        px[x + w - 1, j] = jitter(STEEL_LO, 4)


# ---------------------------------------------------------------- head (helmet)
def helm_front(x, y, w, h):
    plate(x, y, w, h)
    # visor slit with soul glow
    for i in range(x + 1, x + w - 1):
        px[i, y + 3] = jitter(SOUL_LO, 4)
        px[i, y + 4] = jitter(SOUL, 6)
    px[x + 2, y + 4] = SOUL_HI + (255,)
    px[x + 5, y + 4] = SOUL_HI + (255,)
    # breathing grille
    for i in (x + 2, x + 4, x + 5):
        px[i, y + 6] = jitter(STEEL_LO, 3)
    # nose ridge
    for j in range(y, y + 3):
        px[x + 3, j] = jitter(STEEL_HI, 4)
        px[x + 4, j] = jitter(STEEL_HI, 4)


def helm_top(x, y, w, h):
    plate(x, y, w, h)
    for j in range(y, y + h):  # crest
        px[x + 3, j] = jitter(SOUL_LO, 6)
        px[x + 4, j] = jitter(SOUL, 6)


box(0, 0, 8, 8, 8, {"front": helm_front, "top": helm_top, "default": plate})


# hat layer: glowing crest plume on top and back
def hat_top(x, y, w, h):
    for j in range(y + 1, y + h - 1):
        px[x + 3, j] = SOUL + (230,)
        px[x + 4, j] = SOUL_HI + (230,)


def hat_back(x, y, w, h):
    for j in range(y, y + 3):
        px[x + 3, j] = SOUL + (200,)
        px[x + 4, j] = SOUL_LO + (200,)


def nothing(x, y, w, h):
    pass


box(32, 0, 8, 8, 8, {"top": hat_top, "back": hat_back, "default": nothing})


# ---------------------------------------------------------------- body (chestplate)
def chest_front(x, y, w, h):
    plate(x, y, w, h)
    # belt
    for i in range(x, x + w):
        px[i, y + 9] = jitter(CLOTH_HI, 3)
        px[i, y + 10] = jitter(CLOTH, 3)
    px[x + 3, y + 9] = GOLD + (255,)
    px[x + 4, y + 9] = GOLD + (255,)
    # soul flame emblem (S-ish)
    flame = [(4, 1), (3, 2), (4, 2), (3, 3), (2, 4), (3, 4), (4, 5), (5, 5), (4, 6), (3, 7), (4, 7)]
    for fx, fy in flame:
        px[x + fx, y + fy] = jitter(SOUL, 8)
    px[x + 4, y + 2] = SOUL_HI + (255,)
    px[x + 4, y + 5] = SOUL_HI + (255,)


def chest_back(x, y, w, h):
    plate(x, y, w, h)
    for i in range(x, x + w):
        px[i, y + 9] = jitter(CLOTH_HI, 3)
        px[i, y + 10] = jitter(CLOTH, 3)
    for j in range(y + 1, y + 8):  # spine
        px[x + 3, j] = jitter(STEEL_LO, 3)
        px[x + 4, j] = jitter(STEEL_LO, 3)


def chest_side(x, y, w, h):
    plate(x, y, w, h)
    for i in range(x, x + w):
        px[i, y + 9] = jitter(CLOTH_HI, 3)
        px[i, y + 10] = jitter(CLOTH, 3)


box(16, 16, 8, 12, 4, {"front": chest_front, "back": chest_back, "default": chest_side})


# ---------------------------------------------------------------- arms
def arm(x, y, w, h):
    # pauldron (top 4 rows), cloth sleeve, gauntlet (bottom 4 rows)
    fill(x, y, w, h, CLOTH, 4)
    plate(x, y, w, min(4, h))
    if h > 4:
        plate(x, y + h - 4, w, 4)
        for i in range(x, x + w):
            px[i, y + h - 4] = jitter(SOUL_LO, 6)  # glowing cuff


def arm_top(x, y, w, h):
    plate(x, y, w, h)


box(40, 16, 4, 12, 4, {"top": arm_top, "bottom": arm_top, "default": arm})
box(32, 48, 4, 12, 4, {"top": arm_top, "bottom": arm_top, "default": arm})


# ---------------------------------------------------------------- legs
def leg(x, y, w, h):
    fill(x, y, w, h, CLOTH, 4)
    plate(x, y + 3, w, 5)          # greave / knee
    px[x + w // 2, y + 5] = jitter(SOUL, 6)
    fill(x, y + h - 3, w, 3, STEEL_LO, 4)  # sabatons


def leg_cap(x, y, w, h):
    fill(x, y, w, h, STEEL_LO, 4)


box(0, 16, 4, 12, 4, {"top": leg_cap, "bottom": leg_cap, "default": leg})
box(16, 48, 4, 12, 4, {"top": leg_cap, "bottom": leg_cap, "default": leg})

img.save(OUT)
print("wrote", OUT)
