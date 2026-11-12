#!/usr/bin/env python3
"""Generate every DeadCraft client asset: models, textures, item/block models, lang, sounds.json.

Run from anywhere:  python3 deadcraft/tools/gen_assets.py
Then:               python3 deadcraft/tools/gen_data.py   (server data)
                    python3 deadcraft/tools/gen_sounds.py (audio)
"""
from __future__ import annotations

import base64
import io
import json
import os
import random
import shutil

from PIL import Image

import content
import models
import paint

ROOT = content.ROOT
RES = os.path.join(ROOT, "src/main/resources")
ASSETS = os.path.join(RES, "assets/deadcraft")
REPO = os.path.dirname(ROOT)
FONT = os.path.join(REPO, "assets/fonts/PirataOne-Regular.ttf")


def out(path, data):
    path = os.path.join(ASSETS, path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if isinstance(data, Image.Image):
        data.save(path)
    elif isinstance(data, (dict, list)):
        with open(path, "w") as f:
            json.dump(data, f, indent=2)
            f.write("\n")
    else:
        with open(path, "w") as f:
            f.write(data)


def png_b64(img):
    buf = io.BytesIO()
    img.save(buf, "PNG")
    return base64.b64encode(buf.getvalue()).decode()


def title(s):
    small = {"of", "the", "s"}
    words = s.split("_")
    return " ".join(w if (i and w in small) else w.capitalize() for i, w in enumerate(words))


MOBS = content.mobs()
ITEMS = content.items()
BLOCKS = content.blocks()
SOUNDS = content.sounds()
PARTICLES = content.particles()

ARCH_MODEL = {"gaunt": "gaunt", "crawler": "crawler", "wraith": "wraith", "brute": "brute",
              "quadruped": "quadruped", "critter": "critter", "flyer": "flyer"}


# =============================================================================================
# Entity models and textures
# =============================================================================================
def entities():
    java_model_dir = os.path.join(content.JAVA, "client/model")
    os.makedirs(java_model_dir, exist_ok=True)
    models.write_java(os.path.join(java_model_dir, "ModelGeometry.java"))
    bb_dir = os.path.join(ROOT, "blockbench")
    os.makedirs(bb_dir, exist_ok=True)
    written_bb = set()

    for mob in MOBS:
        arch = mob["archetype"]
        if arch in ARCH_MODEL:
            parts, w, h = models.build(ARCH_MODEL[arch])
            img, glow = paint.paint_entity(parts, w, h, mob, arch)
            out(f"textures/entity/{mob['id']}.png", img)
            if mob["eye"] and (mob["kind"] != "PEACEFUL" or "GLOWS" in mob["features"]):
                out(f"textures/entity/{mob['id']}_eyes.png", glow)
            bb_path = os.path.join(bb_dir, f"{mob['id']}.bbmodel")
            with open(bb_path, "w") as f:
                f.write(models.bbmodel(mob["id"], parts, w, h, png_b64(img)))
            if ARCH_MODEL[arch] not in written_bb:
                written_bb.add(ARCH_MODEL[arch])
        elif arch in ("humanoid", "zombie"):
            pal = paint.mob_palette(mob)
            if arch == "humanoid":
                pal.update({"base": (196, 150, 120), "dark": paint.rgb(mob["accent"]), "bone": (60, 40, 30)})
                pal["base"] = (196, 150, 120)
            cubes = models.player_layout()

            def feats(p, f, mob=mob, arch=arch):
                p.face_features(f["front"], pal["eye"] if arch == "zombie" else (40, 30, 30), mouth=True, eyes_style="dot")
                if arch == "humanoid":
                    # bandana over the lower face and a hat brim colour on top
                    x0, y0, w, h = f["front"]
                    for yy in range(y0 + 5, y0 + 8):
                        for xx in range(x0, x0 + w):
                            p.px[xx, yy] = paint.jitter(paint.rgb(mob["accent"]), p.rnd, 8) + (255,)

            pn = paint.paint_layout(cubes, 64, 64, {**pal, "base": pal["base"]}, hash(mob["id"]) & 0xffff, feats)
            # cloth roles use mob base colour for bandits' coats
            if arch == "humanoid":
                coat = paint.rgb(mob["base"])
                for c in cubes:
                    if c.role in ("cloth", "pants"):
                        for rect in paint.faces(c).values():
                            pn.paint_face(rect, coat if c.role == "cloth" else paint.shade(coat, 0.6), 1.0, "stripes")
            out(f"textures/entity/{mob['id']}.png", pn.img)
            if arch == "zombie":
                out(f"textures/entity/{mob['id']}_eyes.png", pn.glow)
        elif arch == "skeleton":
            pal = paint.mob_palette(mob)
            pal["base"] = paint.rgb(mob["base"])
            pn = paint.paint_layout(models.skeleton_layout(), 64, 32, pal, 77,
                                    lambda p, f: p.face_features(f["front"], pal["eye"], mouth=True, eyes_style="slit"))
            # gold crown band on the head
            hx, hy, hw, hh = paint.faces(models.skeleton_layout()[0])["front"]
            for c in models.skeleton_layout()[:1]:
                for name, (x0, y0, w, h) in paint.faces(c).items():
                    if name in ("front", "back", "left", "right"):
                        for xx in range(x0, x0 + w):
                            pn.px[xx, y0] = (212, 175, 55, 255)
            out(f"textures/entity/{mob['id']}.png", pn.img)
            out(f"textures/entity/{mob['id']}_eyes.png", pn.glow)

    # Vehicles
    for name, model, paint_col in (("ruin_runner", "buggy", 0x8b2a1a), ("sky_skiff", "skiff", 0x2f3a4a)):
        parts, w, h = models.build(model)
        fake = {"id": name, "base": 0x55575c, "accent": paint_col, "eye": 0xffd27f, "kind": "VEHICLE", "ability": "NONE",
                "features": set()}
        pal = paint.mob_palette(fake)
        pal["glow"] = (255, 210, 120) if model == "buggy" else (90, 220, 255)
        p = paint.Painter(w, h, pal, 5)
        for part in parts:
            for c in part.cubes:
                f = p.paint_cube(c)
                if c.role in ("metal", "paint"):
                    for rect in f.values():
                        p.splatter(rect, (110, 60, 30), max(1, rect[2] * rect[3] // 25))  # rust
        out(f"textures/entity/{name}.png", p.img)
        out(f"textures/entity/{name}_glow.png", p.glow)
        with open(os.path.join(bb_dir, f"{name}.bbmodel"), "w") as fh:
            fh.write(models.bbmodel(name, parts, w, h, png_b64(p.img)))

    # Projectiles
    proj = {
        "bullet": ((255, 250, 220), (255, 180, 60), 0), "pellet": ((255, 240, 200), (255, 150, 50), 0),
        "shard": ((220, 255, 255), (60, 200, 255), 4), "star": ((255, 255, 230), (255, 200, 60), 5),
        "acid": ((220, 255, 120), (80, 160, 20), 0), "bone": ((250, 245, 230), (180, 170, 150), 2),
        "rock": ((150, 140, 130), (70, 65, 60), 6), "soul": ((220, 255, 255), (40, 160, 255), 0),
        "ember": ((255, 240, 180), (255, 80, 10), 6), "chrono": ((255, 250, 210), (212, 175, 55), 8),
    }
    for k, (core, edge, spikes) in proj.items():
        out(f"textures/entity/projectile/{k}.png", paint.glow_orb(core, edge, 16, spikes))


# =============================================================================================
# Armour
# =============================================================================================
ARMOR = {"necrite": (0xcfc6b0, 0x4a3a2a), "bloodstone": (0x8b0f14, 0x2a0505), "voidsteel": (0x2b2b3a, 0x7a5cff),
         "stardust": (0x2a3a6a, 0xffe680)}


def armor():
    for name, (base, accent) in ARMOR.items():
        pal = {"base": paint.rgb(base), "dark": paint.shade(paint.rgb(base), 0.6), "accent": paint.rgb(accent),
               "glow": paint.rgb(accent)}
        for layer in (1, 2):
            p = paint.Painter(64, 32, pal, hash(name) & 0xffff + layer)
            for c in models.armor_layout(layer):
                f = p.paint_cube(c)
                for rect in f.values():
                    p.cracks(rect, paint.rgb(accent), glow=False)
                if c.role == "head":
                    x0, y0, w, h = f["front"]
                    for xx in range(x0 + 1, x0 + w - 1):
                        p.px[xx, y0 + 3] = (10, 6, 8, 255)  # visor slit
                        p.px[xx, y0 + 4] = (10, 6, 8, 255)
            # armour textures must leave the inside transparent: clear the face of non-head cubes' bottom faces
            out(f"textures/models/armor/{name}_layer_{layer}.png", p.img)


# =============================================================================================
# Items
# =============================================================================================
def C(h):
    return paint.rgb(h)


ITEM_ART = {
    # materials
    "raw_necrite": ("raw", paint.item_palette(C(0xcfc6b0))),
    "necrite_ingot": ("ingot", paint.item_palette(C(0xd8d0bc))),
    "bloodstone": ("gem", paint.item_palette(C(0xb0101a))),
    "raw_voidsteel": ("raw", paint.item_palette(C(0x3b3550))),
    "voidsteel_ingot": ("ingot", paint.item_palette(C(0x3a3550))),
    "star_shard": ("gem", paint.item_palette(C(0xffe066))),
    "hollow_essence": ("essence", paint.item_palette(C(0x5b0f0f), glow=C(0xf2f2f2))),
    "ectoplasm": ("slime", paint.item_palette(C(0x8fd3c9))),
    "demon_horn": ("horn", paint.item_palette(C(0x5e0b0b))),
    "scrap_metal": ("scrap", paint.item_palette(C(0x7a6a5a))),
    "fracture_core": ("orb", paint.item_palette(C(0x7df9ff))),
    "chronos_hourglass": ("hourglass", paint.item_palette(C(0xd4af37), glow=C(0xfff3b0))),
    "skeleton_crown": ("crown", paint.item_palette(C(0xd4af37), glow=C(0x40ff60))),
    "dim_heart": ("heart", paint.item_palette(C(0x4b0082), glow=C(0xb388ff))),
    "petrified_eye": ("eye", paint.item_palette(C(0x77736b), glow=C(0x9bff7a))),
    # food
    "raw_ash_venison": ("meat", paint.item_palette(C(0xb8495a))),
    "cooked_ash_venison": ("meat", paint.item_palette(C(0x7a4128))),
    "raw_gloom_pork": ("meat", paint.item_palette(C(0xc77a9a))),
    "cooked_gloom_pork": ("meat", paint.item_palette(C(0x8a5530))),
    "raw_cinder_fowl": ("drumstick", paint.item_palette(C(0xe0907a))),
    "cooked_cinder_fowl": ("drumstick", paint.item_palette(C(0xa8642a))),
    "raw_marrow_beef": ("meat", paint.item_palette(C(0xa8202a))),
    "cooked_marrow_beef": ("meat", paint.item_palette(C(0x6a3a20))),
    "raw_dusk_hare": ("drumstick", paint.item_palette(C(0xc08aa0))),
    "cooked_dusk_hare": ("drumstick", paint.item_palette(C(0x8a5a3a))),
    # melee
    "bone_cleaver": ("cleaver", paint.item_palette(C(0xd8d0bc), accent=C(0x4a3a2a))),
    "bloodreaver": ("sword", paint.item_palette(C(0xb0101a), accent=C(0x2a0505), glow=C(0xff4040))),
    "voidsteel_longsword": ("sword", paint.item_palette(C(0x4a4466), accent=C(0x7a5cff), glow=C(0x7a5cff))),
    "star_edge": ("sword", paint.item_palette(C(0xffe680), accent=C(0x2a3a6a), glow=C(0xffffff))),
    "hollow_scythe": ("scythe", paint.item_palette(C(0x8a8a90), accent=C(0x5b0f0f), glow=C(0xf2f2f2))),
    "chrono_blade": ("sword", paint.item_palette(C(0xd4af37), accent=C(0x2a2410), glow=C(0xfff3b0))),
    "fracture_greatsword": ("greatsword", paint.item_palette(C(0x7df9ff), accent=C(0x2b2f3a), glow=C(0xffffff))),
    # ammo
    "pistol_rounds": ("bullets", paint.item_palette(C(0xd4af37), accent=C(0x8a6a2a))),
    "shotgun_shells": ("shells", paint.item_palette(C(0xb0201a), accent=C(0xd4af37))),
    "rifle_rounds": ("bullets", paint.item_palette(C(0xb87333), accent=C(0x6a4a2a))),
    # magic
    "soul_staff": ("staff", paint.item_palette(C(0x3a2a20), accent=C(0x8a8a90), handle=C(0x4a3020), glow=C(0x5ad8ff))),
    "ember_staff": ("staff", paint.item_palette(C(0x3a2a20), accent=C(0xb0101a), handle=C(0x2a1a10), glow=C(0xff8020))),
    "frost_tome": ("tome", paint.item_palette(C(0x2a5a8a), accent=C(0xc0e0ff), glow=C(0xe0f8ff))),
    "void_tome": ("tome", paint.item_palette(C(0x2a1a4a), accent=C(0x7a5cff), glow=C(0xc0a0ff))),
    "storm_rod": ("rod", paint.item_palette(C(0xb87333), accent=C(0x7df9ff), handle=C(0x6a4a2a), glow=C(0x7df9ff))),
    "blood_chalice": ("chalice", paint.item_palette(C(0xd4af37), glow=C(0xb0101a))),
    "grimoire_of_awakening": ("grimoire", paint.item_palette(C(0x4a0a10), accent=C(0xd4af37), glow=C(0xff3030))),
    "mana_tonic": ("bottle", paint.item_palette(C(0xc0c0d0), handle=C(0x8a5a3a), glow=C(0x4a8aff))),
    # travel
    "rift_key_hells_paradise": ("key", paint.item_palette(C(0xb0101a), glow=C(0xff8020))),
    "rift_key_skyfall_torn": ("key", paint.item_palette(C(0x8fd3ff), glow=C(0xffffff))),
    "rift_key_lights_descent": ("key", paint.item_palette(C(0xffe680), glow=C(0xfff6b0))),
    "rift_key_disposed_skies": ("key", paint.item_palette(C(0x4a3a6a), glow=C(0x7df9ff))),
    "ruin_runner": ("buggy", paint.item_palette(C(0x8b2a1a), handle=C(0x202020), glow=C(0xffd27f))),
    "sky_skiff": ("skiff", paint.item_palette(C(0x2f3a4a), glow=C(0x5ad8ff))),
    # sigils
    "sigil_fracture": ("sigil", paint.item_palette(C(0x2b2f3a), glow=C(0x7df9ff))),
    "sigil_chronos": ("sigil", paint.item_palette(C(0x2a2410), glow=C(0xffd700))),
    "sigil_skeleton": ("sigil", paint.item_palette(C(0xe8e0c8), glow=C(0x40ff60))),
    "sigil_dim_light_demon": ("sigil", paint.item_palette(C(0x14070a), glow=C(0xb388ff))),
    "sigil_petrified": ("sigil", paint.item_palette(C(0x77736b), glow=C(0x9bff7a))),
}
for mat, (base, accent) in ARMOR.items():
    for piece in ("helmet", "chestplate", "leggings", "boots"):
        ITEM_ART[f"{mat}_{piece}"] = (piece, paint.item_palette(C(base), glow=C(accent)))

HANDHELD = {"bone_cleaver", "bloodreaver", "voidsteel_longsword", "star_edge", "hollow_scythe", "chrono_blade",
            "fracture_greatsword", "soul_staff", "ember_staff", "storm_rod"}


# ---- 3D gun models (Blockbench "Java Block/Item" compatible) --------------------------------
SWATCH = {"metal": (0, 0), "steel": (4, 0), "wood": (8, 0), "rust": (12, 0), "black": (0, 4), "gold": (4, 4),
          "glow": (8, 4), "leather": (12, 4), "brass": (0, 8), "glass": (4, 8)}


def gun_texture(seed, tint):
    colors = {"metal": (58, 60, 66), "steel": (130, 134, 142), "wood": (110, 66, 36), "rust": (130, 64, 30),
              "black": (24, 24, 28), "gold": (200, 160, 60), "glow": (255, 90, 40), "leather": (80, 50, 34),
              "brass": (190, 140, 60), "glass": (120, 190, 230)}
    rnd = random.Random(seed)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for name, (sx, sy) in SWATCH.items():
        base = paint.mix(colors[name], tint, 0.15) if name in ("metal", "steel") else colors[name]
        for y in range(4):
            for x in range(4):
                c = paint.shade(base, 1.15 - 0.1 * y)
                c = paint.jitter(c, rnd, 7)
                if name == "rust" or (name == "metal" and rnd.random() < 0.15):
                    c = paint.jitter(paint.mix(c, (130, 64, 30), 0.3), rnd, 8)
                px[sx + x, sy + y] = c + (255,)
    return img


def el(frm, to, mat, rot=None):
    sx, sy = SWATCH[mat]
    uv = [sx, sy, sx + 4, sy + 4]
    e = {"from": frm, "to": to, "faces": {f: {"uv": uv, "texture": "#gun"} for f in ("north", "south", "east", "west", "up", "down")}}
    if rot:
        e["rotation"] = rot
    return e


GUNS = {
    "rusted_revolver": [
        el([5, 7, 7], [11, 10, 9], "metal"), el([11, 8, 7.5], [17, 9.5, 8.5], "steel"),
        el([7, 6.5, 6.5], [10, 9.5, 9.5], "rust"), el([16.5, 9.5, 7.75], [17, 10.25, 8.25], "steel"),
        el([4, 2.5, 7], [6.5, 7.5, 9], "wood", {"angle": -22.5, "axis": "z", "origin": [5, 7, 8]}),
        el([4.5, 9.5, 7.5], [5.5, 11, 8.5], "black"), el([6.5, 5.5, 7.75], [9, 6.5, 8.25], "black"),
    ],
    "doomsday_shotgun": [
        el([2, 7, 7], [12, 10, 9], "metal"), el([12, 8.6, 7.1], [25, 9.8, 8], "steel"),
        el([12, 8.6, 8], [25, 9.8, 8.9], "steel"), el([13, 6.8, 7.2], [19, 8.4, 8.8], "wood"),
        el([-6, 5, 7.25], [2.5, 8.5, 8.75], "wood", {"angle": 22.5, "axis": "z", "origin": [2, 8, 8]}),
        el([3, 3, 7.25], [5, 7, 8.75], "wood", {"angle": -22.5, "axis": "z", "origin": [4, 7, 8]}),
        el([5, 5.5, 7.75], [7.5, 6.5, 8.25], "black"), el([24, 9.8, 7.75], [24.5, 10.4, 8.25], "gold"),
    ],
    "marksman_rifle": [
        el([0, 7, 7], [14, 9.75, 9], "black"), el([14, 8, 7.5], [29, 9, 8.5], "steel"),
        el([4, 10.5, 7.25], [12, 12, 8.75], "metal"), el([3.5, 10.25, 7.1], [4.5, 12.25, 8.9], "glass"),
        el([11.5, 10.25, 7.1], [12.5, 12.25, 8.9], "glass"), el([6, 9.75, 7.75], [10, 10.5, 8.25], "metal"),
        el([-8, 5.5, 7.1], [0.5, 9.5, 8.9], "wood", {"angle": 22.5, "axis": "z", "origin": [0, 8, 8]}),
        el([7, 3.5, 7.25], [9, 7, 8.75], "metal"), el([2.5, 3, 7.25], [4.5, 7, 8.75], "wood", {"angle": -22.5, "axis": "z", "origin": [3.5, 7, 8]}),
        el([14, 7, 7.25], [20, 8, 8.75], "wood"),
    ],
    "scrap_smg": [
        el([3, 7, 7], [13, 10, 9], "rust"), el([13, 8, 7.5], [16, 9, 8.5], "steel"),
        el([15.5, 7.6, 7.1], [19, 9.4, 8.9], "black"), el([8, 1.5, 7.3], [10, 7, 8.7], "metal"),
        el([4, 3, 7.25], [6, 7, 8.75], "black", {"angle": -22.5, "axis": "z", "origin": [5, 7, 8]}),
        el([-2, 8, 7.75], [3, 9, 8.25], "steel"), el([-2.5, 6, 7.6], [-1.5, 9, 8.4], "steel"),
        el([6, 10, 7.6], [10, 10.6, 8.4], "glow"),
    ],
}
GUN_TINT = {"rusted_revolver": (130, 64, 30), "doomsday_shotgun": (60, 40, 30), "marksman_rifle": (40, 40, 60),
            "scrap_smg": (100, 90, 70)}

GUN_DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, -90, 0], "translation": [0, 2.5, 1.5], "scale": [0.7, 0.7, 0.7]},
    "thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [0, 2.5, 1.5], "scale": [0.7, 0.7, 0.7]},
    "firstperson_righthand": {"rotation": [0, -90, 0], "translation": [1.5, 3.5, 1.5], "scale": [0.75, 0.75, 0.75]},
    "firstperson_lefthand": {"rotation": [0, 90, 0], "translation": [1.5, 3.5, 1.5], "scale": [0.75, 0.75, 0.75]},
    "ground": {"translation": [0, 2, 0], "scale": [0.45, 0.45, 0.45]},
    "gui": {"rotation": [15, -30, 0], "translation": [-1, 0, 0], "scale": [0.55, 0.55, 0.55]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7]},
    "fixed": {"rotation": [0, 180, 0], "scale": [0.6, 0.6, 0.6]},
}


