"""DeadCraft entity model definitions.

Each model is a tree of parts made of cuboids, in Minecraft's Java model space (Y points down,
the feet sit at y = 24). From these definitions the generator:

* packs box-UV coordinates automatically,
* writes ``ModelGeometry.java`` (the LayerDefinitions the game loads),
* writes a Blockbench ``.bbmodel`` per model (open it in Blockbench to refine shapes and paint),
* hands the UV layout to the texture painter so faces line up with the geometry.
"""
from __future__ import annotations

import json
import math
import uuid
from dataclasses import dataclass, field


@dataclass
class Cube:
    x: float
    y: float
    z: float
    w: int
    h: int
    d: int
    role: str = "skin"
    inflate: float = 0.0
    u: int = 0
    v: int = 0

    @property
    def uv_size(self):
        return (2 * self.d + 2 * self.w, self.d + self.h)


@dataclass
class Part:
    name: str
    parent: str | None
    pivot: tuple
    rot: tuple = (0.0, 0.0, 0.0)
    cubes: list = field(default_factory=list)


def C(x, y, z, w, h, d, role="skin", inflate=0.0):
    return Cube(x, y, z, w, h, d, role, inflate)


def P(name, parent, pivot, cubes, rot=(0.0, 0.0, 0.0)):
    return Part(name, parent, pivot, rot, cubes)


# ---------------------------------------------------------------------------------------------
# Hostile archetypes
# ---------------------------------------------------------------------------------------------
def gaunt():
    return [
        P("body", None, (0, -1, 0), [C(-4, 0, -2.5, 8, 13, 5, "skin"), C(-3.5, 2, -3.5, 7, 6, 1, "bone"),
                                     C(-0.5, 0, 2.5, 1, 12, 1, "bone")], rot=(0.2, 0, 0)),
        P("head", None, (0, -1, -1), [C(-3.5, -8, -4, 7, 8, 7, "head")]),
        P("jaw", "head", (0, -1, -1), [C(-3, 0, -3, 6, 2, 5, "skin")], rot=(0.3, 0, 0)),
        P("right_arm", None, (-5, 0, 0), [C(-1, -1, -1, 2, 19, 2, "skin"), C(-1.5, 17, -1.5, 3, 5, 3, "bone")], rot=(-0.15, 0, 0.1)),
        P("left_arm", None, (5, 0, 0), [C(-1, -1, -1, 2, 19, 2, "skin"), C(-1.5, 17, -1.5, 3, 5, 3, "bone")], rot=(-0.15, 0, -0.1)),
        P("right_leg", None, (-2, 12, 0), [C(-1.5, 0, -1.5, 3, 12, 3, "cloth")]),
        P("left_leg", None, (2, 12, 0), [C(-1.5, 0, -1.5, 3, 12, 3, "cloth")]),
    ]


def crawler():
    parts = [
        P("body", None, (0, 15, 0), [C(-4, -3, -7, 8, 6, 14, "skin"), C(-1, -5, -6, 2, 2, 12, "bone")]),
        P("head", None, (0, 14, -7), [C(-3, -3, -6, 6, 6, 6, "head")]),
        P("jaw", "head", (0, 2, -3), [C(-2.5, 0, -3, 5, 2, 4, "skin")], rot=(0.35, 0, 0)),
        P("abdomen", None, (0, 15, 7), [C(-3, -3, 0, 6, 5, 6, "accent")]),
    ]
    for i, z in enumerate((-5, 0, 5)):
        spread = (0.45, 0.0, -0.45)[i]
        parts.append(P(f"right_leg{i}", None, (-4, 15, z), [C(-12, -1, -1, 12, 2, 2, "skin")], rot=(0, spread, -0.65)))
        parts.append(P(f"left_leg{i}", None, (4, 15, z), [C(0, -1, -1, 12, 2, 2, "skin")], rot=(0, -spread, 0.65)))
    return parts


def wraith():
    return [
        P("head", None, (0, -6, 0), [C(-4, -8, -4, 8, 8, 8, "head")]),
        P("body", None, (0, -6, 0), [C(-5, 0, -3, 10, 12, 6, "cloth")]),
        P("robe", "body", (0, 12, 0), [C(-4, 0, -2.5, 8, 8, 5, "cloth")], rot=(0.15, 0, 0)),
        P("tail", "robe", (0, 8, 0), [C(-2.5, 0, -1.5, 5, 6, 3, "cloth")], rot=(0.2, 0, 0)),
        P("right_arm", None, (-6, -5, 0), [C(-1, 0, -1, 2, 16, 2, "skin"), C(-1.5, 14, -1.5, 3, 4, 3, "bone")]),
        P("left_arm", None, (6, -5, 0), [C(-1, 0, -1, 2, 16, 2, "skin"), C(-1.5, 14, -1.5, 3, 4, 3, "bone")]),
    ]


