"""Keyframe animations for every DeadCraft rig.

Animations are authored here as sums of smooth waves and hand-placed key poses, sampled into
keyframes and written twice:
  * NeoForge JSON (assets/deadcraft/neoforge/animations/entity/<creature>/<anim>.json), which the
    game plays through HierarchicalModel.animate / animateWalk;
  * the creature's .bbmodel, so the same animations appear in Blockbench's Animate tab.

All values are Minecraft Java model space: rotations in degrees, positions in pixels with +Y DOWN.
"""
from __future__ import annotations

import math
import re


class Anim:
    def __init__(self, length, loop=True):
        self.length = length
        self.loop = loop
        self.comps: dict[tuple[str, str], list] = {}

    def _add(self, bone, ch, fn):
        self.comps.setdefault((bone, ch), []).append(fn)
        return self

    def wave(self, bone, ch, amp=(0, 0, 0), cycles=1, phase=0.0, offset=(0, 0, 0)):
        L = self.length

        def f(t):
            s = math.sin(2 * math.pi * cycles * t / L + phase)
            return tuple(o + a * s for a, o in zip(amp, offset))
        return self._add(bone, ch, f)

    def const(self, bone, ch, value):
        return self._add(bone, ch, lambda t: tuple(value))

    def keys(self, bone, ch, pts):
        """Smoothstep-interpolated key poses [(time, (x, y, z)), ...]."""
        pts = sorted(pts)

        def f(t):
            if t <= pts[0][0]:
                return tuple(pts[0][1])
            for (t0, a), (t1, b) in zip(pts, pts[1:]):
                if t0 <= t <= t1:
                    k = (t - t0) / max(1e-6, t1 - t0)
                    k = k * k * (3 - 2 * k)
                    return tuple(x + (y - x) * k for x, y in zip(a, b))
            return tuple(pts[-1][1])
        return self._add(bone, ch, f)

    def spin(self, bone, axis="y", turns=1):
        L = self.length
        idx = "xyz".index(axis)

        def f(t):
            v = [0.0, 0.0, 0.0]
            v[idx] = 360.0 * turns * t / L
            return tuple(v)
        return self._add(bone, "rotation", f)

    def sample(self, bones):
        n = max(8, int(self.length * (10 if self.loop else 20)))
        times = [self.length * i / n for i in range(n + 1)]
        out = {}
        for (bone, ch), fns in self.comps.items():
            if bone not in bones:
                continue
            keys = []
            for t in times:
                v = [0.0, 0.0, 0.0] if ch != "scale" else [1.0, 1.0, 1.0]
                for fn in fns:
                    r = fn(t)
                    if ch == "scale":
                        v = [a * b for a, b in zip(v, r)]
                    else:
                        v = [a + b for a, b in zip(v, r)]
                keys.append((round(t, 4), tuple(round(x, 3) for x in v)))
            out.setdefault(bone, {})[ch] = keys
        return {"length": self.length, "loop": self.loop, "bones": out}


PI = math.pi
R, X3 = "rotation", "position"


def rot(x=0, y=0, z=0):
    return (x, y, z)


# =============================================================================================
# Archetype animation sets
# =============================================================================================
def gaunt_set():
    walk = Anim(1.2)
    for side, ph in (("right", 0), ("left", PI)):
        walk.wave(f"{side}_leg", R, rot(28), phase=ph)
        walk.wave(f"{side}_shin", R, rot(16), phase=ph - PI / 2, offset=rot(16))
        walk.wave(f"{side}_foot", R, rot(12), phase=ph + PI / 2)
        walk.wave(f"{side}_arm", R, rot(24, 0, 3), phase=ph + PI)
        walk.wave(f"{side}_forearm", R, rot(14), phase=ph + PI - 0.6, offset=rot(-12))
        walk.wave(f"{side}_hand", R, rot(10), phase=ph + PI - 1.2)
    walk.wave("body", X3, (0, 0.9, 0), cycles=2, phase=PI / 2, offset=(0, -0.4, 0))
    walk.wave("body", R, rot(0, 4, 3))
    walk.wave("chest", R, rot(3, -5, 0), cycles=2)
    walk.wave("head", R, rot(-3, 4, -2), cycles=2, phase=PI)

    idle = Anim(4.0)
    idle.wave("chest", R, rot(2.5), cycles=1)
    idle.wave("neck", R, rot(0, 0, 5), phase=1.0)
    idle.keys("head", R, [(0, rot()), (1.55, rot()), (1.62, rot(0, 0, 24)), (1.9, rot(0, 0, 18)), (2.1, rot()),
                          (3.05, rot()), (3.1, rot(-10, -28, 0)), (3.35, rot()), (4, rot())])
    idle.wave("jaw", R, rot(9), cycles=2, offset=rot(6))
    for side, s in (("right", 1), ("left", -1)):
        idle.wave(f"{side}_arm", R, rot(3, 0, 4 * s), phase=0.5)
        idle.wave(f"{side}_hand", R, rot(8, 0, 0), cycles=3, phase=s)
        idle.wave(f"{side}_back_arm", R, rot(10, 0, 8 * s), cycles=2, phase=s)
        idle.wave(f"{side}_back_forearm", R, rot(18), cycles=4, phase=s)

    attack = Anim(0.7, loop=False)
    attack.keys("right_arm", R, [(0, rot()), (0.15, rot(-150, 0, 10)), (0.35, rot(-15)), (0.7, rot())])
    attack.keys("right_forearm", R, [(0, rot()), (0.15, rot(-35)), (0.35, rot(15)), (0.7, rot())])
    attack.keys("left_arm", R, [(0, rot()), (0.25, rot(-110, 0, -10)), (0.45, rot(-10)), (0.7, rot())])
    attack.keys("chest", R, [(0, rot()), (0.18, rot(-12, 10, 0)), (0.35, rot(18, -10, 0)), (0.7, rot())])
    attack.keys("jaw", R, [(0, rot()), (0.2, rot(35)), (0.5, rot())])

    cast = Anim(1.0, loop=False)
    for side, s in (("right", 1), ("left", -1)):
        cast.keys(f"{side}_arm", R, [(0, rot()), (0.3, rot(-165, 0, 25 * s)), (0.8, rot(-165, 0, 25 * s)), (1, rot())])
    cast.keys("head", R, [(0, rot()), (0.3, rot(-25)), (0.8, rot(-25)), (1, rot())])
    cast.keys("jaw", R, [(0, rot()), (0.3, rot(40)), (0.8, rot(40)), (1, rot())])
    return {"walk": walk, "idle": idle, "attack": attack, "cast": cast}


