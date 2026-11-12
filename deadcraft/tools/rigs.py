"""DeadCraft creature rigs.

Every creature has its own model built on a deep bone hierarchy (hips → spine → chest → neck →
head → jaw, shoulder → elbow → wrist → fingers, hip → knee → ankle, tail and tendril chains).
Archetype base rigs share bone names, so one set of keyframe animations drives every creature of
that shape; each creature then bolts on its own geometry (extra arms, spines, horns, wings,
orbiting rings, candles, lanterns...).

Coordinates are Minecraft Java model space: units are pixels, Y points DOWN, the feet rest on
y = 24 and the creature faces -Z. Rotations are given in DEGREES here.
"""
from __future__ import annotations

import math

from models import Cube, Part


class Rig:
    def __init__(self, name):
        self.name = name
        self.parts: list[Part] = []
        self.by: dict[str, Part] = {}

    # ---- primitives ------------------------------------------------------------------------
    def bone(self, name, parent=None, pivot=(0, 0, 0), rot=(0, 0, 0)):
        assert name not in self.by, f"{self.name}: duplicate bone {name}"
        assert parent is None or parent in self.by, f"{self.name}: unknown parent {parent} for {name}"
        p = Part(name, parent, tuple(float(v) for v in pivot), tuple(math.radians(a) for a in rot), [])
        self.parts.append(p)
        self.by[name] = p
        return name

    def box(self, bone, x, y, z, w, h, d, role="skin", inflate=0.0):
        self.by[bone].cubes.append(Cube(x, y, z, int(w), int(h), int(d), role, inflate))
        return bone

    def has(self, name):
        return name in self.by

    # ---- helpers ---------------------------------------------------------------------------
    def mirror(self, src_prefix="right_", dst_prefix="left_"):
        """Copy every bone whose name starts with src_prefix to the other side (x mirrored)."""
        for p in list(self.parts):
            if not p.name.startswith(src_prefix):
                continue
            name = dst_prefix + p.name[len(src_prefix):]
            if name in self.by:
                continue
            parent = p.parent
            if parent and parent.startswith(src_prefix):
                parent = dst_prefix + parent[len(src_prefix):]
            px, py, pz = p.pivot
            rx, ry, rz = p.rot
            q = Part(name, parent, (-px, py, pz), (rx, -ry, -rz), [])
            for c in p.cubes:
                q.cubes.append(Cube(-(c.x + c.w), c.y, c.z, c.w, c.h, c.d, c.role, c.inflate))
            self.parts.append(q)
            self.by[name] = q

    def chain(self, prefix, parent, pivot, segs, role="skin", axis="z", rot=(0, 0, 0), each=(0, 0, 0), taper=0):
        """A chain of bones (tail, tendril, robe strip). segs = [(length, thickness), ...].
        axis 'z' extends backwards, 'y' extends downwards, '-y' upwards, '-x'/'x' sideways."""
        prev, pv = parent, pivot
        for i, (L, t) in enumerate(segs):
            name = f"{prefix}{i}" if i else prefix
            self.bone(name, prev, pv, rot if i == 0 else each)
            h = t / 2
            if axis == "z":
                self.box(name, -h, -h, 0, t, t, L, role)
                pv = (0, 0, L)
            elif axis == "y":
                self.box(name, -h, 0, -h, t, L, t, role)
                pv = (0, L, 0)
            elif axis == "-y":
                self.box(name, -h, -L, -h, t, L, t, role)
                pv = (0, -L, 0)
            elif axis == "-x":
                self.box(name, -L, -h, -h, L, t, t, role)
                pv = (-L, 0, 0)
            elif axis == "x":
                self.box(name, 0, -h, -h, L, t, t, role)
                pv = (L, 0, 0)
            prev = name
        return prev

    def spike(self, name, parent, pivot, height, rot=(0, 0, 0), role="spike", base=2):
        """A tapering spike pointing up (−Y) from pivot."""
        self.bone(name, parent, pivot, rot)
        total = max(2, int(round(height)))
        hb = max(1, int(round(total * 0.55))) if base > 1 else total
        self.box(name, -base / 2, -hb, -base / 2, base, hb, base, role)
        if base > 1:
            self.box(name, -0.5, -total, -0.5, 1, total - hb, 1, role)
        return name

    def orbit(self, name, parent, pivot, radius, count, size=2, role="crystal", tilt=(0, 0, 0), height=None):
        """A ring of floating objects on one bone; animations spin it."""
        self.bone(name, parent, pivot, tilt)
        hgt = size if height is None else height
        for i in range(count):
            a = 2 * math.pi * i / count
            x, z = math.cos(a) * radius, math.sin(a) * radius
            self.box(name, x - size / 2, -hgt / 2 + (i % 2), z - size / 2, size, hgt, size, role)
        return name


def _r(v):
    return [round(x, 3) for x in v]


# =============================================================================================
# Base rigs
# =============================================================================================
def gaunt(name, arm=1.0, hunch=16, leg_cloth=True):
    """Tall starved humanoid. Bones: right/left_leg→shin→foot, body→spine→chest→neck→head→jaw,
    chest→right/left_arm→forearm→hand."""
    r = Rig(name)
    r.bone("right_leg", None, (-2.5, 11, 0))
    r.box("right_leg", -1.5, 0, -1.5, 3, 7, 3, "cloth" if leg_cloth else "skin")
    r.bone("right_shin", "right_leg", (0, 7, 0))
    r.box("right_shin", -1, 0, -1, 2, 5, 2, "skin")
    r.bone("right_foot", "right_shin", (0, 5, 0))
    r.box("right_foot", -1.5, 0, -3, 3, 1, 4, "skin")
    r.box("right_foot", -1.5, 0, -4, 1, 1, 1, "claw")
    r.box("right_foot", 0.5, 0, -4, 1, 1, 1, "claw")

    r.bone("body", None, (0, 11, 0))
    r.box("body", -3, -3, -2, 6, 3, 4, "cloth")
    r.bone("spine", "body", (0, -3, 0), (hunch * 0.4, 0, 0))
    r.box("spine", -2, -4, -1.5, 4, 4, 3, "skin")
    r.box("spine", -0.5, -4, 1.5, 1, 4, 1, "bone")
    r.bone("chest", "spine", (0, -4, 0), (hunch * 0.6, 0, 0))
    r.box("chest", -4.5, -8, -2.5, 9, 8, 5, "skin")
    for k in range(3):
        r.box("chest", -4, -7 + 2 * k, -3, 8, 1, 1, "bone")
    r.box("chest", -0.5, -8, 2.5, 1, 8, 1, "bone")
    r.bone("neck", "chest", (0, -8, -1), (-hunch * 0.7, 0, 0))
    r.box("neck", -1, -3, -1, 2, 3, 2, "skin")
    r.bone("head", "neck", (0, -3, -0.5))
    r.box("head", -3.5, -7, -4, 7, 7, 7, "head")
    r.box("head", -3.5, -5, -4.5, 7, 1, 1, "skin")
    r.bone("jaw", "head", (0, -1, -1), (12, 0, 0))
    r.box("jaw", -3, 0, -3, 6, 2, 5, "skin")
    r.box("jaw", -2.5, -1, -3.2, 5, 1, 1, "tooth")

    r.bone("right_arm", "chest", (-5, -7, 0), (-8, 0, 6))
    r.box("right_arm", -1.5, -1, -1.5, 3, int(8 * arm), 3, "skin")
    r.bone("right_forearm", "right_arm", (0, int(8 * arm), 0), (-10, 0, 0))
    r.box("right_forearm", -1, 0, -1, 2, int(9 * arm), 2, "skin")
    r.bone("right_hand", "right_forearm", (0, int(9 * arm), 0))
    r.box("right_hand", -1.5, 0, -1.5, 3, 2, 3, "skin")
    r.box("right_hand", -1.5, 2, -1.5, 1, 5, 1, "claw")
    r.box("right_hand", 0.5, 2, -1.5, 1, 5, 1, "claw")
    r.box("right_hand", -0.5, 2, 0.5, 1, 4, 1, "claw")
    r.mirror()
    return r


def crawler(name, pairs=3, leg_len=1.0, body_w=7, abdomen=(8, 7, 8)):
    """Many-legged crawler. Bones: body→head→mandibles, body→abdomen→abdomen_tip→stinger,
    body→right/left_legN→right/left_legN_lower."""
    r = Rig(name)
    r.bone("body", None, (0, 15, 0))
    r.box("body", -body_w / 2, -3, -4, body_w, 5, 8, "skin")
    r.box("body", -1, -5, -3, 2, 2, 6, "bone")
    r.bone("head", "body", (0, -0.5, -4))
    r.box("head", -3, -3, -5, 6, 5, 5, "head")
    r.bone("right_mandible", "head", (-2, 1, -5), (0, 20, 0))
    r.box("right_mandible", -1, 0, -4, 1, 1, 4, "claw")
    r.box("right_mandible", -1, -1, -4.5, 1, 1, 1, "claw")
    r.bone("jaw", "head", (0, 1, -2), (10, 0, 0))
    r.box("jaw", -2.5, 0, -3, 5, 1, 3, "skin")
    aw, ah, ad = abdomen
    r.bone("abdomen", "body", (0, -1, 4), (-8, 0, 0))
    r.box("abdomen", -aw / 2, -ah / 2 - 1, 0, aw, ah, ad, "accent")
    r.bone("abdomen_tip", "abdomen", (0, -1, ad), (10, 0, 0))
    r.box("abdomen_tip", -2, -2, 0, 4, 4, 3, "accent")
    up, low = int(7 * leg_len), int(14 * leg_len)
    zs = [(-3 + 6 * i / max(1, pairs - 1)) for i in range(pairs)]
    for i, z in enumerate(zs):
        spread = 25 - 50 * i / max(1, pairs - 1)
        r.bone(f"right_leg{i}", "body", (-body_w / 2, 0, z), (0, spread, 35))
        r.box(f"right_leg{i}", -up, -1, -1, up, 2, 2, "skin")
        r.bone(f"right_leg{i}_lower", f"right_leg{i}", (-up, 0, 0), (0, 0, -105))
        r.box(f"right_leg{i}_lower", -low, -0.5, -0.5, low, 1, 1, "claw")
    r.mirror()
    return r


def wraith(name, robe=3, hood=True, arm=1.0):
    """Floating robed spirit. Bones: body→neck→head→jaw, body→robe→robe1→robe2 (+strips),
    body→right/left_arm→forearm→hand."""
    r = Rig(name)
    r.bone("body", None, (0, 2, 0))
    r.box("body", -4.5, -10, -3, 9, 10, 6, "cloth")
    r.box("body", -3.5, -9, -3.5, 7, 3, 1, "bone")
    r.bone("neck", "body", (0, -10, 0))
    r.box("neck", -1, -2, -1, 2, 2, 2, "skin")
    r.bone("head", "neck", (0, -2, 0))
    r.box("head", -3.5, -7, -3.5, 7, 7, 7, "head")
    if hood:
        r.box("head", -4.5, -8.5, -4, 9, 2, 9, "cloth")
        r.box("head", -4.5, -7, 3.5, 9, 8, 2, "cloth")
        r.box("head", -4.5, -6.5, -3.5, 1, 7, 7, "cloth")
        r.box("head", 3.5, -6.5, -3.5, 1, 7, 7, "cloth")
        r.box("head", -1, -9.5, 1, 2, 2, 5, "cloth")
    r.bone("jaw", "head", (0, -1, -1), (14, 0, 0))
    r.box("jaw", -2.5, 0, -2.5, 5, 2, 4, "skin")
    sizes = [(10, 6, 7), (9, 5, 6), (7, 5, 5), (5, 4, 4)][:robe]
    prev = "body"
    for i, (w, h, d) in enumerate(sizes):
        name_i = "robe" if i == 0 else f"robe{i}"
        r.bone(name_i, prev, (0, 0 if i == 0 else sizes[i - 1][1], 0), (6, 0, 0))
        r.box(name_i, -w / 2, 0, -d / 2, w, h, d, "cloth")
        prev = name_i
    for k, x in enumerate((-3, 0, 3)):
        r.chain(f"strip{k}", prev, (x, sizes[-1][1], 0), [(3, 1), (3, 1)], "cloth", axis="y", rot=(4 * (k - 1), 0, 0))
    a = arm
    r.bone("right_arm", "body", (-5.5, -9, 0), (-10, 0, 8))
    r.box("right_arm", -1, -1, -1, 2, int(8 * a), 2, "cloth")
    r.box("right_arm", -1.5, int(8 * a) - 2, -1.5, 3, 3, 3, "cloth")
    r.bone("right_forearm", "right_arm", (0, int(8 * a), 0), (-15, 0, 0))
    r.box("right_forearm", -1, 0, -1, 2, int(7 * a), 2, "skin")
    r.bone("right_hand", "right_forearm", (0, int(7 * a), 0))
    for k, x in enumerate((-1.5, -0.5, 0.5)):
        r.box("right_hand", x, 0, -1, 1, 5 - (k == 2), 1, "claw")
    r.mirror()
    return r


