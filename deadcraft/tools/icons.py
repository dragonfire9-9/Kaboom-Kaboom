"""16x16 item icons with automatic pixel-art lighting.

Templates are ASCII masks. Letters pick a material and a base tone:
    o outline   w/a/b/c primary (highlight → shadow)   g accent   h handle   e glow
The renderer then lights every pixel from the top-left, shifts shadows toward violet and
highlights toward warm yellow, and colours each outline pixel from what it borders
("selective outlining") instead of using flat black.
"""
from __future__ import annotations

import numpy as np
from PIL import Image

import tex
from paint import TEMPLATES as BASE_TEMPLATES

T = dict(BASE_TEMPLATES)
T.update({
    "serrated": """
..............oo
.............oeo
............owbo
...........owbbo
..........owbo..
.........owbbo..
........owbo....
...o...owbbo....
...oo.owbo......
....ogwbo.......
.....ggo........
....ohgg........
...oho.oo.......
..oho...........
.oeo............
.oo.............""",
    "longsword": """
..............oo
.............owo
............owbo
...........owbo.
..........owbo..
.........owbo...
........owbo....
.......owbo.....
......owbo......
..oo.owbo.......
..ogowbo........
...oggo.........
...ohggo........
..oho.oo........
.oeo............
.oo.............""",
    "curved": """
..........ooo...
.........owwbo..
.........owbbo..
..........owbo..
..........owbo..
.........owbo...
........owbbo...
.......owbbo....
..oo..owbbo.....
..ogoowbbo......
...ogwbbo.......
...ohgoo........
..oho...........
.oho............
oeo.............
oo..............""",
    "clocksword": """
..............oo
.............owo
............owbo
...........owbo.
..........owbo..
.........owbo...
........owbo....
...ooo.owbo.....
..ogggowbo......
..ogeggbo.......
..oggggo........
...ogho.........
..oho...........
.oho............
oeo.............
oo..............""",
    "key_flame": """
..........o.....
.........oeo....
........oeaeo...
.......oeaaaeo..
.......oaa.aao..
.......oeaaaeo..
......ooaeeaoo..
.....obooooo....
....obo.........
...obo..........
..obo.o.........
.obooao.........
.oo..oo.........
................
................
................""",
    "key_star": """
..........o.....
.........oeo....
.....ooooeaeoooo
......oeaaaaaeo.
.......oaa.aao..
......oeaaaaaeo.
.....ooooeaeoooo
.....obo.oeo....
....obo...o.....
...obo..........
..obo.o.........
.obooao.........
.oo..oo.........
................
................
................""",
    "tome_frost": """
................
..ooooooooooo...
.obbbbbbbbbbbo..
.obgggggggggbwo.
.obga.e.e.agbwo.
.obga..e..agbwo.
.obgaeeeeeagbwo.
.obga..e..agbwo.
.obga.e.e.agbwo.
.obgggggggggbwo.
.obbbbbbbbbbbwo.
.occcccccccccwo.
.owwwwwwwwwwwwo.
..ooooooooooooo.
................
................""",
    "tome_eye": """
................
..ooooooooooo...
.obbbbbbbbbbbo..
.obgggggggggbwo.
.obgaaaaaaagbwo.
.obga.eee.agbwo.
.obgaee.eeagbwo.
.obga.eee.agbwo.
.obgaaaaaaagbwo.
.obgggggggggbwo.
.obbbbbbbbbbbwo.
.occcccccccccwo.
.owwwwwwwwwwwwo.
..ooooooooooooo.
................
................""",
    "meat_cooked": """
................
................
.....ooooo......
...ooaaaaaoo....
..oaaacbbaaao...
.oaabbcbbbbaao..
.oabbbbcbcbbao..
.oabbcbbbcbbao..
.oaabbbcbbbaao..
..oaabbbbbaaoo..
...ooaaaaaoowo..
.....ooooo.owwo.
...........owwo.
............oo..
................
................""",
    "drumstick_cooked": """
................
................
......oooo......
....ooaaaaoo....
...oaacbbbaao...
...oabbcbcbao...
...oabcbbcbao...
...oaabcbbbao...
....oaabbbao....
.....ooaaoo.....
.......owo......
.......owo......
......owwwo.....
......oo.oo.....
................
................""",
})

