#!/usr/bin/env python3
"""Cross-check generated resources: every referenced item, block, entity, feature, texture and
translation must exist. Run after gen_assets.py / gen_data.py. Needs the vanilla resources jar
that ModDevGradle downloads (run ./gradlew compileJava once first)."""
import glob
import json
import os
import re
import sys
import zipfile

import content

ROOT = content.ROOT
RES = os.path.join(ROOT, "src/main/resources")
JAR = glob.glob(os.path.join(ROOT, "build/moddev/artifacts/*client-extra*.jar"))

errors = []


def err(msg):
    errors.append(msg)


vanilla = {"item": set(), "block": set(), "placed_feature": set(), "entity": set(), "texture": set(), "tag_item": set(),
           "tag_biome": set()}
if JAR:
    with zipfile.ZipFile(JAR[0]) as z:
        for n in z.namelist():
            m = re.match(r"assets/minecraft/models/item/([a-z0-9_]+)\.json", n)
            if m:
                vanilla["item"].add("minecraft:" + m.group(1))
            m = re.match(r"assets/minecraft/blockstates/([a-z0-9_]+)\.json", n)
            if m:
                vanilla["block"].add("minecraft:" + m.group(1))
            m = re.match(r"data/minecraft/worldgen/placed_feature/([a-z0-9_]+)\.json", n)
            if m:
                vanilla["placed_feature"].add("minecraft:" + m.group(1))
            m = re.match(r"assets/minecraft/textures/(.+)\.png", n)
            if m:
                vanilla["texture"].add("minecraft:" + m.group(1))
            m = re.match(r"data/minecraft/tags/item/(.+)\.json", n)
            if m:
                vanilla["tag_item"].add("minecraft:" + m.group(1))
            m = re.match(r"data/minecraft/tags/worldgen/biome/(.+)\.json", n)
            if m:
                vanilla["tag_biome"].add("minecraft:" + m.group(1))
else:
    print("warning: vanilla resources jar not found; skipping vanilla checks")

mobs = [m["id"] for m in content.mobs()]
items = {f"deadcraft:{i}" for i, _ in content.items()} | {f"deadcraft:{m}_spawn_egg" for m in mobs}
blocks = {f"deadcraft:{b}" for b in content.blocks()}
items |= {b for b in blocks if b != "deadcraft:rift_portal"}
entities = {f"deadcraft:{m}" for m in mobs} | {"deadcraft:projectile", "deadcraft:ruin_runner", "deadcraft:sky_skiff",
                                               "minecraft:skeleton"}
our_features = {"deadcraft:" + os.path.basename(p)[:-5] for p in glob.glob(os.path.join(RES, "data/deadcraft/worldgen/placed_feature/*.json"))}
our_biomes = {"deadcraft:" + os.path.basename(p)[:-5] for p in glob.glob(os.path.join(RES, "data/deadcraft/worldgen/biome/*.json"))}
our_item_tags = {"deadcraft:" + p.split("tags/item/")[1][:-5] for p in glob.glob(os.path.join(RES, "data/deadcraft/tags/item/**/*.json"), recursive=True)}


def item_ok(i):
    return i in items or not JAR or i in vanilla["item"]


def walk(o, fn):
    if isinstance(o, dict):
        for k, v in o.items():
            fn(k, v, o)
            walk(v, fn)
    elif isinstance(o, list):
        for v in o:
            walk(v, fn)


# Loot tables and recipes reference items
for path in glob.glob(os.path.join(RES, "data/deadcraft/loot_table/**/*.json"), recursive=True) + \
        glob.glob(os.path.join(RES, "data/deadcraft/recipe/*.json")):
    data = json.load(open(path))

    def check(k, v, parent):
        if k in ("name", "id") and isinstance(v, str) and ":" in v and parent.get("type", "minecraft:item") in (
                "minecraft:item", "item") or (k == "item" and isinstance(v, str)):
            if not item_ok(v):
                err(f"{os.path.relpath(path, RES)}: unknown item {v}")
        if k == "tag" and isinstance(v, str) and ":" in v:
            if v not in our_item_tags and JAR and v not in vanilla["tag_item"]:
                err(f"{os.path.relpath(path, RES)}: unknown item tag {v}")
    walk(data, check)