def brute():
    return [
        P("body", None, (0, 4, 2), [C(-7, -10, -5, 14, 14, 10, "skin")], rot=(0.35, 0, 0)),
        P("spikes", "body", (0, -10, 2), [C(-4, -3, -3, 2, 3, 2, "accent"), C(2, -4, -1, 2, 4, 2, "accent"),
                                           C(-1, -3, 1, 2, 3, 2, "accent")]),
        P("head", None, (0, -3, -6), [C(-4, -4, -7, 8, 8, 7, "head")]),
        P("jaw", "head", (0, 3, -3), [C(-3.5, 0, -4, 7, 3, 5, "skin")], rot=(0.25, 0, 0)),
        P("left_horn", "head", (3, -4, -3), [C(0, -6, -1, 2, 6, 2, "horn")], rot=(-0.3, 0, 0.5)),
        P("right_horn", "head", (-3, -4, -3), [C(-2, -6, -1, 2, 6, 2, "horn")], rot=(-0.3, 0, -0.5)),
        P("right_arm", None, (-8.5, -3, 0), [C(-3, -2, -3, 6, 14, 6, "skin")]),
        P("right_fist", "right_arm", (0, 12, 0), [C(-3.5, 0, -3.5, 7, 8, 7, "accent")]),
        P("left_arm", None, (8.5, -3, 0), [C(-3, -2, -3, 6, 14, 6, "skin")]),
        P("left_fist", "left_arm", (0, 12, 0), [C(-3.5, 0, -3.5, 7, 8, 7, "accent")]),
        P("right_leg", None, (-4, 15, 1), [C(-3, 0, -3, 6, 9, 6, "skin")]),
        P("left_leg", None, (4, 15, 1), [C(-3, 0, -3, 6, 9, 6, "skin")]),
    ]


# ---------------------------------------------------------------------------------------------
# Peaceful archetypes
# ---------------------------------------------------------------------------------------------
def quadruped():
    return [
        P("body", None, (0, 13, 0), [C(-4, -4, -7, 8, 8, 14, "skin"), C(-3.5, 3.5, -6, 7, 1, 12, "accent")]),
        P("head", None, (0, 10, -7), [C(-3, -4, -6, 6, 6, 6, "head"), C(-2, -1, -9, 4, 3, 3, "accent")]),
        P("ears", "head", (0, -4, -2), [C(2, -3, -0.5, 2, 3, 1, "accent"), C(-4, -3, -0.5, 2, 3, 1, "accent")]),
        P("horns", "head", (0, -4, -3), [C(3, -1, -1, 5, 2, 2, "horn"), C(-8, -1, -1, 5, 2, 2, "horn")], rot=(-0.2, 0, 0)),
        P("antlers", "head", (0, -4, -3), [C(1.5, -8, -0.5, 1, 8, 1, "horn"), C(2.5, -7, -0.5, 3, 1, 1, "horn"),
                                            C(2.5, -4, -0.5, 2, 1, 1, "horn"), C(-2.5, -8, -0.5, 1, 8, 1, "horn"),
                                            C(-5.5, -7, -0.5, 3, 1, 1, "horn"), C(-4.5, -4, -0.5, 2, 1, 1, "horn")]),
        P("tusks", "head", (0, 0, -6), [C(1, 0, -3, 1, 3, 1, "bone"), C(-2, 0, -3, 1, 3, 1, "bone")]),
        P("tail", None, (0, 10, 7), [C(-1, 0, 0, 2, 7, 2, "accent")], rot=(0.5, 0, 0)),
        P("right_hind_leg", None, (-2.5, 17, 5), [C(-1.5, 0, -1.5, 3, 7, 3, "skin")]),
        P("left_hind_leg", None, (2.5, 17, 5), [C(-1.5, 0, -1.5, 3, 7, 3, "skin")]),
        P("right_front_leg", None, (-2.5, 17, -5), [C(-1.5, 0, -1.5, 3, 7, 3, "skin")]),
        P("left_front_leg", None, (2.5, 17, -5), [C(-1.5, 0, -1.5, 3, 7, 3, "skin")]),
    ]


