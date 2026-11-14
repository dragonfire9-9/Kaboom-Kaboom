"""Generates the bot skins (64x64, classic arms) as hand-designed pixel art.

  sword_bot   - "Soulguard": obsidian-navy plate, silver trim, soul-blue T-visor and soul-flame crest
  crystal_bot - "Prism Knight": deep violet plate, rose-gold trim, magenta visor and crystal growths

Each face is authored as a character map. Side and back faces are shaded one tone darker,
top faces one tone lighter, so the model reads with consistent top-down lighting.

Usage: python3 tools/gen_bot_skin.py
"""
from pathlib import Path
from PIL import Image

DIR = Path(__file__).resolve().parent.parent / "src/main/resources/assets/soulbound/textures/entity"
DIR.mkdir(parents=True, exist_ok=True)


def hexc(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


PALETTES = {
    "sword_bot": {
        "a": "#101421", "b": "#191f33", "c": "#242c46", "d": "#313c60", "e": "#43527e",
        "s": "#4a5470", "t": "#7c87a9", "T": "#c0c9e3",
        "k": "#06070c", "m": "#0f121d", "M": "#202740",
        "n": "#1b3888", "B": "#2a55c2",
        "g": "#3c70ff", "G": "#86b6ff", "W": "#eef6ff",
    },
    "uhc_bot": {
        "a": "#1a1b1f", "b": "#2a2c33", "c": "#3d4049", "d": "#565a66", "e": "#767b8a",
        "s": "#7a5a1e", "t": "#c99a35", "T": "#f2d27a",
        "k": "#0a0a0c", "m": "#141518", "M": "#26282e",
        "n": "#6e1414", "B": "#a32222",
        "g": "#d1342e", "G": "#ff6a52", "W": "#ffe2d6",
    },
    "builder_bot": {
        "a": "#0f1a1c", "b": "#16282b", "c": "#20393d", "d": "#2c4f54", "e": "#3e6b70",
        "s": "#7a4a2a", "t": "#c07a4a", "T": "#f0b07c",
        "k": "#050a0b", "m": "#0d1517", "M": "#1c2a2d",
        "n": "#b5741a", "B": "#e9a23a",
        "g": "#2fc4b2", "G": "#7cf2e0", "W": "#e6fffb",
    },
    "crystal_bot": {
        "a": "#160c20", "b": "#211333", "c": "#2f1b49", "d": "#402664", "e": "#573585",
        "s": "#6a4550", "t": "#b07a70", "T": "#efc1a6",
        "k": "#07030c", "m": "#13091c", "M": "#261535",
        "n": "#5a1c72", "B": "#8c2ea6",
        "g": "#c42ca8", "G": "#ff66d9", "W": "#fff0fb",
    },
    "cart_bot": {
        "a": "#121212", "b": "#1c1c1e", "c": "#29292d", "d": "#38383e", "e": "#4c4c55",
        "s": "#6b6b72", "t": "#a9a9b2", "T": "#e6e6ec",
        "k": "#060606", "m": "#101012", "M": "#232327",
        "n": "#8c1414", "B": "#d02a22",
        "g": "#ff6a1a", "G": "#ffb347", "W": "#fff3d6",
    },
}

ARMOR = "abcde"


def shift(ch, by):
    """Shift an armour tone lighter (+) or darker (-); other materials are unchanged."""
    if ch in ARMOR:
        return ARMOR[max(0, min(4, ARMOR.index(ch) + by))]
    return ch


class Painter:
    def __init__(self, pal):
        self.img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        self.px = self.img.load()
        self.pal = {k: hexc(v) for k, v in pal.items()}

    def face(self, x, y, rows, tone=0, mirror=False):
        for j, row in enumerate(rows):
            if mirror:
                row = row[::-1]
            for i, ch in enumerate(row):
                if ch == ".":
                    continue
                self.px[x + i, y + j] = self.pal[shift(ch, tone)]

    def fill(self, x, y, w, h, ch, tone=0):
        self.face(x, y, [ch * w] * h, tone)

    def box(self, u, v, w, h, d, front, side=None, back=None, top=None, bottom=None, side_tone=-1):
        """Paint a cuboid in the standard skin UV layout from per-face maps."""
        side = side or front
        back = back or side
        self.face(u + d, v + d, front)                                   # front
        self.face(u, v + d, side, side_tone)                             # right
        self.face(u + d + w, v + d, side, side_tone, mirror=True)        # left
        self.face(u + 2 * d + w, v + d, back, -1)                        # back
        if top:
            self.face(u + d, v, top, 1)
        if bottom:
            self.face(u + d + w, v, bottom, -1)


# ----------------------------------------------------------------------------------------- shared maps
HEAD_SIDE = ["abccccba", "bcddddcb", "sTTTTTTs", "bcdkkdcb", "bcdkkdcb", "bcddddcb", "bccddccb", "abssssba"]
HEAD_BACK = ["abccccba", "bcddddcb", "sTTTTTTs", "bcddddcb", "bssssssb", "bcddddcb", "bssssssb", "abccccba"]
HEAD_TOP = ["abcTTcba", "bcdTTdcb", "cdeTTedc", "cdeTTedc", "cdeTTedc", "cdeTTedc", "bcdTTdcb", "abcTTcba"]
HEAD_BOTTOM = ["aaaaaaaa"] * 8

BODY_SIDE = ["sTTs", "bddb", "bcdb", "bcdb", "bcdb", "bccb", "abba", "kssk", "kmmk", "bnnb", "bnnb", "anna"]
BODY_BACK = ["sTTTTTTs", "bcdsscdb", "bcdsscdb", "bcdsscdb", "bcdsscdb", "bccssccb", "abcsscba",
             "kssTTssk", "kmmmmmmk", "bnBBBBnb", "bnBBBBnb", "anBBBBna"]
BODY_TOP = ["sTTTTTTs", "cdeeeedc", "cdeeeedc", "sTTTTTTs"]
BODY_BOTTOM = ["annnnnna"] * 4

ARM = ["bdeb", "cddc", "cddc", "sTTs", "mMmM", "MmMm", "mMmM", "MmMm", "gGGg", "bdeb", "bcdb", "abba"]
ARM_TOP = ["cddc", "deed", "deed", "cddc"]
ARM_BOTTOM = ["abba", "bccb", "bccb", "abba"]

LEG_FRONT = ["bccb", "bdeb", "sTTs", "kmmk", "kMMk", "sTTs", "cGgc", "bddb", "bdeb", "bddb", "abba", "kaak"]
LEG_SIDE = ["bccb", "bdcb", "sTTs", "kmmk", "kMMk", "sTTs", "cddc", "bddb", "bdcb", "bddb", "abba", "kaak"]
LEG_TOP = ["bccb", "cddc", "cddc", "bccb"]
LEG_BOTTOM = ["kaak", "aaaa", "aaaa", "kaak"]


def common(p, head_front, body_front):
    p.box(0, 0, 8, 8, 8, head_front, HEAD_SIDE, HEAD_BACK, HEAD_TOP, HEAD_BOTTOM)
    p.box(16, 16, 8, 12, 4, body_front, BODY_SIDE, BODY_BACK, BODY_TOP, BODY_BOTTOM)
    for u, v in ((40, 16), (32, 48)):  # right arm, left arm
        p.box(u, v, 4, 12, 4, ARM, ARM, ARM, ARM_TOP, ARM_BOTTOM)
    for u, v in ((0, 16), (16, 48)):   # right leg, left leg
        p.box(u, v, 4, 12, 4, LEG_FRONT, LEG_SIDE, LEG_SIDE, LEG_TOP, LEG_BOTTOM)


def soulguard(p):
    head_front = [
        "abccccba",
        "bcdeedcb",
        "sTTTTTTs",
        "kgWGGWgk",   # visor slit, eyes brightest
        "bckGGkcb",   # T-slit drops down the centre
        "bcdkkdcb",
        "bcdssdcb",
        "abssssba",
    ]
    body_front = [
        "sTTTTTTs",   # collar
        "bcdeedcb",
        "bcdgGdcb",   # soul-flame emblem
        "bcdGWdcb",
        "bcgGGgcb",
        "bcdgGdcb",
        "abcddcba",
        "kssTTssk",   # belt and buckle
        "kmmssmmk",
        "bcBBBBcb",   # tabard
        "bcBnnBcb",
        "abBnnBba",
    ]
    common(p, head_front, body_front)
    # --- overlay layer: silver crest fin running front-to-back, and raised pauldron rims
    hat_top = ["...TT...", "...TT...", "...Tt...", "...Tt...", "...tT...", "...tT...", "...TT...", "...TT..."]
    p.face(40, 0, hat_top)
    p.face(40, 8, ["...TT..."] + ["........"] * 7)                    # crest lip over the brow
    p.face(56, 8, ["...TT...", "...tt...", "...ss..."] + ["........"] * 5)  # trailing down the back
    for u, v in ((40, 32), (48, 48)):  # right sleeve, left sleeve: pauldron rim
        p.face(u + 4, v, ["TTTT", "t..t", "t..t", "TTTT"])            # top
        for fx in (u, u + 4, u + 8, u + 12):
            p.face(fx, v + 4, ["TTTT", "e..e", "dTTd"])


def prism_knight(p):
    head_front = [
        "abccccba",
        "bcdeedcb",
        "sTTTTTTs",
        "kGWdeWGk",   # twin eye slits either side of a raised nose guard
        "bcgdegcb",
        "bcdGGdcb",   # crystal set in the faceplate
        "bcdssdcb",
        "abssssba",
    ]
    body_front = [
        "sTTTTTTs",
        "bcdeedcb",
        "bcdGgdcb",   # faceted gem emblem
        "bcGWGgcb",
        "bcdGgdcb",
        "bcdgddcb",
        "abcddcba",
        "kssTTssk",
        "kmmssmmk",
        "bcBBBBcb",
        "bcBnnBcb",
        "abBnnBba",
    ]
    common(p, head_front, body_front)
    # --- overlay layer: crystal crown and shoulder clusters
    p.face(40, 0, ["........", ".G....W.", ".gW..Gg.", "..g..g..", "........", "..G.....", ".gW...G.", "........"])
    p.face(40, 8, [".G.WG.G.", ".g.gg.g."] + ["........"] * 6)       # crown spikes over the brow
    p.face(32, 8, ["..G.....", "..g....."] + ["........"] * 6)
    p.face(48, 8, [".....G..", ".....g.."] + ["........"] * 6)
    for u, v in ((40, 32), (48, 48)):
        p.face(u + 4, v, ["GW.g", "gG.W", ".gGg", "g..."])            # top: shard cluster
        for fx in (u, u + 4, u + 8, u + 12):
            p.face(fx, v + 4, ["G.W.", "g.g."])


def vanguard(p):
    """UHC bot: iron plate, gold trim, crimson visor and tabard, a golden-apple crest."""
    soulguard(p)
    p.face(8, 8, ["abccccba", "bcdeedcb", "sTTTTTTs", "kgGGGGgk", "bcdkkdcb", "bcdkkdcb", "bcdssdcb", "abssssba"])
    p.face(20, 20, ["sTTTTTTs", "bcdeedcb", "bcdTTdcb", "bcTGGTcb", "bcTGGTcb", "bcdTTdcb", "abcddcba",
                    "kssTTssk", "kmmssmmk", "bcBBBBcb", "bcBnnBcb", "abBnnBba"])
    p.face(40, 0, ["........", "...TT...", "...Tt...", "...tT...", "...TT...", "...Tt...", "...tT...", "........"])


def architect(p):
    """Schematic builder: teal-glass visor, copper trim, hi-vis tabard and a hard hat."""
    soulguard(p)
    p.face(8, 8, ["abccccba", "bcdeedcb", "sTTTTTTs", "kgGGGGgk", "kgGWWGgk", "bcdkkdcb", "bcdssdcb", "abssssba"])
    p.face(20, 20, ["sTTTTTTs", "bcdeedcb", "bcdgGdcb", "bcgGGgcb", "bcdgGdcb", "bcdddddb"[:8], "abcddcba",
                    "kssTTssk", "kmmssmmk", "bcBBBBcb", "bcBnnBcb", "abBnnBba"])
    # hard hat (overlay): brim all round the hat layer, crown on top
    p.face(40, 0, ["BBBBBBBB", "BnnnnnnB", "BnBBBBnB", "BnBnnBnB", "BnBnnBnB", "BnBBBBnB", "BnnnnnnB", "BBBBBBBB"])
    for x in (32, 40, 48, 56):
        p.face(x, 8, ["BBBBBBBB", "nnnnnnnn"] + ["........"] * 6)


def demolisher(p):
    """Cart PvP bot: charcoal plate, a TNT band across the chest, an ember visor and a lit fuse on the helm."""
    soulguard(p)
    p.face(8, 8, ["abccccba", "bcdeedcb", "sTTTTTTs", "kgGGGGgk", "kGWGGWGk", "bcdkkdcb", "bcdssdcb", "abssssba"])
    # chest: red/white TNT band with a dark label stripe
    p.face(20, 20, ["sTTTTTTs", "bcdeedcb", "BBTBBTBB", "nBTnnTBn", "kkkkkkkk", "nBTnnTBn", "BBTBBTBB",
                    "kssTTssk", "kmmssmmk", "bcBBBBcb", "bcBnnBcb", "abBnnBba"])
    # fuse (overlay) rising from the crown, glowing at the tip
    p.face(40, 0, ["........", "...W....", "...G....", "...g....", "...k....", "...k....", "........", "........"])
    for x in (32, 40, 48, 56):
        p.face(x, 8, ["........", "........"] + ["........"] * 6)


def preview(name, img):
    """Flat sheet for quick inspection (not shipped)."""
    big = img.resize((256, 256), Image.NEAREST)
    bg = Image.new("RGBA", big.size, (40, 40, 52, 255))
    bg.alpha_composite(big)
    return bg


if __name__ == "__main__":
    for name, fn in (("sword_bot", soulguard), ("crystal_bot", prism_knight), ("uhc_bot", vanguard), ("builder_bot", architect), ("cart_bot", demolisher)):
        p = Painter(PALETTES[name])
        fn(p)
        p.img.save(DIR / f"{name}.png")
        print("wrote", name)