def crawler_set(pairs=4):
    walk = Anim(0.6)
    for i in range(pairs):
        for side, s in (("right", 1), ("left", -1)):
            ph = ((i + (side == "left")) % 2) * PI
            walk.wave(f"{side}_leg{i}", R, rot(0, 20 * s, 14 * s), phase=ph)
            walk.wave(f"{side}_leg{i}_lower", R, rot(0, 0, 10 * s), phase=ph + PI / 2)
    walk.wave("body", X3, (0, 0.5, 0), cycles=2)
    walk.wave("abdomen", R, rot(3, 6, 0))
    walk.wave("head", R, rot(0, 6, 0), cycles=2)

    idle = Anim(3.0)
    idle.wave("abdomen", "scale", (0.05, 0.05, 0.04), cycles=2, offset=(1, 1, 1))
    idle.wave("right_mandible", R, rot(0, 12, 0), cycles=4, offset=rot(0, 4, 0))
    idle.wave("left_mandible", R, rot(0, -12, 0), cycles=4, offset=rot(0, -4, 0))
    idle.wave("jaw", R, rot(10), cycles=3, offset=rot(5))
    idle.keys("head", R, [(0, rot()), (1.2, rot()), (1.25, rot(0, 30, 15)), (1.6, rot(0, 25, 10)), (1.8, rot()), (3, rot())])
    for i in range(pairs):
        idle.wave(f"right_leg{i}", R, rot(0, 0, 3), cycles=3, phase=i)
        idle.wave(f"left_leg{i}", R, rot(0, 0, -3), cycles=3, phase=i)

    attack = Anim(0.5, loop=False)
    attack.keys("body", X3, [(0, (0, 0, 0)), (0.12, (0, -1, -4)), (0.3, (0, 0, 0)), (0.5, (0, 0, 0))])
    attack.keys("head", R, [(0, rot()), (0.12, rot(-25)), (0.3, rot(10)), (0.5, rot())])
    attack.keys("jaw", R, [(0, rot()), (0.12, rot(45)), (0.35, rot())])
    attack.keys("right_mandible", R, [(0, rot()), (0.1, rot(0, 40, 0)), (0.2, rot(0, -10, 0)), (0.5, rot())])
    attack.keys("left_mandible", R, [(0, rot()), (0.1, rot(0, -40, 0)), (0.2, rot(0, 10, 0)), (0.5, rot())])
    attack.keys("right_leg0", R, [(0, rot()), (0.15, rot(0, 40, 30)), (0.35, rot()), (0.5, rot())])
    attack.keys("left_leg0", R, [(0, rot()), (0.15, rot(0, -40, -30)), (0.35, rot()), (0.5, rot())])

    cast = Anim(1.0, loop=False)
    cast.keys("abdomen", R, [(0, rot()), (0.3, rot(-30)), (0.7, rot(-30)), (1, rot())])
    cast.keys("abdomen_tip", R, [(0, rot()), (0.3, rot(-25)), (0.5, rot(10)), (1, rot())])
    cast.keys("head", R, [(0, rot()), (0.3, rot(-20)), (0.7, rot(-20)), (1, rot())])
    return {"walk": walk, "idle": idle, "attack": attack, "cast": cast}


def wraith_set():
    idle = Anim(3.0)
    idle.wave("body", X3, (0, 1.6, 0))
    idle.wave("body", R, rot(2, 4, 2), phase=0.7)
    for i, bone in enumerate(("robe", "robe1", "robe2", "robe3")):
        idle.wave(bone, R, rot(6 + 3 * i, 0, 4 + 2 * i), phase=-0.8 * i)
    idle.wave("head", R, rot(4, 8, 6), phase=1.3)
    idle.keys("head", R, [(0, rot()), (2.0, rot()), (2.06, rot(0, 0, -30)), (2.3, rot(0, 0, -22)), (2.5, rot()), (3, rot())])
    idle.wave("jaw", R, rot(8), cycles=2, offset=rot(8))
    for side, s in (("right", 1), ("left", -1)):
        idle.wave(f"{side}_arm", R, rot(5, 0, 5 * s), phase=0.4 * s)
        idle.wave(f"{side}_forearm", R, rot(8), phase=0.4 * s - 0.6)
        idle.wave(f"{side}_hand", R, rot(10), cycles=2, phase=s)
        idle.wave(f"{side}_low_arm", R, rot(12, 0, 6 * s), cycles=1, phase=s + 1)

    walk = Anim(1.5)
    walk.const("body", R, rot(14))
    for i, bone in enumerate(("robe", "robe1", "robe2", "robe3")):
        walk.wave(bone, R, rot(6, 0, 3), cycles=2, phase=-0.9 * i, offset=rot(8 + 4 * i))
    for side in ("right", "left"):
        walk.wave(f"{side}_arm", R, rot(6), cycles=2, offset=rot(22))

    attack = Anim(0.6, loop=False)
    for side, s, d in (("right", 1, 0.0), ("left", -1, 0.06)):
        attack.keys(f"{side}_arm", R, [(0, rot()), (0.18 + d, rot(-115, 0, -8 * s)), (0.38 + d, rot(-60)), (0.6, rot())])
        attack.keys(f"{side}_forearm", R, [(0, rot()), (0.18 + d, rot(-10)), (0.38 + d, rot(-25)), (0.6, rot())])
    attack.keys("body", X3, [(0, (0, 0, 0)), (0.2, (0, 0, -4)), (0.45, (0, 0, 0)), (0.6, (0, 0, 0))])
    attack.keys("jaw", R, [(0, rot()), (0.2, rot(40)), (0.5, rot())])

    cast = Anim(1.2, loop=False)
    for side, s in (("right", 1), ("left", -1)):
        cast.keys(f"{side}_arm", R, [(0, rot()), (0.35, rot(-150, 0, 55 * s)), (0.95, rot(-150, 0, 55 * s)), (1.2, rot())])
        cast.keys(f"{side}_low_arm", R, [(0, rot()), (0.35, rot(-60, 0, 70 * s)), (0.95, rot(-60, 0, 70 * s)), (1.2, rot())])
    cast.keys("body", X3, [(0, (0, 0, 0)), (0.35, (0, -3, 0)), (0.95, (0, -3, 0)), (1.2, (0, 0, 0))])
    cast.keys("head", R, [(0, rot()), (0.35, rot(-30)), (0.95, rot(-30)), (1.2, rot())])
    for i, bone in enumerate(("robe", "robe1", "robe2", "robe3")):
        cast.keys(bone, R, [(0, rot()), (0.35, rot(-10 - 6 * i)), (0.95, rot(-10 - 6 * i)), (1.2, rot())])
    return {"walk": walk, "idle": idle, "attack": attack, "cast": cast}


