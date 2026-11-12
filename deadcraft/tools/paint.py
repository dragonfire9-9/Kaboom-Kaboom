"""Procedural pixel-art painter for DeadCraft textures (entities, items, blocks, particles, GUI)."""
from __future__ import annotations

import math
import random

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont


# ---------------------------------------------------------------------------------------------
# Colour helpers
# ---------------------------------------------------------------------------------------------
def rgb(hex_color: int):
    return ((hex_color >> 16) & 255, (hex_color >> 8) & 255, hex_color & 255)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3])


def mix(a, b, t):
    return tuple(int(a[i] * (1 - t) + b[i] * t) for i in range(3))


def jitter(c, rnd, amount=10):
    return tuple(max(0, min(255, v + rnd.randint(-amount, amount))) for v in c[:3])


# ---------------------------------------------------------------------------------------------
# Box-UV face painting for entity models
# ---------------------------------------------------------------------------------------------
def faces(c):
    """Return dict face -> (x, y, w, h) in texture space for a box-UV cube."""
    u, v, w, h, d = c.u, c.v, c.w, c.h, c.d
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }


ROLE_BASE = {
    "skin": "base", "head": "base", "cloth": "dark", "bone": "bone", "accent": "accent", "horn": "horn",
    "shell": "accent", "wing": "accent", "metal": "metal", "paint": "accent", "tire": "tire",
    "leather": "leather", "glass": "glass", "glow": "glowc", "hat": "base", "jacket": "dark",
    "sleeve": "base", "pants": "dark", "body": "base", "arm": "base", "leg": "base",
}


