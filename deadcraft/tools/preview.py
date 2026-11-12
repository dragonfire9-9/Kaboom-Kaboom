#!/usr/bin/env python3
"""Tiny software renderer: draws a box-UV model with its texture in 3/4 view, for reviewing skins.

    python3 tools/preview.py out.png            # every creature on one sheet
"""
from __future__ import annotations

import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

import content
import models

TEX = os.path.join(content.ROOT, "src/main/resources/assets/deadcraft/textures/entity")


def rot_matrix(rx, ry, rz):
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


def part_transforms(parts):
    by = {p.name: p for p in parts}
    cache = {}

    def get(p):
        if p.name in cache:
            return cache[p.name]
        M = rot_matrix(*p.rot)
        T = np.array(p.pivot, dtype=float)
        if p.parent:
            PM, PT = get(by[p.parent])
            M, T = PM @ M, PT + PM @ T
        cache[p.name] = (M, T)
        return cache[p.name]

    return {p.name: get(p) for p in parts}


def face_quads(c):
    """Yield (face, corner function(s,t)->local xyz, uv rect)."""
    i = c.inflate
    x1, y1, z1 = c.x - i, c.y - i, c.z - i
    x2, y2, z2 = c.x + c.w + i, c.y + c.h + i, c.z + c.d + i
    W, Hh, D = x2 - x1, y2 - y1, z2 - z1
    u, v, w, h, d = c.u, c.v, c.w, c.h, c.d
    yield "front", (lambda s, t: (x1 + s * W, y1 + t * Hh, z1)), (u + d, v + d, w, h)
    yield "back", (lambda s, t: (x2 - s * W, y1 + t * Hh, z2)), (u + 2 * d + w, v + d, w, h)
    yield "right", (lambda s, t: (x1, y1 + t * Hh, z2 - s * D)), (u, v + d, d, h)
    yield "left", (lambda s, t: (x2, y1 + t * Hh, z1 + s * D)), (u + d + w, v + d, d, h)
    yield "top", (lambda s, t: (x1 + s * W, y1, z2 - t * D)), (u + d, v, w, d)
    yield "bottom", (lambda s, t: (x1 + s * W, y2, z1 + t * D)), (u + d + w, v, w, d)


SHADE = {"front": 1.0, "back": 0.6, "right": 0.75, "left": 0.75, "top": 1.15, "bottom": 0.5}


def render(parts, texture, glow=None, size=220, yaw=-35, pitch=22, hidden=(), decl=None):
    if decl is not None and texture.size[0] != decl[0]:
        texture = texture.resize(decl, Image.NEAREST)
        glow = glow.resize(decl, Image.NEAREST) if glow is not None else None
    tex = np.asarray(texture.convert("RGBA")).astype(float)
    gl = np.asarray(glow.convert("RGBA")).astype(float) if glow is not None else None
    tf = part_transforms(parts)
    view = rot_matrix(math.radians(pitch), 0, 0) @ rot_matrix(0, math.radians(yaw), 0)
    quads = []
    for p in parts:
        if p.name in hidden:
            continue
        M, T = tf[p.name]
        for c in p.cubes:
            for face, corner, (fu, fv, fw, fh) in face_quads(c):
                for j in range(fh):
                    for i in range(fw):
                        px = tex[fv + j, fu + i] if 0 <= fv + j < tex.shape[0] and 0 <= fu + i < tex.shape[1] else None
                        if px is None or px[3] < 10:
                            continue
                        col = px[:3] * SHADE[face]
                        if gl is not None:
                            g = gl[fv + j, fu + i]
                            if g[3] > 10:
                                col = np.maximum(col, g[:3])
                        pts = []
                        for s, t in ((i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1)):
                            loc = np.array(corner(s / max(fw, 1), t / max(fh, 1)))
                            world = view @ (M @ loc + T)
                            pts.append(world)
                        pts = np.array(pts)
                        quads.append((pts[:, 2].mean(), pts[:, :2], tuple(int(v) for v in np.clip(col, 0, 255)) + (int(px[3]),)))
    if not quads:
        return Image.new("RGBA", (size, size))
    allp = np.concatenate([q[1] for q in quads])
    lo, hi = allp.min(0), allp.max(0)
    scale = (size - 16) / max(hi - lo)
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img, "RGBA")
    off = (size - (hi - lo) * scale) / 2
    for _, pts, col in sorted(quads, key=lambda q: -q[0]):
        poly = [tuple((pt - lo) * scale + off) for pt in pts]
        d.polygon(poly, fill=col)
    return img


def apply_pose(parts, anim, t):
    """Offset bones by an animation sampled at time t (rotation degrees, position pixels)."""
    import copy
    out = copy.deepcopy(parts)
    by = {p.name: p for p in out}
    for bone, chans in anim["bones"].items():
        if bone not in by:
            continue
        p = by[bone]
        for ch, keys in chans.items():
            if ch == "scale":
                continue
            v = keys[-1][1]
            for (t0, a), (t1, b) in zip(keys, keys[1:]):
                if t0 <= t <= t1:
                    k = (t - t0) / max(1e-6, t1 - t0)
                    v = tuple(x + (y - x) * k for x, y in zip(a, b))
                    break
            if ch == "rotation":
                p.rot = tuple(r + math.radians(d) for r, d in zip(p.rot, v))
            else:
                p.pivot = tuple(q + d for q, d in zip(p.pivot, v))
    return out


def sheet(out_path, cols=7, size=200, anim=None, t=0.0):
    import anims as A
    import rigs
    names = list(rigs.CREATURES)
    rows = math.ceil(len(names) / cols)
    canvas = Image.new("RGBA", (cols * size, rows * (size + 14)), (58, 58, 64, 255))
    d = ImageDraw.Draw(canvas)
    for i, name in enumerate(names):
        parts, dw, dh = models.build(name)
        if anim:
            sets = A.build(name, rigs.ARCHETYPE_OF[name], [p.name for p in parts])
            if anim in sets:
                parts = apply_pose(parts, sets[anim], t * sets[anim]["length"])
        texp = os.path.join(TEX, name + ".png")
        glowp = os.path.join(TEX, name + "_eyes.png")
        img = render(parts, Image.open(texp), Image.open(glowp) if os.path.exists(glowp) else None, size, decl=(dw, dh))
        x, y = (i % cols) * size, (i // cols) * (size + 14)
        canvas.alpha_composite(img, (x, y))
        d.text((x + 4, y + size), name, fill=(230, 230, 230, 255))
    canvas.save(out_path)


def extras(out_path, size=240):
    import rigs
    items = list(rigs.FAMILIES) + list(rigs.VEHICLES)
    canvas = Image.new("RGBA", (len(items) * size, size + 14), (58, 58, 64, 255))
    d = ImageDraw.Draw(canvas)
    for i, name in enumerate(items):
        parts, dw, dh = models.build(name)
        texp = os.path.join(TEX, name + ".png")
        glowp = os.path.join(TEX, name + ("_glow.png" if name in rigs.VEHICLES else "_eyes.png"))
        img = render(parts, Image.open(texp), Image.open(glowp) if os.path.exists(glowp) else None, size, decl=(dw, dh))
        canvas.alpha_composite(img, (i * size, 0))
        d.text((i * size + 4, size), name, fill=(230, 230, 230, 255))
    canvas.save(out_path)


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "preview.png"
    sheet(out)
    extras(out.replace(".png", "_extras.png"))
    if len(sys.argv) > 2:
        sheet(out.replace(".png", f"_{sys.argv[2]}.png"), anim=sys.argv[2], t=float(sys.argv[3]) if len(sys.argv) > 3 else 0.25)