def brute_set():
    walk = Anim(1.4)
    for side, ph in (("right", 0), ("left", PI)):
        walk.wave(f"{side}_leg", R, rot(24), phase=ph)
        walk.wave(f"{side}_shin", R, rot(14), phase=ph - PI / 2, offset=rot(14))
        walk.wave(f"{side}_foot", R, rot(10), phase=ph + PI / 2)
        walk.wave(f"{side}_arm", R, rot(22), phase=ph + PI)
        walk.wave(f"{side}_forearm", R, rot(10), phase=ph + PI - 0.5, offset=rot(-8))
    walk.wave("body", X3, (0, 1.4, 0), cycles=2, phase=PI / 2, offset=(0, -0.6, 0))
    walk.wave("body", R, rot(0, 0, 4))
    walk.wave("chest", R, rot(0, 6, 0), phase=PI)
    walk.wave("head", R, rot(0, -5, -3), phase=PI)

    idle = Anim(4.0)
    idle.wave("chest", R, rot(3.5), cycles=1)
    idle.wave("head", R, rot(0, 18, 0), cycles=0.5, phase=0)
    idle.wave("jaw", R, rot(8), cycles=2, offset=rot(5))
    for side, s in (("right", 1), ("left", -1)):
        idle.wave(f"{side}_arm", R, rot(3, 0, 3 * s), phase=0.5)
        idle.wave(f"{side}_fist", R, rot(8), cycles=2, phase=s)
        idle.wave(f"{side}_wing", R, rot(5, 8 * s, 10 * s), cycles=1, phase=0.3)
        idle.wave(f"{side}_wing_tip", R, rot(0, 10 * s, 0), cycles=1, phase=-0.4)

    attack = Anim(0.8, loop=False)
    attack.keys("right_arm", R, [(0, rot()), (0.25, rot(-165, 0, 15)), (0.45, rot(-25)), (0.8, rot())])
    attack.keys("right_forearm", R, [(0, rot()), (0.25, rot(-40)), (0.45, rot(5)), (0.8, rot())])
    attack.keys("chest", R, [(0, rot()), (0.25, rot(-10, -20, 0)), (0.45, rot(12, 15, 0)), (0.8, rot())])
    attack.keys("jaw", R, [(0, rot()), (0.25, rot(30)), (0.6, rot())])

    cast = Anim(0.75, loop=False)
    for side in ("right", "left"):
        cast.keys(f"{side}_arm", R, [(0, rot()), (0.3, rot(-170)), (0.45, rot(-20)), (0.75, rot())])
        cast.keys(f"{side}_forearm", R, [(0, rot()), (0.3, rot(-20)), (0.45, rot(10)), (0.75, rot())])
    cast.keys("chest", R, [(0, rot()), (0.3, rot(-22)), (0.45, rot(26)), (0.75, rot())])
    cast.keys("body", X3, [(0, (0, 0, 0)), (0.3, (0, -2, 0)), (0.45, (0, 1.5, 0)), (0.75, (0, 0, 0))])
    cast.keys("jaw", R, [(0, rot()), (0.3, rot(35)), (0.6, rot())])
    return {"walk": walk, "idle": idle, "attack": attack, "cast": cast}


def quadruped_set():
    walk = Anim(0.9)
    for leg, ph in (("right_front", 0), ("left_hind", 0), ("left_front", PI), ("right_hind", PI)):
        walk.wave(f"{leg}_leg", R, rot(30), phase=ph)
        walk.wave(f"{leg.split('_')[0]}_{leg.split('_')[1]}_shin", R, rot(18), phase=ph - PI / 2, offset=rot(8))
    walk.wave("body", X3, (0, 0.5, 0), cycles=2)
    walk.wave("neck", R, rot(4), cycles=2)
    walk.wave("tail", R, rot(0, 10, 0), cycles=1)

    idle = Anim(6.0)
    idle.keys("neck", R, [(0, rot()), (1.8, rot()), (2.4, rot(42)), (4.0, rot(42)), (4.6, rot()), (6, rot())])
    idle.keys("head", R, [(0, rot()), (1.8, rot()), (2.4, rot(10)), (4.0, rot(10)), (4.6, rot()), (6, rot())])
    idle.wave("jaw", R, rot(6), cycles=12, offset=rot(6))
    idle.keys("right_ear", R, [(0, rot()), (0.9, rot()), (1.0, rot(0, 0, -25)), (1.15, rot()), (6, rot())])
    idle.keys("left_ear", R, [(0, rot()), (3.4, rot()), (3.5, rot(0, 0, 25)), (3.65, rot()), (6, rot())])
    idle.wave("body", R, rot(1.5), cycles=3)

    attack = Anim(0.5, loop=False)
    attack.keys("neck", R, [(0, rot()), (0.15, rot(25)), (0.3, rot(-18)), (0.5, rot())])
    attack.keys("body", X3, [(0, (0, 0, 0)), (0.2, (0, 0, -2)), (0.5, (0, 0, 0))])
    return {"walk": walk, "idle": idle, "attack": attack}


