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
    fixed: bool = False

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
    """Shelf-pack box UVs. Cubes marked fixed keep their vanilla UVs; others go below them."""
    cubes = [c for p in parts for c in p.cubes]
    fixed = [c for c in cubes if c.fixed]
    free = [c for c in cubes if not c.fixed]
    start = max((c.v + c.uv_size[1] for c in fixed), default=0)
    order = sorted(free, key=lambda c: (-c.uv_size[1], -c.uv_size[0]))
    while True:
        x, y, row_h = 0, start, 0
        ok = True
        for c in order:
            w, h = c.uv_size
            if w > width:
                ok = False
                break
            if x + w > width:
                x, y, row_h = 0, y + row_h, 0
            c.u, c.v = x, y
            x += w
            row_h = max(row_h, h)
        if ok:
            break
        width *= 2
    height = max([y + row_h] + [c.v + c.uv_size[1] for c in fixed])
    tex_w = width
    tex_h = 16
    while tex_h < height:
        tex_h *= 2
    tex_h = max(tex_h, 32)
    return tex_w, tex_h


def build(name):
    """Build and UV-pack a named rig (creature, humanoid family or vehicle)."""
    import rigs
    rig = rigs.build_rig(name)
    width = 128 if name in rigs.VEHICLES else 64
    w, h = pack(rig.parts, width)
    # keep sheets roughly square so textures stay readable
    while h > w * 2:
        w *= 2
        w, h = pack(rig.parts, w)
    return rig.parts, w, h


def fmt(v):
    if abs(v - round(v)) < 1e-6:
        return f"{int(round(v))}.0F"
    return f"{v:g}F"


def method_name(name):
    head, *rest = name.split("_")
    return head + "".join(w.capitalize() for w in rest)


def java_method(name, parts, width, height):
    lines = [f"    public static LayerDefinition {method_name(name)}() {{",
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


def write_java(path, names):
    methods, entries = [], []
    for name in names:
        parts, w, h = build(name)
        methods.append(java_method(name, parts, w, h))
        entries.append(f"        m.put(\"{name}\", ModelGeometry::{method_name(name)});")
    src = """package com.deadcraft.client.model;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * GENERATED by tools/gen_assets.py from tools/rigs.py. Do not edit by hand: change the rig
 * (or refine the matching .bbmodel in /blockbench and port the change back) and regenerate.
 */
@SuppressWarnings("unused")
public final class ModelGeometry {
    /** Every model by id: creatures, humanoid families and vehicles. */
    public static final Map<String, Supplier<LayerDefinition>> ALL;

    static {
        Map<String, Supplier<LayerDefinition>> m = new LinkedHashMap<>();
""" + "\n".join(entries) + """
        ALL = Map.copyOf(m);
    }

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


def bbmodel(name, parts, width, height, texture_png_b64=None, animations=None):
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
        "elements": elements, "outliner": outliner, "textures": [], "animations": [],
    }
    for anim_name, A in (animations or {}).items():
        animators = {}
        for bone, chans in A["bones"].items():
            if bone not in groups:
                continue
            kfs = []
            for channel, keys in chans.items():
                for t, (x, y, z) in keys:
                    if channel == "rotation":
                        x, y, z = -x, -y, z
                    elif channel == "position":
                        x, y, z = -x, -y, z
                    kfs.append({"channel": channel, "data_points": [{"x": round(x, 3), "y": round(y, 3), "z": round(z, 3)}],
                                "uuid": str(uuid.uuid4()), "time": round(t, 4), "color": -1, "interpolation": "catmullrom"})
            animators[groups[bone]["uuid"]] = {"name": bone, "type": "bone", "keyframes": kfs}
        model["animations"].append({"uuid": str(uuid.uuid4()), "name": f"animation.{name}.{anim_name}",
                                    "loop": "loop" if A["loop"] else "once", "override": False, "length": A["length"],
                                    "snapping": 20, "selected": False, "animators": animators})
    if texture_png_b64:
        model["textures"].append({"path": "", "name": f"{name}.png", "folder": "", "namespace": "", "id": "0",
                                  "particle": False, "render_mode": "default", "visible": True, "mode": "bitmap",
                                  "saved": True, "uuid": str(uuid.uuid4()), "relative_path": "",
                                  "source": "data:image/png;base64," + texture_png_b64})
    return json.dumps(model, indent=1)