def brute(name, horns=False, shoulders="accent"):
    """Hulking knuckle-walker. Bones: right/left_leg→shin→foot, body→chest→head→jaw,
    chest→right/left_arm→forearm→fist."""
    r = Rig(name)
    r.bone("right_leg", None, (-4.5, 13, 1))
    r.box("right_leg", -2.5, 0, -2.5, 5, 6, 5, "skin")
    r.bone("right_shin", "right_leg", (0, 6, 0))
    r.box("right_shin", -2, 0, -2, 4, 4, 4, "skin")
    r.bone("right_foot", "right_shin", (0, 4, 0))
    r.box("right_foot", -2.5, 0, -4, 5, 1, 6, "skin")
    for x in (-2.5, -0.5, 1.5):
        r.box("right_foot", x, 0, -5, 1, 1, 1, "claw")
    r.bone("body", None, (0, 13, 1))
    r.box("body", -6, -6, -4, 12, 6, 8, "skin")
    r.bone("chest", "body", (0, -6, 0), (25, 0, 0))
    r.box("chest", -8, -9, -5, 16, 9, 10, "skin")
    r.box("chest", -6, -8, -5.5, 12, 3, 1, "skin")
    r.bone("head", "chest", (0, -8, -5), (-25, 0, 0))
    r.box("head", -4, -4, -6, 8, 8, 7, "head")
    r.box("head", -4.5, -3, -6.5, 9, 1, 2, "skin")
    r.bone("jaw", "head", (0, 2, -2), (10, 0, 0))
    r.box("jaw", -3.5, 0, -4.5, 7, 3, 5, "skin")
    r.box("jaw", -3, -1, -4.8, 6, 1, 1, "tooth")
    r.box("jaw", -3.5, -2, -4.6, 1, 2, 1, "tooth")
    r.box("jaw", 2.5, -2, -4.6, 1, 2, 1, "tooth")
    if horns:
        r.chain("right_horn", "head", (-3, -3, -3), [(4, 2), (4, 2), (3, 1)], "horn", axis="-y", rot=(-20, 0, -40), each=(-25, 0, 10))
    r.bone("right_arm", "chest", (-9, -7, 0), (-10, 0, 10))
    r.box("right_arm", -3, -2, -3, 6, 9, 6, "skin")
    r.box("right_arm", -3.5, -3, -3.5, 7, 3, 7, shoulders)
    r.bone("right_forearm", "right_arm", (0, 9, 0), (-15, 0, 0))
    r.box("right_forearm", -3, 0, -3, 6, 8, 6, "skin")
    r.bone("right_fist", "right_forearm", (0, 8, 0))
    r.box("right_fist", -3.5, 0, -3.5, 7, 5, 7, "accent")
    for x in (-3, -1, 1):
        r.box("right_fist", x, 5, -3.5, 1, 1, 1, "claw")
    r.mirror()
    return r


def quadruped(name, neck=6, leg=1.0, body_len=15, size=8):
    """Four-legged animal. Bones: body→neck→head→jaw/ears, body→tail chain,
    right/left_front/hind_leg→shin."""
    r = Rig(name)
    half = size / 2
    r.bone("body", None, (0, 12, 0))
    r.box("body", -half, -4, -body_len / 2, size, 8, body_len, "skin")
    r.box("body", -half - 0.5, -4.5, -body_len / 2, size + 1, 9, 5, "skin")
    r.box("body", -half + 0.5, 3.5, -body_len / 2 + 1, size - 1, 1, body_len - 2, "belly")
    r.bone("neck", "body", (0, -2, -body_len / 2), (35, 0, 0))
    r.box("neck", -2, -neck, -2, 4, neck, 4, "skin")
    r.bone("head", "neck", (0, -neck, 0), (-35, 0, 0))
    r.box("head", -3, -3, -6, 6, 6, 6, "head")
    r.box("head", -2, -1, -9, 4, 4, 3, "snout")
    r.bone("jaw", "head", (0, 3, -6), (0, 0, 0))
    r.box("jaw", -1.5, 0, -3, 3, 1, 3, "skin")
    r.bone("right_ear", "head", (-2.5, -3, -2), (-10, 0, -30))
    r.box("right_ear", -0.5, -3, -0.5, 1, 3, 2, "accent")
    r.chain("tail", "body", (0, -3, body_len / 2), [(3, 2), (3, 2), (2, 1)], "accent", axis="y", rot=(40, 0, 0), each=(10, 0, 0))
    up, low = int(5 * leg), int(3 * leg)
    for side, z in (("front", -body_len / 2 + 2), ("hind", body_len / 2 - 2)):
        r.bone(f"right_{side}_leg", None, (-half + 1.5, 15, z))
        r.box(f"right_{side}_leg", -1.5, 0, -1.5, 3, up, 3, "skin")
        r.bone(f"right_{side}_shin", f"right_{side}_leg", (0, up, 0))
        r.box(f"right_{side}_shin", -1, 0, -1, 2, low, 2, "skin")
        r.box(f"right_{side}_shin", -1.5, low, -1.5, 3, 1, 3, "claw")
    r.mirror()
    return r


def critter(name, legs=2):
    """Small ground creature. Bones: body→head→right/left_ear, body→tail, legs."""
    r = Rig(name)
    r.bone("body", None, (0, 20, 0))
    r.box("body", -2.5, -2.5, -3.5, 5, 5, 7, "skin")
    r.box("body", -2, 2, -3, 4, 1, 6, "belly")
    r.bone("head", "body", (0, -1, -3.5))
    r.box("head", -2, -2.5, -3.5, 4, 4, 4, "head")
    r.box("head", -0.5, -0.5, -4, 1, 1, 1, "snout")
    r.bone("tail", "body", (0, -1, 3.5), (30, 0, 0))
    r.box("tail", -1, -1, 0, 2, 2, 2, "accent")
    for side, z in (("front", -2.5), ("hind", 2.5)):
        r.bone(f"right_{side}_leg", None, (-1.5, 22, z))
        r.box(f"right_{side}_leg", -1, 0, -1, 2, legs, 2, "skin")
    r.mirror()
    return r


def flyer(name, wing=1.0):
    """Bird. Bones: body→head→beak, body→right/left_wing→wing_tip, body→tail, legs."""
    r = Rig(name)
    r.bone("body", None, (0, 19, 0), (-10, 0, 0))
    r.box("body", -2, -2, -3, 4, 4, 6, "skin")
    r.box("body", -2.5, -1.5, -3.5, 5, 4, 3, "belly")
    r.bone("head", "body", (0, -2, -3))
    r.box("head", -1.5, -3, -3, 3, 3, 3, "head")
    r.box("head", -0.5, -2, -5, 1, 1, 2, "beak")
    w1, w2 = int(5 * wing), int(5 * wing)
    r.bone("right_wing", "body", (-2, -1.5, -1))
    r.box("right_wing", -w1, 0, -2.5, w1, 1, 5, "wing")
    r.bone("right_wing_tip", "right_wing", (-w1, 0, 0))
    r.box("right_wing_tip", -w2, 0, -2, w2, 1, 4, "wing")
    r.box("right_wing_tip", -w2, 0, 2, w2 - 1, 1, 1, "wing")
    r.bone("tail", "body", (0, -0.5, 3), (-15, 0, 0))
    r.box("tail", -1.5, 0, 0, 3, 1, 4, "wing")
    r.box("tail", -2, 0, 3, 4, 1, 2, "wing")
    r.bone("right_leg", None, (-1, 21, 0))
    r.box("right_leg", -0.5, 0, -0.5, 1, 2, 1, "claw")
    r.box("right_leg", -1, 2, -2, 2, 1, 3, "claw")
    r.mirror()
    return r


# =============================================================================================
# Creatures
# =============================================================================================
def the_hollow():
    r = gaunt("the_hollow", arm=1.25, hunch=22)
    # a second pair of folded arms bursting from the shoulder blades
    r.bone("right_back_arm", "chest", (-3, -7, 2.5), (-160, 20, 40))
    r.box("right_back_arm", -0.5, 0, -0.5, 1, 9, 1, "bone")
    r.bone("right_back_forearm", "right_back_arm", (0, 9, 0), (100, 0, 0))
    r.box("right_back_forearm", -0.5, 0, -0.5, 1, 10, 1, "bone")
    r.box("right_back_forearm", -1, 9, -1, 2, 2, 2, "claw")
    r.mirror("right_back", "left_back")
    r.box("jaw", -3, 2, -3, 6, 2, 1, "tooth")
    return r


def severed():
    """A torso dragging itself on four arms, trailing its spine."""
    r = Rig("severed")
    r.bone("body", None, (0, 16, 0))
    r.box("body", -4, -2.5, -6, 8, 5, 9, "skin")
    for k in range(4):
        r.box("body", -4.5, -2, -5 + 2 * k, 9, 1, 1, "bone")
    r.bone("head", "body", (0, -2, -6), (-15, 0, 0))
    r.box("head", -3.5, -7, -5, 7, 7, 7, "head")
    r.bone("jaw", "head", (0, -1, -2), (30, 0, 0))
    r.box("jaw", -3, 0, -3, 6, 2, 5, "skin")
    r.box("jaw", -2.5, -1, -3.2, 5, 1, 1, "tooth")
    for i, (z, spread) in enumerate(((-5, 30), (-1, -10))):
        r.bone(f"right_leg{i}", "body", (-4, -1, z), (0, spread, 45))
        r.box(f"right_leg{i}", -7, -1, -1, 7, 2, 2, "skin")
        r.bone(f"right_leg{i}_lower", f"right_leg{i}", (-7, 0, 0), (0, 0, -110))
        r.box(f"right_leg{i}_lower", -9, -1, -1, 9, 2, 2, "skin")
        r.box(f"right_leg{i}_lower", -11, -1, -1.5, 2, 2, 3, "claw")
    r.mirror()
    r.chain("tail", "body", (0, -1, 3), [(3, 2)] * 6, "bone", axis="z", rot=(-10, 0, 0), each=(8, 6, 0))
    r.chain("gut", "body", (1, 2.5, 1), [(3, 1), (3, 1), (2, 1)], "accent", axis="y", rot=(10, 0, 0), each=(15, 0, 0))
    return r


def cracked():
    r = brute("cracked", shoulders="rock")
    r.box("chest", -3, -6, -5.6, 6, 4, 1, "glow")  # molten core showing through the cracks
    for i, (x, y, z, h, rx, rz) in enumerate(((-5, -9, 1, 5, -20, -25), (0, -9, 2, 7, -30, 0), (5, -9, 1, 5, -20, 25),
                                              (-3, -6, 4, 4, -60, -10), (3, -6, 4, 4, -60, 10))):
        r.spike(f"rock_spike{i}", "chest", (x, y, z), h, (rx, 0, rz), "rock", base=3)
    r.orbit("orbit", "head", (0, -2, -2), 7, 5, 2, "rock", tilt=(10, 0, 0))
    return r


def depart():
    r = wraith("depart", robe=4)
    r.chain("lantern_chain", "left_hand", (0, 4, 0), [(2, 1), (2, 1)], "metal", axis="y")
    r.bone("lantern", "lantern_chain1", (0, 2, 0))
    r.box("lantern", -1.5, 0, -1.5, 3, 4, 3, "metal")
    r.box("lantern", -1, 0.5, -1, 2, 3, 2, "glow")
    return r