def critter_set(hop=False):
    walk = Anim(0.4)
    for leg, ph in (("right_front", 0), ("left_hind", 0), ("left_front", PI), ("right_hind", PI),
                    ("right_mid", PI), ("left_mid", 0)):
        walk.wave(f"{leg}_leg", R, rot(35), phase=ph)
    if hop:
        walk.keys("body", X3, [(0, (0, 0, 0)), (0.2, (0, -2.5, 0)), (0.4, (0, 0, 0))])
        walk.keys("body", R, [(0, rot()), (0.1, rot(-12)), (0.3, rot(8)), (0.4, rot())])
    else:
        walk.wave("body", X3, (0, 0.4, 0), cycles=2)
    walk.wave("head", R, rot(4), cycles=2)

    idle = Anim(3.0)
    idle.keys("head", R, [(0, rot()), (0.8, rot()), (0.85, rot(0, 25, 0)), (1.5, rot(0, 25, 0)), (1.6, rot()), (3, rot())])
    idle.wave("body", "scale", (0.03, 0.04, 0.03), cycles=3, offset=(1, 1, 1))
    idle.wave("tail", R, rot(0, 20, 0), cycles=3)
    for side, s in (("right", 1), ("left", -1)):
        idle.wave(f"{side}_elytra", R, rot(0, 0, 3 * s), cycles=2)
    return {"walk": walk, "idle": idle}


def flyer_set():
    # resting birds tuck their wings back along the flanks
    fold = {"right_wing": rot(0, 82, -12), "left_wing": rot(0, -82, 12),
            "right_wing_tip": rot(0, 8, 0), "left_wing_tip": rot(0, -8, 0),
            "right_wing_low": rot(0, 75, -20), "left_wing_low": rot(0, -75, 20)}
    walk = Anim(0.5)
    idle = Anim(3.0)
    for A in (walk, idle):
        for b, v in fold.items():
            A.const(b, R, v)
    for side, ph in (("right", 0), ("left", PI)):
        walk.wave(f"{side}_leg", R, rot(30), phase=ph)
    walk.wave("head", X3, (0, 0, 0.7), cycles=2)
    walk.wave("body", X3, (0, 0.3, 0), cycles=2)
    idle.keys("head", R, [(0, rot()), (1.0, rot()), (1.1, rot(0, 40, 0)), (1.8, rot(0, 40, 0)), (1.9, rot(20)), (2.3, rot()), (3, rot())])
    idle.wave("tail", R, rot(6), cycles=3)

    fly = Anim(0.4)
    for side, s in (("right", 1), ("left", -1)):
        fly.wave(f"{side}_wing", R, rot(0, 0, 55 * s))
        fly.wave(f"{side}_wing_tip", R, rot(0, 0, 30 * s), phase=-0.7)
        fly.wave(f"{side}_wing_low", R, rot(0, 0, 45 * s), phase=-0.4)
        fly.const(f"{side}_leg", R, rot(65))
    fly.wave("body", X3, (0, 0.8, 0), phase=PI)
    fly.wave("tail", R, rot(8), phase=-1)
    return {"walk": walk, "idle": idle, "fly": fly}


SETS = {"gaunt": gaunt_set, "crawler": crawler_set, "wraith": wraith_set, "brute": brute_set,
        "quadruped": quadruped_set, "critter": critter_set, "flyer": flyer_set}

SWAY = re.compile(r"^(right_shackle_chain|left_shackle_chain|strip|bandage|wisp|gut|drool|plume|crest|tail_long|lantern_chain|cape|veil|tail|tail_a|tail_b)")


# =============================================================================================
# Bespoke boss sets: each boss has its own walk and its own attack swing
# =============================================================================================
def fracture_set():
    """Hovers: the body bobs and leans into its glide, the shard pedestal trails behind it.
    Attack: both blade arms rise and scissor shut across the chest."""
    walk = Anim(1.6)
    walk.wave("body", X3, (0, 1.6, 0), cycles=1, offset=(0, -1.2, 0))
    walk.const("body", R, rot(12))
    walk.wave("shard_base", R, rot(10, 0, 6), cycles=1, phase=-0.8, offset=rot(14))
    for side, s in (("right", 1), ("left", -1)):
        walk.wave(f"{side}_arm", R, rot(10, 0, 6 * s), cycles=1, phase=0 if s > 0 else PI, offset=rot(-20, 0, 8 * s))
        walk.wave(f"{side}_forearm", R, rot(8), cycles=1, phase=-0.6)
    walk.wave("head", R, rot(-4, 6, 0), cycles=1, phase=PI)
    idle = Anim(5.0)
    idle.wave("body", X3, (0, 1.2, 0), cycles=1)
    idle.wave("chest", R, rot(3, 4, 0), cycles=1)
    idle.wave("core", "scale", (0.15, 0.15, 0.15), cycles=4, offset=(1, 1, 1))
    idle.wave("head", R, rot(0, 0, 4), cycles=0.5)
    idle.wave("shard_base", R, rot(0, 30, 0), cycles=0.5)
    attack = Anim(1.0, loop=False)
    for side, s in (("right", 1), ("left", -1)):
        attack.keys(f"{side}_arm", R, [(0, rot()), (0.35, rot(-150, 25 * s, 60 * s)), (0.55, rot(-60, -40 * s, -30 * s)), (1.0, rot())])
        attack.keys(f"{side}_forearm", R, [(0, rot()), (0.35, rot(-20)), (0.55, rot(-10)), (1.0, rot())])
    attack.keys("chest", R, [(0, rot()), (0.35, rot(-18)), (0.55, rot(22)), (1.0, rot())])
    attack.keys("body", X3, [(0, (0, 0, 0)), (0.35, (0, -3, 0)), (0.55, (0, 1, -2)), (1.0, (0, 0, 0))])
    cast = Anim(1.2, loop=False)
    cast.keys("chest", R, [(0, rot()), (0.4, rot(-30)), (0.8, rot(-30)), (1.2, rot())])
    for side, s in (("right", 1), ("left", -1)):
        cast.keys(f"{side}_arm", R, [(0, rot()), (0.4, rot(-40, 0, 110 * s)), (0.8, rot(-40, 0, 110 * s)), (1.2, rot())])
    cast.keys("core", "scale", [(0, (1, 1, 1)), (0.4, (1.8, 1.8, 1.8)), (0.8, (1.8, 1.8, 1.8)), (1.2, (1, 1, 1))])
    return {"walk": walk, "idle": idle, "attack": attack, "cast": cast}