T.update({
    "heart9": """
.oo...oo.
oaaooaabo
oawaaaabo
oaaaaaabo
.oaaaabo.
..oaabo..
...obo...
....o....
.........""",
    "food9": """
...ooo...
..oaabo..
.oaaabbo.
.oaabbbo.
..obbbo..
...oho...
...oho...
..ohhho..
..oo.oo..""",
    "armor9": """
oo.....oo
oaoooooao
oaaaaaabo
.oaaaabo.
.oaaaabo.
.oaaaabo.
.oabbbbo.
.ooooooo.
.........""",
    "mana9": """
....o....
...oao...
..oaaao..
..owaao..
.owaaabo.
.oaaaabo.
.oaaabbo.
..obbbo..
...ooo...""",
})

TONE = {"w": 1.45, "a": 1.15, "b": 1.0, "c": 0.68}


def _grid(name, size=16):
    rows = T[name].strip("\n").split("\n")
    rows = [(r + "." * size)[:size] for r in rows] + ["." * size] * (size - len(rows))
    return np.array([list(r) for r in rows[:size]])


def icon(template, primary, accent=(170, 140, 60), handle=(80, 52, 34), glow=(255, 60, 40), seed=0, size=16):
    g = _grid(template, size)
    h, w = g.shape
    P = np.array(primary, float) / 255
    A = np.array(accent, float) / 255
    Hc = np.array(handle, float) / 255
    E = np.array(glow, float) / 255
    interior = (g != ".") & (g != "o")
    col = np.zeros((h, w, 3))
    rng = np.random.default_rng(seed)
    for y in range(h):
        for x in range(w):
            ch = g[y, x]
            if ch in TONE:
                col[y, x] = tex.shade(P, TONE[ch] * (0.97 + 0.06 * rng.random()))
            elif ch == "g":
                col[y, x] = A
            elif ch == "h":
                col[y, x] = tex.shade(Hc, 1.1 if (x + y) % 3 == 0 else 0.92)  # wood/leather wrap
            elif ch == "e":
                col[y, x] = E

    def inside(y, x):
        return 0 <= y < h and 0 <= x < w and interior[y, x]

    lit = col.copy()
    for y in range(h):
        for x in range(w):
            if not interior[y, x] or g[y, x] == "e":
                continue
            k = 1.0
            if not inside(y - 1, x) or not inside(y, x - 1):
                k *= 1.16
            if not inside(y + 1, x) or not inside(y, x + 1):
                k *= 0.82
            lit[y, x] = tex.shade(col[y, x], k)
    # glow pixels: hot centre, soft rim
    for y in range(h):
        for x in range(w):
            if g[y, x] == "e":
                n = sum(inside(y + dy, x + dx) and g[y + dy, x + dx] == "e" for dy, dx in ((0, 1), (1, 0), (0, -1), (-1, 0)))
                lit[y, x] = tex.lerp(E, np.ones(3), 0.15 + 0.12 * n)
    # selective outline
    out = lit.copy()
    for y in range(h):
        for x in range(w):
            if g[y, x] != "o":
                continue
            nb = [lit[y + dy, x + dx] for dy, dx in ((0, 1), (1, 0), (0, -1), (-1, 0)) if inside(y + dy, x + dx)]
            if nb:
                facing_light = inside(y + 1, x) or inside(y, x + 1)
                out[y, x] = tex.shade(np.mean(nb, axis=0), 0.42 if facing_light else 0.3)
            else:
                out[y, x] = np.array([0.08, 0.05, 0.08])
    alpha = (g != ".").astype(float)
    rgba = np.dstack([np.clip(out, 0, 1), alpha])
    return Image.fromarray((rgba * 255).astype(np.uint8), "RGBA")


# =============================================================================================
# 32x32 icons: Scale2x the template shape, then light and outline at full resolution
# =============================================================================================
def _scale2x(g):
    h, w = g.shape
    out = np.full((h * 2, w * 2), ".", dtype=g.dtype)
    for y in range(h):
        for x in range(w):
            P = g[y, x]
            A = g[y - 1, x] if y > 0 else P
            B = g[y, x + 1] if x < w - 1 else P
            C = g[y, x - 1] if x > 0 else P
            D = g[y + 1, x] if y < h - 1 else P
            e0 = A if (C == A and C != D and A != B) else P
            e1 = B if (A == B and A != C and B != D) else P
            e2 = C if (D == C and D != B and C != A) else P
            e3 = D if (B == D and B != A and D != C) else P
            out[2 * y, 2 * x], out[2 * y, 2 * x + 1], out[2 * y + 1, 2 * x], out[2 * y + 1, 2 * x + 1] = e0, e1, e2, e3
    return out