def scorched():
    r = gaunt("scorched", arm=1.0, hunch=12)
    for i, (x, z, h) in enumerate(((-2, 0, 4), (0, -1, 6), (2, 0, 4), (-1, 2, 3), (1, 2, 5))):
        r.bone(f"flame{i}", "head", (x, -7, z))
        r.box(f"flame{i}", -1, -h, -1, 2, h, 2, "flame")
    for i, (x, y, h, rz) in enumerate(((-3, -7, 4, -30), (3, -7, 4, 30), (-2, -3, 3, -45), (2, -3, 3, 45))):
        r.spike(f"char_spike{i}", "chest", (x, y, 2.5), h, (-50, 0, rz), "charred", base=2)
    return r


def parasite():
    r = crawler("parasite", pairs=4, leg_len=0.8, body_w=6, abdomen=(10, 9, 10))
    for i, (x, y, z) in enumerate(((-3, -6, 3), (2, -7, 5), (-1, -5, 8), (4, -3, 6), (-4, -2, 7))):
        r.box("abdomen", x, y, z, 2, 2, 2, "pustule")
    r.chain("stinger", "abdomen_tip", (0, -1, 3), [(3, 1), (3, 1), (2, 1)], "claw", axis="z", rot=(-35, 0, 0), each=(-25, 0, 0))
    r.box("head", -2, -4, -5.5, 1, 1, 1, "eye")
    r.box("head", 1, -4, -5.5, 1, 1, 1, "eye")
    r.box("head", -0.5, -3, -5.5, 1, 1, 1, "eye")
    return r


def illusion():
    r = wraith("illusion", robe=3)
    for i, x in enumerate((-8, 8)):
        r.bone(f"mask{i}", "head", (x, -4, 0), (0, 30 if x > 0 else -30, 0))
        r.box(f"mask{i}", -2.5, -3, -1, 5, 6, 1, "mask")
    r.orbit("orbit", "body", (0, -6, 0), 10, 6, 2, "mirror", height=4)
    return r


def the_forgotten():
    r = gaunt("the_forgotten", arm=1.05, hunch=10)
    for i, (bone, x, z) in enumerate((("chest", -3, -2.6), ("chest", 2, -2.6), ("right_forearm", 0, -1.1),
                                      ("left_forearm", 0, -1.1), ("body", 1, -2.1))):
        r.chain(f"bandage{i}", bone, (x, 4 if bone != "body" else 0, z), [(4, 1), (3, 1)], "bandage", axis="y",
                rot=(5, 0, 5 * (i % 2 * 2 - 1)))
    for i, x in enumerate((-4, -1.5, 1, 3.5)):  # iron cage around the head
        r.box("head", x, -8.5, -4.5, 1, 9, 1, "metal")
    r.box("head", -4.5, -8.5, -4.5, 9, 1, 9, "metal")
    return r


def wretched():
    r = crawler("wretched", pairs=3, leg_len=0.7, body_w=8, abdomen=(12, 11, 12))
    for i, (x, y, z, s) in enumerate(((-5, -7, 2, 3), (3, -8, 5, 3), (-2, -9, 8, 2), (5, -4, 9, 2), (-6, -3, 9, 2),
                                      (0, -5, 11, 2))):
        r.box("abdomen", x, y, z, s, s, s, "pustule")
    r.chain("drool", "jaw", (0, 1, -2.5), [(2, 1), (2, 1), (1, 1)], "pustule", axis="y")
    return r


def ghost():
    r = wraith("ghost", robe=4, hood=False, arm=1.15)
    r.box("jaw", -2, 2, -2.5, 4, 3, 1, "void")  # endlessly open mouth
    r.chain("wisp", "robe3", (0, 4, 0), [(3, 2), (3, 1), (2, 1)], "cloth", axis="y", rot=(10, 0, 0), each=(15, 0, 0))
    return r


def blood_demon():
    r = brute("blood_demon", horns=True)
    for i in range(5):
        r.spike(f"spine_spike{i}", "chest", (0, -9 + i * 2, 4.5), 5 - i * 0.6, (-55, 0, 0), "horn", base=2)
    r.chain("tail", "body", (0, -3, 4), [(4, 3), (4, 2), (4, 2), (3, 1)], "skin", axis="z", rot=(-20, 0, 0), each=(15, 0, 0))
    r.bone("tail_blade", "tail3", (0, 0, 3))
    r.box("tail_blade", -0.5, -2, 0, 1, 4, 4, "horn")
    return r


def star_fall():
    r = wraith("star_fall", robe=3)
    r.orbit("orbit", "head", (0, -9, 0), 6, 8, 1, "star", tilt=(0, 0, 0))
    r.orbit("orbit2", "body", (0, -4, 0), 11, 5, 2, "star", tilt=(25, 0, 10))
    return r


def collapse():
    r = brute("collapse", shoulders="slab")
    for i, (x, y, z, h, rx, rz) in enumerate(((-6, -8, 2, 9, -35, -30), (5, -9, 1, 11, -20, 25), (0, -4, 4, 7, -70, 5),
                                              (-3, -2, 4, 6, -80, -20))):
        r.bone(f"rebar{i}", "chest", (x, y, z), (rx, 0, rz))
        r.box(f"rebar{i}", -0.5, -h, -0.5, 1, h, 1, "rebar")
    r.box("chest", -9, -10, -2, 6, 3, 6, "slab")
    r.box("chest", 4, -10.5, -1, 5, 3, 5, "slab")
    return r


def fracture():
    """The Fracture: a crystal titan hovering over a pedestal of floating shards. Two huge blade arms,
    two small grasping arms, a split crystal skull around a burning core, and two rings of shards."""
    r = Rig("fracture")
    r.bone("body", None, (0, 8, 0))
    # the hovering pedestal of shards beneath it
    r.bone("shard_base", "body", (0, 4, 0))
    for i, (x, z, h) in enumerate(((-3, -2, 7), (2, -3, 9), (3, 2, 8), (-2, 3, 10), (0, 0, 12))):
        r.box("shard_base", x - 1, 0, z - 1, 2, h - 2, 2, "crystal")
        r.box("shard_base", x - 0.5, h - 2, z - 0.5, 1, 2, 1, "crystal")
    r.box("body", -5, -2, -3, 10, 6, 6, "rock")
    r.box("body", -6, -4, -4, 12, 3, 8, "crystal")
    r.bone("chest", "body", (0, -4, 0), (8, 0, 0))
    r.box("chest", -8, -12, -5, 16, 12, 10, "rock")
    r.box("chest", -3, -10, -5.5, 6, 6, 1, "glow")         # the exposed core
    for i, (x, z, h, rx, rz) in enumerate(((-6, 2, 9, -20, -35), (-2, 4, 13, -30, -10), (3, 4, 12, -30, 12),
                                           (7, 2, 10, -20, 35), (0, 5, 8, -70, 0))):
        r.spike(f"crystal{i}", "chest", (x, -11, z), h, (rx, 0, rz), "crystal", base=3)
    r.bone("core", "chest", (0, -7, -2))
    r.box("core", -2, -2, -2, 4, 4, 4, "glow")
    # head: a skull split in two halves with the core light between
    r.bone("head", "chest", (0, -12, -2))
    r.box("head", -4.5, -8, -4, 4, 8, 8, "rock")
    r.box("head", 0.5, -9, -4, 4, 9, 8, "rock")
    r.box("head", -0.5, -7, -3, 1, 6, 6, "glow")
    r.box("head", -4, -5, -4.5, 3, 1, 1, "eye")
    r.box("head", 1, -5, -4.5, 3, 1, 1, "eye")
    r.spike("head_crystal", "head", (2.5, -9, 0), 7, (-10, 0, 25), "crystal", base=2)
    r.spike("head_crystal2", "head", (-2.5, -8, 1), 5, (-10, 0, -30), "crystal", base=2)
    r.bone("jaw", "head", (0, 0, -1), (6, 0, 0))
    r.box("jaw", -4, 0, -3.5, 8, 2, 6, "rock")
    # great blade arms
    r.bone("right_arm", "chest", (-9, -10, 0), (-10, 0, 20))
    r.box("right_arm", -3, -3, -3, 6, 6, 6, "crystal")
    r.box("right_arm", -2, 2, -2, 4, 9, 4, "rock")
    r.bone("right_forearm", "right_arm", (0, 11, 0), (-30, 0, 0))
    r.box("right_forearm", -2.5, 0, -2.5, 5, 7, 5, "rock")
    r.bone("right_fist", "right_forearm", (0, 7, 0))
    r.box("right_fist", -1, 0, -4, 2, 18, 8, "crystal")       # the blade
    r.box("right_fist", -0.5, 18, -2, 1, 4, 4, "crystal")
    # small inner arms reaching out of the chest
    r.bone("right_inner_arm", "chest", (-4, -5, -4), (-50, 0, 30))
    r.box("right_inner_arm", -1, 0, -1, 2, 7, 2, "crystal")
    r.bone("right_inner_hand", "right_inner_arm", (0, 7, 0), (-40, 0, 0))
    r.box("right_inner_hand", -1.5, 0, -1.5, 3, 4, 3, "crystal")
    r.mirror()
    r.orbit("orbit", "body", (0, -10, 0), 15, 8, 2, "crystal", height=5)
    r.orbit("orbit2", "body", (0, -2, 0), 10, 5, 2, "crystal", tilt=(0, 0, 25), height=3)
    return r


def chronos():
    r = wraith("chronos", robe=4, arm=1.1)
    r.box("head", -3.5, -6, -4.2, 7, 5, 1, "gold")  # mask
    # a second, lower pair of arms
    r.bone("right_low_arm", "body", (-4.5, -4, 0), (-30, 0, 25))
    r.box("right_low_arm", -1, 0, -1, 2, 7, 2, "skin")
    r.bone("right_low_hand", "right_low_arm", (0, 7, 0), (-30, 0, 0))
    r.box("right_low_hand", -1, 0, -1, 2, 4, 2, "claw")
    r.mirror("right_low", "left_low")
    # clock halo: twelve hour marks and two hands
    r.orbit("halo", "head", (0, -4, 4), 8, 12, 1, "gold", tilt=(90, 0, 0), height=2)
    r.bone("clock_hand", "head", (0, -4, 4.5))
    r.box("clock_hand", -0.5, -7, -0.5, 1, 7, 1, "gold")
    r.box("clock_hand", 0, -0.5, -0.5, 5, 1, 1, "gold")
    r.chain("pendulum", "robe3", (0, 5, 0), [(4, 1), (4, 1)], "gold", axis="y")
    r.bone("pendulum_weight", "pendulum1", (0, 4, 0))
    r.box("pendulum_weight", -1.5, 0, -1.5, 3, 3, 3, "gold")
    return r