def demon_set():
    """Stalks on digitigrade legs, shoulders rolling, wings half-furled and twitching.
    Attack: wings flare wide while both claws rake down in turn."""
    walk = Anim(1.3)
    for side, ph in (("right", 0), ("left", PI)):
        walk.wave(f"{side}_leg", R, rot(30), phase=ph)
        walk.wave(f"{side}_shin", R, rot(22), phase=ph - PI / 2)
        walk.wave(f"{side}_foot", R, rot(18), phase=ph + PI / 2)
        walk.wave(f"{side}_arm", R, rot(26, 0, 0), phase=ph + PI)
        walk.wave(f"{side}_forearm", R, rot(16), phase=ph + PI - 0.5)
        s = 1 if side == "right" else -1
        walk.wave(f"{side}_wing", R, rot(4, 6 * s, 6 * s), cycles=2, offset=rot(0, 30 * s, -20 * s))
    walk.wave("body", X3, (0, 1.0, 0), cycles=2, phase=PI / 2)
    walk.wave("chest", R, rot(0, 10, 4))
    walk.wave("neck", R, rot(6, -8, 0), cycles=2)
    idle = Anim(4.0)
    idle.wave("chest", R, rot(4), cycles=1)
    idle.wave("neck", R, rot(6, 14, 0), cycles=0.5)
    idle.wave("jaw", R, rot(10), cycles=2, offset=rot(6))
    for side, s in (("right", 1), ("left", -1)):
        idle.wave(f"{side}_wing", R, rot(6, 10 * s, 10 * s), cycles=1, phase=0.3)
        idle.wave(f"{side}_wing_tip", R, rot(0, 12 * s, 0), cycles=1, phase=-0.4)
        idle.wave(f"{side}_fist", R, rot(10), cycles=2, phase=s)
    attack = Anim(0.9, loop=False)
    for side, s, t0 in (("right", 1, 0.0), ("left", -1, 0.2)):
        attack.keys(f"{side}_arm", R, [(0, rot()), (0.2 + t0, rot(-160, 0, 20 * s)), (0.4 + t0, rot(-20, 0, -10 * s)), (0.9, rot())])
        attack.keys(f"{side}_wing", R, [(0, rot()), (0.25, rot(-20, -40 * s, 30 * s)), (0.6, rot(-20, -40 * s, 30 * s)), (0.9, rot())])
        attack.keys(f"{side}_wing_tip", R, [(0, rot()), (0.25, rot(0, 0, 30 * s)), (0.6, rot(0, 0, 30 * s)), (0.9, rot())])
    attack.keys("chest", R, [(0, rot()), (0.2, rot(-15)), (0.45, rot(20, 15, 0)), (0.65, rot(20, -15, 0)), (0.9, rot())])
    attack.keys("jaw", R, [(0, rot()), (0.2, rot(40)), (0.6, rot())])
    cast = Anim(1.1, loop=False)
    cast.keys("chest", R, [(0, rot()), (0.4, rot(-35)), (0.8, rot(-35)), (1.1, rot())])
    for side, s in (("right", 1), ("left", -1)):
        cast.keys(f"{side}_wing", R, [(0, rot()), (0.4, rot(-30, -60 * s, 40 * s)), (0.8, rot(-30, -60 * s, 40 * s)), (1.1, rot())])
        cast.keys(f"{side}_arm", R, [(0, rot()), (0.4, rot(-170, 0, 30 * s)), (0.8, rot(-170, 0, 30 * s)), (1.1, rot())])
    cast.keys("head", R, [(0, rot()), (0.4, rot(-30)), (0.8, rot(-30)), (1.1, rot())])
    return {"walk": walk, "idle": idle, "attack": attack, "cast": cast}