# Biomes: features and spawns
for path in glob.glob(os.path.join(RES, "data/deadcraft/worldgen/biome/*.json")):
    b = json.load(open(path))
    for step in b["features"]:
        for f in step:
            if f not in our_features and JAR and f not in vanilla["placed_feature"]:
                err(f"{os.path.basename(path)}: unknown placed feature {f}")
    for cat in b["spawners"].values():
        for s in cat:
            if s["type"] not in entities:
                err(f"{os.path.basename(path)}: unknown entity {s['type']}")
for path in glob.glob(os.path.join(RES, "data/deadcraft/neoforge/biome_modifier/*.json")):
    m = json.load(open(path))
    for s in m.get("spawners", []):
        if s["type"] not in entities:
            err(f"{os.path.basename(path)}: unknown entity {s['type']}")
    for f in m.get("features", []):
        if f not in our_features:
            err(f"{os.path.basename(path)}: unknown feature {f}")
for path in glob.glob(os.path.join(RES, "data/deadcraft/worldgen/structure/*.json")):
    s = json.load(open(path))
    if "boss" in s and s["boss"] not in entities:
        err(f"{os.path.basename(path)}: unknown boss {s['boss']}")
    for ov in s["spawn_overrides"].values():
        for sp in ov["spawns"]:
            if sp["type"] not in entities:
                err(f"{os.path.basename(path)}: unknown entity {sp['type']}")
for path in glob.glob(os.path.join(RES, "data/deadcraft/tags/worldgen/biome/**/*.json"), recursive=True):
    for v in json.load(open(path))["values"]:
        if v.startswith("#"):
            if JAR and v[1:] not in vanilla["tag_biome"]:
                err(f"{os.path.basename(path)}: unknown biome tag {v}")
        elif v.startswith("deadcraft:") and v not in our_biomes:
            err(f"{os.path.basename(path)}: unknown biome {v}")
for path in glob.glob(os.path.join(RES, "data/deadcraft/dimension/*.json")):
    def chk(k, v, parent):
        if k == "biome" and isinstance(v, str) and v not in our_biomes:
            err(f"{os.path.basename(path)}: unknown biome {v}")
    walk(json.load(open(path)), chk)

# Models reference textures
for path in glob.glob(os.path.join(RES, "assets/deadcraft/models/**/*.json"), recursive=True):
    m = json.load(open(path))
    for t in m.get("textures", {}).values():
        if t.startswith("#"):
            continue
        ns, p = t.split(":") if ":" in t else ("minecraft", t)
        if ns == "deadcraft":
            if not os.path.exists(os.path.join(RES, f"assets/deadcraft/textures/{p}.png")):
                err(f"{os.path.relpath(path, RES)}: missing texture {t}")
        elif JAR and f"{ns}:{p}" not in vanilla["texture"]:
            err(f"{os.path.relpath(path, RES)}: unknown vanilla texture {t}")

# Every item has a model, every block a blockstate, everything a translation
lang = json.load(open(os.path.join(RES, "assets/deadcraft/lang/en_us.json")))
for i in items:
    name = i.split(":")[1]
    if not os.path.exists(os.path.join(RES, f"assets/deadcraft/models/item/{name}.json")):
        err(f"item {i} has no model")
    if f"item.deadcraft.{name}" not in lang and f"block.deadcraft.{name}" not in lang:
        err(f"item {i} has no translation")
for b in blocks:
    name = b.split(":")[1]
    if not os.path.exists(os.path.join(RES, f"assets/deadcraft/blockstates/{name}.json")):
        err(f"block {b} has no blockstate")
for e in entities:
    if e.startswith("deadcraft:") and f"entity.deadcraft.{e.split(':')[1]}" not in lang:
        err(f"entity {e} has no translation")
# Translation keys used in Java
java_keys = set()
for path in glob.glob(os.path.join(content.JAVA, "**/*.java"), recursive=True):
    java_keys |= set(re.findall(r'"((?:message|tooltip|menu|key|itemGroup|spell)\.deadcraft\.[a-z_.]+)"', open(path).read()))
for k in java_keys:
    if k.endswith(".") or k in lang:
        continue
    if not any(x.startswith(k) for x in lang):
        err(f"translation key {k} used in code but missing")

if errors:
    print("\n".join(errors))
    print(f"{len(errors)} problem(s)")
    sys.exit(1)
print(f"validation passed: {len(items)} items, {len(blocks)} blocks, {len(entities)} entities, "
      f"{len(our_features)} placed features, {len(lang)} translations")
