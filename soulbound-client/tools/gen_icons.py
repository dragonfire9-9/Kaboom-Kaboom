"""Generates SoulBound's interface icons as one consistent line-icon family.

All icons are drawn on a 128px grid with the same stroke weight, round caps and joins and
~14px of padding, supersampled 4x, white on transparent. The client resamples them to the
exact on-screen size.

Usage: python3 tools/gen_icons.py
"""
import math
from pathlib import Path
from PIL import Image, ImageDraw

TEX = Path(__file__).resolve().parent.parent / "src/main/resources/assets/soulbound/textures/gui"
TEX.mkdir(parents=True, exist_ok=True)

N, SS = 128, 4
STROKE = 10
W = (255, 255, 255, 255)


class Icon:
    def __init__(self, name):
        self.name = name
        self.img = Image.new("RGBA", (N * SS, N * SS), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.img)

    def p(self, x, y):
        return (x * SS, y * SS)

    def dot(self, x, y, r):
        self.d.ellipse([(x - r) * SS, (y - r) * SS, (x + r) * SS, (y + r) * SS], fill=W)

    def line(self, pts, w=STROKE):
        self.d.line([self.p(x, y) for x, y in pts], fill=W, width=int(w * SS), joint="curve")
        for x, y in pts:  # round caps and joins
            self.dot(x, y, w / 2)

    def poly(self, pts, w=STROKE):
        self.line(list(pts) + [pts[0]], w)

    def circle(self, cx, cy, r, w=STROKE):
        self.d.ellipse([(cx - r) * SS, (cy - r) * SS, (cx + r) * SS, (cy + r) * SS], outline=W, width=int(w * SS))

    def arc(self, cx, cy, r, a0, a1, w=STROKE, steps=48):
        pts = [(cx + r * math.cos(math.radians(a0 + (a1 - a0) * i / steps)),
                cy + r * math.sin(math.radians(a0 + (a1 - a0) * i / steps))) for i in range(steps + 1)]
        self.line(pts, w)

    def rrect(self, x0, y0, x1, y1, r, w=STROKE):
        self.d.rounded_rectangle([x0 * SS, y0 * SS, x1 * SS, y1 * SS], radius=r * SS, outline=W, width=int(w * SS))

    def fill_rrect(self, x0, y0, x1, y1, r):
        self.d.rounded_rectangle([x0 * SS, y0 * SS, x1 * SS, y1 * SS], radius=r * SS, fill=W)

    def fill_poly(self, pts):
        self.d.polygon([self.p(x, y) for x, y in pts], fill=W)

    def save(self):
        self.img.resize((N, N), Image.LANCZOS).save(TEX / f"{self.name}.png")


def make(name):
    def deco(fn):
        i = Icon(name)
        fn(i)
        i.save()
        return fn
    return deco


# ------------------------------------------------------------------ categories
@make("icon_combat")
def _(i):  # crossed swords: blades, cross-guards and pommels
    for flip in (False, True):
        def X(x):
            return 128 - x if flip else x
        i.line([(X(20), 20), (X(84), 84)], 9)             # blade
        i.line([(X(66), 98), (X(98), 66)], 10)            # cross-guard
        i.line([(X(88), 88), (X(104), 104)], 11)          # grip
        i.dot(X(108), 108, 8)                             # pommel


@make("icon_visual")
def _(i):  # eye
    pts_top = [(14 + t * 100 / 40, 64 - 34 * math.sin(math.pi * t / 40)) for t in range(41)]
    pts_bot = [(14 + t * 100 / 40, 64 + 34 * math.sin(math.pi * t / 40)) for t in range(41)]
    i.line(pts_top)
    i.line(pts_bot)
    i.circle(64, 64, 16)
    i.dot(64, 64, 6)


@make("icon_util")
def _(i):  # open-ended wrench
    i.line([(30, 98), (70, 58)], 15)
    i.arc(84, 44, 22, 100, 350, 12)
    i.dot(30, 98, 9)


@make("icon_movement")
def _(i):  # wind / speed lines
    i.line([(14, 44), (82, 44)])
    i.arc(82, 30, 14, 90, -180 + 360 - 180 + 90 + 90, STROKE)
    i.line([(14, 70), (100, 70)])
    i.arc(100, 84, 14, -90, 180, STROKE)
    i.line([(30, 96), (64, 96)])


@make("icon_misc")
def _(i):  # four rounded tiles, one a circle
    i.rrect(16, 16, 56, 56, 12)
    i.rrect(72, 16, 112, 56, 12)
    i.rrect(16, 72, 56, 112, 12)
    i.circle(92, 92, 20)


@make("icon_bots")
def _(i):  # robot head
    i.rrect(22, 42, 106, 108, 18, 9)
    i.line([(64, 42), (64, 24)], 9)
    i.dot(64, 19, 8)
    i.dot(48, 70, 8)
    i.dot(80, 70, 8)
    i.line([(50, 90), (78, 90)], 8)
    i.line([(12, 66), (12, 84)], 9)
    i.line([(116, 66), (116, 84)], 9)


@make("icon_settings")
def _(i):  # sliders
    for y, k in ((32, 84), (64, 40), (96, 72)):
        i.line([(16, y), (k - 14, y)])
        i.line([(k + 14, y), (112, y)])
        i.circle(k, y, 12, 9)