def petrified_set():
    """Slithers: a travelling wave runs down the serpent tail while the torso sways above it.
    Attack: a spear thrust with the shield pulled in, then a lash of the tail."""
    walk = Anim(1.8)
    for i in range(7):
        b = "tail" if i == 0 else f"tail{i}"
        walk.wave(b, R, rot(0, 18, 0), cycles=1, phase=-0.9 * i)
    walk.wave("body", R, rot(0, 8, 0), cycles=1, phase=0.6)
    walk.wave("waist", R, rot(0, -10, 6), cycles=1, phase=0.2)
    walk.wave("chest", R, rot(4, -6, -4), cycles=1, phase=0.4)
    walk.wave("head", R, rot(0, 10, 0), cycles=1, phase=PI)
    walk.wave("right_arm", R, rot(10), cycles=1)
    idle = Anim(5.0)
    for i in range(7):
        idle.wave("tail" if i == 0 else f"tail{i}", R, rot(0, 8, 0), cycles=1, phase=-0.7 * i)
    idle.wave("chest", R, rot(3, 0, 3), cycles=1)
    idle.wave("head", R, rot(0, 20, 0), cycles=0.5)
    attack = Anim(1.0, loop=False)
    attack.keys("right_arm", R, [(0, rot()), (0.3, rot(30, 0, 20)), (0.5, rot(-60, 0, -5)), (1.0, rot())])
    attack.keys("right_forearm", R, [(0, rot()), (0.3, rot(-50)), (0.5, rot(40)), (1.0, rot())])
    attack.keys("left_arm", R, [(0, rot()), (0.3, rot(-40, 0, 20)), (0.6, rot(-40, 0, 20)), (1.0, rot())])
    attack.keys("chest", R, [(0, rot()), (0.3, rot(-10, 20, 0)), (0.5, rot(20, -15, 0)), (1.0, rot())])
    for i in range(7):
        attack.keys("tail" if i == 0 else f"tail{i}", R, [(0, rot()), (0.55 + 0.04 * i, rot(0, -40, 0)), (0.8 + 0.03 * i, rot(0, 35, 0)), (1.0, rot())])
    cast = Anim(1.2, loop=False)
    cast.keys("chest", R, [(0, rot()), (0.4, rot(-25)), (0.9, rot(-25)), (1.2, rot())])
    cast.keys("head", R, [(0, rot()), (0.4, rot(-20)), (0.9, rot(-20)), (1.2, rot())])
    cast.keys("left_arm", R, [(0, rot()), (0.4, rot(-90, 0, 0)), (0.9, rot(-90, 0, 0)), (1.2, rot())])
    for k in range(6):
        cast.keys(f"strip{k}", R, [(0, rot()), (0.4, rot(-40, 0, 0)), (0.9, rot(-40, 0, 0)), (1.2, rot())])
    return {"walk": walk, "idle": idle, "attack": attack, "cast": cast}


def skeleton_set():
    """The Ossuary Colossus lumbers: knuckles almost dragging, the whole frame lurching side to side.
    Attack: the jawbone cleaver is hauled overhead with both shoulders and slammed into the ground."""
    walk = Anim(1.8)
    for side, ph in (("right", 0), ("left", PI)):
        walk.wave(f"{side}_leg", R, rot(22), phase=ph)
        walk.wave(f"{side}_shin", R, rot(14), phase=ph - PI / 2, offset=rot(10))
        walk.wave(f"{side}_foot", R, rot(10), phase=ph + PI / 2)
        walk.wave(f"{side}_arm", R, rot(18, 0, 0), phase=ph + PI, offset=rot(8))
        walk.wave(f"{side}_forearm", R, rot(10), phase=ph + PI - 0.5)
    walk.wave("body", X3, (0, 1.6, 0), cycles=2, phase=PI / 2)
    walk.wave("body", R, rot(0, 6, 7))
    walk.wave("chest", R, rot(4, -8, -5), phase=0.3)
    walk.wave("head", R, rot(-6, 8, 4), phase=PI)
    idle = Anim(4.5)
    idle.wave("chest", R, rot(4), cycles=1)
    idle.wave("head", R, rot(0, 14, 6), cycles=0.5)
    idle.wave("jaw", R, rot(10), cycles=2, offset=rot(5))
    idle.wave("flame", "scale", (0.2, 0.3, 0.2), cycles=6, offset=(1, 1, 1))
    idle.wave("soul_cage", R, rot(3, 0, 3), cycles=1)
    attack = Anim(1.2, loop=False)
    attack.keys("right_arm", R, [(0, rot()), (0.45, rot(-185, 10, 10)), (0.65, rot(-40, 0, 0)), (1.2, rot())])
    attack.keys("left_arm", R, [(0, rot()), (0.45, rot(-160, -10, -20)), (0.65, rot(-40, 0, 0)), (1.2, rot())])
    attack.keys("right_forearm", R, [(0, rot()), (0.45, rot(-30)), (0.65, rot(0)), (1.2, rot())])
    attack.keys("chest", R, [(0, rot()), (0.45, rot(-30)), (0.65, rot(30)), (0.9, rot(25)), (1.2, rot())])
    attack.keys("body", X3, [(0, (0, 0, 0)), (0.45, (0, -2, 1)), (0.65, (0, 2.5, -2)), (1.2, (0, 0, 0))])
    attack.keys("head", R, [(0, rot()), (0.45, rot(20)), (0.65, rot(-20)), (1.2, rot())])
    cast = Anim(1.0, loop=False)
    cast.keys("chest", R, [(0, rot()), (0.35, rot(-28)), (0.75, rot(-28)), (1.0, rot())])
    for side, s in (("right", 1), ("left", -1)):
        cast.keys(f"{side}_arm", R, [(0, rot()), (0.35, rot(-60, 0, 80 * s)), (0.75, rot(-60, 0, 80 * s)), (1.0, rot())])
    cast.keys("jaw", R, [(0, rot()), (0.35, rot(45)), (0.75, rot(45)), (1.0, rot())])
    cast.keys("flame", "scale", [(0, (1, 1, 1)), (0.35, (2, 2.4, 2)), (0.75, (2, 2.4, 2)), (1.0, (1, 1, 1))])
    return {"walk": walk, "idle": idle, "attack": attack, "cast": cast}