def critter():
    return [
        P("body", None, (0, 20, 0), [C(-2.5, -2.5, -3.5, 5, 5, 7, "skin")]),
        P("head", None, (0, 19, -3.5), [C(-2, -2.5, -3.5, 4, 4, 4, "head")]),
        P("ears", "head", (0, -2, -1), [C(-1.8, -5, 0, 1, 5, 1, "accent"), C(0.8, -5, 0, 1, 5, 1, "accent")], rot=(-0.3, 0, 0)),
        P("shell", None, (0, 17.5, 0), [C(-3, -2, -4, 6, 3, 8, "shell")]),
        P("tail", None, (0, 19, 3.5), [C(-1, -1, 0, 2, 2, 3, "accent")]),
        P("right_hind_leg", None, (-1.5, 22, 2.5), [C(-1, 0, -1, 2, 2, 2, "skin")]),
        P("left_hind_leg", None, (1.5, 22, 2.5), [C(-1, 0, -1, 2, 2, 2, "skin")]),
        P("right_front_leg", None, (-1.5, 22, -2.5), [C(-1, 0, -1, 2, 2, 2, "skin")]),
        P("left_front_leg", None, (1.5, 22, -2.5), [C(-1, 0, -1, 2, 2, 2, "skin")]),
    ]


def flyer():
    return [
        P("body", None, (0, 19, 0), [C(-2, -2, -3, 4, 4, 6, "skin")], rot=(-0.15, 0, 0)),
        P("head", None, (0, 17, -3), [C(-1.5, -3, -3, 3, 3, 3, "head"), C(-0.5, -2, -4, 1, 1, 1, "accent")]),
        P("ears", "head", (0, -3, -1), [C(-1.5, -1, 0, 1, 1, 1, "accent"), C(0.5, -1, 0, 1, 1, 1, "accent")]),
        P("right_wing", None, (-2, 17.5, -1), [C(-7, 0, -2, 7, 1, 5, "wing")]),
        P("left_wing", None, (2, 17.5, -1), [C(0, 0, -2, 7, 1, 5, "wing")]),
        P("tail", None, (0, 18, 3), [C(-1.5, 0, 0, 3, 1, 4, "accent")], rot=(-0.3, 0, 0)),
        P("right_leg", None, (-1, 21, 0), [C(-0.5, 0, -0.5, 1, 3, 1, "bone")]),
        P("left_leg", None, (1, 21, 0), [C(-0.5, 0, -0.5, 1, 3, 1, "bone")]),
    ]


# ---------------------------------------------------------------------------------------------
# Vehicles
# ---------------------------------------------------------------------------------------------
def buggy():
    frame = [
        C(-10, -3, -17, 20, 4, 34, "metal"), C(-9, -6, -17, 18, 3, 10, "paint"),
        C(-11, -2, -19, 22, 3, 2, "metal"), C(-9, -1, -21, 2, 2, 2, "bone"), C(7, -1, -21, 2, 2, 2, "bone"),
        C(-4, -6, 0, 8, 3, 6, "leather"), C(-4, -12, 5, 8, 6, 2, "leather"),
        C(-6, -8, 10, 12, 5, 6, "metal"), C(-8, -10, 14, 2, 6, 2, "metal"), C(6, -10, 14, 2, 6, 2, "metal"),
        C(-8, -5, -18, 3, 2, 1, "glow"), C(5, -5, -18, 3, 2, 1, "glow"), C(-1.5, -10, -3, 3, 3, 1, "metal"),
    ]
    cage = [C(-9, -17, -4, 2, 14, 2, "metal"), C(7, -17, -4, 2, 14, 2, "metal"), C(-9, -17, 6, 2, 14, 2, "metal"),
            C(7, -17, 6, 2, 14, 2, "metal"), C(-9, -19, -4, 18, 2, 2, "metal"), C(-9, -19, 6, 18, 2, 2, "metal"),
            C(-9, -19, -2, 2, 2, 8, "metal"), C(7, -19, -2, 2, 2, 8, "metal")]
    parts = [P("chassis", None, (0, 18, 0), frame), P("cage", "chassis", (0, 0, 0), cage)]
    for name, x, z in (("wheel_fl", -11, -11), ("wheel_fr", 11, -11), ("wheel_bl", -11, 11), ("wheel_br", 11, 11)):
        parts.append(P(name, None, (x, 19, z), [C(-2, -4, -4, 4, 8, 8, "tire")]))
    return parts