def items():
    for item_id, kind in ITEMS:
        if kind == "gun":
            out(f"textures/item/{item_id}.png", gun_texture(hash(item_id) & 0xffff, GUN_TINT[item_id]))
            out(f"models/item/{item_id}.json", {
                "credit": "DeadCraft - open in Blockbench (Java Block/Item) to refine",
                "texture_size": [16, 16],
                "textures": {"gun": f"deadcraft:item/{item_id}", "particle": f"deadcraft:item/{item_id}"},
                "elements": GUNS[item_id], "display": GUN_DISPLAY})
            continue
        art = ITEM_ART.get(item_id)
        if art is None:
            raise SystemExit(f"No item art for {item_id}")
        template, pal = art
        out(f"textures/item/{item_id}.png", paint.sprite(template, pal, hash(item_id) & 0xffff))
        parent = "minecraft:item/handheld" if item_id in HANDHELD else "minecraft:item/generated"
        out(f"models/item/{item_id}.json", {"parent": parent, "textures": {"layer0": f"deadcraft:item/{item_id}"}})
    for mob in MOBS:
        out(f"models/item/{mob['id']}_spawn_egg.json", {"parent": "minecraft:item/template_spawn_egg"})


# =============================================================================================
# Blocks
# =============================================================================================
def blocks():
    stone = paint.noise_tile((122, 122, 124), 1)
    deep = paint.noise_tile((70, 70, 78), 2, streaks=True)
    voidbase = paint.noise_tile((40, 36, 52), 3, 12)
    tex = {
        "necrite_ore": paint.ore(stone, (225, 215, 195), 10),
        "deepslate_necrite_ore": paint.ore(deep, (225, 215, 195), 11),
        "bloodstone_ore": paint.ore(stone, (190, 20, 30), 12),
        "deepslate_bloodstone_ore": paint.ore(deep, (190, 20, 30), 13),
        "voidsteel_ore": paint.ore(voidbase, (140, 110, 255), 14),
        "stardust_ore": paint.ore(stone, (255, 225, 110), 15),
        "necrite_block": paint.storage((210, 200, 180), 20),
        "bloodstone_block": paint.storage((150, 15, 22), 21),
        "voidsteel_block": paint.storage((52, 48, 70), 22),
        "stardust_block": paint.storage((230, 200, 90), 23),
        "gravestone": paint.noise_tile((105, 105, 110), 30, 10),
        "blood_puddle": paint.noise_tile((110, 6, 8), 31, 14),
        "dread_lantern": paint.noise_tile((30, 30, 34), 32, 6),
    }
    for name, img in tex.items():
        out(f"textures/block/{name}.png", img)
    out("textures/block/charred_log.png", paint.log_side(40))
    out("textures/block/charred_log_top.png", paint.log_top(41))
    lantern_glow = paint.glow_orb((220, 255, 255), (90, 60, 200), 16)
    out("textures/block/dread_lantern_core.png", lantern_glow)
    out("textures/block/rift_portal.png", paint.portal_frames())
    out("textures/block/rift_portal.png.mcmeta", {"animation": {"frametime": 2, "interpolate": True}})

    simple = ["necrite_ore", "deepslate_necrite_ore", "bloodstone_ore", "deepslate_bloodstone_ore", "voidsteel_ore",
              "stardust_ore", "necrite_block", "bloodstone_block", "voidsteel_block", "stardust_block"]
    for name in simple:
        out(f"models/block/{name}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"deadcraft:block/{name}"}})
        out(f"blockstates/{name}.json", {"variants": {"": {"model": f"deadcraft:block/{name}"}}})
        out(f"models/item/{name}.json", {"parent": f"deadcraft:block/{name}"})
    # Charred log (axis)
    out("models/block/charred_log.json", {"parent": "minecraft:block/cube_column",
                                          "textures": {"side": "deadcraft:block/charred_log", "end": "deadcraft:block/charred_log_top"}})
    out("models/block/charred_log_horizontal.json", {"parent": "minecraft:block/cube_column_horizontal",
                                                     "textures": {"side": "deadcraft:block/charred_log", "end": "deadcraft:block/charred_log_top"}})
    out("blockstates/charred_log.json", {"variants": {
        "axis=y": {"model": "deadcraft:block/charred_log"},
        "axis=z": {"model": "deadcraft:block/charred_log_horizontal", "x": 90},
        "axis=x": {"model": "deadcraft:block/charred_log_horizontal", "x": 90, "y": 90}}})
    out("models/item/charred_log.json", {"parent": "deadcraft:block/charred_log"})
    # Gravestone
    g = "#stone"

    def face_all(uv_w=16):
        return {f: {"texture": g} for f in ("north", "south", "east", "west", "up", "down")}

    out("models/block/gravestone.json", {
        "parent": "minecraft:block/block", "textures": {"stone": "deadcraft:block/gravestone", "particle": "deadcraft:block/gravestone"},
        "elements": [
            {"from": [1, 0, 5], "to": [15, 2, 11], "faces": face_all()},
            {"from": [2, 2, 6], "to": [14, 12, 10], "faces": face_all()},
            {"from": [3, 12, 6], "to": [13, 14, 10], "faces": face_all()},
            {"from": [5, 14, 6], "to": [11, 15, 10], "faces": face_all()},
            {"from": [7.5, 5, 5.5], "to": [8.5, 11, 6], "faces": face_all()},
            {"from": [6, 8.5, 5.5], "to": [10, 9.5, 6], "faces": face_all()},
        ]})
    out("blockstates/gravestone.json", {"variants": {
        "facing=north": {"model": "deadcraft:block/gravestone"},
        "facing=east": {"model": "deadcraft:block/gravestone", "y": 90},
        "facing=south": {"model": "deadcraft:block/gravestone", "y": 180},
        "facing=west": {"model": "deadcraft:block/gravestone", "y": 270}}})
    out("models/item/gravestone.json", {"parent": "deadcraft:block/gravestone"})
    # Blood puddle
    out("models/block/blood_puddle.json", {"parent": "minecraft:block/carpet", "textures": {"wool": "deadcraft:block/blood_puddle"}})
    out("blockstates/blood_puddle.json", {"variants": {"": {"model": "deadcraft:block/blood_puddle"}}})
    out("models/item/blood_puddle.json", {"parent": "deadcraft:block/blood_puddle"})
    # Dread lantern
    def faces(t):
        return {f: {"texture": t} for f in ("north", "south", "east", "west", "up", "down")}
    out("models/block/dread_lantern.json", {
        "parent": "minecraft:block/block", "render_type": "minecraft:cutout",
        "textures": {"frame": "deadcraft:block/dread_lantern", "core": "deadcraft:block/dread_lantern_core",
                     "particle": "deadcraft:block/dread_lantern"},
        "elements": [
            {"from": [4, 0, 4], "to": [12, 1, 12], "faces": faces("#frame")},
            {"from": [4, 9, 4], "to": [12, 10, 12], "faces": faces("#frame")},
            {"from": [6, 10, 6], "to": [10, 12, 10], "faces": faces("#frame")},
            {"from": [4, 1, 4], "to": [5, 9, 5], "faces": faces("#frame")},
            {"from": [11, 1, 4], "to": [12, 9, 5], "faces": faces("#frame")},
            {"from": [4, 1, 11], "to": [5, 9, 12], "faces": faces("#frame")},
            {"from": [11, 1, 11], "to": [12, 9, 12], "faces": faces("#frame")},
            {"from": [5.5, 1.5, 5.5], "to": [10.5, 8.5, 10.5], "shade": False, "faces": faces("#core")},
        ]})
    out("blockstates/dread_lantern.json", {"variants": {"": {"model": "deadcraft:block/dread_lantern"}}})
    out("models/item/dread_lantern.json", {"parent": "deadcraft:block/dread_lantern"})
    # Rift portal
    out("models/block/rift_portal.json", {"parent": "minecraft:block/cube_all", "render_type": "minecraft:translucent",
                                          "textures": {"all": "deadcraft:block/rift_portal"}})
    out("blockstates/rift_portal.json", {"variants": {f"destination={i}": {"model": "deadcraft:block/rift_portal"} for i in range(5)}})


# =============================================================================================
# Particles, effects, GUI
# =============================================================================================
def particles():
    for kind in ("blood", "ember", "soul", "spark", "star", "rune", "ash", "frost"):
        frames = paint.particle_frames(kind)
        names = []
        for i, img in enumerate(frames):
            out(f"textures/particle/{kind}_{i}.png", img)
            names.append(f"deadcraft:{kind}_{i}")
        out(f"particles/{kind}.json", {"textures": names})
    names = []
    for i, img in enumerate(paint.slash_frames()):
        out(f"textures/particle/slash_{i}.png", img)
        names.append(f"deadcraft:slash_{i}")
    out("particles/slash.json", {"textures": names})
    out("textures/particle/muzzle_flash.png", paint.muzzle_flash())
    out("particles/muzzle_flash.json", {"textures": ["deadcraft:muzzle_flash"]})
    out("textures/particle/shockwave.png", paint.shockwave())
    out("particles/shockwave.json", {"textures": ["deadcraft:shockwave"]})
    assert set(PARTICLES) == {"blood", "ember", "soul", "spark", "star", "rune", "slash", "ash", "frost", "muzzle_flash", "shockwave"}


def effects():
    out("textures/mob_effect/bleeding.png", paint.effect_icon((180, 10, 20), "drop"))
    out("textures/mob_effect/infested.png", paint.effect_icon((107, 125, 42), "bug"))
    out("textures/mob_effect/dread.png", paint.effect_icon((60, 60, 90), "eye"))


def gui():
    out("textures/gui/title/deadcraft_logo.png", paint.logo(FONT))
    for state in ("normal", "hover", "disabled"):
        out(f"textures/gui/title/button_{state}.png", paint.button(state))
    out("textures/gui/title/blood.png", paint.blood_overlay())
    out("textures/gui/title/skeletons.png", paint.skeleton_strip())
    out("textures/gui/static.png", paint.static_noise())
    out("textures/gui/vignette.png", paint.vignette())
    out("textures/gui/hud_icons.png", paint.hud_icons())
    # Re-skin every vanilla button in the game (pause menu, options, world select, ...)
    mc = os.path.join(RES, "assets/minecraft/textures/gui/sprites/widget")
    os.makedirs(mc, exist_ok=True)
    meta = {"gui": {"scaling": {"type": "nine_slice", "width": 200, "height": 20, "border": 3}}}
    for state, name in (("normal", "button"), ("hover", "button_highlighted"), ("disabled", "button_disabled")):
        paint.button(state, seed=4).save(os.path.join(mc, f"{name}.png"))
        with open(os.path.join(mc, f"{name}.png.mcmeta"), "w") as f:
            json.dump(meta, f, indent=2)
    # Mod list icon + logo
    icon = paint.mod_icon(FONT)
    icon.save(os.path.join(RES, "deadcraft_logo.png"))
    icon.save(os.path.join(RES, "pack.png"))
    icon.save(os.path.join(ROOT, "icon.png"))
    # Gothic UI font
    os.makedirs(os.path.join(ASSETS, "font"), exist_ok=True)
    shutil.copy(FONT, os.path.join(ASSETS, "font/pirata_one.ttf"))
    shutil.copy(os.path.join(REPO, "assets/fonts/OFL.txt"), os.path.join(ASSETS, "font/pirata_one_license.txt"))
    out("font/gothic.json", {"providers": [{"type": "ttf", "file": "deadcraft:pirata_one.ttf", "shift": [0, 1],
                                            "size": 10.0, "oversample": 6.0}]})


# =============================================================================================
# sounds.json and language
# =============================================================================================
VARIANTS = {"player.step_gear": 4, "player.step_run": 4, "player.breath": 3, "combat.swing": 3, "combat.hit": 3,
            "mob.whisper": 4, "mob.flesh_hurt": 3, "mob.chitter": 3, "mob.wail": 3, "mob.growl": 3,
            "critter.ambient": 3, "critter.chirp": 3, "gun.impact": 3, "mob.stone_step": 2, "critter.beast_call": 2}
STREAM = {"music.menu", "music.dimension", "ui.static", "world.ambient_drone", "world.ambient_wind"}


def sounds_json():
    data = {}
    for s in SOUNDS:
        n = VARIANTS.get(s, 1)
        files = [s.replace(".", "/") + (f"_{i}" if n > 1 else "") for i in range(n)]
        entries = []
        for f in files:
            e = {"name": f"deadcraft:{f}"}
            if s in STREAM:
                e["stream"] = True
            entries.append(e if len(e) > 1 else f"deadcraft:{f}")
        data[s] = {"sounds": entries, "subtitle": f"subtitles.deadcraft.{s}"}
    out("sounds.json", data)


SUBTITLES = {
    "player.step_gear": "Gear rustles", "player.step_run": "Footsteps pound", "player.breath": "Heavy breathing",
    "player.heartbeat": "Heart pounds", "player.land": "Heavy landing", "player.dodge": "Player dodges",
    "ui.hover": "Whisper", "ui.click": "Bone snaps", "ui.static": "Static hisses", "music.menu": "Dread",
    "music.dimension": "Otherworldly music", "combat.swing": "Blade swings", "combat.hit": "Flesh tears",
    "combat.crit": "Critical strike", "combat.finisher": "Finishing blow", "combat.evade": "Attack evaded",
    "gun.revolver": "Revolver fires", "gun.shotgun": "Shotgun blasts", "gun.rifle": "Rifle cracks",
    "gun.smg": "SMG rattles", "gun.reload": "Gun reloads", "gun.empty": "Dry fire click", "gun.impact": "Bullet ricochets",
    "magic.cast": "Spell cast", "magic.soul_bolt": "Soul bolt shrieks", "magic.ember": "Ember roars",
    "magic.frost_nova": "Frost shatters", "magic.blink": "Space tears", "magic.storm": "Storm strikes",
    "magic.heal": "Blood pact", "magic.awaken": "Something awakens", "mob.whisper": "Something whispers",
    "mob.flesh_hurt": "Creature hurts", "mob.flesh_death": "Creature dies", "mob.chitter": "Something chitters",
    "mob.wail": "Ghostly wail", "mob.growl": "Deep growl", "mob.brute_roar": "Brute roars", "mob.stone_step": "Heavy footsteps",
    "mob.stinger": "You are being watched", "mob.shout": "Bandit shouts", "critter.ambient": "Critter squeaks",
    "critter.hurt": "Critter hurts", "critter.death": "Critter dies", "critter.chirp": "Bird chirps",
    "critter.beast_call": "Beast calls", "boss.roar": "Boss roars", "boss.phase": "Boss grows enraged",
    "boss.death": "Boss falls", "boss.slam": "Ground shakes", "world.portal_hum": "Rift hums",
    "world.portal_travel": "Rift opens", "world.ambient_wind": "Wind howls", "world.ambient_drone": "The dark hums",
    "world.thunder_far": "Distant thunder", "vehicle.engine": "Engine growls", "vehicle.hover": "Thrusters hum",
}

ITEM_NAMES = {"rusted_revolver": "Rusted Revolver", "doomsday_shotgun": "Doomsday Shotgun", "scrap_smg": "Scrap SMG",
              "ruin_runner": "Ruin Runner", "sky_skiff": "Sky Skiff", "dim_heart": "Heart of Dim Light",
              "rift_key_hells_paradise": "Rift Key: Hell's Paradise", "rift_key_skyfall_torn": "Rift Key: Skyfall Torn",
              "rift_key_lights_descent": "Rift Key: Light's Descent", "rift_key_disposed_skies": "Rift Key: Disposed Skies",
              "sigil_fracture": "Sigil of Fracture", "sigil_chronos": "Sigil of Chronos", "sigil_skeleton": "Sigil of the Skeleton",
              "sigil_dim_light_demon": "Sigil of the Dim Light Demon", "sigil_petrified": "Sigil of the Petrified"}
ENTITY_NAMES = {"the_hollow": "The Hollow", "the_forgotten": "The Forgotten", "ghost": "Ghost", "star_fall": "Star Fall",
                "dim_light_demon": "Dim Light Demon", "skeleton": "The Skeleton", "projectile": "Projectile"}


def lang():
    L = {}
    for item_id, _ in ITEMS:
        L[f"item.deadcraft.{item_id}"] = ITEM_NAMES.get(item_id, title(item_id))
    for b in BLOCKS:
        L[f"block.deadcraft.{b}"] = title(b)
    for mob in MOBS:
        name = ENTITY_NAMES.get(mob["id"], title(mob["id"]))
        L[f"entity.deadcraft.{mob['id']}"] = name
        L[f"item.deadcraft.{mob['id']}_spawn_egg"] = f"{name} Spawn Egg"
    L.update({
        "entity.deadcraft.projectile": "Projectile", "entity.deadcraft.ruin_runner": "Ruin Runner",
        "entity.deadcraft.sky_skiff": "Sky Skiff",
        "itemGroup.deadcraft.world": "DeadCraft: World", "itemGroup.deadcraft.arsenal": "DeadCraft: Arsenal",
        "itemGroup.deadcraft.creatures": "DeadCraft: Creatures",
        "effect.deadcraft.bleeding": "Bleeding", "effect.deadcraft.infested": "Infested", "effect.deadcraft.dread": "Dread",
        "enchantment.deadcraft.bloodthirst": "Bloodthirst", "enchantment.deadcraft.soul_reaper": "Soul Reaper",
        "enchantment.deadcraft.hollow_point": "Hollow Point", "enchantment.deadcraft.quick_reload": "Quick Reload",
        "enchantment.deadcraft.phantom_step": "Phantom Step", "enchantment.deadcraft.static_ward": "Static Ward",
        "enchantment.deadcraft.exorcism": "Exorcism",
        "key.categories.deadcraft": "DeadCraft", "key.deadcraft.dodge": "Dodge", "key.deadcraft.reload": "Reload",
        "biome.deadcraft.scorched_forest": "Scorched Forest", "biome.deadcraft.dark_plains": "Dark Plains",
        "biome.deadcraft.deserted_cliffs": "Deserted Cliffs", "biome.deadcraft.storm_field": "Storm Field",
        "biome.deadcraft.floating_islands": "Floating Islands",
        "dimension.deadcraft.hells_paradise": "Hell's Paradise", "dimension.deadcraft.skyfall_torn": "Skyfall Torn",
        "dimension.deadcraft.lights_descent": "Light's Descent", "dimension.deadcraft.disposed_skies": "Disposed Skies",
        "menu.deadcraft.singleplayer": "Descend Alone", "menu.deadcraft.multiplayer": "Descend Together",
        "menu.deadcraft.mods": "Rituals", "menu.deadcraft.options": "Options", "menu.deadcraft.quit": "Flee",
        "menu.deadcraft.tagline": "There is no morning here.",
        "message.deadcraft.reload_hint": "Empty. Press R to reload.", "message.deadcraft.no_ammo": "No ammunition",
        "message.deadcraft.not_awakened": "The words mean nothing to you. Read a Grimoire of Awakening.",
        "message.deadcraft.no_mana": "Not enough mana", "message.deadcraft.already_awakened": "You are already awake.",
        "message.deadcraft.awakened": "Your eyes open. Magic flows through you.",
        "message.deadcraft.peaceful": "Nothing answers in Peaceful difficulty.",
        "tooltip.deadcraft.ammo": "Loaded: %s / %s", "tooltip.deadcraft.gun_damage": "Damage: %s",
        "tooltip.deadcraft.uses_ammo": "Uses %s", "tooltip.deadcraft.reload_key": "Right-click to fire, R to reload",
        "tooltip.deadcraft.mana_cost": "Mana: %s", "tooltip.deadcraft.combo": "Chain hits for combo damage; the 5th is a finisher",
        "tooltip.deadcraft.grimoire": "Read to awaken magic. Permanent.",
        "tooltip.deadcraft.mana_tonic": "Restores 50 mana",
        "tooltip.deadcraft.rift_key": "Tears a rift between worlds. Use again to return.",
        "tooltip.deadcraft.sigil": "Use on the ground to summon %s",
        "tooltip.deadcraft.vehicle_pickup": "Sneak-punch to pick it back up",
        "item.deadcraft.ruin_runner.desc": "Scrap buggy. W/S throttle, A/D steer. Loud.",
        "item.deadcraft.sky_skiff.desc": "Hover craft. Fly where you look.",
        "tooltip.deadcraft.trait.cleave": "Cleave: finishers hit everything around the target",
        "tooltip.deadcraft.trait.lifesteal": "Lifesteal: every hit feeds you",
        "tooltip.deadcraft.trait.void": "Void: pierces armour",
        "tooltip.deadcraft.trait.starfall": "Starfall: finishers call down a falling star",
        "tooltip.deadcraft.trait.reap": "Reap: kills restore mana",
        "tooltip.deadcraft.trait.time": "Time: hits slow the target",
        "tooltip.deadcraft.trait.shatter": "Shatter: finishers send out a shockwave",
        "spell.deadcraft.soul_bolt": "Soul Bolt", "spell.deadcraft.soul_bolt.desc": "A homing bolt of trapped souls",
        "spell.deadcraft.ember": "Ember Blast", "spell.deadcraft.ember.desc": "An exploding ball of hellfire",
        "spell.deadcraft.frost_nova": "Frost Nova", "spell.deadcraft.frost_nova.desc": "Freezes everything around you",
        "spell.deadcraft.blink": "Blink", "spell.deadcraft.blink.desc": "Step through space to where you look",
        "spell.deadcraft.storm": "Storm Call", "spell.deadcraft.storm.desc": "Lightning strikes where you look",
        "spell.deadcraft.blood_pact": "Blood Pact", "spell.deadcraft.blood_pact.desc": "Heal and harden your skin",
    })
    for s in SOUNDS:
        L[f"subtitles.deadcraft.{s}"] = SUBTITLES.get(s, title(s.split(".")[-1]))
    out("lang/en_us.json", dict(sorted(L.items())))


def main():
    entities()
    armor()
    items()
    blocks()
    particles()
    effects()
    gui()
    sounds_json()
    lang()
    print("assets generated")


if __name__ == "__main__":
    main()