def chronos_set():
    """Glides without touching the ground; its pendulum swings faster as it moves.
    Attack: all four arms sweep the hands of a clock, the upper pair clockwise, the lower against."""
    base = wraith_set()
    walk = Anim(2.0)
    walk.wave("body", X3, (0, 1.4, 0), cycles=1, offset=(0, -1, 0))
    walk.const("body", R, rot(14))
    walk.wave("pendulum", R, rot(0, 0, 35), cycles=2)
    walk.wave("pendulum1", R, rot(0, 0, 15), cycles=2, phase=-0.6)
    for side, s in (("right", 1), ("left", -1)):
        walk.wave(f"{side}_arm", R, rot(12, 0, 4 * s), cycles=1, phase=0 if s > 0 else PI, offset=rot(-25, 0, 10 * s))
        walk.wave(f"{side}_low_arm", R, rot(10, 0, 0), cycles=1, phase=PI if s > 0 else 0)
    for i, b in enumerate(("robe", "robe1", "robe2", "robe3")):
        walk.wave(b, R, rot(8), cycles=1, phase=-0.7 * i, offset=rot(8))
    attack = Anim(1.0, loop=False)
    for side, s in (("right", 1), ("left", -1)):
        attack.keys(f"{side}_arm", R, [(0, rot()), (0.3, rot(-170, 0, 40 * s)), (0.6, rot(-40, 0, -50 * s)), (1.0, rot())])
        attack.keys(f"{side}_low_arm", R, [(0, rot()), (0.3, rot(-20, 0, -70 * s)), (0.6, rot(-90, 0, 50 * s)), (1.0, rot())])
    attack.keys("body", R, [(0, rot()), (0.3, rot(-10, 25, 0)), (0.6, rot(10, -25, 0)), (1.0, rot())])
    out = dict(base)
    out.update({"walk": walk, "attack": attack})
    if "fly" in base:
        out["fly"] = walk
    return out


def hive_mother_set():
    """Scuttles on eight legs with the egg sac heaving; attacks by rearing up and bringing both
    scythe limbs down."""
    base = crawler_set(4)
    walk = base["walk"]
    walk.wave("abdomen", "scale", (0.04, 0.06, 0.04), cycles=2, offset=(1, 1, 1))
    for side, s in (("right", 1), ("left", -1)):
        walk.wave(f"{side}_scythe", R, rot(15, 0, 0), cycles=2, phase=0 if s > 0 else PI)
    idle = base["idle"]
    idle.wave("abdomen", "scale", (0.06, 0.08, 0.06), cycles=2, offset=(1, 1, 1))
    for side, s in (("right", 1), ("left", -1)):
        idle.wave(f"{side}_scythe", R, rot(8, 6 * s, 0), cycles=1, phase=s)
    attack = Anim(1.0, loop=False)
    attack.keys("body", R, [(0, rot()), (0.35, rot(-35)), (0.55, rot(15)), (1.0, rot())])
    attack.keys("body", X3, [(0, (0, 0, 0)), (0.35, (0, -4, 2)), (0.55, (0, 1, -2)), (1.0, (0, 0, 0))])
    for side, s in (("right", 1), ("left", -1)):
        attack.keys(f"{side}_scythe", R, [(0, rot()), (0.35, rot(-80, 20 * s, 0)), (0.55, rot(50, 0, 0)), (1.0, rot())])
        attack.keys(f"{side}_scythe_blade", R, [(0, rot()), (0.35, rot(-30)), (0.55, rot(30)), (1.0, rot())])
    attack.keys("jaw", R, [(0, rot()), (0.3, rot(40)), (0.7, rot())])
    cast = Anim(1.2, loop=False)
    cast.keys("abdomen", "scale", [(0, (1, 1, 1)), (0.4, (1.25, 1.3, 1.25)), (0.8, (0.9, 0.9, 0.9)), (1.2, (1, 1, 1))])
    cast.keys("abdomen", R, [(0, rot()), (0.4, rot(-25)), (0.8, rot(-25)), (1.2, rot())])
    return {"walk": walk, "idle": idle, "attack": attack, "cast": cast}


def bride_set():
    """Drifts with her veil and train streaming behind; attacks by reaching both endless arms out
    and closing them like a curtain; her cast is the wail itself."""
    base = wraith_set()
    walk = Anim(2.4)
    walk.wave("body", X3, (0, 1.2, 0), cycles=1, offset=(0, -1.5, 0))
    walk.const("body", R, rot(10))
    for i, b in enumerate(("robe", "robe1", "robe2", "robe3")):
        walk.wave(b, R, rot(10, 0, 3), cycles=1, phase=-0.7 * i, offset=rot(10))
    for side, s in (("right", 1), ("left", -1)):
        walk.wave(f"{side}_arm", R, rot(8, 0, 4 * s), cycles=1, phase=0 if s > 0 else PI, offset=rot(-35, 0, 10 * s))
    walk.wave("head", R, rot(0, 0, 10), cycles=0.5)
    attack = Anim(1.1, loop=False)
    for side, s in (("right", 1), ("left", -1)):
        attack.keys(f"{side}_arm", R, [(0, rot()), (0.35, rot(-90, 0, 100 * s)), (0.6, rot(-90, 0, -20 * s)), (1.1, rot())])
        attack.keys(f"{side}_forearm", R, [(0, rot()), (0.35, rot(-10)), (0.6, rot(-50)), (1.1, rot())])
    attack.keys("head", R, [(0, rot()), (0.35, rot(-20)), (0.6, rot(15)), (1.1, rot())])
    attack.keys("jaw", R, [(0, rot()), (0.35, rot(50)), (0.8, rot())])
    cast = Anim(1.4, loop=False)
    cast.keys("head", R, [(0, rot()), (0.3, rot(-40)), (1.0, rot(-40)), (1.4, rot())])
    cast.keys("jaw", R, [(0, rot()), (0.3, rot(70)), (1.0, rot(70)), (1.4, rot())])
    for side, s in (("right", 1), ("left", -1)):
        cast.keys(f"{side}_arm", R, [(0, rot()), (0.3, rot(-30, 0, 120 * s)), (1.0, rot(-30, 0, 120 * s)), (1.4, rot())])
    cast.keys("body", X3, [(0, (0, 0, 0)), (0.3, (0, -4, 0)), (1.0, (0, -4, 0)), (1.4, (0, 0, 0))])
    out = dict(base)
    out.update({"walk": walk, "attack": attack, "cast": cast})
    if "fly" in base:
        out["fly"] = walk
    return out