def icon32(template, primary, accent=(170, 140, 60), handle=(80, 52, 34), glow=(255, 60, 40), seed=0):
    g16 = _grid(template)
    g16 = np.where(g16 == "o", ".", g16)  # drop the hand-drawn outline; a crisp 1px one is added at 32px
    g = _scale2x(g16)
    h, w = g.shape
    P = np.array(primary, float) / 255
    A = np.array(accent, float) / 255
    Hc = np.array(handle, float) / 255
    E = np.array(glow, float) / 255
    inside = g != "."
    ys, xs = np.mgrid[0:h, 0:w]
    X, Y = xs.astype(float), ys.astype(float)
    n = tex.fbm(X / 3, Y / 3, seed, 3)
    col = np.zeros((h, w, 3))
    for ch, base in (("w", 1.45), ("a", 1.15), ("b", 1.0), ("c", 0.68)):
        m = g == ch
        col[m] = tex.shade(P, base * (0.93 + 0.14 * n))[m]
    streak = ((xs - ys) % 9 == 0) & np.isin(g, list("wab"))
    col[streak] = tex.shade(np.broadcast_to(P, (h, w, 3)), 1.18)[streak]
    m = g == "g"
    col[m] = tex.shade(np.broadcast_to(A, (h, w, 3)), 0.9 + 0.25 * n[..., None])[m]
    m = g == "h"
    wrap = ((ys + xs // 2) % 3 == 0)
    col[m] = np.where(wrap[..., None], tex.shade(Hc, 0.7), tex.shade(Hc, 1.08))[m]
    m = g == "e"
    dist = np.zeros((h, w))
    for y in range(h):
        for x in range(w):
            if g[y, x] == "e":
                dist[y, x] = sum(0 <= y + dy < h and 0 <= x + dx < w and g[y + dy, x + dx] == "e"
                                 for dy in (-2, -1, 0, 1, 2) for dx in (-2, -1, 0, 1, 2))
    core = np.clip(dist / 25.0, 0, 1)
    col[m] = tex.lerp(np.broadcast_to(E, (h, w, 3)), np.ones((h, w, 3)), core ** 2 * 0.85)[m]

    def ins(y, x):
        return 0 <= y < h and 0 <= x < w and inside[y, x]

    lit = col.copy()
    for y in range(h):
        for x in range(w):
            if not inside[y, x] or g[y, x] == "e":
                continue
            k = 1.0
            if not ins(y - 1, x) or not ins(y, x - 1):
                k *= 1.22
            elif not ins(y - 2, x) or not ins(y, x - 2):
                k *= 1.08
            if not ins(y + 1, x) or not ins(y, x + 1):
                k *= 0.78
            elif not ins(y + 2, x) or not ins(y, x + 2):
                k *= 0.9
            lit[y, x] = tex.shade(col[y, x], k)
    rng = np.random.default_rng(seed)
    for _ in range(3):  # specular glints on the lit side
        cand = [(y, x) for y in range(h) for x in range(w) if inside[y, x] and g[y, x] in "wa" and not ins(y - 1, x)]
        if cand:
            y, x = cand[rng.integers(len(cand))]
            lit[y, x] = np.ones(3)
    out = np.zeros((h, w, 4))
    out[..., :3] = lit
    out[..., 3] = inside
    for y in range(h):
        for x in range(w):
            if inside[y, x]:
                continue
            nb = [(y + dy, x + dx) for dy, dx in ((0, 1), (1, 0), (0, -1), (-1, 0)) if ins(y + dy, x + dx)]
            if nb:
                c = np.mean([lit[a, b] for a, b in nb], axis=0)
                lightside = any(a > y or b > x for a, b in nb)
                out[y, x, :3] = tex.shade(c, 0.4 if lightside else 0.26)
                out[y, x, 3] = 1
    return Image.fromarray((np.clip(out, 0, 1) * 255).astype(np.uint8), "RGBA")