def dim_light_demon():
    """The Dim Light Demon: a demon lord on backward-bending legs, with vast tattered wings, a
    four-horned goat skull, raking claws, a flame-tipped tail and a halo of black fire."""
    r = Rig("dim_light_demon")
    # digitigrade legs
    r.bone("right_leg", None, (-4, 8, 1), (-25, 0, 0))
    r.box("right_leg", -2.5, -1, -2.5, 5, 8, 5, "skin")
    r.bone("right_shin", "right_leg", (0, 7, 0), (70, 0, 0))
    r.box("right_shin", -1.5, 0, -1.5, 3, 7, 3, "skin")
    r.bone("right_foot", "right_shin", (0, 7, 0), (-45, 0, 0))
    r.box("right_foot", -1.5, 0, -1.5, 3, 4, 3, "skin")
    r.box("right_foot", -2, 4, -3, 4, 2, 4, "horn")         # hoof
    r.bone("body", None, (0, 8, 1))
    r.box("body", -5, -4, -3, 10, 5, 6, "skin")
    r.bone("chest", "body", (0, -4, 0), (18, 0, 0))
    r.box("chest", -7, -12, -4.5, 14, 12, 9, "skin")
    r.box("chest", -5, -11, -5, 10, 3, 1, "accent")
    for k in range(3):
        r.box("chest", -6, -8 + 3 * k, -5, 12, 1, 1, "bone")
    r.bone("neck", "chest", (0, -12, -2), (-10, 0, 0))
    r.box("neck", -2, -4, -2, 4, 4, 4, "skin")
    r.bone("head", "neck", (0, -4, -1), (-8, 0, 0))
    r.box("head", -3.5, -7, -5, 7, 7, 7, "head")
    r.box("head", -2.5, -4, -9, 5, 4, 4, "bone")             # goat muzzle
    r.bone("jaw", "head", (0, 0, -3), (10, 0, 0))
    r.box("jaw", -2.5, 0, -6, 5, 2, 6, "bone")
    r.box("jaw", -2, -1, -6.2, 4, 1, 1, "tooth")
    r.chain("right_horn", "head", (-3, -6, -2), [(4, 2), (4, 2), (4, 1)], "horn", axis="-y", rot=(-30, 0, -40), each=(-30, 0, 5))
    r.chain("right_horn_low", "head", (-3.5, -3, -1), [(3, 2), (4, 1)], "horn", axis="-x", rot=(0, 30, 20), each=(0, 30, 20))
    r.bone("halo", "head", (0, -10, 3), (70, 0, 0))
    for i in range(10):
        a = 2 * math.pi * i / 10
        r.box("halo", math.cos(a) * 6 - 0.5, -0.5, math.sin(a) * 6 - 0.5, 1, 2, 1, "void")
    # long arms with raking claws
    r.bone("right_arm", "chest", (-8, -11, 0), (-15, 0, 15))
    r.box("right_arm", -2, -1, -2, 4, 10, 4, "skin")
    r.bone("right_forearm", "right_arm", (0, 10, 0), (-25, 0, 0))
    r.box("right_forearm", -1.5, 0, -1.5, 3, 9, 3, "skin")
    r.bone("right_fist", "right_forearm", (0, 9, 0))
    r.box("right_fist", -2, 0, -2, 4, 3, 4, "skin")
    for x in (-2, -0.5, 1):
        r.box("right_fist", x, 3, -2, 1, 7, 1, "claw")
    # wings
    r.bone("right_wing", "chest", (-3, -10, 4), (10, -30, 20))
    r.box("right_wing", -14, -1, -1, 14, 2, 2, "bone")
    r.box("right_wing", -14, 1, 0, 14, 10, 0, "membrane")
    r.bone("right_wing_tip", "right_wing", (-14, 0, 0), (0, 0, -20))
    r.box("right_wing_tip", -12, -0.5, -0.5, 12, 1, 1, "bone")
    r.box("right_wing_tip", -12, 0.5, 0, 12, 13, 0, "membrane")
    for x in (-4, -8):
        r.box("right_wing_tip", x, 0, -0.5, 1, 13, 1, "bone")
    r.mirror()
    r.chain("tail", "body", (0, -1, 3), [(4, 2), (4, 2), (4, 1), (4, 1), (3, 1)], "skin", axis="z", rot=(30, 0, 0), each=(-12, 0, 0))
    r.bone("tail_flame", "tail4", (0, 0, 3))
    r.box("tail_flame", -1.5, -1.5, 0, 3, 3, 3, "flame")
    r.bone("flame", "chest", (0, -6, -4))
    r.box("flame", -1.5, -2, -1, 3, 3, 1, "flame")
    return r


def petrified():
    """The Petrified: a gorgon statue. A coiled serpent body of mossy stone, a cracked torso, a
    crown of stone snakes for hair, a spear in one hand and a shield in the other."""
    r = Rig("petrified")
    r.bone("body", None, (0, 18, 0))
    r.box("body", -6, -2, -6, 12, 6, 12, "skin")            # the coil it rests on
    r.box("body", -7, 2, -7, 14, 4, 14, "skin")
    r.chain("tail", "body", (0, 3, 6), [(5, 5), (5, 4), (5, 4), (4, 3), (4, 3), (4, 2), (3, 1)], "skin", axis="z",
            rot=(0, 40, 0), each=(0, 28, 0))
    r.bone("waist", "body", (0, -2, -1))
    r.box("waist", -4, -6, -3, 8, 6, 6, "skin")
    r.bone("chest", "waist", (0, -6, 0), (6, 0, 0))
    r.box("chest", -6, -10, -4, 12, 10, 8, "skin")
    r.box("chest", -6.5, -10, -4.5, 13, 3, 9, "accent")      # stone mantle
    r.bone("head", "chest", (0, -10, -1))
    r.box("head", -3.5, -7, -4, 7, 7, 7, "head")
    r.box("head", -4, -8, -4.5, 8, 2, 8, "accent")
    r.bone("jaw", "head", (0, -1, -2), (4, 0, 0))
    r.box("jaw", -3, 0, -2.5, 6, 2, 5, "skin")
    # the snakes that are her hair
    for i, (x, z, ry) in enumerate(((-3, -2, -60), (-1, 2, -20), (1, 2, 20), (3, -2, 60), (-3, 2, -110), (3, 2, 110))):
        r.chain(f"strip{i}", "head", (x, -7, z), [(3, 1), (3, 1), (2, 1)], "skin", axis="-y", rot=(-35, ry, 0), each=(25, 0, 0))
    # the spear arm
    r.bone("right_arm", "chest", (-7, -9, 0), (-20, 0, 10))
    r.box("right_arm", -2, -1, -2, 4, 9, 4, "skin")
    r.bone("right_forearm", "right_arm", (0, 9, 0), (-40, 0, 0))
    r.box("right_forearm", -1.5, 0, -1.5, 3, 8, 3, "skin")
    r.bone("right_fist", "right_forearm", (0, 8, 0))
    r.box("right_fist", -2, 0, -2, 4, 3, 4, "skin")
    r.bone("spear", "right_fist", (0, 1, 0), (-90, 0, 0))
    r.box("spear", -0.5, -12, -0.5, 1, 28, 1, "slab")
    r.box("spear", -1.5, 16, -0.5, 3, 5, 1, "crystal")
    # the shield arm
    r.bone("left_arm", "chest", (7, -9, 0), (-20, 0, -10))
    r.box("left_arm", -2, -1, -2, 4, 9, 4, "skin")
    r.bone("left_forearm", "left_arm", (0, 9, 0), (-60, 0, 0))
    r.box("left_forearm", -1.5, 0, -1.5, 3, 8, 3, "skin")
    r.bone("left_fist", "left_forearm", (0, 8, 0))
    r.box("left_fist", -2, 0, -2, 4, 3, 4, "skin")
    r.bone("shield", "left_forearm", (2, 4, 0), (0, 0, 0))
    r.box("shield", 0, -6, -6, 1, 12, 12, "slab")
    r.box("shield", 1, -2, -2, 1, 4, 4, "eye")              # the gorgon face on the shield
    for k in range(3):
        r.spike(f"rock_spike{k}", "chest", (-4 + 4 * k, -10, 3), 4 + k % 2 * 2, (-40, 0, -20 + 20 * k), "rock", base=2)
    return r


def ashgrazer():
    r = quadruped("ashgrazer", neck=7, leg=1.0)
    r.chain("right_antler", "head", (-1.5, -3, -2), [(4, 1), (3, 1), (3, 1)], "horn", axis="-y", rot=(-10, 0, -25), each=(-20, 0, -15))
    r.bone("right_antler_tine", "right_antler1", (0, -1, 0), (0, 0, 60))
    r.box("right_antler_tine", -0.5, -3, -0.5, 1, 3, 1, "horn")
    r.mirror("right_antler", "left_antler")
    r.box("neck", -2.5, -6, 1, 5, 5, 2, "accent")  # mane
    return r


def gloomhog():
    r = quadruped("gloomhog", neck=2, leg=0.7, body_len=14, size=9)
    r.box("head", -1.5, 1, -9, 1, 3, 1, "tooth")
    r.box("head", 0.5, 1, -9, 1, 3, 1, "tooth")
    for i in range(5):
        r.box("body", -0.5, -6, -6 + i * 3, 1, 2, 2, "accent")  # bristle ridge
    return r


def cinderhen():
    r = flyer("cinderhen", wing=0.8)
    r.box("head", -0.5, -5, -2, 1, 2, 3, "flame")  # comb of embers
    r.box("head", -0.5, 0, -3.5, 1, 1, 1, "accent")
    r.chain("plume", "tail", (0, 0, 2), [(3, 1), (3, 1)], "flame", axis="-y", rot=(-40, 0, 0), each=(-15, 0, 0))
    return r


def marrow_ox():
    r = quadruped("marrow_ox", neck=4, leg=1.0, body_len=16, size=10)
    r.box("body", -3.5, -7, -6, 7, 3, 6, "skin")  # hump
    r.chain("right_horn", "head", (-3, -2, -3), [(3, 2), (3, 1), (2, 1)], "horn", axis="-x", rot=(0, 0, -10), each=(0, 0, -40))
    r.mirror("right_horn", "left_horn")
    for i in range(4):
        r.box("body", -1.5, -5, -4 + i * 3, 3, 1, 2, "bone")  # bone plates along the spine
    return r


def duskhare():
    r = critter("duskhare", legs=2)
    r.chain("right_ear", "head", (-1, -2.5, -1), [(3, 1), (3, 1)], "accent", axis="-y", rot=(-15, 0, -10), each=(25, 0, 0))
    r.mirror("right_ear", "left_ear")
    r.box("right_hind_leg", -1, 1, -3, 2, 1, 3, "skin")
    r.box("left_hind_leg", -1, 1, -3, 2, 1, 3, "skin")
    return r


def lantern_moth():
    r = flyer("lantern_moth", wing=1.0)
    r.bone("right_wing_low", "body", (-2, 0, 1), (0, 20, 0))
    r.box("right_wing_low", -5, 0, -2, 5, 1, 4, "wing")
    r.mirror("right_wing_low", "left_wing_low")
    r.chain("right_antenna", "head", (-1, -3, -2), [(3, 1), (2, 1)], "claw", axis="-y", rot=(-30, 0, -20), each=(-30, 0, 0))
    r.mirror("right_antenna", "left_antenna")
    r.box("tail", -1.5, -1, 0, 3, 3, 3, "glow")  # the lantern
    return r


def hush_owl():
    r = flyer("hush_owl", wing=0.9)
    r.box("head", -2, -3.5, -3.2, 4, 3, 1, "belly")  # facial disc
    r.box("head", -2, -5, -1, 1, 2, 1, "accent")
    r.box("head", 1, -5, -1, 1, 2, 1, "accent")
    return r


def gravecat():
    r = quadruped("gravecat", neck=2, leg=0.8, body_len=12, size=6)
    for side, x in (("a", -1), ("b", 1)):
        r.chain(f"tail_{side}", "body", (x, -3, 6), [(3, 1), (3, 1), (3, 1)], "accent", axis="-y", rot=(-30, 0, 15 * x), each=(-15, 0, 0))
    return r


def wisplet():
    r = critter("wisplet", legs=1)
    for i, (x, z, h) in enumerate(((-1, -1, 3), (1, -1, 4), (0, 1, 5))):
        r.bone(f"flame{i}", "head", (x, -2.5, z))
        r.box(f"flame{i}", -1, -h, -1, 2, h, 2, "flame")
    r.orbit("orbit", "body", (0, -1, 0), 5, 4, 1, "glow")
    return r


def shellmourn():
    r = critter("shellmourn", legs=1)
    r.box("body", -2.5, -2.5, -6, 5, 3, 3, "skin")
    r.bone("shell", "body", (0, -2.5, 1))
    for i, s in enumerate((6, 5, 4, 3, 2)):
        r.box("shell", -s / 2, -2 - i * 1.5, -s / 2 + i * 0.5, s, 2, s, "shell")
    for side, x in (("right", -1), ("left", 1)):
        r.chain(f"{side}_eye_stalk", "head", (x, -2.5, -2), [(2, 1), (2, 1)], "skin", axis="-y", rot=(-15, 0, x * 15))
        r.box(f"{side}_eye_stalk1", -0.5 - 0.5, -3, -1, 2, 1, 2, "eye")
    return r