class Painter:
    def __init__(self, w, h, palette, seed):
        self.img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        self.glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        self.px = self.img.load()
        self.gx = self.glow.load()
        self.pal = palette
        self.rnd = random.Random(seed)
        self.w, self.h = w, h

    def color_for(self, role):
        return self.pal[ROLE_BASE.get(role, "base")]

    def paint_face(self, rect, base, light, pattern="noise"):
        x0, y0, w, h = rect
        for yy in range(h):
            for xx in range(w):
                x, y = x0 + xx, y0 + yy
                if not (0 <= x < self.w and 0 <= y < self.h):
                    continue
                grad = 1.08 - 0.22 * (yy / max(1, h - 1))
                c = shade(base, light * grad)
                c = jitter(c, self.rnd, 9)
                if pattern == "stripes" and yy % 3 == 0:
                    c = shade(c, 0.85)
                if pattern == "scales" and (xx + (yy // 2) % 2) % 2 == 0:
                    c = shade(c, 0.9)
                if xx in (0, w - 1) or yy in (0, h - 1):
                    c = shade(c, 0.82)
                self.px[x, y] = c + (255,)

    def paint_cube(self, c, role_override=None):
        role = role_override or c.role
        base = self.color_for(role)
        pattern = {"cloth": "stripes", "shell": "scales", "jacket": "stripes", "pants": "stripes"}.get(role, "noise")
        f = faces(c)
        light = {"top": 1.18, "bottom": 0.62, "front": 1.0, "back": 0.88, "left": 0.92, "right": 0.92}
        for name, rect in f.items():
            self.paint_face(rect, base, light[name], pattern)
        if role == "glow":
            for rect in f.values():
                x0, y0, w, h = rect
                for yy in range(h):
                    for xx in range(w):
                        if 0 <= x0 + xx < self.w and 0 <= y0 + yy < self.h:
                            self.gx[x0 + xx, y0 + yy] = self.pal["glow"] + (255,)
        return f

    def splatter(self, rect, color, count, glow=False):
        x0, y0, w, h = rect
        for _ in range(count):
            cx, cy = x0 + self.rnd.randrange(max(1, w)), y0 + self.rnd.randrange(max(1, h))
            for dx, dy in ((0, 0), (1, 0), (0, 1), (-1, 0), (0, -1)):
                if self.rnd.random() < 0.6 or (dx, dy) == (0, 0):
                    x, y = cx + dx, cy + dy
                    if x0 <= x < x0 + w and y0 <= y < y0 + h:
                        self.px[x, y] = jitter(color, self.rnd, 12) + (255,)
                        if glow:
                            self.gx[x, y] = color + (255,)

    def cracks(self, rect, color, glow=True):
        x0, y0, w, h = rect
        if w < 3 or h < 3:
            return
        for _ in range(max(1, (w * h) // 30)):
            x, y = x0 + self.rnd.randrange(w), y0 + self.rnd.randrange(h)
            for _ in range(self.rnd.randint(3, 7)):
                if x0 <= x < x0 + w and y0 <= y < y0 + h:
                    self.px[x, y] = color + (255,)
                    if glow:
                        self.gx[x, y] = color + (255,)
                x += self.rnd.choice((-1, 0, 1))
                y += self.rnd.choice((0, 1, 1))

    def face_features(self, rect, eye, mouth=True, eyes_style="slit"):
        """Eyes and a mouth on a front face."""
        x0, y0, w, h = rect
        ey = y0 + max(1, h // 3)
        ex1 = x0 + max(1, w // 4)
        ex2 = x0 + w - 1 - max(1, w // 4)
        sock = shade(self.pal["base"], 0.25)
        for ex in (ex1, ex2):
            for dx in (-1, 0, 1):
                for dy in (0, 1):
                    x, y = ex + dx, ey + dy
                    if x0 <= x < x0 + w and y0 <= y < y0 + h:
                        self.px[x, y] = sock + (255,)
            pts = [(ex, ey)] if eyes_style == "dot" else [(ex, ey), (ex, ey + 1)] if eyes_style == "tall" else [(ex - 1, ey), (ex, ey)]
            if w <= 4:
                pts = [(ex, ey)]
            for x, y in pts:
                if x0 <= x < x0 + w and y0 <= y < y0 + h:
                    self.px[x, y] = eye + (255,)
                    self.gx[x, y] = eye + (255,)
        if mouth and h >= 5:
            my = y0 + h - 2
            for x in range(x0 + 1 + w // 5, x0 + w - 1 - w // 5):
                self.px[x, my] = (20, 6, 6, 255)
                if self.rnd.random() < 0.4:
                    self.px[x, my - 1] = (220, 210, 190, 255)  # teeth


def mob_palette(mob):
    base, accent, eye = rgb(mob["base"]), rgb(mob["accent"]), rgb(mob["eye"]) if mob["eye"] else (255, 255, 255)
    return {
        "base": base, "accent": accent, "dark": shade(base, 0.6), "bone": mix((225, 215, 195), base, 0.15),
        "horn": mix((60, 50, 45), accent, 0.2), "metal": (90, 92, 98), "tire": (28, 28, 30),
        "leather": (92, 58, 36), "glass": (150, 200, 220), "glowc": accent, "glow": accent, "eye": eye,
    }


def paint_entity(parts, w, h, mob, archetype):
    pal = mob_palette(mob)
    p = Painter(w, h, pal, hash(mob["id"]) & 0xffffffff)
    ability = mob["ability"]
    hostile = mob["kind"] != "PEACEFUL"
    glow_accent = ability in ("CRACKED", "FRACTURE", "STAR_FALL", "SCORCHED", "CHRONOS") or mob["id"] in (
        "stormstag", "lumen_beetle", "lantern_moth", "wisplet", "candlewick_toad")
    for part in parts:
        for c in part.cubes:
            f = p.paint_cube(c)
            if c.role == "head":
                style = "slit" if hostile else "dot"
                if archetype in ("wraith",):
                    style = "tall"
                p.face_features(f["front"], pal["eye"], mouth=hostile, eyes_style=style)
            if hostile and c.role in ("skin", "head", "cloth") and ability not in ("GHOST", "ILLUSION"):
                for name in ("front", "left", "right", "top"):
                    p.splatter(f[name], (120, 10, 10), max(1, (f[name][2] * f[name][3]) // 40))
            if glow_accent and c.role in ("skin", "head", "accent", "cloth", "shell"):
                for name in ("front", "left", "right", "top", "back"):
                    if ability == "STAR_FALL" or mob["id"] in ("stormstag",):
                        p.splatter(f[name], pal["accent"], max(1, (f[name][2] * f[name][3]) // 30), glow=True)
                    else:
                        p.cracks(f[name], pal["accent"], glow=True)
    return p.img, p.glow


def paint_layout(cubes, w, h, palette, seed, features=None):
    """Paint a vanilla-layout skin (player / skeleton / armour)."""
    p = Painter(w, h, palette, seed)
    for c in cubes:
        if c.role in ("hat", "jacket"):
            continue  # transparent overlay layers
        f = p.paint_cube(c)
        if c.role == "head" and features:
            features(p, f)
    return p


# ---------------------------------------------------------------------------------------------
# Pixel-art sprites from ASCII templates
# ---------------------------------------------------------------------------------------------
TEMPLATES = {
    "sword": """
..............oo
.............owo
............owbo
...........owbo.
..........owbo..
.........owbo...
........owbo....
...o...owbo.....
...oo.owbo......
....ogwbo.......
.....ggo........
....ohgg........
...oho.oo.......
..oho...........
.oeo............
.oo.............""",
    "cleaver": """
.......oooooo...
......owwaaaao..
......owaaabbo..
......oaaabbbo..
.....oaaabbbco..
.....oaabbbcco..
....oaabbbccoo..
....oabbbcco....
...ogbbbccoo....
...ogggcoo......
..ohgo..........
..oho...........
.oho............
oeo.............
oo..............
................""",
    "greatsword": """
.............ooo
............owwo
...........owabo
..........owabbo
.........owabbo.
........owabbo..
.......owabbo...
......owabbo....
..oo.owabbo.....
..ogowabbo......
...oggbbo.......
...ohggo........
..ohooggo.......
.oho...oo.......
oeo.............
oo..............""",
    "scythe": """
...ooooooooo....
.oowwaaaaabbo...
owaabbbcoooo....
oabbcoo.....oo..
obco.......oho..
oco.......oho...
oo.......oho....
........oho.....
.......oho......
......oho.......
.....oho........
....oho.........
...oho..........
..oho...........
.oeo............
.oo.............""",
    "staff": """
............ooo.
...........oeeeo
..........oewweo
..........oeeeeo
.........ogoeeo.
........ogho.o..
.......oho......
......oho.......
.....oho........
....oho.........
...oho..........
..oho...........
.oho............
.oho............
..o.............
................""",
    "rod": """
...........o.o..
..........oeoeo.
...........oeo..
..........ogeo..
.........ogho...
........oho.....
.......oho......
......oho.......
.....oho........
....oho.........
...oho..........
..oho...........
.oho............
.oo.............
................
................""",
    "tome": """
................
..ooooooooooo...
.obbbbbbbbbbbo..
.obgggggggggbwo.
.obgaaaaaaagbwo.
.obga.eee.agbwo.
.obga.e.e.agbwo.
.obga.eee.agbwo.
.obgaaaaaaagbwo.
.obgggggggggbwo.
.obbbbbbbbbbbwo.
.occcccccccccwo.
.owwwwwwwwwwwwo.
..ooooooooooooo.
................
................""",
    "chalice": """
................
...oooooooooo...
..oaaaaaaaaaao..
..oaeeeeeeeeao..
..obeeeeeeeebo..
...obeeeeeebo...
....obbbbbbo....
.....obbbbo.....
......obbo......
......obbo......
......obbo......
.....obbbbo.....
....obbbbbbo....
...oooooooooo...
................
................""",
    "key": """
................
.........oooo...
........oaaaao..
.......oao..oao.
.......oa.ee.ao.
.......oao..oao.
......ooaaaaao..
.....obo.oooo...
....obo.........
...obo..........
..obo.o.........
.obooaao........
.oo..oo.........
................
................
................""",
    "sigil": """
.....oooooo.....
...ooaaaaaaoo...
..oaabbbbbbaao..
.oabbo.ee.obbao.
.oabo.eeee.obao.
oabo.ee..ee.obao
oab.ee.oo.ee.bao
oab.e.oeeo.e.bao
oab.e.oeeo.e.bao
oab.ee.oo.ee.bao
oabo.ee..ee.obao
.oabo.eeee.obao.
.oabbo.ee.obbao.
..oaabbbbbbaao..
...ooaaaaaaoo...
.....oooooo.....""",
    "ingot": """
................
................
................
................
.....oooooooo...
....owwaaaaabo..
...owaaaaaabbo..
..owaaaaaabbco..
..oaaaaaabbcco..
..obbbbbbbccoo..
..occcccccco....
..oooooooooo....
................
................
................
................""",
    "gem": """
................
................
......oooo......
.....owwaao.....
....owwaaabo....
...owaaaabbbo...
...oaaaabbbbo...
...obaaabbbco...
....obabbbco....
.....obbbco.....
......obco......
.......oo.......
................
................
................
................""",
    "raw": """
................
................
.......ooo......
.....oobbaoo....
....obbaacbbo...
...obaacbbcabo..
...oacbbaabbco..
..obbaccbbaabo..
..obcbbaabcbo...
...obbacbbbbo...
....oobbccoo....
......oooo......
................
................
................
................""",
    "meat": """
................
................
.....ooooo......
...ooaaaaaoo....
..oaaabbbaaao...
.oaabbbbbbbaao..
.oabbbccbbbbao..
.oabbcccccbbao..
.oaabbccbbbaao..
..oaabbbbbaaoo..
...ooaaaaaoowo..
.....ooooo.owwo.
...........owwo.
............oo..
................
................""",
    "drumstick": """
................
................
......oooo......
....ooaaaaoo....
...oaabbbbaao...
...oabbbcbbao...
...oabbcccbao...
...oaabbcbbao...
....oaabbbao....
.....ooaaoo.....
.......owo......
.......owo......
......owwwo.....
......oo.oo.....
................
................""",
    "bullets": """
................
................
................
....o...o...o...
...owo.owo.owo..
...owo.owo.owo..
...oao.oao.oao..
...oao.oao.oao..
...oao.oao.oao..
...ogo.ogo.ogo..
...ogo.ogo.ogo..
...ooo.ooo.ooo..
................
................
................
................""",
    "shells": """
................
................
....oo....oo....
...oaao..oaao...
...oaao..oaao...
...oaao..oaao...
...oaao..oaao...
...oaao..oaao...
...oggo..oggo...
...oggo..oggo...
...oooo..oooo...
................
................
................
................
................""",
    "bottle": """
................
......oooo......
......ohho......
.......oo.......
......owwo......
.....oweeeo.....
....oweeeeeo....
....oeeeeeeo....
....oeeeeeeo....
....oeeeeeeo....
....oeeeeeeo....
.....oeeeeo.....
......oooo......
................
................
................""",
    "essence": """
................
.......o........
......oeo.......
.....oewo.o.....
....oewwo.eo....
....oewweoeo....
...oewwwweeo....
...oewwwwweo....
...oeewwweeo....
....oeeeeeo.....
.....ooooo......
................
................
................
................
................""",
    "orb": """
................
................
......oooo......
....ooaaaaoo....
...oawwaaabbo...
...oawaaaabbo...
..oaaaaaabbbco..
..oaaaaabbbbco..
..obaaabbbbcco..
...obbbbbbcco...
...oobbbbcco....
.....oooooo.....
................
................
................
................""",
    "horn": """
................
............oo..
...........obo..
..........obao..
.........obao...
........obaao...
.......obaao....
......obaao.....
.....obbaao.....
....obbaao......
...obbbaao......
...occcbo.......
....ocooo.......
................
................
................""",
    "crown": """
................
................
................
..o....o....o...
.oeo..oeo..oeo..
.oao..oao..oao..
.oaao.oaoo.aao..
.oaaaoaaaoaaao..
.oaaaaaaaaaaao..
.obaeabaeabaeo..
.obbbbbbbbbbbo..
.ocowcococowco..
.ooooooooooooo..
................
................
................""",
    "hourglass": """
................
...oooooooooo...
...obbbbbbbbo...
....oeeeeeeo....
....oaeeeeao....
.....oaeeao.....
......oaeo......
.......oo.......
......oaeo......
.....oa.eao.....
....oa..eeao....
....oaeeeeao....
...obbbbbbbbo...
...oooooooooo...
................
................""",
    "heart": """
................
................
...ooo....ooo...
..oaaao..oaaao..
.oawaaaooaaaabo.
.oawaaaaaaaaabo.
.oaaaaaeeaaaabo.
.oaaaaeeeeaaabo.
..oaaaaeeaaabo..
...oaaaaaaabo...
....oaaaaabo....
.....oaaabo.....
......oabo......
.......oo.......
................
................""",
    "eye": """
................
................
................
.....oooooo.....
...ooaaaaaaoo...
..oaaaooooaaao..
.oaaaoeeeeoaaao.
.oaaoeeoweeoaao.
.oaaoeeooeeoaao.
.oaaaoeeeeoaaao.
..oaaaooooaaao..
...ooaaaaaaoo...
.....oooooo.....
................
................
................""",
    "slime": """
................
................
................
......oooo......
....ooaawaoo....
...oaaawwaaao...
..oaaaaaaaaabo..
..oaaaaaaaabbo..
.oaaaaaaaaabbbo.
.obaaaaaaaabbbo.
.obbaaaaaabbbco.
..obbbbbbbbcco..
...oooooooooo...
................
................
................""",
    "scrap": """
................
................
.....ooo........
....oaao.ooo....
...oaabooaabo...
...obbcooabco...
....oco.oocoo...
...oo..ooaao....
..oaao.oaabbo...
..oabbooabbco...
..obco..obco....
...oo....oo.....
................
................
................
................""",
    "helmet": """
................
................
................
....oooooooo....
...oaaaaaaabo...
..oaaaaaaaaabo..
..oaawaaaaaabo..
..oaaaaaaaaabo..
..obboooooobbo..
..obo......obo..
..oco......oco..
..oo........oo..
................
................
................
................""",
    "chestplate": """
................
..ooo......ooo..
.oaaaoooooobaao.
.oaawaaaaaabbao.
.oaaaaaaaaaabao.
..ooaaaaaaabboo.
...oaaaaaaabbo..
...oaaaeeaabbo..
...oaaaaaaabbo..
...oaaaaaaabbo..
...obbbbbbbbco..
...oooooooooo...
................
................
................
................""",
    "leggings": """
................
...oooooooooo...
...oaaaaaaabbo..
...owaaaaaabbo..
...oaaaoooabbo..
...oaabo.oaabo..
...oaabo.oaabo..
...oaabo.oaabo..
...oaabo.oaabo..
...obbco.obbco..
...oooo..oooo...
................
................
................
................
................""",
    "boots": """
................
................
................
................
...oooo..oooo...
...oaao..oaao...
...owao..oaao...
...oaao..oaao...
..oaabo.oaabo...
.oaaabooaaabo...
.obbbbcoobbbco..
.ooooooooooooo..
................
................
................
................""",
    "buggy": """
................
................
................
.....oooooo.....
.....o....o.....
....oooooooo....
...oaaaaaaaabo..
..oaawaaaaaaabo.
..obbbbbbbbbbbo.
..ooeoooooooeoo.
..ohhho...ohhho.
...ooo.....ooo..
................
................
................
................""",
    "skiff": """
................
................
................
................
.........oo.....
........obo.....
..oooooobboooo..
.oaawaaaaaaabbo.
oaaaaaaaaaaabbbo
.obbbbbbbbbbbco.
..oeeoooooeeoo..
...oo.....oo....
................
................
................
................""",
    "grimoire": """
................
..ooooooooooo...
.occcccccccccoo.
.ocbbbbbbbbbcwo.
.ocbaooooobbcwo.
.ocbao.e.oabcwo.
.ocbaoeeeoabcwo.
.ocba.oeo.abcwo.
.ocbaoeeeoabcwo.
.ocbao.e.oabcwo.
.ocbaooooobbcwo.
.ocbbbbbbbbbcwo.
.owwwwwwwwwwwwo.
..ooooooooooooo.
................
................""",
}


def sprite(template, palette, seed=0, size=16):
    rows = [r for r in TEMPLATES[template].strip("\n").split("\n")]
    rows = [(r + "." * size)[:size] for r in rows] + ["." * size] * (size - len(rows))
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    rnd = random.Random(seed)
    for y, row in enumerate(rows[:size]):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            c = palette.get(ch)
            if c is None:
                continue
            if ch in "abcgh":
                c = jitter(c, rnd, 6)
            px[x, y] = tuple(c[:3]) + (255,)
    return img


def item_palette(primary, accent=(170, 140, 60), handle=(70, 45, 30), glow=(255, 60, 40), outline=(18, 14, 16)):
    return {
        "o": outline, "w": shade(primary, 1.5), "a": shade(primary, 1.15), "b": primary, "c": shade(primary, 0.68),
        "g": accent, "h": handle, "e": glow,
    }


# ---------------------------------------------------------------------------------------------
# Blocks
# ---------------------------------------------------------------------------------------------
def noise_tile(base, seed, variance=18, size=16, streaks=False):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (size, size))
    px = img.load()
    for y in range(size):
        for x in range(size):
            c = jitter(base, rnd, variance)
            if streaks and (y + rnd.randint(0, 1)) % 4 == 0:
                c = shade(c, 0.85)
            px[x, y] = c + (255,)
    # soft blotches
    for _ in range(6):
        cx, cy, r = rnd.randrange(size), rnd.randrange(size), rnd.randint(1, 3)
        f = rnd.choice((0.88, 1.1))
        for y in range(cy - r, cy + r + 1):
            for x in range(cx - r, cx + r + 1):
                if (x - cx) ** 2 + (y - cy) ** 2 <= r * r:
                    xx, yy = x % size, y % size
                    px[xx, yy] = shade(px[xx, yy], f) + (255,)
    return img


def ore(base_tile, ore_color, seed, glow=False):
    img = base_tile.copy()
    px = img.load()
    rnd = random.Random(seed)
    for _ in range(5):
        cx, cy = rnd.randint(2, 13), rnd.randint(2, 13)
        for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1), (-1, 0), (0, -1)):
            if rnd.random() < 0.8:
                x, y = cx + dx, cy + dy
                c = shade(ore_color, rnd.choice((0.75, 1.0, 1.0, 1.3)))
                px[x, y] = c + (255,)
        px[cx, cy] = shade(ore_color, 1.6) + (255,)
        px[min(15, cx + 1), min(15, cy + 1)] = shade(ore_color, 0.5) + (255,)
    return img


def storage(color, seed):
    img = noise_tile(color, seed, 8)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 15, 15], outline=shade(color, 0.6) + (255,))
    d.rectangle([1, 1, 14, 14], outline=shade(color, 1.3) + (255,))
    d.line([(2, 2), (5, 2)], fill=shade(color, 1.6) + (255,))
    return img


def log_side(seed):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            v = 22 + rnd.randint(-6, 6) + (8 if x % 4 == 0 else 0)
            c = (v + 6, v, v - 2)
            if rnd.random() < 0.03:
                c = (200, 70, 20)  # embers in the cracks
            px[x, y] = c + (255,)
    return img


def log_top(seed):
    img = Image.new("RGBA", (16, 16), (30, 24, 20, 255))
    d = ImageDraw.Draw(img)
    for r, col in ((7, (40, 32, 26)), (5, (34, 27, 22)), (3, (48, 38, 30)), (1, (60, 30, 18))):
        d.ellipse([8 - r - 0.5, 8 - r - 0.5, 7 + r + 0.5, 7 + r + 0.5], outline=col + (255,))
    d.rectangle([0, 0, 15, 15], outline=(18, 14, 12, 255))
    return img


def portal_frames(n=16):
    frames = Image.new("RGBA", (16, 16 * n))
    for f in range(n):
        img = Image.new("RGBA", (16, 16))
        px = img.load()
        t = f / n * math.tau
        for y in range(16):
            for x in range(16):
                dx, dy = x - 7.5, y - 7.5
                r = math.hypot(dx, dy)
                a = math.atan2(dy, dx)
                v = 0.5 + 0.5 * math.sin(a * 3 + r * 0.9 - t * 2)
                v2 = 0.5 + 0.5 * math.sin(r * 1.4 - t * 3)
                c = mix((40, 0, 60), (190, 40, 255), v * 0.8)
                c = mix(c, (255, 60, 90), v2 * 0.35)
                px[x, y] = c + (200,)
        frames.paste(img, (0, 16 * f))
    return frames


# ---------------------------------------------------------------------------------------------
# Particles
# ---------------------------------------------------------------------------------------------
def particle_frames(kind, count=4, size=8):
    out = []
    rnd = random.Random(kind)
    for i in range(count):
        img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        d = ImageDraw.Draw(img)
        t = i / max(1, count - 1)
        c = size / 2 - 0.5
        if kind == "blood":
            r = 3 - t * 1.5
            d.ellipse([c - r, c - r + 1, c + r, c + r + 1], fill=(150, 8, 8, 255))
            d.point((int(c - 1), int(c)), fill=(230, 40, 40, 255))
        elif kind in ("ember", "soul", "star", "frost", "spark"):
            col = {"ember": (255, 140, 40), "soul": (90, 220, 255), "star": (255, 240, 170),
                   "frost": (200, 240, 255), "spark": (255, 250, 220)}[kind]
            r = (3.2 - t * 2.0) if kind != "spark" else (2.5 - t * 1.5)
            for k in range(int(r * 2), 0, -1):
                a = int(255 * (1 - k / (r * 2 + 0.01)) ** 0.6)
                rr = k / 2
                d.ellipse([c - rr, c - rr, c + rr, c + rr], fill=col + (min(255, a + 60),))
            if kind in ("star", "frost", "spark"):
                L = int(3 - t * 2)
                d.line([(c - L, c), (c + L, c)], fill=col + (255,))
                d.line([(c, c - L), (c, c + L)], fill=col + (255,))
            d.point((int(c), int(c)), fill=(255, 255, 255, 255))
        elif kind == "ash":
            d.rectangle([c - 1, c - 1 + (i % 2), c, c + (i % 2)], fill=(90, 85, 80, 255))
        elif kind == "rune":
            col = (190, 90, 255)
            glyphs = [[(1, 1), (6, 1), (3, 6)], [(1, 6), (3, 1), (6, 6)], [(1, 3), (6, 3), (3, 1), (3, 6)], [(1, 1), (6, 6), (6, 1), (1, 6)]]
            pts = glyphs[i % 4]
            for a, b in zip(pts, pts[1:]):
                d.line([a, b], fill=col + (255,))
            d.point(pts[0], fill=(255, 220, 255, 255))
        out.append(img)
    return out


def slash_frames(count=4, size=32):
    out = []
    for i in range(count):
        img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        d = ImageDraw.Draw(img)
        t = i / (count - 1)
        start = -60 + t * 40
        a = int(255 * (1 - t * 0.7))
        for w, alpha, col in ((5, a // 3, (255, 60, 60)), (3, a // 2, (255, 160, 160)), (1, a, (255, 255, 255))):
            d.arc([3, 3, size - 4, size - 4], start, start + 150 - t * 40, fill=col + (alpha,), width=w)
        out.append(img)
    return out


def muzzle_flash():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for k in range(8):
        a = k * math.tau / 8
        L = 7 if k % 2 == 0 else 4
        d.line([(7.5, 7.5), (7.5 + math.cos(a) * L, 7.5 + math.sin(a) * L)], fill=(255, 210, 120, 255), width=1)
    d.ellipse([4, 4, 11, 11], fill=(255, 230, 160, 230))
    d.ellipse([6, 6, 9, 9], fill=(255, 255, 255, 255))
    return img


def shockwave():
    size = 64
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for w, a in ((8, 50), (5, 110), (2, 230)):
        d.ellipse([4, 4, size - 5, size - 5], outline=(255, 255, 255, a), width=w)
    return img


# ---------------------------------------------------------------------------------------------
# Projectiles and effects
# ---------------------------------------------------------------------------------------------
def glow_orb(core, edge, size=16, spikes=0):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    c = (size - 1) / 2
    for y in range(size):
        for x in range(size):
            r = math.hypot(x - c, y - c) / (size / 2)
            ang = math.atan2(y - c, x - c)
            if spikes:
                r /= 0.75 + 0.25 * abs(math.cos(ang * spikes / 2))
            if r > 1:
                continue
            col = mix(core, edge, min(1, r * 1.2))
            a = int(255 * (1 - r) ** 0.8)
            px[x, y] = col + (a,)
    return img


def effect_icon(color, symbol):
    img = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse([1, 1, 16, 16], fill=shade(color, 0.5) + (255,), outline=(20, 10, 10, 255))
    if symbol == "drop":
        d.polygon([(9, 3), (13, 10), (9, 15), (5, 10)], fill=color + (255,))
        d.point((8, 8), fill=(255, 200, 200, 255))
    elif symbol == "bug":
        d.ellipse([5, 6, 12, 14], fill=color + (255,))
        for y in (8, 10, 12):
            d.line([(3, y), (14, y)], fill=shade(color, 0.6) + (255,))
    else:
        d.ellipse([5, 5, 12, 12], outline=(220, 220, 255, 255))
        d.point((8, 8), fill=(255, 255, 255, 255))
    return img


# ---------------------------------------------------------------------------------------------
# GUI art
# ---------------------------------------------------------------------------------------------
def logo(font_path, width=1024, height=256, text="DEADCRAFT"):
    big = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    font = ImageFont.truetype(font_path, 170)
    d = ImageDraw.Draw(big)
    bbox = d.textbbox((0, 0), text, font=font)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    ox, oy = (width - tw) // 2 - bbox[0], (height - th) // 2 - bbox[1] - 18
    mask = Image.new("L", (width, height), 0)
    ImageDraw.Draw(mask).text((ox, oy), text, font=font, fill=255)
    # blood drips hanging from the letters
    rnd = random.Random(7)
    mk = mask.load()
    drip = Image.new("L", (width, height), 0)
    dd = ImageDraw.Draw(drip)
    for x in range(0, width, 3):
        bottom = None
        for y in range(height - 1, 0, -1):
            if mk[x, y] > 128:
                bottom = y
                break
        if bottom is not None and rnd.random() < 0.12:
            L = rnd.randint(8, 60)
            w = rnd.choice((2, 3, 4))
            dd.rectangle([x - w // 2, bottom - 2, x + w // 2, bottom + L], fill=255)
            dd.ellipse([x - w, bottom + L - w, x + w, bottom + L + w], fill=255)
    full = Image.composite(Image.new("L", (width, height), 255), mask, drip)
    fill_mask = Image.eval(Image.merge("L", [full]), lambda v: v)
    # gradient fill: bright crimson at top to near-black red at the bottom, with grain
    grad = Image.new("RGBA", (width, height))
    gp = grad.load()
    nrnd = np.random.default_rng(3).integers(-18, 18, size=(height, width))
    for y in range(height):
        t = y / height
        base = mix((220, 20, 24), (70, 0, 4), min(1, t * 1.4))
        for x in range(width):
            n = int(nrnd[y, x])
            gp[x, y] = (max(0, min(255, base[0] + n)), max(0, base[1] + n // 3), max(0, base[2] + n // 3), 255)
    out = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    # dark outline + glow
    outline = fill_mask.filter(ImageFilter.MaxFilter(9))
    glow = fill_mask.filter(ImageFilter.GaussianBlur(14))
    out.paste(Image.new("RGBA", (width, height), (120, 0, 0, 255)), (0, 0), Image.eval(glow, lambda v: int(v * 0.55)))
    out.paste(Image.new("RGBA", (width, height), (10, 0, 0, 255)), (0, 0), outline)
    out.paste(grad, (0, 0), fill_mask)
    # highlight sheen on top edges
    sheen = ImageChops_offset(fill_mask, 0, 3)
    hl = Image.eval(Image.merge("L", [fill_mask]), lambda v: v)
    hl = ImageChops_subtract(hl, sheen)
    out.paste(Image.new("RGBA", (width, height), (255, 120, 110, 255)), (0, 0), Image.eval(hl, lambda v: int(v * 0.7)))
    return out


def ImageChops_offset(img, dx, dy):
    from PIL import ImageChops
    return ImageChops.offset(img, dx, dy)


def ImageChops_subtract(a, b):
    from PIL import ImageChops
    return ImageChops.subtract(a, b)


def button(state, w=200, h=20, seed=1):
    rnd = random.Random(seed + {"normal": 0, "hover": 1, "disabled": 2}[state])
    img = Image.new("RGBA", (w, h))
    px = img.load()
    base = {"normal": (34, 28, 30), "hover": (70, 14, 16), "disabled": (20, 18, 19)}[state]
    for y in range(h):
        for x in range(w):
            t = y / (h - 1)
            c = shade(base, 1.25 - 0.45 * t)
            c = jitter(c, rnd, 5)
            px[x, y] = c + (255,)
    d = ImageDraw.Draw(img)
    # cracks
    for _ in range(5):
        x, y = rnd.randrange(w), rnd.randrange(h)
        for _ in range(rnd.randint(4, 12)):
            if 0 <= x < w and 0 <= y < h:
                px[x, y] = shade(base, 0.45) + (255,)
            x += rnd.choice((-1, 1, 1))
            y += rnd.choice((-1, 0, 1))
    border = {"normal": (90, 20, 22), "hover": (230, 40, 40), "disabled": (50, 40, 40)}[state]
    d.rectangle([0, 0, w - 1, h - 1], outline=(8, 4, 5, 255))
    d.rectangle([1, 1, w - 2, h - 2], outline=border + (255,))
    d.line([(2, 2), (w - 3, 2)], fill=shade(border, 1.3) + (140,))
    if state == "hover":
        # blood dripping off the bottom edge
        for x in range(4, w - 4, 7):
            if rnd.random() < 0.5:
                L = rnd.randint(1, 4)
                d.line([(x, h - 3), (x, h - 3 + L)], fill=(150, 0, 0, 255))
    return img


def blood_overlay(w=1024, h=576, seed=11):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # drips from the top edge
    for x in range(0, w, 4):
        if rnd.random() < 0.35:
            L = int(abs(rnd.gauss(0, 1)) * 60) + rnd.randint(4, 20)
            ww = rnd.choice((2, 3, 4, 5))
            col = (rnd.randint(90, 140), 0, 0, 235)
            d.rectangle([x, 0, x + ww, L], fill=col)
            d.ellipse([x - 1, L - 2, x + ww + 1, L + ww + 1], fill=col)
    # splatters in the corners
    for cx, cy in ((0, h), (w, h), (0, 0), (w, 0), (w * 0.15, h * 0.85), (w * 0.88, h * 0.2)):
        for _ in range(70):
            a = rnd.random() * math.tau
            r = abs(rnd.gauss(0, 1)) * 110
            x, y = cx + math.cos(a) * r, cy + math.sin(a) * r
            s = max(1, int(12 - r / 12 + rnd.random() * 4))
            d.ellipse([x - s, y - s, x + s, y + s], fill=(rnd.randint(80, 130), 0, 0, 220))
    return img.filter(ImageFilter.GaussianBlur(0.6))


def skeleton_strip(w=1024, h=256, seed=5):
    """Silhouettes of skeletons and skull piles for the bottom of the title screen."""
    rnd = random.Random(seed)
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    bone = (205, 196, 178, 255)
    dark = (12, 8, 8, 255)

    def skull(x, y, s):
        d.ellipse([x - s, y - s, x + s, y + s * 0.9], fill=bone, outline=dark)
        d.rectangle([x - s * 0.55, y + s * 0.5, x + s * 0.55, y + s * 1.15], fill=bone, outline=dark)
        for ex in (-0.42, 0.42):
            d.ellipse([x + ex * s - s * 0.28, y - s * 0.15, x + ex * s + s * 0.28, y + s * 0.35], fill=dark)
        d.polygon([(x, y + s * 0.35), (x - s * 0.12, y + s * 0.6), (x + s * 0.12, y + s * 0.6)], fill=dark)
        for tx in range(-2, 3):
            d.line([(x + tx * s * 0.18, y + s * 0.75), (x + tx * s * 0.18, y + s * 1.1)], fill=dark, width=1)

    def skeleton(x, base, s, arms_up):
        top = base - s * 7
        skull(x, top, s)
        d.line([(x, top + s * 1.1), (x, top + s * 4.4)], fill=bone, width=max(2, int(s * 0.3)))
        for i in range(5):
            yy = top + s * (1.6 + i * 0.5)
            d.arc([x - s * 1.3, yy - s * 0.4, x + s * 1.3, yy + s * 0.4], 200, 340, fill=bone, width=max(1, int(s * 0.18)))
        d.ellipse([x - s * 1.0, top + s * 4.2, x + s * 1.0, top + s * 5.0], fill=bone, outline=dark)
        for side in (-1, 1):
            sx, sy = x + side * s * 1.2, top + s * 1.5
            if arms_up:
                ex, ey = sx + side * s * 1.2, sy - s * 1.5
                hx, hy = ex + side * s * 0.3, ey - s * 1.6
            else:
                ex, ey = sx + side * s * 0.8, sy + s * 1.6
                hx, hy = ex + side * s * 0.4, ey + s * 1.5
            d.line([(sx, sy), (ex, ey), (hx, hy)], fill=bone, width=max(2, int(s * 0.25)))
            lx = x + side * s * 0.6
            d.line([(lx, top + s * 4.8), (lx + side * s * 0.3, top + s * 6.2), (lx + side * s * 0.2, base)], fill=bone, width=max(2, int(s * 0.28)))

    # back row (darker, smaller)
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    for x in range(20, w, 70):
        skeleton(x + rnd.randint(-15, 15), h - 30, rnd.uniform(9, 12), rnd.random() < 0.5)
    img = Image.eval(img, lambda v: v)
    img = Image.blend(img, img, 1)
    back = img.copy()
    back = Image.eval(back, lambda v: v)
    # dim the back row
    arr = np.array(back).astype(np.float32)
    arr[..., :3] *= 0.45
    back = Image.fromarray(arr.astype(np.uint8))
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for x in range(50, w, 140):
        skeleton(x + rnd.randint(-20, 20), h - 8, rnd.uniform(13, 17), rnd.random() < 0.6)
    # skull piles
    for x in range(0, w, 26):
        skull(x + rnd.randint(-6, 6), h - rnd.randint(6, 22), rnd.uniform(8, 12))
    out = Image.alpha_composite(back, img)
    return out


def static_noise(size=256, seed=9):
    rng = np.random.default_rng(seed)
    v = rng.integers(0, 256, size=(size, size), dtype=np.uint8)
    rgba = np.stack([v, v, v, np.full_like(v, 255)], axis=-1)
    # faint horizontal scanlines
    rgba[::2, :, :3] = (rgba[::2, :, :3] * 0.8).astype(np.uint8)
    return Image.fromarray(rgba, "RGBA")


def vignette(size=256):
    img = Image.new("RGBA", (size, size))
    px = img.load()
    c = (size - 1) / 2
    for y in range(size):
        for x in range(size):
            r = math.hypot((x - c) / c, (y - c) / c) / math.sqrt(2)
            a = int(255 * max(0.0, min(1.0, (r - 0.45) / 0.55)) ** 1.6)
            px[x, y] = (0, 0, 0, a)
    return img


def hud_icons():
    img = Image.new("RGBA", (36, 9), (0, 0, 0, 0))
    heart = sprite("heart", item_palette((200, 20, 30), glow=(255, 120, 120)), 1).resize((9, 9), Image.NEAREST)
    meat = sprite("drumstick", item_palette((170, 90, 50)), 2).resize((9, 9), Image.NEAREST)
    shield = sprite("chestplate", item_palette((150, 150, 160)), 3).resize((9, 9), Image.NEAREST)
    mana = sprite("essence", item_palette((60, 120, 255), glow=(120, 200, 255)), 4).resize((9, 9), Image.NEAREST)
    for i, s in enumerate((heart, meat, shield, mana)):
        img.paste(s, (i * 9, 0), s)
    return img


def mod_icon(font_path, size=256):
    img = Image.new("RGBA", (size, size), (14, 8, 10, 255))
    d = ImageDraw.Draw(img)
    # radial red glow background
    for r in range(size // 2, 0, -2):
        t = r / (size / 2)
        col = mix((120, 0, 6), (14, 8, 10), t)
        d.ellipse([size / 2 - r, size / 2 - r - 10, size / 2 + r, size / 2 + r - 10], fill=col + (255,))
    s = size * 0.27
    x, y = size / 2, size * 0.42
    bone = (225, 215, 195, 255)
    dark = (20, 8, 8, 255)
    d.ellipse([x - s, y - s, x + s, y + s * 0.95], fill=bone, outline=dark, width=3)
    d.rectangle([x - s * 0.6, y + s * 0.55, x + s * 0.6, y + s * 1.25], fill=bone, outline=dark, width=3)
    for ex in (-0.42, 0.42):
        d.ellipse([x + ex * s - s * 0.3, y - s * 0.2, x + ex * s + s * 0.3, y + s * 0.38], fill=dark)
        d.ellipse([x + ex * s - s * 0.1, y - s * 0.02, x + ex * s + s * 0.1, y + s * 0.18], fill=(255, 30, 30, 255))
    d.polygon([(x, y + s * 0.38), (x - s * 0.14, y + s * 0.68), (x + s * 0.14, y + s * 0.68)], fill=dark)
    for tx in range(-3, 4):
        d.line([(x + tx * s * 0.16, y + s * 0.85), (x + tx * s * 0.16, y + s * 1.2)], fill=dark, width=3)
    # blood running from the eyes
    for ex in (-0.42, 0.42):
        d.line([(x + ex * s, y + s * 0.3), (x + ex * s + 2, y + s * 1.3)], fill=(150, 0, 0, 255), width=4)
    font = ImageFont.truetype(font_path, 54)
    tb = d.textbbox((0, 0), "DEADCRAFT", font=font)
    tw = tb[2] - tb[0]
    tx, ty = (size - tw) / 2 - tb[0], size * 0.78
    for dx in (-2, 0, 2):
        for dy in (-2, 0, 2):
            d.text((tx + dx, ty + dy), "DEADCRAFT", font=font, fill=(0, 0, 0, 255))
    d.text((tx, ty), "DEADCRAFT", font=font, fill=(220, 30, 30, 255))
    return img