def skiff():
    return [
        P("hull", None, (0, 20, 0), [C(-8, -3, -12, 16, 4, 26, "metal"), C(-5, -2, -18, 10, 3, 6, "paint"),
                                     C(-8, -5, -12, 1, 2, 24, "paint"), C(7, -5, -12, 1, 2, 24, "paint"),
                                     C(-1, -10, 8, 2, 7, 6, "paint"), C(-3, -6, 0, 6, 3, 5, "leather"),
                                     C(-3, -11, 4, 6, 5, 1, "leather"), C(-6, -9, -9, 12, 4, 1, "glass"),
                                     C(-6, 1, -8, 12, 1, 4, "glow"), C(-6, 1, 6, 12, 1, 4, "glow")]),
        P("thruster_l", None, (-9, 19, 8), [C(-3, -3, -6, 5, 5, 12, "metal"), C(-2, -2, 6, 3, 3, 1, "glow")]),
        P("thruster_r", None, (9, 19, 8), [C(-2, -3, -6, 5, 5, 12, "metal"), C(-1, -2, 6, 3, 3, 1, "glow")]),
    ]


MODELS = {
    "gaunt": (gaunt, 64),
    "crawler": (crawler, 64),
    "wraith": (wraith, 64),
    "brute": (brute, 64),
    "quadruped": (quadruped, 64),
    "critter": (critter, 64),
    "flyer": (flyer, 64),
    "buggy": (buggy, 128),
    "skiff": (skiff, 128),
}


# ---------------------------------------------------------------------------------------------
# Vanilla-layout humanoids (bandits, Rotwalker, the Skeleton boss) — painted on fixed UVs
# ---------------------------------------------------------------------------------------------
def player_layout():
    """Cubes of the 64x64 player/zombie skin layout with their fixed UV offsets."""
    cubes = [
        (0, 0, 8, 8, 8, "head"), (32, 0, 8, 8, 8, "hat"),
        (16, 16, 8, 12, 4, "cloth"), (16, 32, 8, 12, 4, "jacket"),
        (40, 16, 4, 12, 4, "sleeve"), (40, 32, 4, 12, 4, "jacket"),
        (32, 48, 4, 12, 4, "sleeve"), (48, 48, 4, 12, 4, "jacket"),
        (0, 16, 4, 12, 4, "pants"), (0, 32, 4, 12, 4, "jacket"),
        (16, 48, 4, 12, 4, "pants"), (0, 48, 4, 12, 4, "jacket"),
    ]
    out = []
    for u, v, w, h, d, role in cubes:
        c = Cube(0, 0, 0, w, h, d, role)
        c.u, c.v = u, v
        out.append(c)
    return out


def skeleton_layout():
    cubes = [(0, 0, 8, 8, 8, "head"), (32, 0, 8, 8, 8, "hat"), (16, 16, 8, 12, 4, "bone"),
             (40, 16, 2, 12, 2, "bone"), (0, 16, 2, 12, 2, "bone")]
    out = []
    for u, v, w, h, d, role in cubes:
        c = Cube(0, 0, 0, w, h, d, role)
        c.u, c.v = u, v
        out.append(c)
    return out


def armor_layout(layer):
    """Vanilla humanoid armour layout (64x32)."""
    if layer == 1:
        cubes = [(0, 0, 8, 8, 8, "head"), (16, 16, 8, 12, 4, "body"), (40, 16, 4, 12, 4, "arm"), (0, 16, 4, 12, 4, "leg")]
    else:
        cubes = [(16, 16, 8, 12, 4, "body"), (0, 16, 4, 12, 4, "leg")]
    out = []
    for u, v, w, h, d, role in cubes:
        c = Cube(0, 0, 0, w, h, d, role)
        c.u, c.v = u, v
        out.append(c)
    return out


# ---------------------------------------------------------------------------------------------
# UV packing, Java and Blockbench output
# ---------------------------------------------------------------------------------------------
def pack(parts, width):
    cubes = [c for p in parts for c in p.cubes]
    order = sorted(cubes, key=lambda c: (-c.uv_size[1], -c.uv_size[0]))
    x = y = row_h = 0
    for c in order:
        w, h = c.uv_size
        if x + w > width:
            x, y, row_h = 0, y + row_h, 0
        c.u, c.v = x, y
        x += w
        row_h = max(row_h, h)
    height = y + row_h
    tex_h = width
    while tex_h < height:
        tex_h *= 2
    return tex_h


def build(name):
    fn, width = MODELS[name]
    parts = fn()
    height = pack(parts, width)
    return parts, width, height


def fmt(v):
    if abs(v - round(v)) < 1e-6:
        return f"{int(round(v))}.0F"
    return f"{v:g}F"