@make("icon_theme")
def _(i):  # artist's palette with a thumb hole and paint dots
    i.d.ellipse([12 * SS, 18 * SS, 116 * SS, 110 * SS], outline=W, width=STROKE * SS)
    i.d.ellipse([70 * SS, 70 * SS, 94 * SS, 94 * SS], fill=W)
    i.d.ellipse([75 * SS, 75 * SS, 89 * SS, 89 * SS], fill=(0, 0, 0, 0))
    for cx, cy in ((42, 48), (66, 38), (90, 50), (38, 74)):
        i.dot(cx, cy, 8)


# ------------------------------------------------------------------ actions and objects
@make("icon_search")
def _(i):
    i.circle(56, 56, 34)
    i.line([(82, 82), (110, 110)], 12)


@make("icon_close")
def _(i):
    i.line([(30, 30), (98, 98)], 12)
    i.line([(98, 30), (30, 98)], 12)


@make("icon_gear")
def _(i):  # cog: solid teeth around a ring with a hollow hub
    teeth = 8
    for k in range(teeth):
        a = k * 2 * math.pi / teeth
        x0, y0 = 64 + 40 * math.cos(a), 64 + 40 * math.sin(a)
        x1, y1 = 64 + 52 * math.cos(a), 64 + 52 * math.sin(a)
        i.line([(x0, y0), (x1, y1)], 20)
    i.d.ellipse([(64 - 42) * SS, (64 - 42) * SS, (64 + 42) * SS, (64 + 42) * SS], fill=W)
    i.d.ellipse([(64 - 16) * SS, (64 - 16) * SS, (64 + 16) * SS, (64 + 16) * SS], fill=(0, 0, 0, 0))


@make("icon_chevron")
def _(i):
    i.line([(30, 46), (64, 82), (98, 46)], 13)


@make("icon_keyboard")
def _(i):
    i.rrect(10, 30, 118, 98, 14, 9)
    for y in (50, 66):
        for x in range(30, 101, 17):
            i.dot(x, y, 5)
    i.line([(42, 82), (86, 82)], 8)


@make("icon_check")
def _(i):
    i.line([(24, 66), (52, 94), (106, 36)], 16)


@make("icon_sword")
def _(i):
    i.line([(30, 98), (100, 28)], 12)
    i.line([(100, 28), (108, 20)], 8)
    i.line([(20, 78), (50, 108)], 11)
    i.line([(36, 92), (16, 112)], 11)


@make("icon_crystal")
def _(i):
    i.poly([(64, 10), (106, 50), (64, 118), (22, 50)], 9)
    i.line([(22, 50), (106, 50)], 7)
    i.line([(44, 50), (64, 118), (84, 50)], 7)
    i.line([(44, 50), (64, 10), (84, 50)], 7)


@make("icon_bow")
def _(i):  # bow with string and a nocked arrow
    i.arc(14, 114, 92, -82, -8, 10)
    i.line([(26, 22), (106, 102)], 5)
    i.line([(30, 98), (100, 28)], 8)
    i.fill_poly([(108, 20), (86, 26), (102, 42)])
    i.line([(30, 98), (18, 96)], 6)
    i.line([(30, 98), (32, 110)], 6)


@make("icon_build")
def _(i):  # hammer
    i.line([(26, 104), (74, 56)], 13)
    i.fill_poly([(58, 40), (88, 10), (118, 40), (88, 70)])
    i.d.polygon([((88 + 10) * SS, (40 - 10) * SS), ((88 + 20) * SS, 40 * SS), ((88 + 10) * SS, (40 + 10) * SS)], fill=(0, 0, 0, 0))


@make("icon_target")
def _(i):
    i.circle(64, 64, 44)
    i.circle(64, 64, 20, 9)
    i.dot(64, 64, 6)
    for a, b in (((64, 4), (64, 20)), ((64, 108), (64, 124)), ((4, 64), (20, 64)), ((108, 64), (124, 64))):
        i.line([a, b], 9)


@make("icon_folder")
def _(i):
    i.line([(14, 40), (14, 104), (114, 104), (114, 46), (62, 46), (52, 32), (14, 32), (14, 40)])
    i.line([(14, 56), (114, 56)], 7)


@make("icon_cube")
def _(i):
    top = [(64, 14), (110, 38), (64, 62), (18, 38)]
    i.poly(top, 9)
    i.line([(18, 38), (18, 90), (64, 114), (110, 90), (110, 38)], 9)
    i.line([(64, 62), (64, 114)], 9)


@make("icon_play")
def _(i):
    i.poly([(38, 22), (104, 64), (38, 106)], 11)


@make("icon_globe")
def _(i):
    i.circle(64, 64, 50, 9)
    i.d.ellipse([42 * SS, 14 * SS, 86 * SS, 114 * SS], outline=W, width=8 * SS)
    i.line([(14, 64), (114, 64)], 8)
    i.line([(24, 40), (104, 40)], 6)
    i.line([(24, 88), (104, 88)], 6)


@make("icon_realms")
def _(i):
    i.poly([(64, 10), (112, 38), (112, 90), (64, 118), (16, 90), (16, 38)], 9)
    i.line([(16, 38), (64, 66), (112, 38)], 8)
    i.line([(64, 66), (64, 118)], 8)


@make("icon_power")
def _(i):
    i.arc(64, 68, 42, -50, 230, 11)
    i.line([(64, 12), (64, 58)], 12)


@make("icon_sparkle")
def _(i):  # filled four-point star (used for particles)
    c = 64
    pts = []
    for k in range(8):
        a = k * math.pi / 4 - math.pi / 2
        r = 60 if k % 2 == 0 else 11
        pts.append((c + r * math.cos(a), c + r * math.sin(a)))
    i.fill_poly(pts)


if __name__ == "__main__":
    print("ok")