def gravewyrm_set():
    """A sideways wave runs through every segment; it lunges with all three jaws flaring."""
    walk = Anim(1.0)
    for i in range(6):
        walk.wave("tail" if i == 0 else f"tail{i}", R, rot(0, 22, 0), cycles=1, phase=-0.8 * (i + 1))
    walk.wave("body", R, rot(0, 12, 0), cycles=1)
    walk.wave("head", R, rot(5, -14, 0), cycles=1, phase=0.6)
    for i in range(3):
        for side, s in (("right", 1), ("left", -1)):
            walk.wave(f"{side}_leg{i}", R, rot(0, 25 * s, 10 * s), cycles=2, phase=i + (side == "left") * PI)
    idle = Anim(3.0)
    for i in range(6):
        idle.wave("tail" if i == 0 else f"tail{i}", R, rot(0, 8, 0), cycles=1, phase=-0.6 * i)
    idle.wave("head", R, rot(10, 10, 0), cycles=1)
    for k in range(3):
        idle.wave(f"mandible{k}", R, rot(10, 0, 0), cycles=2, phase=k)
    attack = Anim(0.7, loop=False)
    attack.keys("body", X3, [(0, (0, 0, 0)), (0.25, (0, -2, 3)), (0.4, (0, 0, -5)), (0.7, (0, 0, 0))])
    attack.keys("head", R, [(0, rot()), (0.25, rot(-30)), (0.4, rot(20)), (0.7, rot())])
    for k, (rx, rz) in enumerate(((40, 0), (0, 50), (0, -50))):
        attack.keys(f"mandible{k}", R, [(0, rot()), (0.25, rot(rx, 0, rz)), (0.4, rot(-10, 0, 0)), (0.7, rot())])
    return {"walk": walk, "idle": idle, "attack": attack, "cast": attack}


CUSTOM_SETS = {"fracture": fracture_set, "dim_light_demon": demon_set, "petrified": petrified_set,
               "skeleton": skeleton_set, "chronos": chronos_set, "hive_mother": hive_mother_set,
               "wailing_bride": bride_set, "gravewyrm": gravewyrm_set}


def depth_of(name):
    m = re.search(r"(\d+)$", name)
    return int(m.group(1)) if m else 0


def add_extras(anims, bones):
    """Generic motion for creature-specific bones, layered onto the archetype set."""
    idle = anims["idle"]
    walk = anims["walk"]
    for b in bones:
        side = -1 if b.startswith("left") else 1
        d = depth_of(b)
        if b.startswith(("orbit", "halo")):
            idle.spin(b, "y")  # rings lie in the bone's XZ plane, so spin about local Y
        elif b == "clock_hand":
            idle.spin(b, "z")
        elif b.startswith("flame"):
            idle.wave(b, "scale", (0.12, 0.3, 0.12), cycles=6, phase=d * 1.7, offset=(1, 1, 1))
        elif b.startswith("mask"):
            idle.wave(b, R, rot(0, 25 * (1 if b.endswith("0") else -1), 10), cycles=1, phase=d)
        elif b.startswith("pendulum") and b != "pendulum_weight":
            idle.wave(b, R, rot(0, 0, 25 if d == 0 else 10), cycles=2, phase=-0.5 * d)
        elif "antenna" in b or "eye_stalk" in b:
            idle.wave(b, R, rot(8, 0, 10 * side), cycles=2, phase=d)
        elif b.endswith("_ear") or b.endswith("_ear1"):
            idle.wave(b, R, rot(6, 0, 6 * side), cycles=2, phase=d)
        elif SWAY.match(b):
            idle.wave(b, R, rot(10, 8, 6), cycles=1, phase=-0.8 * d)
            walk.wave(b, R, rot(14, 6, 0), cycles=2, phase=-0.8 * d, offset=rot(10 if not b.startswith("tail") else 0))
        elif "bone_wing" in b:
            tip = b.endswith("_tip")
            idle.wave(b, R, rot(0, 12 * side, (14 if tip else 8) * side), cycles=1, phase=0.6 if tip else 0)
            walk.wave(b, R, rot(0, 6 * side, 10 * side), cycles=2, phase=0.6 if tip else 0)
        elif b == "soul_orb":
            idle.wave(b, X3, (0, 0.8, 0), cycles=2)
        elif b.startswith(("right_horn", "left_horn", "right_antler", "left_antler", "rebar", "crystal", "rock_spike",
                           "spine_spike", "char_spike", "candle")):
            continue
    return anims


def build(name, archetype, bones):
    if name in CUSTOM_SETS:
        anims = CUSTOM_SETS[name]()
    elif archetype == "crawler":
        pairs = sum(1 for b in bones if re.fullmatch(r"right_leg\d", b))
        anims = crawler_set(pairs)
    elif archetype == "critter":
        anims = critter_set(hop=name in ("duskhare", "candlewick_toad"))
    else:
        anims = SETS[archetype]()
    anims = add_extras(anims, bones)
    return {k: a.sample(set(bones)) for k, a in anims.items()}


def to_neoforge_json(A):
    anims = []
    for bone, chans in A["bones"].items():
        for ch, keys in chans.items():
            kf = []
            for t, (x, y, z) in keys:
                if ch == "position":
                    y = -y  # NeoForge applies posVec, which flips Y back into model space
                kf.append({"timestamp": t, "target": [x, y, z], "interpolation": "minecraft:catmullrom"})
            anims.append({"bone": bone, "target": f"minecraft:{ch}", "keyframes": kf})
    return {"length": A["length"], "loop": A["loop"], "animations": anims}
