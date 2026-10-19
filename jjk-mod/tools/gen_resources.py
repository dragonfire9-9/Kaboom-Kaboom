"""Generates sounds.json, item models, recipes, tags and advancements. Run from jjk-mod/."""
import json, os, re

R = "src/main/resources"
J = "src/main/java/com/kaboom/jjk"

def w(path, obj):
    p = os.path.join(R, path); os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w") as f: json.dump(obj, f, indent=2)

# ---------------------------------------------------------------- sounds.json from ModSounds.java
sounds = re.findall(r'register\("([a-z0-9_.]+)"\)', open(f"{J}/sound/ModSounds.java").read())
sounds += ["technique." + a for a in re.findall(r'(?:tech|universal|physical)\("([a-z_]+)"', open(f"{J}/ability/Abilities.java").read())]
sounds += ["curse." + c + "." + k for c in re.findall(r'^    [A-Z_]+\("([a-z_]+)", Grade', open(f"{J}/entity/CurseType.java").read(), re.M) for k in ("ambient", "hurt", "death")]
sounds += ["domain." + d for d in re.findall(r'^\s+[A-Z_]+\("([a-z_]+)", 0x', open(f"{J}/ability/DomainType.java").read(), re.M)]
STREAM = {"domain.expansion"} | {s for s in sounds if s.startswith("music.")}
w("assets/jjk/sounds.json", {s: {"category": "record" if s.startswith("music.") else "hostile" if s.startswith("curse.") else "player",
                                 "subtitle": f"subtitles.jjk.{s}",
                                 "sounds": [{"name": "jjk:" + s.replace(".", "/"), "stream": s in STREAM}]} for s in sounds})

# ---------------------------------------------------------------- item models
items_src = open(f"{J}/item/ModItems.java").read()
items = re.findall(r'(?:ITEMS\.register(?:SimpleItem)?|tool|armor)\("([a-z0-9_]+)"', items_src)
HANDHELD = {n for n in re.findall(r'tool\("([a-z0-9_]+)"', items_src)}
SPECIAL_MODELS = set()  # GeckoLib-rendered items write their own model
for n in items:
    if n in SPECIAL_MODELS: continue
    w(f"assets/jjk/models/item/{n}.json",
      {"parent": "minecraft:item/handheld" if n in HANDHELD else "minecraft:item/generated", "textures": {"layer0": f"jjk:item/{n}"}})

w("assets/jjk/particles/cursed_energy.json", {"textures": [f"jjk:cursed_energy_{i}" for i in range(4)]})
for name, frames in {"glow": 4, "spark": 4, "slash": 4, "ring": 1, "lightning": 3}.items():
    w(f"assets/jjk/particles/{name}.json", {"textures": [f"jjk:{name}_{i}" for i in range(frames)]})

# ---------------------------------------------------------------- blocks (no items: domain blocks are technical)
for b in re.findall(r'registerSimpleBlock\("([a-z0-9_]+)"', open(f"{J}/block/ModBlocks.java").read()):
    w(f"assets/jjk/blockstates/{b}.json", {"variants": {"": {"model": f"jjk:block/{b}"}}})
    w(f"assets/jjk/models/block/{b}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"jjk:block/{b}"}})

# ---------------------------------------------------------------- curses: eggs, loot, spawns, tags
curse_src = open(f"{J}/entity/CurseType.java").read()
curses = re.findall(r'^    [A-Z_]+\("([a-z_]+)", Grade\.(GRADE_\d|SEMI_GRADE_1|SPECIAL_GRADE), .*?, (\d+), 0x', curse_src, re.M)
shikigami = re.findall(r'^    [A-Z_]+\("([a-z_]+)", \d+', open(f"{J}/entity/ShikigamiType.java").read(), re.M)
for cid, grade, weight in curses:
    w(f"assets/jjk/models/item/{cid}_spawn_egg.json", {"parent": "minecraft:item/template_spawn_egg"})
    strong = grade in ("GRADE_1", "SEMI_GRADE_1", "SPECIAL_GRADE")
    pools = [
        {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "jjk:cursed_fragment",
            "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 4 if strong else 2}},
                          {"function": "minecraft:enchanted_count_increase", "enchantment": "minecraft:looting", "count": {"min": 0, "max": 1}}]}],
         "conditions": [{"condition": "minecraft:killed_by_player"}]},
        {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "jjk:ce_shard",
            "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 3 if strong else 1}}]}],
         "conditions": [{"condition": "minecraft:killed_by_player"},
                        {"condition": "minecraft:random_chance_with_enchanted_bonus", "enchantment": "minecraft:looting",
                         "unenchanted_chance": 0.8 if strong else 0.35, "enchanted_chance": {"type": "minecraft:linear", "base": 0.85 if strong else 0.45, "per_level_above_first": 0.05}}]},
    ]
    if strong:
        pools.append({"rolls": 1, "entries": [{"type": "minecraft:item", "name": "jjk:special_grade_object"}],
                      "conditions": [{"condition": "minecraft:killed_by_player"}, {"condition": "minecraft:random_chance", "chance": 0.12}]})
    w(f"data/jjk/loot_table/entities/{cid}.json", {"type": "minecraft:entity", "pools": pools})