def java_method(name, parts, width, height):
    lines = [f"    public static LayerDefinition {name}() {{",
             "        MeshDefinition mesh = new MeshDefinition();",
             "        PartDefinition root = mesh.getRoot();"]
    for p in parts:
        parent = "root" if p.parent is None else f"p_{p.parent}"
        builder = "CubeListBuilder.create()"
        for c in p.cubes:
            deform = f", new CubeDeformation({fmt(c.inflate)})" if c.inflate else ""
            builder += f".texOffs({c.u}, {c.v}).addBox({fmt(c.x)}, {fmt(c.y)}, {fmt(c.z)}, {fmt(c.w)}, {fmt(c.h)}, {fmt(c.d)}{deform})"
        px, py, pz = p.pivot
        rx, ry, rz = p.rot
        pose = (f"PartPose.offsetAndRotation({fmt(px)}, {fmt(py)}, {fmt(pz)}, {fmt(rx)}, {fmt(ry)}, {fmt(rz)})"
                if any(p.rot) else f"PartPose.offset({fmt(px)}, {fmt(py)}, {fmt(pz)})")
        lines.append(f"        PartDefinition p_{p.name} = {parent}.addOrReplaceChild(\"{p.name}\", {builder}, {pose});")
    lines.append(f"        return LayerDefinition.create(mesh, {width}, {height});")
    lines.append("    }")
    return "\n".join(lines)


def write_java(path):
    methods = []
    for name in MODELS:
        parts, w, h = build(name)
        methods.append(java_method(name, parts, w, h))
    src = """package com.deadcraft.client.model;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

/**
 * GENERATED by tools/gen_assets.py from tools/models.py. Do not edit by hand: change the model
 * definitions (or the matching .bbmodel in /blockbench) and regenerate.
 */
@SuppressWarnings("unused")
public final class ModelGeometry {
    private ModelGeometry() {}

""" + "\n\n".join(methods) + "\n}\n"
    with open(path, "w") as f:
        f.write(src)


def absolute_pivots(parts):
    by_name = {p.name: p for p in parts}
    out = {}

    def resolve(p):
        if p.name in out:
            return out[p.name]
        if p.parent is None:
            res = p.pivot
        else:
            px, py, pz = resolve(by_name[p.parent])
            res = (px + p.pivot[0], py + p.pivot[1], pz + p.pivot[2])
        out[p.name] = res
        return res

    for p in parts:
        resolve(p)
    return out


def bbmodel(name, parts, width, height, texture_png_b64=None):
    """Blockbench 'Modded Entity' project. Java space (y down, feet at 24) → Blockbench (y up, feet at 0, x mirrored)."""
    abs_piv = absolute_pivots(parts)
    elements, groups = [], {}
    for p in parts:
        ax, ay, az = abs_piv[p.name]
        origin = [-ax, 24 - ay, az]
        rot = [-math.degrees(p.rot[0]), -math.degrees(p.rot[1]), math.degrees(p.rot[2])]
        g = {"name": p.name, "origin": origin, "rotation": rot, "uuid": str(uuid.uuid4()), "export": True,
             "isOpen": False, "visibility": True, "autouv": 0, "children": []}
        groups[p.name] = g
        for i, c in enumerate(p.cubes):
            x1, y1, z1 = ax + c.x, ay + c.y, az + c.z
            x2, y2, z2 = x1 + c.w, y1 + c.h, z1 + c.d
            el = {"name": f"{p.name}_{i}", "box_uv": True, "rescale": False, "locked": False,
                  "from": [-x2, 24 - y2, z1], "to": [-x1, 24 - y1, z2], "autouv": 0, "color": i % 8,
                  "inflate": c.inflate, "origin": origin, "uv_offset": [c.u, c.v], "type": "cube",
                  "uuid": str(uuid.uuid4()), "faces": {}}
            elements.append(el)
            g["children"].append(el["uuid"])
    outliner = []
    for p in parts:
        if p.parent is None:
            outliner.append(groups[p.name])
        else:
            groups[p.parent]["children"].append(groups[p.name])
    model = {
        "meta": {"format_version": "4.5", "model_format": "modded_entity", "box_uv": True},
        "name": name, "model_identifier": name, "modded_entity_version": "1.17",
        "modded_entity_flip_y": True, "visible_box": [1, 1, 0],
        "resolution": {"width": width, "height": height},
        "elements": elements, "outliner": outliner, "textures": [],
    }
    if texture_png_b64:
        model["textures"].append({"path": "", "name": f"{name}.png", "folder": "", "namespace": "", "id": "0",
                                  "particle": False, "render_mode": "default", "visible": True, "mode": "bitmap",
                                  "saved": True, "uuid": str(uuid.uuid4()), "relative_path": "",
                                  "source": "data:image/png;base64," + texture_png_b64})
    return json.dumps(model, indent=1)