def candlewick_toad():
    r = critter("candlewick_toad", legs=2)
    r.box("body", -3.5, -2, -3, 7, 4, 6, "skin")
    r.box("head", -2.5, 1, -3, 5, 1, 2, "pustule")  # throat sac
    for i, (x, z, h) in enumerate(((-2, -1, 3), (1, 0, 4), (-1, 2, 2), (2, 2, 3))):
        r.bone(f"candle{i}", "body", (x, -2.5, z))
        r.box(f"candle{i}", -0.5, -h, -0.5, 1, h, 1, "wax")
        r.box(f"candle{i}", -0.5, -h - 1, -0.5, 1, 1, 1, "flame")
    return r


def mossback_tortoise():
    r = critter("mossback_tortoise", legs=2)
    r.bone("shell", "body", (0, -2.5, 0))
    r.box("shell", -3.5, -3, -4.5, 7, 3, 9, "shell")
    r.box("shell", -2.5, -4, -3.5, 5, 1, 7, "shell")
    for i, (x, z, h) in enumerate(((-2, -2, 2), (1, 1, 3), (2, -2, 1), (-1, 2, 2))):
        r.box("shell", x, -4 - h, z, 1, h, 1, "moss")
    r.box("shell", 0, -6, -1, 2, 1, 2, "mushroom")
    r.box("head", -1.5, 1.5, -3, 3, 1, 2, "moss")  # moss beard
    return r


def ember_fox():
    r = quadruped("ember_fox", neck=2, leg=0.8, body_len=12, size=6)
    r.box("right_ear", -1, -1, -0.5, 2, 1, 2, "accent")
    r.box("tail", -1.5, 0, -0.5, 3, 3, 3, "accent")
    r.box("tail1", -1.5, 0, -0.5, 3, 3, 3, "accent")
    r.box("tail2", -1, 0, -1, 2, 2, 2, "flame")
    return r


def veilfawn():
    r = quadruped("veilfawn", neck=7, leg=1.25, body_len=13, size=7)
    r.chain("right_antler", "head", (-1.5, -3, -2), [(3, 1), (2, 1)], "horn", axis="-y", rot=(-10, 0, -20), each=(-20, 0, -20))
    r.mirror("right_antler", "left_antler")
    r.bone("veil", "head", (0, -3, -1), (10, 0, 0))
    r.box("veil", -4, 0, -1, 8, 7, 1, "veil")
    return r


def grimfinch():
    r = flyer("grimfinch", wing=0.8)
    r.chain("crest", "head", (0, -3, -1), [(2, 1), (2, 1)], "accent", axis="-y", rot=(-30, 0, 0), each=(-30, 0, 0))
    r.chain("tail_long", "tail", (0, 0, 3), [(3, 1), (3, 1)], "wing", axis="z", rot=(10, 0, 0))
    return r


def sorrowdove():
    r = flyer("sorrowdove", wing=0.9)
    r.box("tail", -3, 0, 4, 6, 1, 2, "wing")
    r.box("head", -1, 0, -3.5, 2, 1, 1, "accent")
    return r


def stormstag():
    r = quadruped("stormstag", neck=8, leg=1.15, body_len=16, size=9)
    r.chain("right_antler", "head", (-1.5, -3, -2), [(5, 1), (4, 1), (4, 1), (3, 1)], "antler", axis="-y",
            rot=(-10, 0, -25), each=(-15, 0, -15))
    for i, b in enumerate(("right_antler1", "right_antler2")):
        r.bone(f"right_antler_tine{i}", b, (0, -1, 0), (0, 0, 70))
        r.box(f"right_antler_tine{i}", -0.5, -4, -0.5, 1, 4, 1, "antler")
    r.mirror("right_antler", "left_antler")
    for i in range(5):
        r.box("neck", -0.5, -7 + i, 1.5, 1, 1, 3, "glow")  # crackling mane
    return r


def cragstrider():
    r = quadruped("cragstrider", neck=5, leg=1.1, body_len=14, size=8)
    r.chain("right_horn", "head", (-3, -3, -2), [(3, 2), (3, 2), (2, 1)], "horn", axis="z", rot=(-30, -30, 0), each=(70, 0, 0))
    r.mirror("right_horn", "left_horn")
    for i, (x, z) in enumerate(((-2, -5), (1, -1), (-1, 3), (2, 5))):
        r.box("body", x - 1.5, -6, z - 1.5, 3, 2, 3, "rock")
    return r


def lumen_beetle():
    r = critter("lumen_beetle", legs=2)
    r.box("head", -0.5, -4, -4, 1, 3, 1, "shell")  # horn
    r.box("head", -0.5, -5, -3, 1, 1, 1, "shell")
    for side, x in (("right", -1), ("left", 1)):
        r.bone(f"{side}_elytra", "body", (x * 0.5, -2.5, -3), (0, 0, x * 5))
        r.box(f"{side}_elytra", min(0, x * 3), -1, 0, 3, 1, 7, "shell")
    r.bone("right_mid_leg", None, (-2, 22, 0))
    r.box("right_mid_leg", -1, 0, -1, 2, 2, 2, "skin")
    r.mirror("right_mid", "left_mid")
    return r


def hollowfleece():
    r = quadruped("hollowfleece", neck=3, leg=0.9, body_len=14, size=9)
    r.box("body", -5.5, -5.5, -7.5, 11, 10, 15, "wool")
    r.box("head", -2, -2, -6.5, 1, 1, 1, "eye")  # an extra pair of eyes
    r.box("head", 1, -2, -6.5, 1, 1, 1, "eye")
    r.chain("right_horn", "head", (-3, -3, -2), [(2, 2), (2, 1), (2, 1)], "horn", axis="z", rot=(-20, -40, 0), each=(80, 0, 0))
    r.mirror("right_horn", "left_horn")
    return r


def skeleton():
    """The Skeleton: the Ossuary Colossus. A hunched giant of thick, yellowed bone with a horned,
    iron-crowned skull, a ribcage caging a soul fire, shackled wrists trailing broken chains, a
    cleaver hewn from a giant's jawbone, a cage of souls lashed to its spine and a spiked tail."""
    r = Rig("skeleton")
    # legs: heavy femur with a knee knob, twin shin bones, splayed clawed foot
    r.bone("right_leg", None, (-5, 12, 1))
    r.box("right_leg", -2.5, -1, -2.5, 5, 3, 5, "bone")
    r.box("right_leg", -1.5, 2, -1.5, 3, 4, 3, "bone")
    r.bone("right_shin", "right_leg", (0, 6, 0), (8, 0, 0))
    r.box("right_shin", -2, -1, -2, 4, 2, 4, "bone")
    r.box("right_shin", -1.5, 1, -1, 2, 4, 2, "bone")
    r.box("right_shin", 0.5, 1, -0.5, 1, 4, 1, "bone")
    r.box("right_shin", -2.5, 2, -2.5, 5, 2, 5, "metal")  # rusted greave band
    r.bone("right_foot", "right_shin", (0, 5, 0), (-8, 0, 0))
    r.box("right_foot", -2, 0, -3, 4, 1, 4, "bone")
    for x in (-2, -0.5, 1):
        r.box("right_foot", x, 0, -6, 1, 1, 3, "claw")
    # pelvis and lower spine
    r.bone("body", None, (0, 12, 1))
    r.box("body", -6, -1, -3, 12, 3, 6, "bone")
    r.box("body", -7, -3, -2, 3, 3, 4, "bone")
    r.box("body", 4, -3, -2, 3, 3, 4, "bone")
    r.box("body", -6.5, 1, -3.5, 13, 5, 7, "cloth")  # rotten loincloth
    for k in range(4):
        r.box("body", -1.5, -3 - 2 * k, -1.5, 3, 1, 3, "bone")
        r.box("body", -1, -2 - 2 * k, -1, 2, 1, 2, "bone")
    # the ribcage, pitched forward
    r.bone("chest", "body", (0, -9, 0), (32, 0, 0))
    r.box("chest", -2, -16, 2.5, 4, 16, 3, "bone")            # spine
    r.box("chest", -1, -13, -7, 2, 10, 2, "bone")             # sternum
    for k in range(6):
        y = -14 + 2 * k
        hw = 8 - abs(k - 2) * 0.8
        r.box("chest", -hw, y, -6.5, hw - 1, 1, 1, "bone")
        r.box("chest", 1, y, -6.5, hw - 1, 1, 1, "bone")
        r.box("chest", -hw - 1, y, -6, 1, 1, 9, "bone")
        r.box("chest", hw, y, -6, 1, 1, 9, "bone")
    r.box("chest", -12, -17, -2.5, 24, 2, 5, "bone")          # shoulder yoke
    r.box("chest", -9, -16, 3, 6, 6, 1, "bone")               # scapulae
    r.box("chest", 3, -16, 3, 6, 6, 1, "bone")
    r.bone("flame", "chest", (0, -8, -1.5))                   # the soul fire inside the cage
    r.box("flame", -2.5, -3, -2.5, 5, 5, 5, "flame")
    r.box("flame", -1.5, -6, -1.5, 3, 3, 3, "flame")
    r.box("flame", -3, -1, -3, 6, 1, 6, "metal")              # iron brazier holding it
    for k in range(4):
        r.spike(f"spine_spike{k}", "chest", (0, -15 + 4 * k, 5.5), 6 - k, rot=(-60, 0, 0), role="bone", base=2)
    # the cage of souls lashed to its back
    r.bone("soul_cage", "chest", (0, -12, 6), (-32, 0, 0))
    for x in (-3, 2):
        for z in (0, 5):
            r.box("soul_cage", x, -6, z, 1, 7, 1, "metal")
    r.box("soul_cage", -3.5, -7, -0.5, 7, 1, 7, "metal")
    r.box("soul_cage", -3.5, 1, -0.5, 7, 1, 7, "metal")
    r.box("soul_cage", -2, -5, 1.5, 4, 4, 3, "glow")
    # head: a huge horned skull with an iron crown driven into it
    r.bone("head", "chest", (0, -17, -3), (-32, 0, 0))
    r.box("head", -5, -9, -9, 10, 9, 10, "head")
    r.box("head", -5.5, -6, -9.5, 11, 2, 1, "bone")           # heavy brow
    r.box("head", -5.5, -3, -9, 2, 3, 6, "bone")              # cheekbones
    r.box("head", 3.5, -3, -9, 2, 3, 6, "bone")
    r.box("head", -4, -1, -9.6, 8, 1, 1, "tooth")
    r.chain("right_horn", "head", (-4.5, -7, -4), [(4, 3), (4, 2), (4, 2), (3, 1)], "horn", axis="-y",
            rot=(-10, 0, -70), each=(-35, 0, 15))
    r.chain("left_horn", "head", (4.5, -7, -4), [(4, 3), (4, 2), (4, 2), (3, 1)], "horn", axis="-y",
            rot=(-10, 0, 70), each=(-35, 0, -15))
    r.bone("crown", "head", (0, -9, -4), (0, 0, -9))
    r.box("crown", -5.5, -1, -5.5, 11, 2, 11, "metal")
    for i, (x, z) in enumerate(((-5, -5), (-1, -5.5), (3, -5), (-5, 3), (3, 3))):
        r.spike(f"crown_spike{i}", "crown", (x + 0.5, -1, z + 0.5), 3 + (i % 3), role="metal", base=1)
    r.bone("jaw", "head", (0, -1, -3), (16, 0, 0))
    r.box("jaw", -4.5, 0, -6.5, 9, 3, 7, "bone")
    r.box("jaw", -4, -1, -6.8, 8, 1, 1, "tooth")
    r.box("jaw", -4.5, -3, -6.7, 1, 3, 1, "tooth")            # tusks
    r.box("jaw", 3.5, -3, -6.7, 1, 3, 1, "tooth")
    # arms: shoulder ball, humerus, twin forearm bones, a huge bony hand, iron shackles
    r.bone("right_arm", "chest", (-13, -15, 0), (-25, 0, 12))
    r.box("right_arm", -3, -3, -3, 6, 5, 6, "bone")
    r.box("right_arm", -1.5, 2, -1.5, 3, 10, 3, "bone")
    r.box("right_arm", -3.5, -4, -3.5, 7, 3, 7, "metal")       # pauldron
    r.spike("right_pauldron_spike", "right_arm", (-1, -4, 0), 5, rot=(0, 0, -30), role="metal", base=2)
    r.bone("right_forearm", "right_arm", (0, 12, 0), (-20, 0, 0))
    r.box("right_forearm", -2, -1, -2, 4, 3, 4, "bone")
    r.box("right_forearm", -1.5, 2, -1, 2, 10, 2, "bone")
    r.box("right_forearm", 0.5, 2, -0.5, 1, 10, 1, "bone")
    r.box("right_forearm", -3, 9, -3, 6, 2, 6, "metal")        # shackle
    r.chain("right_shackle_chain", "right_forearm", (-2, 11, 0), [(3, 1), (3, 1), (3, 1), (2, 1)], "metal", axis="y",
            rot=(10, 0, 15), each=(12, 0, 0))
    r.bone("right_fist", "right_forearm", (0, 12, 0))
    r.box("right_fist", -2.5, 0, -2.5, 5, 3, 5, "bone")
    for x in (-2.5, -1, 0.5, 2):
        r.box("right_fist", x - 0.5 if x > 0 else x, 3, -2.5, 1, 6, 1, "claw")
    r.mirror()
    # a cleaver hewn from a giant's jawbone, in the right hand
    r.bone("cleaver", "right_fist", (0, 3, -1), (-80, 0, 0))
    r.box("cleaver", -1, -4, -1, 2, 6, 2, "cloth")
    r.box("cleaver", -1.5, 2, -1.5, 3, 2, 3, "metal")
    r.box("cleaver", -1, 4, -1, 2, 16, 8, "bone")
    r.box("cleaver", -1, 6, 7, 2, 14, 2, "metal")             # rusted edge
    for k in range(4):
        r.box("cleaver", -1.5, 6 + 3 * k, -2, 3, 2, 1, "tooth")  # teeth still in the jawbone
    # a tattered war banner hanging from the yoke
    r.chain("cape", "chest", (0, -16, 4.5), [(7, 1), (7, 1), (6, 1)], "cloth", axis="y", rot=(-20, 0, 0), each=(8, 0, 0))
    r.box("cape", -7, 0, -0.5, 14, 7, 1, "cloth")
    r.box("cape1", -7, 0, -0.5, 14, 7, 1, "cloth")
    r.box("cape2", -7, 0, -0.5, 5, 6, 1, "cloth")
    r.box("cape2", 0, 0, -0.5, 6, 4, 1, "cloth")
    # a long spiked tail of vertebrae dragging behind
    r.chain("tail", "body", (0, 0, 3), [(4, 3), (4, 3), (4, 2), (4, 2), (3, 2), (3, 1)], "bone", axis="z",
            rot=(25, 0, 0), each=(-6, 0, 0))
    return r