w("data/jjk/tags/entity_type/curses.json", {"replace": False, "values": [f"jjk:{c[0]}" for c in curses]})
w("data/jjk/tags/entity_type/bosses.json", {"replace": False, "values": []})
# Natural spawns: weak curses everywhere in the overworld, strong ones much rarer (and gated in checkCurseSpawnRules).
w("data/jjk/neoforge/biome_modifier/curse_spawns.json", {"type": "neoforge:add_spawns", "biomes": "#minecraft:is_overworld",
    "spawners": [{"type": f"jjk:{cid}", "weight": max(1, int(weight) // 2), "minCount": 1, "maxCount": 3 if grade == "GRADE_4" else 1}
                 for cid, grade, weight in curses]})
w("data/jjk/tags/worldgen/structure/negative_emotion_hotspots.json", {"replace": False, "values": []})

# ---------------------------------------------------------------- damage types + tags
w("data/jjk/damage_type/cursed_technique.json", {"message_id": "jjk.cursed_technique", "scaling": "never", "exhaustion": 0.1})
w("data/jjk/damage_type/domain_sure_hit.json", {"message_id": "jjk.domain_sure_hit", "scaling": "never", "exhaustion": 0.0})
w("data/minecraft/tags/damage_type/bypasses_armor.json", {"replace": False, "values": ["jjk:domain_sure_hit"]})
w("data/minecraft/tags/damage_type/bypasses_shield.json", {"replace": False, "values": ["jjk:domain_sure_hit"]})

w("data/jjk/tags/item/weapons.json", {"replace": False, "values": ["#minecraft:swords", "#minecraft:axes", "minecraft:trident", "minecraft:mace"]
                                       + [f"jjk:{n}" for n in sorted(HANDHELD)]})
w("data/jjk/tags/item/cursed_tools.json", {"replace": False, "values": [f"jjk:{n}" for n in sorted(HANDHELD)]})
w("data/minecraft/tags/item/swords.json", {"replace": False, "values": [f"jjk:{n}" for n in sorted(HANDHELD)]})
w("data/minecraft/tags/item/head_armor.json", {"replace": False, "values": ["jjk:curse_glasses"]})

# ---------------------------------------------------------------- recipes
def shaped(name, pattern, key, result, count=1):
    w(f"data/jjk/recipe/{name}.json", {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern,
        "key": {k: ({"tag": v[1:]} if v.startswith("#") else {"item": v}) for k, v in key.items()},
        "result": {"id": result, "count": count}})

def shapeless(name, ingredients, result, count=1):
    w(f"data/jjk/recipe/{name}.json", {"type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [({"tag": v[1:]} if v.startswith("#") else {"item": v}) for v in ingredients],
        "result": {"id": result, "count": count}})

shaped("cursed_energy_scroll", [" A ", "PEP", " A "], {"A": "minecraft:amethyst_shard", "P": "minecraft:paper", "E": "minecraft:ender_pearl"}, "jjk:cursed_energy_scroll")
shaped("cursed_energy_scroll_from_shards", ["SSS", "PFP", "SSS"], {"S": "jjk:ce_shard", "P": "minecraft:paper", "F": "jjk:cursed_fragment"}, "jjk:cursed_energy_scroll")
shaped("clan_scroll", [" G ", "PIP", " G "], {"G": "minecraft:gold_ingot", "P": "minecraft:paper", "I": "minecraft:ink_sac"}, "jjk:clan_scroll")
shaped("heavenly_restriction_scroll", ["NDN", "PCP", "NDN"], {"N": "minecraft:netherite_scrap", "D": "minecraft:diamond", "P": "minecraft:paper", "C": "minecraft:chain"}, "jjk:heavenly_restriction_scroll")
shaped("talisman", ["P", "F", "R"], {"P": "minecraft:paper", "F": "jjk:cursed_fragment", "R": "minecraft:redstone"}, "jjk:talisman", 2)
shaped("ce_bottle", [" S ", "SBS", " S "], {"S": "jjk:ce_shard", "B": "minecraft:glass_bottle"}, "jjk:ce_bottle")
shaped("curse_glasses", ["F F", "GIG"], {"F": "jjk:cursed_fragment", "G": "minecraft:glass_pane", "I": "minecraft:iron_ingot"}, "jjk:curse_glasses")
shaped("student_id", ["PPP", "GIB"], {"P": "minecraft:paper", "G": "minecraft:gold_nugget", "I": "minecraft:ink_sac", "B": "minecraft:blue_dye"}, "jjk:student_id")
shaped("slaughter_demon", [" F", "I ", "S "], {"F": "jjk:cursed_fragment", "I": "minecraft:iron_ingot", "S": "minecraft:stick"}, "jjk:slaughter_demon")
shaped("black_rope", ["FSF", "SFS", "FSF"], {"F": "jjk:cursed_fragment", "S": "minecraft:string"}, "jjk:black_rope")
shaped("chain_of_a_thousand_miles", ["CFC", "CFC", "CFC"], {"C": "minecraft:chain", "F": "jjk:cursed_fragment"}, "jjk:chain_of_a_thousand_miles")
shaped("dragon_bone", [" FB", "FBF", "SF "], {"F": "jjk:cursed_fragment", "B": "minecraft:bone_block", "S": "minecraft:stick"}, "jjk:dragon_bone")
shaped("split_soul_katana", ["  O", " O ", "F  "], {"O": "jjk:special_grade_object", "F": "jjk:cursed_fragment"}, "jjk:split_soul_katana")
shaped("playful_cloud", ["OCO", "RCR", "OCO"], {"O": "jjk:special_grade_object", "C": "minecraft:chain", "R": "minecraft:red_dye"}, "jjk:playful_cloud")
shaped("prison_realm", ["OOO", "OEO", "OOO"], {"O": "jjk:special_grade_object", "E": "minecraft:ender_eye"}, "jjk:prison_realm")
shaped("special_grade_object", ["FFF", "FDF", "FFF"], {"F": "jjk:cursed_fragment", "D": "minecraft:diamond"}, "jjk:special_grade_object")
shaped("jujutsu_uniform_jacket", ["W W", "WFW", "WWW"], {"W": "minecraft:blue_wool", "F": "jjk:cursed_fragment"}, "jjk:jujutsu_uniform_jacket")
shaped("jujutsu_uniform_pants", ["WWW", "WFW", "W W"], {"W": "minecraft:blue_wool", "F": "jjk:cursed_fragment"}, "jjk:jujutsu_uniform_pants")
shaped("jujutsu_uniform_shoes", ["L L", "LFL"], {"L": "minecraft:leather", "F": "jjk:cursed_fragment"}, "jjk:jujutsu_uniform_shoes")
# The Inverted Spear of Heaven, fingers and the named outfits are loot or trades only.

# ---------------------------------------------------------------- advancements
def adv(name, parent, icon, frame="task", criteria=None, hidden=False):
    obj = {"display": {"icon": {"id": icon}, "title": {"translate": f"advancements.jjk.{name}.title"},
                       "description": {"translate": f"advancements.jjk.{name}.description"}, "frame": frame, "hidden": hidden},
           "criteria": criteria or {name: {"trigger": "minecraft:impossible"}}}
    if parent: obj["parent"] = f"jjk:{parent}"
    else:
        obj["display"]["background"] = "minecraft:textures/block/black_concrete.png"
        obj["display"]["show_toast"] = False
        obj["display"]["announce_to_chat"] = False
    w(f"data/jjk/advancement/{name}.json", obj)

adv("root", None, "jjk:jjk_emblem", criteria={"has_scroll": {"trigger": "minecraft:inventory_changed",
    "conditions": {"items": [{"items": ["jjk:cursed_energy_scroll", "jjk:clan_scroll", "jjk:heavenly_restriction_scroll"]}]}}})
adv("special_grade", "root", "jjk:cursed_energy_scroll", "challenge")
adv("first_finger", "root", "jjk:sukuna_finger")
adv("vessel", "first_finger", "jjk:sukuna_finger", "goal")
adv("ten_fingers", "first_finger", "jjk:sukuna_finger", "goal")
adv("all_fingers", "ten_fingers", "jjk:sukuna_finger", "challenge")
adv("first_domain", "root", "jjk:jjk_emblem", "goal")
adv("domain_clash", "first_domain", "jjk:jjk_emblem", "challenge")
adv("black_flash", "root", "jjk:cursed_fragment", "goal")
adv("cursed_tool", "root", "jjk:playful_cloud", criteria={"tool": {"trigger": "minecraft:inventory_changed",
    "conditions": {"items": [{"items": "#jjk:cursed_tools"}]}}})
print("resources written:", len(sounds), "sounds,", len(items), "items")