# =============================================================================================
# New hostiles
# =============================================================================================
def butcher():
    """The Butcher: a huge, sack-hooded slaughterman with a gut, a bloody apron, a meat cleaver
    in one hand and a chain of hooks in the other."""
    r = Rig("butcher")
    r.bone("right_leg", None, (-3.5, 13, 0))
    r.box("right_leg", -2.5, 0, -2.5, 5, 6, 5, "cloth")
    r.bone("right_shin", "right_leg", (0, 6, 0))
    r.box("right_shin", -2, 0, -2, 4, 4, 4, "cloth")
    r.bone("right_foot", "right_shin", (0, 4, 0))
    r.box("right_foot", -2.5, 0, -3.5, 5, 1, 6, "leather")
    r.bone("body", None, (0, 13, 0))
    r.box("body", -6, -6, -4.5, 12, 7, 9, "skin")          # the gut
    r.box("body", -6.5, -6, -5, 13, 9, 1, "bandage")        # the apron
    r.bone("chest", "body", (0, -6, 0), (14, 0, 0))
    r.box("chest", -7, -9, -4, 14, 9, 8, "skin")
    r.box("chest", -6.5, -9, -4.5, 13, 9, 1, "bandage")
    r.box("chest", -7.5, -3, -4.5, 15, 2, 9, "leather")     # belt of knives
    for x in (-5, -2, 3):
        r.box("chest", x, -2, -5.5, 1, 4, 1, "metal")
    r.bone("head", "chest", (0, -9, -1), (-14, 0, 0))
    r.box("head", -4, -8, -4, 8, 8, 8, "cloth")              # burlap sack hood
    r.box("head", -3, -5, -4.4, 2, 1, 1, "eye")
    r.box("head", 1, -5, -4.4, 2, 1, 1, "eye")
    r.box("head", -2, -2, -4.4, 4, 1, 1, "tooth")
    r.box("head", -4.5, -1, -4.5, 9, 1, 9, "leather")        # rope at the neck
    r.bone("jaw", "head", (0, 0, -1))
    r.box("jaw", -3, 0, -3, 6, 1, 4, "skin")
    r.bone("right_arm", "chest", (-8, -8, 0), (-10, 0, 10))
    r.box("right_arm", -2.5, -2, -2.5, 5, 9, 5, "skin")
    r.bone("right_forearm", "right_arm", (0, 7, 0), (-20, 0, 0))
    r.box("right_forearm", -2.5, 0, -2.5, 5, 7, 5, "skin")
    r.box("right_forearm", -3, 3, -3, 6, 4, 6, "bandage")
    r.bone("right_fist", "right_forearm", (0, 7, 0))
    r.box("right_fist", -2.5, 0, -2.5, 5, 4, 5, "skin")
    r.mirror()
    r.bone("cleaver", "right_fist", (0, 2, -1), (-70, 0, 0))
    r.box("cleaver", -0.5, -3, -0.5, 1, 4, 1, "leather")
    r.box("cleaver", -0.5, 1, -0.5, 1, 9, 6, "metal")
    r.chain("lantern_chain", "left_fist", (0, 4, 0), [(3, 1), (3, 1), (3, 1)], "metal", axis="y")
    r.bone("hook", "lantern_chain2", (0, 3, 0))
    r.box("hook", -0.5, 0, -0.5, 1, 3, 1, "metal")
    r.box("hook", -0.5, 2, -2.5, 1, 1, 2, "metal")
    for i, x in enumerate((-4, 0, 4)):
        r.bone(f"strip{i}", "chest", (x, -8, 4.5), (10, 0, 0))   # meat hooks on its back
        r.box(f"strip{i}", -0.5, 0, -0.5, 1, 3, 1, "metal")
        r.box(f"strip{i}", -1.5, 3, -1.5, 3, 4, 3, "flesh")
    return r


def gravewyrm():
    """The Gravewyrm: a pale segmented burrower with a three-way jaw and grasping feelers."""
    r = Rig("gravewyrm")
    r.bone("body", None, (0, 18, 0))
    r.box("body", -4, -4, -5, 8, 8, 10, "skin")
    for k in range(3):
        r.box("body", -4.5, -4.5, -4 + 3 * k, 9, 9, 1, "accent")
    r.bone("head", "body", (0, -1, -5), (-25, 0, 0))
    r.box("head", -3.5, -3.5, -5, 7, 7, 5, "head")
    for i, (rx, rz) in enumerate(((25, 0), (-10, 40), (-10, -40))):     # three jaws
        r.bone(f"mandible{i}", "head", (0, 0, -5), (rx, 0, rz))
        r.box(f"mandible{i}", -1.5, -4, -4, 3, 4, 4, "skin")
        r.box(f"mandible{i}", -1, -4, -4.5, 2, 1, 1, "tooth")
    r.bone("jaw", "head", (0, 2, -5), (20, 0, 0))
    r.box("jaw", -2, 0, -3, 4, 1, 3, "tooth")
    for i, x in enumerate((-3, 3)):
        r.chain(f"antenna{i}", "head", (x, -3, -3), [(3, 1), (3, 1)], "skin", axis="-y", rot=(-40, 0, 30 * (-1 if x < 0 else 1)), each=(30, 0, 0))
    r.chain("tail", "body", (0, 0, 5), [(5, 7), (5, 6), (5, 5), (4, 4), (4, 3), (3, 2)], "skin", axis="z", rot=(10, 0, 0), each=(-4, 0, 0))
    for i in range(3):
        r.bone(f"right_leg{i}", "body", (-4, 2, -3 + 3 * i), (0, 0, 40))
        r.box(f"right_leg{i}", -4, -0.5, -0.5, 4, 1, 1, "claw")
        r.bone(f"right_leg{i}_lower", f"right_leg{i}", (-4, 0, 0), (0, 0, -70))
        r.box(f"right_leg{i}_lower", -3, -0.5, -0.5, 3, 1, 1, "claw")
    r.mirror()
    return r


def hanged_man():
    """The Hanged Man: a corpse that floats with its noose still on, neck broken, rope trailing up."""
    r = wraith("hanged_man", robe=2, hood=False, arm=1.2)
    r.bone("noose", "neck", (0, -1, 0))
    r.box("noose", -1.5, -1, -1.5, 3, 1, 3, "leather")
    r.chain("lantern_chain", "noose", (0, -1, 1.5), [(4, 1), (4, 1), (4, 1), (4, 1)], "leather", axis="-y", rot=(-15, 0, 0))
    r.by["head"].rot = (0.2, 0.3, math.radians(40))           # broken neck
    r.box("head", -2.5, -3, -3.8, 5, 1, 1, "tooth")
    for i in range(3):
        r.chain(f"strip{i + 3}", "robe1", (-3 + 3 * i, 5, 0), [(3, 1), (3, 1)], "cloth", axis="y")
    return r


def mire_hag():
    """The Mire Hag: a stooped swamp crone, hair hanging to her knees, a skull-topped staff."""
    r = gaunt("mire_hag", arm=1.1, hunch=30)
    for i, x in enumerate((-3, -1, 1, 3)):
        r.chain(f"strip{i}", "head", (x, -6, 2), [(4, 1), (4, 1), (3, 1)], "accent", axis="y", rot=(15, 0, 0))
    r.box("head", -1, -3, -6, 2, 3, 2, "skin")                 # long hooked nose
    for i, (x, y, z) in enumerate(((-3, -6, -3), (2, -4, 2), (-1, -7, 1))):
        r.box("chest", x, y, z, 2, 2, 1, "pustule")
    r.bone("staff", "right_hand", (0, 2, 0), (-10, 0, 0))
    r.box("staff", -0.5, -16, -0.5, 1, 24, 1, "leather")
    r.box("staff", -2, -20, -2, 4, 4, 4, "bone")
    r.box("staff", -1.5, -19, -2.2, 1, 1, 1, "eye")
    r.box("staff", 0.5, -19, -2.2, 1, 1, 1, "eye")
    return r


def lamplighter():
    """The Lamplighter: a gaunt figure in a long coat whose head is an iron lantern cage with a
    fire inside, carrying a lantern on a pole."""
    r = gaunt("lamplighter", arm=1.15, hunch=6)
    r.by["head"].cubes.clear()
    for x in (-3.5, 2.5):
        for z in (-3.5, 2.5):
            r.box("head", x, -8, z, 1, 8, 1, "metal")
    r.box("head", -4, -9, -4, 8, 1, 8, "metal")
    r.box("head", -4, 0, -4, 8, 1, 8, "metal")
    r.box("head", -3, -11, -3, 6, 2, 6, "metal")
    r.box("head", -1, -13, -1, 2, 2, 2, "metal")
    r.bone("flame", "head", (0, -3, 0))
    r.box("flame", -2, -3, -2, 4, 4, 4, "flame")
    r.box("flame", -1, -5, -1, 2, 2, 2, "flame")
    for i, x in enumerate((-3, 0, 3)):
        r.chain(f"strip{i}", "body", (x, 0, 2), [(4, 1), (4, 1)], "cloth", axis="y")
    r.bone("pole", "right_hand", (0, 2, 0), (-20, 0, 0))
    r.box("pole", -0.5, -18, -0.5, 1, 22, 1, "leather")
    r.box("pole", -0.5, -18, -4, 1, 1, 4, "leather")
    r.chain("lantern_chain", "pole", (0, -17, -4), [(2, 1), (2, 1)], "metal", axis="y")
    r.bone("lantern", "lantern_chain1", (0, 2, 0))
    r.box("lantern", -1.5, 0, -1.5, 3, 4, 3, "metal")
    r.box("lantern", -1, 0.5, -1, 2, 3, 2, "glow")
    return r


# =============================================================================================
# New bosses
# =============================================================================================
def hive_mother():
    """The Hive Mother: a vast crawler queen with a glowing, pulsing egg sac, a crown of spines,
    scything forelimbs and four pairs of legs."""
    r = crawler("hive_mother", pairs=4, leg_len=1.5, body_w=14, abdomen=(18, 14, 20))
    for i, (x, y, z, sz) in enumerate(((-7, -9, 3, 4), (4, -10, 6, 4), (-3, -11, 12, 3), (6, -6, 14, 3), (-8, -4, 14, 3),
                                       (0, -8, 17, 3), (2, -12, 9, 3), (-6, -12, 7, 2))):
        r.box("abdomen", x, y, z, sz, sz, sz, "pustule")
    for i in range(5):
        r.spike(f"spine_spike{i}", "head", (-4 + 2 * i, -3, -1), 4 + (i == 2) * 3, (-20, 0, -30 + 15 * i), "spike", base=2)
    for side, x in (("right", -1), ("left", 1)):
        r.bone(f"{side}_scythe", "head", (x * 3, 1, -3), (-30, x * 20, 0))
        r.box(f"{side}_scythe", -1, 0, -1, 2, 8, 2, "skin")
        r.bone(f"{side}_scythe_blade", f"{side}_scythe", (0, 8, 0), (-110, 0, 0))
        r.box(f"{side}_scythe_blade", -0.5, 0, -1, 1, 12, 3, "claw")
    r.chain("drool", "jaw", (0, 1, -2.5), [(3, 1), (3, 1)], "pustule", axis="y")
    return r


def wailing_bride():
    """The Wailing Bride: a towering spirit in a rotted wedding gown, her veil and train trailing
    for metres behind her, her arms impossibly long, a bouquet of dead roses in her hand."""
    r = wraith("wailing_bride", robe=4, hood=False, arm=1.5)
    r.chain("veil", "head", (0, -7, 3.5), [(6, 1), (6, 1), (6, 1), (5, 1)], "cloth", axis="y", rot=(25, 0, 0), each=(8, 0, 0))
    r.box("veil", -5, 0, -0.5, 10, 6, 1, "cloth")
    r.box("veil1", -5.5, 0, -0.5, 11, 6, 1, "cloth")
    r.box("veil2", -6, 0, -0.5, 12, 6, 1, "cloth")
    r.box("veil3", -6, 0, -0.5, 12, 5, 1, "cloth")
    r.bone("crown", "head", (0, -7, 0))
    for i in range(7):
        a = 2 * math.pi * i / 7
        r.box("crown", math.cos(a) * 3.5 - 0.5, -3 - (i % 2), math.sin(a) * 3.5 - 0.5, 1, 3 + (i % 2), 1, "claw")
    r.chain("tail_long", "robe3", (0, 4, 2), [(6, 2), (6, 2), (6, 1)], "cloth", axis="z", rot=(50, 0, 0), each=(10, 0, 0))
    r.box("tail_long", -5, -1, 0, 10, 2, 6, "cloth")
    r.box("tail_long1", -6, -1, 0, 12, 2, 6, "cloth")
    r.box("tail_long2", -6, -0.5, 0, 12, 1, 6, "cloth")
    r.bone("bouquet", "left_hand", (0, 4, 0))
    r.box("bouquet", -0.5, 0, -0.5, 1, 4, 1, "moss")
    for x, z in ((-1.5, -1), (0.5, -1.5), (-0.5, 0.5), (1, 0.5)):
        r.box("bouquet", x, -2, z, 2, 2, 2, "flesh")
    r.orbit("orbit", "body", (0, -16, 0), 9, 6, 1, "glow", height=2)
    return r


# =============================================================================================
# New peaceful creatures
# =============================================================================================
def ashen_goat():
    r = quadruped("ashen_goat", neck=5, leg=0.9, body_len=13, size=7)
    r.chain("right_horn", "head", (-2, -3, -2), [(3, 2), (3, 1), (3, 1)], "horn", axis="-y", rot=(-40, 0, -30), each=(-50, 0, 0))
    r.mirror("right_horn", "left_horn")
    r.box("jaw", -1, 1, -2, 2, 3, 1, "accent")                 # beard
    return r


def cryptling():
    r = critter("cryptling", legs=1)
    r.by["head"].cubes.clear()
    r.box("head", -2.5, -4, -4, 5, 5, 5, "bone")               # a little skull for a shell
    r.box("head", -1.5, -2, -4.2, 1, 1, 1, "eye")
    r.box("head", 0.5, -2, -4.2, 1, 1, 1, "eye")
    for i in range(3):
        r.bone(f"right_leg{i}x", "body", (-2.5, 1, -2 + 2 * i), (0, 0, 40))
        r.box(f"right_leg{i}x", -3, -0.5, -0.5, 3, 1, 1, "claw")
    r.mirror("right_leg", "left_leg")
    return r


def veil_jelly():
    r = Rig("veil_jelly")
    r.bone("body", None, (0, 12, 0))
    r.box("body", -4, -6, -4, 8, 6, 8, "veil")
    r.box("body", -3, -8, -3, 6, 2, 6, "veil")
    r.box("body", -1.5, -5, -1.5, 3, 3, 3, "glow")
    r.bone("head", "body", (0, 0, 0))
    for i, (x, z) in enumerate(((-3, -3), (3, -3), (-3, 3), (3, 3), (0, 0))):
        r.chain(f"strip{i}", "body", (x, 0, z), [(4, 1), (4, 1), (3, 1)], "veil", axis="y")
    return r


def sinew_slug():
    r = critter("sinew_slug", legs=1)
    r.box("body", -3, -1.5, -3.5, 6, 3, 10, "skin")
    for i, x in enumerate((-1, 1)):
        r.chain(f"antenna{i}", "head", (x, -2.5, -2), [(2, 1), (2, 1)], "skin", axis="-y", rot=(-20, 0, 15 * x), each=(15, 0, 0))
        r.box(f"antenna{i}1", -0.5, -3, -0.5, 1, 1, 1, "eye")
    r.chain("tail_a", "body", (0, 1, 3.5), [(3, 3), (3, 2)], "skin", axis="z")
    return r


def skyray():
    r = flyer("skyray", wing=2.2)
    r.box("body", -3, -1, -4, 6, 2, 8, "skin")
    r.chain("tail_long", "body", (0, 0, 4), [(4, 1), (4, 1), (4, 1)], "accent", axis="z")
    r.box("head", -2, -2, -3.5, 4, 1, 1, "accent")
    return r


CREATURES = {
    "the_hollow": the_hollow, "severed": severed, "cracked": cracked, "depart": depart, "scorched": scorched,
    "parasite": parasite, "illusion": illusion, "the_forgotten": the_forgotten, "wretched": wretched, "ghost": ghost,
    "blood_demon": blood_demon, "star_fall": star_fall, "collapse": collapse, "fracture": fracture, "chronos": chronos,
    "dim_light_demon": dim_light_demon, "petrified": petrified,
    "ashgrazer": ashgrazer, "gloomhog": gloomhog, "cinderhen": cinderhen, "marrow_ox": marrow_ox, "duskhare": duskhare,
    "lantern_moth": lantern_moth, "hush_owl": hush_owl, "gravecat": gravecat, "wisplet": wisplet,
    "shellmourn": shellmourn, "candlewick_toad": candlewick_toad, "mossback_tortoise": mossback_tortoise,
    "ember_fox": ember_fox, "veilfawn": veilfawn, "grimfinch": grimfinch, "sorrowdove": sorrowdove,
    "stormstag": stormstag, "cragstrider": cragstrider, "lumen_beetle": lumen_beetle, "hollowfleece": hollowfleece,
    "skeleton": skeleton,
    "butcher": butcher, "gravewyrm": gravewyrm, "hanged_man": hanged_man, "mire_hag": mire_hag, "lamplighter": lamplighter,
    "hive_mother": hive_mother, "wailing_bride": wailing_bride,
    "ashen_goat": ashen_goat, "cryptling": cryptling, "veil_jelly": veil_jelly, "sinew_slug": sinew_slug, "skyray": skyray,
}

ARCHETYPE_OF = {
    "the_hollow": "gaunt", "scorched": "gaunt", "the_forgotten": "gaunt", "severed": "crawler", "parasite": "crawler",
    "wretched": "crawler", "depart": "wraith", "illusion": "wraith", "ghost": "wraith", "star_fall": "wraith",
    "chronos": "wraith", "cracked": "brute", "blood_demon": "brute", "collapse": "brute", "fracture": "brute",
    "dim_light_demon": "brute", "petrified": "brute", "skeleton": "brute",
    "butcher": "brute", "gravewyrm": "crawler", "hanged_man": "wraith", "mire_hag": "gaunt", "lamplighter": "gaunt",
    "hive_mother": "crawler", "wailing_bride": "wraith",
    "ashen_goat": "quadruped", "cryptling": "critter", "veil_jelly": "critter", "sinew_slug": "critter", "skyray": "flyer",
    "ashgrazer": "quadruped", "gloomhog": "quadruped", "marrow_ox": "quadruped", "gravecat": "quadruped",
    "ember_fox": "quadruped", "veilfawn": "quadruped", "stormstag": "quadruped", "cragstrider": "quadruped",
    "hollowfleece": "quadruped", "duskhare": "critter", "wisplet": "critter", "shellmourn": "critter",
    "candlewick_toad": "critter", "mossback_tortoise": "critter", "lumen_beetle": "critter",
    "cinderhen": "flyer", "lantern_moth": "flyer", "hush_owl": "flyer", "grimfinch": "flyer", "sorrowdove": "flyer",
}


# =============================================================================================
# Vanilla-skeleton families (HumanoidModel / ZombieModel / SkeletonModel) with extra geometry
# =============================================================================================
def _fixed(r, bone, u, v, x, y, z, w, h, d, role, inflate=0.0):
    c = Cube(x, y, z, w, h, d, role, inflate)
    c.u, c.v, c.fixed = u, v, True
    r.by[bone].cubes.append(c)


def player_base(name):
    r = Rig(name)
    r.bone("head")
    _fixed(r, "head", 0, 0, -4, -8, -4, 8, 8, 8, "head")
    r.bone("hat")
    _fixed(r, "hat", 32, 0, -4, -8, -4, 8, 8, 8, "hat", 0.5)
    r.bone("body")
    _fixed(r, "body", 16, 16, -4, 0, -2, 8, 12, 4, "cloth")
    r.bone("right_arm", None, (-5, 2, 0))
    _fixed(r, "right_arm", 40, 16, -3, -2, -2, 4, 12, 4, "sleeve")
    r.bone("left_arm", None, (5, 2, 0))
    _fixed(r, "left_arm", 32, 48, -1, -2, -2, 4, 12, 4, "sleeve")
    r.bone("right_leg", None, (-1.9, 12, 0))
    _fixed(r, "right_leg", 0, 16, -2, 0, -2, 4, 12, 4, "pants")
    r.bone("left_leg", None, (1.9, 12, 0))
    _fixed(r, "left_leg", 16, 48, -2, 0, -2, 4, 12, 4, "pants")
    return r


def bandit():
    r = player_base("bandit")
    r.bone("hat_brim", "head", (0, -7, 0))
    r.box("hat_brim", -6, 0, -6, 12, 1, 12, "hat")
    r.box("hat_brim", -4.5, -4, -4.5, 9, 4, 9, "hat")
    r.box("hat_brim", -4.5, -1, -4.5, 9, 1, 9, "band")
    r.bone("bandana_knot", "head", (0, -3, 4))
    r.box("bandana_knot", -1, 0, 0, 2, 2, 1, "bandana")
    r.box("bandana_knot", -1, 2, 0.5, 1, 3, 1, "bandana")
    r.bone("bandolier", "body", (0, 0, 0), (0, 0, 40))
    r.box("bandolier", -1, -1, -2.6, 2, 15, 1, "leather")
    for i in range(5):
        r.box("bandolier", -0.5, 1 + i * 2.5, -3.1, 1, 1, 1, "brass")
    r.box("body", -4.5, 8, -2.5, 9, 2, 5, "leather")  # belt
    r.box("body", 3, 9, -1, 2, 4, 3, "leather")  # holster
    r.chain("coat_tail", "body", (0, 10, 2), [(5, 1)], "coat", axis="y")
    r.box("coat_tail", -4, 0, -0.5, 8, 5, 1, "coat")
    r.box("right_leg", -2.5, 9, -2.5, 5, 1, 5, "leather")  # boot cuffs
    r.box("left_leg", -2.5, 9, -2.5, 5, 1, 5, "leather")
    return r


def bandit_captain():
    r = player_base("bandit_captain")
    r.bone("tricorn", "head", (0, -7, 0))
    r.box("tricorn", -6, 0, -6, 12, 1, 12, "hat")
    r.box("tricorn", -5, -3, -4.5, 10, 3, 9, "hat")
    r.box("tricorn", -6, -2, -6, 12, 2, 1, "gold")
    r.box("tricorn", -1, -5, -5, 2, 3, 1, "feather")
    for side, x in ((-1, -6), (1, 4)):
        r.box("right_arm" if side < 0 else "left_arm", -3.5 if side < 0 else -1.5, -3, -2.5, 5, 2, 5, "gold")  # epaulettes
    r.box("body", -4.5, 8, -2.5, 9, 2, 5, "leather")
    r.box("body", -0.5, 0, -2.6, 1, 8, 1, "gold")
    r.bone("coat_tail", "body", (0, 10, 2))
    r.box("coat_tail", -4.5, 0, -0.5, 9, 9, 1, "coat")
    r.bone("sabre", "body", (4, 9, 0), (20, 0, 0))
    r.box("sabre", 0, 0, -1, 1, 9, 1, "metal")
    r.box("sabre", -0.5, -1, -1.5, 2, 1, 2, "gold")
    return r


def rotwalker():
    r = player_base("rotwalker")
    r.bone("broken_jaw", "head", (0, -1, -3), (25, 0, 8))
    r.box("broken_jaw", -3, 0, -1.5, 6, 2, 3, "flesh")
    r.box("broken_jaw", -2.5, -1, -1.6, 5, 1, 1, "tooth")
    for k in range(3):
        r.box("body", -3.5, 3 + 2 * k, -2.6, 7, 1, 1, "bone")  # ribs through the torn shirt
    r.box("right_arm", -2, 6, -2.5, 1, 4, 1, "bone")  # bone through the skin
    r.chain("flesh_flap", "body", (2, 2, -2.2), [(3, 2)], "flesh", axis="y", rot=(-10, 0, 0))
    r.box("left_leg", -2.5, 6, -2.5, 5, 2, 5, "rag")
    return r


FAMILIES = {"bandit": bandit, "bandit_captain": bandit_captain, "rotwalker": rotwalker}


# =============================================================================================
# Vehicles
# =============================================================================================
def ruin_runner():
    r = Rig("ruin_runner")
    r.bone("chassis", None, (0, 18, 0))
    r.box("chassis", -10, -3, -17, 20, 4, 34, "metal")
    r.box("chassis", -9, -6, -17, 18, 3, 10, "paint")
    r.box("chassis", -9, -7, -17, 18, 1, 3, "paint")
    r.box("chassis", -11, -2, -19, 22, 3, 2, "metal")
    for x in (-9, -4, 2, 7):
        r.box("chassis", x, -1, -22, 2, 2, 3, "bone")  # bumper spikes
    r.box("chassis", -4, -6, 0, 8, 3, 6, "leather")
    r.box("chassis", -4, -12, 5, 8, 6, 2, "leather")
    r.box("chassis", -6, -9, 10, 12, 6, 6, "metal")
    r.box("chassis", -5, -10, 11, 10, 1, 4, "metal")
    r.box("chassis", -8, -5, -18, 3, 2, 1, "glow")
    r.box("chassis", 5, -5, -18, 3, 2, 1, "glow")
    r.box("chassis", -11, -4, -12, 1, 2, 24, "paint")
    r.box("chassis", 10, -4, -12, 1, 2, 24, "paint")
    r.bone("steering", "chassis", (0, -8, -3), (-30, 0, 0))
    r.box("steering", -2, -2, 0, 4, 4, 1, "metal")
    r.box("steering", -0.5, -0.5, 1, 1, 1, 3, "metal")
    for side, x in (("r", -8), ("l", 6)):
        r.bone(f"exhaust_{side}", "chassis", (x + 1, -8, 15))
        r.box(f"exhaust_{side}", -1, -6, -1, 2, 6, 2, "metal")
        r.box(f"exhaust_{side}", -1.5, -7, -1.5, 3, 1, 3, "metal")
    r.bone("cage", "chassis")
    for x, z in ((-9, -4), (7, -4), (-9, 6), (7, 6)):
        r.box("cage", x, -17, z, 2, 14, 2, "metal")
    r.box("cage", -9, -19, -4, 18, 2, 2, "metal")
    r.box("cage", -9, -19, 6, 18, 2, 2, "metal")
    r.box("cage", -9, -19, -2, 2, 2, 8, "metal")
    r.box("cage", 7, -19, -2, 2, 2, 8, "metal")
    r.box("cage", -1, -21, -2, 2, 2, 2, "bone")  # skull ornament
    for name, x, z in (("wheel_fl", -11, -11), ("wheel_fr", 11, -11), ("wheel_bl", -11, 11), ("wheel_br", 11, 11)):
        r.bone(name, None, (x, 19, z))
        r.box(name, -2, -4, -4, 4, 8, 8, "tire")
        r.box(name, -2.5, -1, -1, 5, 2, 2, "metal")
    return r


def sky_skiff():
    r = Rig("sky_skiff")
    r.bone("hull", None, (0, 20, 0))
    r.box("hull", -8, -3, -12, 16, 4, 26, "metal")
    r.box("hull", -5, -2, -18, 10, 3, 6, "paint")
    r.box("hull", -3, -1, -21, 6, 2, 3, "paint")
    r.box("hull", -8, -5, -12, 1, 2, 24, "paint")
    r.box("hull", 7, -5, -12, 1, 2, 24, "paint")
    r.box("hull", -3, -6, 0, 6, 3, 5, "leather")
    r.box("hull", -3, -11, 4, 6, 5, 1, "leather")
    r.box("hull", -6, -9, -9, 12, 4, 1, "glass")
    r.box("hull", -6, 1, -8, 12, 1, 4, "glow")
    r.box("hull", -6, 1, 6, 12, 1, 4, "glow")
    r.bone("fin", "hull", (0, -3, 10))
    r.box("fin", -1, -8, 0, 2, 8, 6, "paint")
    r.box("fin", -0.5, -9, 2, 1, 1, 4, "glow")
    for side, x in (("l", -9), ("r", 9)):
        r.bone(f"thruster_{side}", None, (x, 19, 8))
        r.box(f"thruster_{side}", -2.5, -3, -6, 5, 5, 12, "metal")
        r.box(f"thruster_{side}", -2, -2.5, 6, 4, 4, 1, "glow")
        r.box(f"thruster_{side}", -3, -3.5, -7, 6, 6, 2, "paint")
        r.bone(f"flap_{side}", f"thruster_{side}", (0, 2, 4))
        r.box(f"flap_{side}", -2, 0, -1, 4, 1, 4, "paint")
    return r


VEHICLES = {"ruin_runner": ruin_runner, "sky_skiff": sky_skiff}



# =============================================================================================
# Detail pass: small 3D features added to every creature after it is built
# =============================================================================================
def _bbox(part, roles=None):
    cs = [c for c in part.cubes if roles is None or c.role in roles]
    if not cs:
        return None
    return (min(c.x for c in cs), min(c.y for c in cs), min(c.z for c in cs),
            max(c.x + c.w for c in cs), max(c.y + c.h for c in cs), max(c.z + c.d for c in cs), cs[0].role)


def detail(r, archetype):
    """Kneecaps, knuckles, toes, hooves, brow ridges, nostrils, vertebrae, tail tufts, feather tips
    and joint knobs, wherever the matching bones exist. Sizes follow each bone's own box."""
    names = set(r.by)
    for name in list(names):
        part = r.by[name]
        bb = _bbox(part)
        if bb is None:
            continue
        x0, y0, z0, x1, y1, z1, role = bb
        w, h, d = x1 - x0, y1 - y0, z1 - z0
        cx = (x0 + x1) / 2
        base = name.replace("right_", "").replace("left_", "")
        # kneecaps on the top front of shins
        if base == "shin" and w >= 2:
            r.box(name, cx - 1, y0 - 0.5, z0 - 1, 2, 2, 1, "bone" if archetype in ("gaunt", "brute") else role)
        # knuckles / toes on hands, fists and feet
        if base in ("hand", "fist") and w >= 3:
            for k in range(min(4, int(w) - 1)):
                r.box(name, x0 + 0.5 + k, y0 - 0.5, z0 - 0.5, 1, 1, 1, "bone" if archetype != "brute" else role)
        if base == "foot" and w >= 3 and archetype in ("gaunt", "brute"):
            r.box(name, x0, y1 - 1, z0 - 1, int(w), 1, 1, role)                       # toe ridge
        # hooves on quadruped legs (bottom of the lowest leg bone)
        if archetype == "quadruped" and ("leg" in base) and not any(n.startswith(name + "_") or n == name.replace("leg", "shin") for n in names):
            if h >= 3:
                r.box(name, x0, y1 - 2, z0, int(w), 2, int(d), "horn", 0.2)
        # brow ridge and cheekbones on heads
        if name == "head" and w >= 5 and h >= 5:
            hr = [c for c in part.cubes if c.role == "head"]
            if hr:
                c = hr[0]
                r.box(name, c.x + 0.5, c.y + c.h * 0.22, c.z - 0.6, int(c.w - 1), 1, 1, "head")
                if archetype in ("gaunt", "brute", "wraith"):
                    r.box(name, c.x - 0.4, c.y + c.h * 0.55, c.z + 0.5, 1, 2, int(max(1, c.d * 0.5)), "head")
                    r.box(name, c.x + c.w - 0.6, c.y + c.h * 0.55, c.z + 0.5, 1, 2, int(max(1, c.d * 0.5)), "head")
        # nostrils on snouts
        sn = [c for c in part.cubes if c.role == "snout"]
        if sn and sn[0].w >= 3:
            c = sn[0]
            r.box(name, c.x + 0.5, c.y + 0.5, c.z - 0.3, 1, 1, 1, "claw")
            r.box(name, c.x + c.w - 1.5, c.y + 0.5, c.z - 0.3, 1, 1, 1, "claw")
        # vertebrae down the back
        if archetype == "quadruped" and name == "body" and d >= 8:
            for k in range(int(d // 3)):
                r.box(name, -0.5, y0 - 1, z0 + 1.5 + k * 3, 1, 1, 2, "bone")
        if archetype in ("gaunt", "brute") and name in ("chest", "spine") and h >= 4:
            for k in range(int(h // 3)):
                r.box(name, -0.5, y0 + 1 + k * 3, z1 - 0.2, 1, 2, 1, "bone")
        # joint knobs on crawler legs
        if archetype == "crawler" and base.startswith("leg") and base.endswith("_lower"):
            r.box(name, -1, -1, -1, 2, 2, 2, "claw")
        # feather tips trailing from wing tips
        if base == "wing_tip" and w >= 3 and archetype == "flyer":
            for k in range(3):
                r.box(name, x0 + k * (w / 3), y0, z1 - 0.5, 1, 1, 2, "wing")
    # a tuft at the end of tails
    tails = sorted([n for n in names if n.startswith("tail") and not n.startswith("tail_flame")], key=len)
    if tails and archetype in ("quadruped", "critter"):
        last = tails[-1]
        bb = _bbox(r.by[last])
        if bb:
            x0, y0, z0, x1, y1, z1, role = bb
            r.box(last, x0 - 0.5, y1 - 1, z0 - 0.5, int(x1 - x0) + 1, 2, int(z1 - z0) + 1, "accent")
    return r


def build_rig(name):
    if name in CREATURES:
        return detail(CREATURES[name](), ARCHETYPE_OF.get(name, ""))
    if name in FAMILIES:
        return FAMILIES[name]()
    return VEHICLES[name]()
