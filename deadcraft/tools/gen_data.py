#!/usr/bin/env python3
"""Generate DeadCraft server data: worldgen, dimensions, structures, loot, recipes, tags, enchantments."""
from __future__ import annotations

import json
import os
import shutil

import content

ROOT = content.ROOT
DATA = os.path.join(ROOT, "src/main/resources/data")
MOBS = {m["id"]: m for m in content.mobs()}


def out(path, obj):
    path = os.path.join(DATA, path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def dc(x):
    return x if ":" in x or x.startswith("#") else f"deadcraft:{x}"


def mc(x):
    return x if ":" in x or x.startswith("#") else f"minecraft:{x}"


# =============================================================================================
# Loot tables
# =============================================================================================
def item_entry(name, lo=1, hi=1, looting=True, smelt=None, weight=1, chance=None):
    funcs = []
    if (lo, hi) != (1, 1):
        funcs.append({"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}, "add": False})
    if smelt:
        funcs.append({"function": "minecraft:furnace_smelt", "conditions": [
            {"condition": "minecraft:entity_properties", "entity": "this", "predicate": {"flags": {"is_on_fire": True}}}]})
    if looting:
        funcs.append({"function": "minecraft:enchanted_count_increase", "enchantment": "minecraft:looting",
                      "count": {"type": "minecraft:uniform", "min": 0, "max": 1}})
    e = {"type": "minecraft:item", "name": name, "weight": weight}
    if funcs:
        e["functions"] = funcs
    return e


def pool(entries, rolls=1, conditions=None):
    p = {"rolls": rolls, "bonus_rolls": 0, "entries": entries}
    if conditions:
        p["conditions"] = conditions
    return p


KILLED_BY_PLAYER = [{"condition": "minecraft:killed_by_player"}]


def chance(p):
    return [{"condition": "minecraft:random_chance_with_enchanted_bonus", "unenchanted_chance": p,
             "enchanted_chance": {"type": "minecraft:linear", "base": p + 0.02, "per_level_above_first": 0.02},
             "enchantment": "minecraft:looting"}]


MOB_DROPS = {
    "the_hollow": [("deadcraft:hollow_essence", 1, 2), ("minecraft:bone", 0, 2)],
    "severed": [("minecraft:rotten_flesh", 1, 3), ("minecraft:bone", 0, 1)],
    "cracked": [("deadcraft:raw_necrite", 0, 2), ("minecraft:cobblestone", 1, 3)],
    "depart": [("deadcraft:ectoplasm", 1, 2)],
    "scorched": [("minecraft:coal", 1, 2), ("minecraft:blaze_powder", 0, 1)],
    "parasite": [("minecraft:spider_eye", 0, 1), ("minecraft:slime_ball", 0, 1)],
    "illusion": [("deadcraft:ectoplasm", 1, 2), ("minecraft:amethyst_shard", 0, 1)],
    "bandit": [("deadcraft:scrap_metal", 1, 2), ("deadcraft:pistol_rounds", 2, 6)],
    "bandit_captain": [("deadcraft:scrap_metal", 2, 4), ("deadcraft:shotgun_shells", 2, 6), ("minecraft:emerald", 1, 3)],
    "the_forgotten": [("deadcraft:hollow_essence", 0, 1), ("minecraft:bone", 1, 3)],
    "wretched": [("minecraft:rotten_flesh", 1, 3), ("minecraft:slime_ball", 0, 2)],
    "ghost": [("deadcraft:ectoplasm", 1, 3)],
    "blood_demon": [("deadcraft:demon_horn", 1, 1), ("deadcraft:bloodstone", 0, 1)],
    "star_fall": [("minecraft:glowstone_dust", 1, 3)],
    "collapse": [("deadcraft:raw_necrite", 1, 3), ("minecraft:gravel", 1, 2)],
    "rotwalker": [("minecraft:rotten_flesh", 0, 2), ("deadcraft:scrap_metal", 0, 1)],
    "butcher": [("minecraft:beef", 1, 4), ("minecraft:porkchop", 1, 3), ("deadcraft:scrap_metal", 0, 2)],
    "gravewyrm": [("minecraft:bone", 1, 3), ("minecraft:slime_ball", 0, 2)],
    "hanged_man": [("minecraft:lead", 0, 1), ("deadcraft:ectoplasm", 1, 2)],
    "mire_hag": [("minecraft:spider_eye", 0, 2), ("minecraft:fermented_spider_eye", 0, 1), ("minecraft:slime_ball", 0, 1)],
    "lamplighter": [("minecraft:lantern", 0, 1), ("minecraft:coal", 1, 3), ("minecraft:blaze_powder", 0, 1)],
}
RARE_DROPS = {
    "star_fall": ("deadcraft:star_shard", 0.25), "the_hollow": ("deadcraft:grimoire_of_awakening", 0.02),
    "bandit": ("deadcraft:rusted_revolver", 0.04), "bandit_captain": ("deadcraft:doomsday_shotgun", 0.12),
    "rotwalker": ("deadcraft:pistol_rounds", 0.2), "illusion": ("deadcraft:mana_tonic", 0.1),
    "butcher": ("deadcraft:butcher_knife", 0.08), "mire_hag": ("deadcraft:frost_tome", 0.03),
    "lamplighter": ("deadcraft:dread_lantern", 0.15), "hanged_man": ("deadcraft:ritual_dagger", 0.05),
}
BOSS_DROPS = {
    "fracture": ("deadcraft:fracture_core", "deadcraft:raw_voidsteel"),
    "chronos": ("deadcraft:chronos_hourglass", "deadcraft:star_shard"),
    "skeleton": ("deadcraft:skeleton_crown", "deadcraft:necrite_ingot"),
    "dim_light_demon": ("deadcraft:dim_heart", "deadcraft:bloodstone"),
    "petrified": ("deadcraft:petrified_eye", "deadcraft:raw_necrite"),
    "hive_mother": ("deadcraft:hive_heart", "deadcraft:bloodstone"),
    "wailing_bride": ("deadcraft:bridal_veil", "deadcraft:ectoplasm"),
}
FOOD_DROPS = {"ashgrazer": "ash_venison", "gloomhog": "gloom_pork", "cinderhen": "cinder_fowl",
              "marrow_ox": "marrow_beef", "duskhare": "dusk_hare"}


def loot():
    for mid, mob in MOBS.items():
        pools = []
        if mid in MOB_DROPS:
            for name, lo, hi in MOB_DROPS[mid]:
                pools.append(pool([item_entry(name, lo, hi)]))
        if mid in RARE_DROPS:
            name, p = RARE_DROPS[mid]
            pools.append(pool([item_entry(name, looting=False)], conditions=KILLED_BY_PLAYER + chance(p)))
        if mid in BOSS_DROPS:
            trophy, mat = BOSS_DROPS[mid]
            pools.append(pool([item_entry(trophy, looting=False)]))
            pools.append(pool([item_entry(mat, 4, 9)]))
            pools.append(pool([item_entry("deadcraft:grimoire_of_awakening", looting=False)]))
            pools.append(pool([item_entry("deadcraft:mana_tonic", 2, 4)]))
            sigil = {"fracture": "chronos", "chronos": "dim_light_demon", "skeleton": "petrified",
                     "petrified": "fracture", "dim_light_demon": "skeleton", "hive_mother": "dim_light_demon",
                     "wailing_bride": "chronos"}[mid]
            pools.append(pool([item_entry(f"deadcraft:sigil_{sigil}", looting=False)], conditions=chance(0.35)))
        if mid in FOOD_DROPS:
            food = FOOD_DROPS[mid]
            pools.append(pool([item_entry(f"deadcraft:raw_{food}", 1, 3, smelt=True)]))
        if mob["kind"] == "PEACEFUL" and mid not in FOOD_DROPS:
            extra = {"lantern_moth": "minecraft:glowstone_dust", "hush_owl": "minecraft:feather", "gravecat": "minecraft:string",
                     "wisplet": "deadcraft:ectoplasm", "shellmourn": "minecraft:slime_ball", "candlewick_toad": "minecraft:honeycomb",
                     "mossback_tortoise": "minecraft:scute" if False else "minecraft:moss_block", "ember_fox": "minecraft:blaze_powder",
                     "veilfawn": "minecraft:leather", "grimfinch": "minecraft:feather", "sorrowdove": "minecraft:feather",
                     "stormstag": "minecraft:leather", "cragstrider": "minecraft:leather", "lumen_beetle": "minecraft:glow_berries",
                     "hollowfleece": "minecraft:black_wool", "ashen_goat": "minecraft:leather", "cryptling": "minecraft:bone_meal",
                     "veil_jelly": "deadcraft:ectoplasm", "sinew_slug": "minecraft:slime_ball", "skyray": "minecraft:phantom_membrane"}.get(mid)
            if extra:
                pools.append(pool([item_entry(extra, 0, 2)]))
        out(f"deadcraft/loot_table/entities/{mid}.json", {"type": "minecraft:entity", "pools": pools,
                                                          "random_sequence": f"deadcraft:entities/{mid}"})

    # Blocks
    def self_drop(b):
        return {"type": "minecraft:block", "pools": [pool([{"type": "minecraft:item", "name": f"deadcraft:{b}"}],
                                                          conditions=[{"condition": "minecraft:survives_explosion"}])],
                "random_sequence": f"deadcraft:blocks/{b}"}

    def ore_drop(b, item, lo=1, hi=1):
        silk = {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
            {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}
        normal = {"type": "minecraft:item", "name": item, "functions": [
            {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
            {"function": "minecraft:explosion_decay"}]}
        if (lo, hi) != (1, 1):
            normal["functions"].insert(0, {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}})
        return {"type": "minecraft:block", "pools": [pool([{"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": f"deadcraft:{b}", "conditions": [silk]}, normal]}])],
                "random_sequence": f"deadcraft:blocks/{b}"}

    SILK_ELSE = {"gloom_grass": "deadcraft:grave_soil", "static_crust": "deadcraft:voidrock"}
    silk_tool = {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
        {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}

    def silk_else(b, other):
        return {"type": "minecraft:block", "random_sequence": f"deadcraft:blocks/{b}", "pools": [pool([{"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": f"deadcraft:{b}", "conditions": [silk_tool]},
            {"type": "minecraft:item", "name": other}]}], conditions=[{"condition": "minecraft:survives_explosion"}])]}

    def leaves_drop(b):
        shears = {"condition": "minecraft:any_of", "terms": [silk_tool, {"condition": "minecraft:match_tool", "predicate": {"items": "minecraft:shears"}}]}
        return {"type": "minecraft:block", "random_sequence": f"deadcraft:blocks/{b}", "pools": [
            pool([{"type": "minecraft:item", "name": f"deadcraft:{b}"}], conditions=[shears]),
            pool([{"type": "minecraft:item", "name": "minecraft:stick", "functions": [
                {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 2}, "add": False}]}],
                conditions=[{"condition": "minecraft:inverted", "term": shears}, {"condition": "minecraft:random_chance", "chance": 0.08}])]}

    ores = {"necrite_ore": "deadcraft:raw_necrite", "deepslate_necrite_ore": "deadcraft:raw_necrite",
            "bloodstone_ore": "deadcraft:bloodstone", "deepslate_bloodstone_ore": "deadcraft:bloodstone",
            "voidsteel_ore": "deadcraft:raw_voidsteel", "stardust_ore": "deadcraft:star_shard"}
    for b in content.blocks():
        if b == "rift_portal":
            continue
        if b in ores:
            table = ore_drop(b, ores[b])
        elif b in SILK_ELSE:
            table = silk_else(b, SILK_ELSE[b])
        elif b == "ghostwood_leaves":
            table = leaves_drop(b)
        else:
            table = self_drop(b)
        out(f"deadcraft/loot_table/blocks/{b}.json", table)

    # Chests
    def chest(name, entries, rolls):
        out(f"deadcraft/loot_table/chests/{name}.json", {"type": "minecraft:chest", "pools": [
            {"rolls": {"type": "minecraft:uniform", "min": rolls[0], "max": rolls[1]}, "bonus_rolls": 0,
             "entries": [item_entry(n, lo, hi, looting=False, weight=w) for n, lo, hi, w in entries]}],
            "random_sequence": f"deadcraft:chests/{name}"})

    common = [("minecraft:bone", 2, 6, 20), ("minecraft:rotten_flesh", 2, 5, 15), ("minecraft:candle", 1, 3, 10),
              ("deadcraft:ectoplasm", 1, 3, 10), ("deadcraft:mana_tonic", 1, 2, 6), ("minecraft:gold_ingot", 1, 3, 8)]
    chest("tomb", common + [("deadcraft:hollow_essence", 1, 2, 8), ("deadcraft:necrite_ingot", 2, 5, 10),
                            ("deadcraft:grimoire_of_awakening", 1, 1, 3), ("deadcraft:bloodstone", 1, 2, 5),
                            ("deadcraft:sigil_petrified", 1, 1, 1), ("deadcraft:frost_tome", 1, 1, 2),
                            ("minecraft:enchanted_book", 1, 1, 4)], (4, 8))
    chest("graveyard", common + [("deadcraft:raw_necrite", 2, 6, 12), ("minecraft:iron_shovel", 1, 1, 6),
                                 ("deadcraft:soul_staff", 1, 1, 2), ("deadcraft:sigil_skeleton", 1, 1, 1)], (3, 6))
    chest("castle", common + [("deadcraft:necrite_ingot", 3, 8, 12), ("deadcraft:bloodstone", 1, 3, 8),
                              ("deadcraft:bone_cleaver", 1, 1, 4), ("deadcraft:bloodreaver", 1, 1, 2),
                              ("deadcraft:necrite_chestplate", 1, 1, 3), ("deadcraft:grimoire_of_awakening", 1, 1, 3),
                              ("deadcraft:rift_key_hells_paradise", 1, 1, 2), ("minecraft:diamond", 1, 2, 4),
                              ("deadcraft:sigil_chronos", 1, 1, 1)], (5, 9))
    chest("city", [("deadcraft:scrap_metal", 2, 7, 25), ("deadcraft:pistol_rounds", 4, 16, 20),
                   ("deadcraft:shotgun_shells", 2, 8, 12), ("deadcraft:rifle_rounds", 2, 6, 8),
                   ("deadcraft:rusted_revolver", 1, 1, 4), ("deadcraft:scrap_smg", 1, 1, 2),
                   ("deadcraft:marksman_rifle", 1, 1, 1), ("minecraft:bread", 1, 4, 12), ("minecraft:iron_ingot", 1, 4, 12),
                   ("minecraft:gunpowder", 2, 6, 12), ("deadcraft:ruin_runner", 1, 1, 1), ("minecraft:rotten_flesh", 2, 6, 10)], (4, 8))


# =============================================================================================
# Recipes
# =============================================================================================
def landmark_loot():
    def chest(name, entries, rolls):
        out(f"deadcraft/loot_table/chests/{name}.json", {"type": "minecraft:chest", "pools": [
            {"rolls": {"type": "minecraft:uniform", "min": rolls[0], "max": rolls[1]}, "bonus_rolls": 0,
             "entries": [item_entry(n, lo, hi, looting=False, weight=w) for n, lo, hi, w in entries]}],
            "random_sequence": f"deadcraft:chests/{name}"})
    base = [("minecraft:bone", 2, 6, 18), ("minecraft:rotten_flesh", 2, 5, 12), ("minecraft:candle", 1, 3, 8),
            ("deadcraft:ectoplasm", 1, 3, 10), ("deadcraft:mana_tonic", 1, 2, 6), ("minecraft:gold_ingot", 1, 3, 6),
            ("minecraft:iron_ingot", 1, 4, 8), ("minecraft:enchanted_book", 1, 1, 2)]
    chest("chapel", base + [("deadcraft:hollow_essence", 1, 2, 8), ("minecraft:golden_apple", 1, 1, 2),
                            ("deadcraft:grimoire_of_awakening", 1, 1, 2), ("deadcraft:blood_chalice", 1, 1, 1)], (4, 7))
    chest("hanging_tree", base + [("minecraft:lead", 1, 2, 8), ("deadcraft:pistol_rounds", 6, 12, 8),
                                  ("deadcraft:rusted_revolver", 1, 1, 1), ("minecraft:emerald", 1, 3, 6)], (3, 6))
    chest("bandit_stash", [("deadcraft:scrap_metal", 2, 6, 14), ("deadcraft:pistol_rounds", 6, 16, 12),
                           ("deadcraft:shotgun_shells", 4, 10, 8), ("minecraft:gunpowder", 2, 6, 10), ("minecraft:emerald", 1, 4, 8),
                           ("minecraft:bread", 2, 5, 10), ("minecraft:cooked_beef", 1, 4, 8), ("deadcraft:scrap_smg", 1, 1, 1),
                           ("deadcraft:doomsday_shotgun", 1, 1, 1), ("minecraft:gold_ingot", 1, 3, 6)], (3, 7))
    chest("hag", base + [("minecraft:spider_eye", 1, 3, 10), ("minecraft:fermented_spider_eye", 1, 2, 8),
                         ("minecraft:glistering_melon_slice", 1, 2, 6), ("minecraft:nether_wart", 2, 6, 10),
                         ("deadcraft:frost_tome", 1, 1, 1), ("deadcraft:void_tome", 1, 1, 1)], (4, 8))
    chest("farm", [("minecraft:wheat", 3, 9, 14), ("minecraft:carrot", 2, 6, 10), ("minecraft:potato", 2, 6, 10),
                   ("minecraft:iron_hoe", 1, 1, 4), ("minecraft:shears", 1, 1, 4), ("deadcraft:raw_marrow_beef", 1, 4, 8),
                   ("minecraft:rotten_flesh", 2, 6, 10), ("deadcraft:pistol_rounds", 4, 10, 4)], (3, 6))
    chest("bone_pit", base + [("minecraft:bone_block", 2, 6, 12), ("deadcraft:hollow_essence", 1, 3, 10),
                              ("deadcraft:sigil_skeleton", 1, 1, 2), ("minecraft:diamond", 1, 2, 3)], (4, 8))
    chest("ritual", base + [("deadcraft:hollow_essence", 1, 3, 10), ("deadcraft:grimoire_of_awakening", 1, 1, 4),
                            ("deadcraft:soul_staff", 1, 1, 2), ("minecraft:crying_obsidian", 1, 4, 6), ("deadcraft:sigil_chronos", 1, 1, 1)], (4, 7))
    chest("watchtower", base + [("minecraft:arrow", 6, 16, 12), ("minecraft:bow", 1, 1, 4), ("minecraft:spyglass", 1, 1, 4),
                                ("deadcraft:rifle_rounds", 4, 10, 6), ("deadcraft:marksman_rifle", 1, 1, 1)], (3, 6))
    chest("fallen_star", [("deadcraft:star_shard", 2, 6, 20), ("minecraft:amethyst_shard", 2, 8, 12), ("minecraft:diamond", 1, 3, 6),
                          ("deadcraft:storm_rod", 1, 1, 2), ("deadcraft:star_edge", 1, 1, 1), ("minecraft:glowstone_dust", 2, 8, 10)], (3, 6))
    chest("hell_cache", base + [("deadcraft:bloodstone", 2, 5, 14), ("deadcraft:demon_horn", 1, 3, 10), ("minecraft:blaze_rod", 1, 3, 8),
                                ("deadcraft:blood_bucket", 1, 1, 4), ("deadcraft:ember_staff", 1, 1, 2), ("deadcraft:bloodreaver", 1, 1, 1),
                                ("deadcraft:sigil_dim_light_demon", 1, 1, 1)], (4, 8))
    chest("forge", [("deadcraft:bloodstone", 2, 6, 14), ("deadcraft:necrite_ingot", 3, 8, 14), ("deadcraft:voidsteel_ingot", 1, 3, 6),
                    ("minecraft:netherite_scrap", 1, 2, 3), ("deadcraft:bone_cleaver", 1, 1, 3), ("deadcraft:bloodstone_chestplate", 1, 1, 1),
                    ("minecraft:iron_block", 1, 2, 6)], (4, 7))
    chest("sky_cache", base + [("deadcraft:star_shard", 1, 4, 12), ("minecraft:phantom_membrane", 1, 3, 10), ("minecraft:feather", 2, 8, 10),
                               ("deadcraft:sky_skiff", 1, 1, 1), ("deadcraft:stardust_helmet", 1, 1, 1), ("minecraft:elytra", 1, 1, 1)], (4, 7))
    chest("airship", [("deadcraft:scrap_metal", 3, 8, 14), ("minecraft:string", 2, 8, 10), ("minecraft:gunpowder", 2, 6, 10),
                      ("minecraft:compass", 1, 1, 4), ("minecraft:map", 1, 1, 4), ("deadcraft:shotgun_shells", 4, 10, 8),
                      ("deadcraft:sky_skiff", 1, 1, 2), ("minecraft:phantom_membrane", 1, 3, 8)], (3, 7))
    chest("descent_cache", base + [("deadcraft:necrite_ingot", 2, 6, 12), ("deadcraft:hollow_essence", 1, 3, 10),
                                   ("deadcraft:hollow_scythe", 1, 1, 1), ("deadcraft:sigil_petrified", 1, 1, 1),
                                   ("deadcraft:necrite_chestplate", 1, 1, 2)], (4, 8))
    chest("mine", [("deadcraft:raw_necrite", 3, 9, 16), ("minecraft:raw_iron", 2, 8, 12), ("minecraft:coal", 4, 12, 14),
                   ("deadcraft:bloodstone", 1, 2, 4), ("minecraft:iron_pickaxe", 1, 1, 4), ("minecraft:rail", 4, 12, 8),
                   ("minecraft:tnt", 1, 3, 4), ("minecraft:diamond", 1, 2, 2)], (4, 8))
    chest("slaughterhouse", [("minecraft:beef", 2, 6, 14), ("minecraft:porkchop", 2, 6, 14), ("minecraft:leather", 2, 5, 10),
                             ("deadcraft:butcher_knife", 1, 1, 3), ("deadcraft:bone_shiv", 1, 1, 4), ("minecraft:iron_ingot", 1, 4, 8),
                             ("deadcraft:sinew_saber", 1, 1, 1)], (4, 7))
    chest("quarantine", base + [("minecraft:golden_apple", 1, 1, 3), ("minecraft:glistering_melon_slice", 1, 3, 6),
                                ("deadcraft:mana_tonic", 1, 3, 8), ("minecraft:paper", 2, 6, 10), ("deadcraft:ritual_dagger", 1, 1, 1)], (4, 7))
    chest("hive", [("deadcraft:bloodstone", 3, 8, 14), ("minecraft:honeycomb", 3, 9, 12), ("minecraft:slime_ball", 2, 6, 10),
                   ("deadcraft:hive_heart", 1, 1, 1), ("deadcraft:voidglass_kris", 1, 1, 2), ("deadcraft:bloodmoon_katana", 1, 1, 2),
                   ("minecraft:diamond", 1, 3, 6)], (5, 9))
    chest("bride", [("deadcraft:ectoplasm", 3, 8, 14), ("minecraft:gold_ingot", 2, 6, 12), ("minecraft:wither_rose", 1, 3, 8),
                    ("deadcraft:bridal_veil", 1, 1, 1), ("deadcraft:static_rapier", 1, 1, 2), ("deadcraft:skystone_claymore", 1, 1, 2),
                    ("minecraft:diamond", 1, 3, 6), ("deadcraft:grimoire_of_awakening", 1, 1, 3)], (5, 9))
    chest("void_cache", base + [("deadcraft:raw_voidsteel", 2, 5, 12), ("deadcraft:star_shard", 1, 3, 10), ("minecraft:ender_pearl", 1, 4, 10),
                                ("deadcraft:void_tome", 1, 1, 2), ("deadcraft:voidsteel_longsword", 1, 1, 1),
                                ("deadcraft:sigil_fracture", 1, 1, 1), ("minecraft:end_crystal", 1, 1, 1)], (4, 8))


# 25 landmarks: (design, biome list, spacing, separation, terrain adaptation)
OW_FOREST = ["#minecraft:is_forest", "#minecraft:is_taiga", "minecraft:dark_forest"]
OW_PLAINS = ["minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow", "minecraft:savanna"]
OW_DRY = ["minecraft:desert", "#minecraft:is_badlands", "minecraft:savanna", "minecraft:savanna_plateau"]
OW_WET = ["minecraft:swamp", "minecraft:mangrove_swamp"]
LANDMARKS = [
    ("plague_chapel", OW_PLAINS + OW_FOREST, 38, 14, "beard_thin"),
    ("hanging_tree", OW_PLAINS + ["minecraft:dark_forest", "#minecraft:is_savanna"], 34, 12, "none"),
    ("bandit_camp", OW_DRY + OW_PLAINS, 36, 12, "beard_thin"),
    ("hag_hovel", OW_WET + ["minecraft:dark_forest"], 28, 9, "none"),
    ("sunken_crypt", OW_FOREST + OW_PLAINS, 34, 12, "none"),
    ("bloodied_farm", OW_PLAINS, 40, 14, "beard_thin"),
    ("bone_pit", OW_DRY + ["minecraft:plains", "#minecraft:is_taiga"], 36, 12, "none"),
    ("ritual_circle", OW_FOREST + OW_PLAINS + ["minecraft:snowy_plains"], 36, 12, "beard_thin"),
    ("ruined_watchtower", OW_PLAINS + OW_FOREST + ["#minecraft:is_hill"], 36, 12, "beard_thin"),
    ("fallen_star", OW_PLAINS + OW_DRY + ["minecraft:snowy_plains"], 44, 16, "none"),
    ("flesh_shrine", ["deadcraft:bleeding_hollows"], 18, 6, "none"),
    ("impaler_field", ["deadcraft:bleeding_hollows", "deadcraft:scorched_forest"], 20, 7, "none"),
    ("demon_forge", ["deadcraft:scorched_forest"], 22, 8, "none"),
    ("cage_gallows", ["deadcraft:scorched_forest", "deadcraft:bleeding_hollows"], 22, 8, "none"),
    ("sky_temple", ["deadcraft:floating_islands"], 18, 6, "none"),
    ("bell_tower", ["deadcraft:shroud_reach", "deadcraft:floating_islands"], 20, 7, "none"),
    ("wrecked_airship", ["deadcraft:floating_islands", "deadcraft:shroud_reach"], 24, 8, "none"),
    ("observatory", ["deadcraft:shroud_reach"], 20, 7, "none"),
    ("cathedral_ruin", ["deadcraft:dark_plains"], 26, 9, "beard_thin"),
    ("mesa_outpost", ["deadcraft:deserted_cliffs"], 22, 8, "beard_thin"),
    ("gallows_square", ["deadcraft:dark_plains"], 20, 7, "beard_thin"),
    ("abandoned_mine", ["deadcraft:deserted_cliffs", "deadcraft:dark_plains"], 22, 8, "none"),
    ("void_obelisk", ["deadcraft:glass_wastes", "deadcraft:storm_field"], 18, 6, "none"),
    ("storm_spire", ["deadcraft:storm_field"], 18, 6, "none"),
    ("shattered_sanctum", ["deadcraft:glass_wastes"], 20, 7, "none"),
    ("slaughterhouse", OW_PLAINS + ["minecraft:savanna"], 0, 0, "beard_thin"),
    ("quarantine_camp", OW_PLAINS + OW_FOREST, 0, 0, "beard_thin"),
    ("scarecrow_field", OW_PLAINS, 0, 0, "beard_thin"),
    ("lamplighter_road", OW_PLAINS + OW_FOREST + ["minecraft:snowy_plains"], 0, 0, "beard_thin"),
    ("hive_nest", ["deadcraft:bleeding_hollows"], 0, 0, "none"),
    ("bride_chapel", ["deadcraft:shroud_reach", "deadcraft:floating_islands"], 0, 0, "none"),
    ("skyray_roost", ["deadcraft:floating_islands"], 0, 0, "none"),
    ("wyrm_burrow", ["deadcraft:deserted_cliffs"], 0, 0, "none"),
    ("hangmans_hill", ["deadcraft:dark_plains"], 0, 0, "beard_thin"),
    ("crystal_garden", ["deadcraft:glass_wastes", "deadcraft:storm_field"], 0, 0, "none"),
]


def landmarks():
    """One structure set per world region, so landmarks share a placement grid and never overlap."""
    groups = {}
    for design, biomes_list, spacing, sep, adapt in LANDMARKS:
        out(f"deadcraft/tags/worldgen/biome/has_structure/{design}.json", {"values": biomes_list})
        out(f"deadcraft/worldgen/structure/{design}.json", {
            "type": "deadcraft:landmark", "design": design, "biomes": f"#deadcraft:has_structure/{design}",
            "step": "top_layer_modification", "terrain_adaptation": adapt, "spawn_overrides": {}})
        region = "overworld" if not biomes_list[0].startswith("deadcraft:") else {
            "bleeding_hollows": "hell", "scorched_forest": "hell", "floating_islands": "sky", "shroud_reach": "sky",
            "dark_plains": "descent", "deserted_cliffs": "descent", "glass_wastes": "void", "storm_field": "void"}[biomes_list[0].split(":")[1]]
        groups.setdefault(region, []).append(design)
    grid = {"overworld": (22, 8), "hell": (12, 4), "sky": (12, 4), "descent": (12, 4), "void": (12, 4)}
    for i, (region, designs) in enumerate(groups.items()):
        spacing, sep = grid[region]
        out(f"deadcraft/worldgen/structure_set/landmarks_{region}.json", {
            "structures": [{"structure": f"deadcraft:{d}", "weight": 1} for d in designs],
            "placement": {"type": "minecraft:random_spread", "spacing": spacing, "separation": sep, "salt": 77100 + i * 37,
                          **({"exclusion_zone": {"other_set": "deadcraft:castles", "chunk_count": 5}} if region == "overworld" else
                             {"exclusion_zone": {"other_set": "deadcraft:portals", "chunk_count": 2}})}})


def shaped(name, pattern, key, result, count=1, category="misc"):
    out(f"deadcraft/recipe/{name}.json", {"type": "minecraft:crafting_shaped", "category": category, "pattern": pattern,
                                          "key": {k: ({"tag": v[1:]} if v.startswith("#") else {"item": v}) for k, v in key.items()},
                                          "result": {"id": result, "count": count}})


def shapeless(name, items, result, count=1, category="misc"):
    out(f"deadcraft/recipe/{name}.json", {"type": "minecraft:crafting_shapeless", "category": category,
                                          "ingredients": [{"tag": i[1:]} if i.startswith("#") else {"item": i} for i in items],
                                          "result": {"id": result, "count": count}})


def cook(name, ingredient, result, xp=0.35, kinds=("smelting",)):
    times = {"smelting": 200, "blasting": 100, "smoking": 100, "campfire_cooking": 600}
    for k in kinds:
        out(f"deadcraft/recipe/{name}_from_{k}.json", {"type": f"minecraft:{k}", "category": "food" if k in ("smoking", "campfire_cooking") else "misc",
                                                       "ingredient": {"item": ingredient}, "result": {"id": result},
                                                       "experience": xp, "cookingtime": times[k]})


def recipes():
    D = "deadcraft:"
    cook("necrite_ingot", D + "raw_necrite", D + "necrite_ingot", 0.7, ("smelting", "blasting"))
    cook("voidsteel_ingot", D + "raw_voidsteel", D + "voidsteel_ingot", 1.0, ("smelting", "blasting"))
    for food in ("ash_venison", "gloom_pork", "cinder_fowl", "marrow_beef", "dusk_hare"):
        cook(f"cooked_{food}", D + f"raw_{food}", D + f"cooked_{food}", 0.35, ("smelting", "smoking", "campfire_cooking"))
    for mat, item in (("necrite", "necrite_ingot"), ("bloodstone", "bloodstone"), ("voidsteel", "voidsteel_ingot"),
                      ("stardust", "star_shard")):
        shaped(f"{mat}_block", ["###", "###", "###"], {"#": D + item}, D + f"{mat}_block", category="building")
        shapeless(f"{item}_from_block", [D + f"{mat}_block"], D + item, 9)
        I = D + item
        shaped(f"{mat}_helmet", ["###", "# #"], {"#": I}, D + f"{mat}_helmet", category="equipment")
        shaped(f"{mat}_chestplate", ["# #", "###", "###"], {"#": I}, D + f"{mat}_chestplate", category="equipment")
        shaped(f"{mat}_leggings", ["###", "# #", "# #"], {"#": I}, D + f"{mat}_leggings", category="equipment")
        shaped(f"{mat}_boots", ["# #", "# #"], {"#": I}, D + f"{mat}_boots", category="equipment")
    E = "equipment"
    shaped("bone_cleaver", [" NN", " NB", "S  "], {"N": D + "necrite_ingot", "B": "minecraft:bone_block", "S": "minecraft:stick"}, D + "bone_cleaver", category=E)
    shaped("bloodreaver", ["  B", " B ", "N  "], {"B": D + "bloodstone", "N": D + "necrite_ingot"}, D + "bloodreaver", category=E)
    shaped("voidsteel_longsword", ["  V", " V ", "N  "], {"V": D + "voidsteel_ingot", "N": D + "necrite_ingot"}, D + "voidsteel_longsword", category=E)
    shaped("star_edge", ["  S", " S ", "V  "], {"S": D + "star_shard", "V": D + "voidsteel_ingot"}, D + "star_edge", category=E)
    shaped("bone_shiv", ["  B", " B ", "S  "], {"B": "minecraft:bone", "S": "minecraft:stick"}, D + "bone_shiv", category=E)
    shaped("ritual_dagger", ["  N", " B ", "S  "], {"N": D + "necrite_ingot", "B": D + "bloodstone", "S": "minecraft:stick"}, D + "ritual_dagger", category=E)
    shaped("butcher_knife", [" II", " IB", "S  "], {"I": "minecraft:iron_ingot", "B": D + "bloodstone", "S": "minecraft:stick"}, D + "butcher_knife", category=E)
    shaped("voidglass_kris", ["  V", " C ", "S  "], {"V": D + "voidsteel_ingot", "C": D + "void_crystal", "S": "minecraft:stick"}, D + "voidglass_kris", category=E)
    shaped("grave_warden_blade", ["  N", " N ", "B  "], {"N": D + "necrite_ingot", "B": "minecraft:bone_block"}, D + "grave_warden_blade", category=E)
    shaped("bloodmoon_katana", ["  B", " B ", "N  "], {"B": D + "bloodstone", "N": D + "necrite_ingot"}, D + "bloodmoon_katana", category=E)
    shaped("sinew_saber", ["  S", " S ", "N  "], {"S": D + "sinew_block", "N": D + "necrite_ingot"}, D + "sinew_saber", category=E)
    shaped("skystone_claymore", [" KS", "KSK", "NK "], {"K": D + "skystone", "S": D + "star_shard", "N": D + "necrite_ingot"}, D + "skystone_claymore", category=E)
    shaped("static_rapier", ["  C", " V ", "R  "], {"C": D + "static_crust", "V": D + "voidsteel_ingot", "R": "minecraft:lightning_rod"}, D + "static_rapier", category=E)
    shaped("hollow_scythe", ["BBH", "  S", " S "], {"B": D + "bloodstone", "H": D + "hollow_essence", "S": "minecraft:stick"}, D + "hollow_scythe", category=E)
    shaped("chrono_blade", [" HV", " V ", "N  "], {"H": D + "chronos_hourglass", "V": D + "voidsteel_ingot", "N": D + "necrite_ingot"}, D + "chrono_blade", category=E)
    shaped("fracture_greatsword", [" VC", "VVV", "NV "], {"C": D + "fracture_core", "V": D + "voidsteel_ingot", "N": D + "necrite_ingot"}, D + "fracture_greatsword", category=E)
    shaped("rusted_revolver", ["SSI", " NW"], {"S": D + "scrap_metal", "I": "minecraft:iron_ingot", "N": D + "necrite_ingot", "W": "#minecraft:planks"}, D + "rusted_revolver", category=E)
    shaped("doomsday_shotgun", ["SSS", "WNI", "W  "], {"S": D + "scrap_metal", "W": "#minecraft:logs", "N": D + "necrite_ingot", "I": "minecraft:iron_ingot"}, D + "doomsday_shotgun", category=E)
    shaped("marksman_rifle", ["G  ", "VSS", "W N"], {"G": "minecraft:spyglass", "V": D + "voidsteel_ingot", "S": D + "scrap_metal", "W": "#minecraft:logs", "N": D + "necrite_ingot"}, D + "marksman_rifle", category=E)
    shaped("scrap_smg", ["SSS", " NI", " I "], {"S": D + "scrap_metal", "N": D + "necrite_ingot", "I": "minecraft:iron_ingot"}, D + "scrap_smg", category=E)
    shaped("pistol_rounds", [" C ", "GNG"], {"C": "minecraft:copper_ingot", "G": "minecraft:gunpowder", "N": "minecraft:iron_nugget"}, D + "pistol_rounds", 12, E)
    shaped("shotgun_shells", ["PNP", " G "], {"P": "minecraft:paper", "N": "minecraft:iron_nugget", "G": "minecraft:gunpowder"}, D + "shotgun_shells", 8, E)
    shaped("rifle_rounds", [" C ", "GNG"], {"C": "minecraft:copper_ingot", "G": "minecraft:gunpowder", "N": D + "necrite_ingot"}, D + "rifle_rounds", 8, E)
    shaped("grimoire_of_awakening", ["SHS", "EBE", "SHS"], {"S": D + "star_shard", "H": D + "hollow_essence", "E": D + "ectoplasm", "B": "minecraft:book"}, D + "grimoire_of_awakening", category=E)
    shaped("mana_tonic", [" G ", "EBE"], {"G": "minecraft:glowstone_dust", "E": D + "ectoplasm", "B": "minecraft:glass_bottle"}, D + "mana_tonic", 2, E)
    shaped("soul_staff", ["  E", " N ", "S  "], {"E": D + "ectoplasm", "N": D + "necrite_ingot", "S": "minecraft:stick"}, D + "soul_staff", category=E)
    shaped("ember_staff", ["  B", " R ", "R  "], {"B": D + "bloodstone", "R": "minecraft:blaze_rod"}, D + "ember_staff", category=E)
    shaped("frost_tome", ["IEI", "EBE", "IEI"], {"I": "minecraft:packed_ice", "E": D + "ectoplasm", "B": "minecraft:book"}, D + "frost_tome", category=E)
    shaped("void_tome", ["VPV", "PBP", "VPV"], {"V": D + "voidsteel_ingot", "P": "minecraft:ender_pearl", "B": "minecraft:book"}, D + "void_tome", category=E)
    shaped("storm_rod", [" SL", " CS", "C  "], {"S": D + "star_shard", "L": "minecraft:lightning_rod", "C": "minecraft:copper_ingot"}, D + "storm_rod", category=E)
    shaped("blood_chalice", ["B B", "GBG", " G "], {"B": D + "bloodstone", "G": "minecraft:gold_ingot"}, D + "blood_chalice", category=E)
    shaped("rift_key_hells_paradise", [" B ", "BHB", " D "], {"B": D + "bloodstone", "H": "minecraft:blaze_rod", "D": D + "demon_horn"}, D + "rift_key_hells_paradise", category=E)
    shaped("rift_key_skyfall_torn", [" E ", "EPE", " F "], {"E": D + "ectoplasm", "P": "minecraft:phantom_membrane", "F": "minecraft:feather"}, D + "rift_key_skyfall_torn", category=E)
    shaped("rift_key_lights_descent", [" S ", "SGS", " N "], {"S": D + "star_shard", "G": "minecraft:glowstone", "N": D + "necrite_ingot"}, D + "rift_key_lights_descent", category=E)
    shaped("rift_key_disposed_skies", [" V ", "VEV", " H "], {"V": D + "voidsteel_ingot", "E": "minecraft:ender_eye", "H": D + "hollow_essence"}, D + "rift_key_disposed_skies", category=E)
    shaped("ruin_runner", ["SSS", "NIN", "B B"], {"S": D + "scrap_metal", "N": D + "necrite_ingot", "I": "minecraft:iron_block", "B": "minecraft:black_concrete"}, D + "ruin_runner", category="transportation")
    shaped("sky_skiff", ["PVP", "SVS", "VVV"], {"P": "minecraft:phantom_membrane", "V": D + "voidsteel_ingot", "S": D + "star_shard"}, D + "sky_skiff", category="transportation")
    shaped("sigil_fracture", ["SAS", "ACA", "SAS"], {"S": D + "star_shard", "A": "minecraft:amethyst_shard", "C": D + "voidsteel_block"}, D + "sigil_fracture", category=E)
    shaped("sigil_chronos", ["GCG", "CHC", "GCG"], {"G": "minecraft:gold_block", "C": "minecraft:clock", "H": D + "hollow_essence"}, D + "sigil_chronos", category=E)
    shaped("sigil_skeleton", ["BHB", "HWH", "BHB"], {"B": "minecraft:bone_block", "H": D + "hollow_essence", "W": "minecraft:wither_skeleton_skull"}, D + "sigil_skeleton", category=E)
    shaped("sigil_dim_light_demon", ["DBD", "BOB", "DBD"], {"D": D + "demon_horn", "B": D + "bloodstone_block", "O": "minecraft:crying_obsidian"}, D + "sigil_dim_light_demon", category=E)
    shaped("sigil_petrified", ["SNS", "NEN", "SNS"], {"S": "minecraft:chiseled_stone_bricks", "N": D + "necrite_block", "E": "minecraft:ender_eye"}, D + "sigil_petrified", category=E)
    shaped("dread_lantern", [" N ", "NEN", " N "], {"N": "minecraft:iron_nugget", "E": D + "ectoplasm"}, D + "dread_lantern", category="building")
    shaped("gravestone", ["SSS", "SCS", "SSS"], {"S": "minecraft:stone_bricks", "C": "minecraft:bone"}, D + "gravestone", 4, "building")


# =============================================================================================
# Tags
# =============================================================================================
def tags():
    W = ["bone_cleaver", "bloodreaver", "voidsteel_longsword", "star_edge", "hollow_scythe", "chrono_blade", "fracture_greatsword"]
    GUNS = ["rusted_revolver", "doomsday_shotgun", "marksman_rifle", "scrap_smg"]
    mats = ["necrite", "bloodstone", "voidsteel", "stardust"]
    W = W + ["bone_shiv", "ritual_dagger", "butcher_knife", "voidglass_kris", "grave_warden_blade", "bloodmoon_katana",
             "sinew_saber", "skystone_claymore", "static_rapier"]
    out("minecraft/tags/item/swords.json", {"values": [dc(w) for w in W]})
    for piece, tag in (("helmet", "head_armor"), ("chestplate", "chest_armor"), ("leggings", "leg_armor"), ("boots", "foot_armor")):
        out(f"minecraft/tags/item/{tag}.json", {"values": [dc(f"{m}_{piece}") for m in mats]})
    out("deadcraft/tags/item/guns.json", {"values": [dc(g) for g in GUNS]})
    out("deadcraft/tags/item/creature_food.json", {"values": ["minecraft:wheat", "minecraft:wheat_seeds", "minecraft:carrot",
                                                              "minecraft:sweet_berries", "minecraft:glow_berries", "minecraft:apple"]})
    out("minecraft/tags/item/meat.json", {"values": [dc(f"{s}_{f}") for f in ("ash_venison", "gloom_pork", "cinder_fowl", "marrow_beef", "dusk_hare") for s in ("raw", "cooked")]})
    # Blocks
    pick = ["necrite_ore", "deepslate_necrite_ore", "bloodstone_ore", "deepslate_bloodstone_ore", "voidsteel_ore", "stardust_ore",
            "necrite_block", "bloodstone_block", "voidsteel_block", "stardust_block", "gravestone", "dread_lantern"]
    pick += ["charnel_stone", "skystone", "umbral_stone", "dustrock", "voidrock", "static_crust", "void_crystal"]
    out("minecraft/tags/block/mineable/pickaxe.json", {"values": [dc(b) for b in pick]})
    out("minecraft/tags/block/mineable/axe.json", {"values": ["deadcraft:charred_log", "deadcraft:ghostwood_log"]})
    out("minecraft/tags/block/mineable/shovel.json", {"values": [dc(b) for b in ("ash_soil", "grave_soil", "gloom_grass", "bone_sand")]})
    out("minecraft/tags/block/mineable/hoe.json", {"values": [dc(b) for b in ("sinew_block", "pale_moss", "shroud_mold", "ghostwood_leaves")]})
    out("minecraft/tags/block/leaves.json", {"values": ["deadcraft:ghostwood_leaves"]})
    soils = ["ash_soil", "sinew_block", "pale_moss", "shroud_mold", "grave_soil", "gloom_grass", "bone_sand", "static_crust",
             "voidrock", "charnel_stone", "skystone"]
    out("deadcraft/tags/block/plant_soil.json", {"values": [dc(b) for b in soils]})
    out("minecraft/tags/block/overworld_carver_replaceables.json", {"replace": False, "values": [dc(b) for b in (
        "umbral_stone", "grave_soil", "gloom_grass", "bone_sand", "dustrock")]})
    out("minecraft/tags/block/infiniburn_nether.json", {"replace": False, "values": ["deadcraft:charnel_stone"]})
    out("minecraft/tags/block/needs_iron_tool.json", {"values": [dc(b) for b in ("necrite_ore", "deepslate_necrite_ore", "necrite_block")]})
    out("minecraft/tags/block/needs_diamond_tool.json", {"values": [dc(b) for b in (
        "bloodstone_ore", "deepslate_bloodstone_ore", "voidsteel_ore", "stardust_ore", "bloodstone_block", "voidsteel_block", "stardust_block")]})
    out("minecraft/tags/block/logs.json", {"values": ["deadcraft:charred_log", "deadcraft:ghostwood_log"]})
    out("minecraft/tags/item/logs.json", {"values": ["deadcraft:charred_log", "deadcraft:ghostwood_log"]})
    # Enchantments
    out("minecraft/tags/enchantment/non_treasure.json", {"values": [dc(e) for e in (
        "bloodthirst", "soul_reaper", "hollow_point", "quick_reload", "phantom_step", "static_ward", "exorcism")]})
    out("minecraft/tags/enchantment/tradeable.json", {"values": [dc(e) for e in ("bloodthirst", "hollow_point", "quick_reload", "exorcism")]})
    out("minecraft/tags/enchantment/in_enchanting_table.json", {"values": [dc(e) for e in (
        "bloodthirst", "soul_reaper", "hollow_point", "quick_reload", "phantom_step", "static_ward", "exorcism")]})


# =============================================================================================
# Enchantments (data-driven; effects implemented in Java)
# =============================================================================================
def enchantments():
    def ench(name, supported, primary, weight, max_level, base, per, slots, anvil=4, exclusive=None):
        e = {"description": {"translate": f"enchantment.deadcraft.{name}"}, "supported_items": supported,
             "primary_items": primary, "weight": weight, "max_level": max_level,
             "min_cost": {"base": base, "per_level_above_first": per},
             "max_cost": {"base": base + 30, "per_level_above_first": per}, "anvil_cost": anvil, "slots": slots, "effects": {}}
        if exclusive:
            e["exclusive_set"] = exclusive
        out(f"deadcraft/enchantment/{name}.json", e)

    ench("bloodthirst", "#minecraft:enchantable/sword", "#minecraft:enchantable/sword", 3, 3, 12, 10, ["mainhand"])
    ench("soul_reaper", "#minecraft:enchantable/weapon", "#minecraft:enchantable/sword", 3, 3, 10, 10, ["mainhand"])
    ench("exorcism", "#minecraft:enchantable/weapon", "#minecraft:enchantable/sword", 5, 5, 5, 8, ["mainhand"], 2,
         "#minecraft:exclusive_set/damage")
    ench("hollow_point", "#deadcraft:guns", "#deadcraft:guns", 8, 5, 1, 10, ["mainhand"], 2)
    ench("quick_reload", "#deadcraft:guns", "#deadcraft:guns", 6, 3, 5, 12, ["mainhand"], 2)
    ench("phantom_step", "#minecraft:enchantable/foot_armor", "#minecraft:enchantable/foot_armor", 3, 3, 10, 10, ["feet"])
    ench("static_ward", "#minecraft:enchantable/chest_armor", "#minecraft:enchantable/chest_armor", 3, 3, 10, 10, ["chest"])


# =============================================================================================
# World generation: biomes, features, dimensions, structures
# =============================================================================================
STEPS = 11  # raw_generation .. top_layer_modification


# One global order for every feature used by DeadCraft biomes. Biomes that share a dimension must
# list shared features in the same relative order, or world generation fails with a cycle error.
FEATURE_ORDER = [
    "deadcraft:blood_springs",
    "minecraft:ore_dirt", "minecraft:ore_gravel", "minecraft:ore_coal_upper", "minecraft:ore_iron_upper",
    "minecraft:ore_iron_middle", "minecraft:ore_gold", "minecraft:ore_redstone", "minecraft:ore_diamond", "minecraft:ore_copper",
    "deadcraft:ore_necrite", "deadcraft:ore_bloodstone", "deadcraft:ore_stardust", "deadcraft:ore_voidsteel",
    "deadcraft:ore_bloodstone_hell",
    "deadcraft:charnel_stalactites", "deadcraft:skystone_stalactites",
    "deadcraft:bone_ribs_cavern", "deadcraft:bone_ribs",
    "deadcraft:dead_trees", "deadcraft:dead_trees_sparse", "deadcraft:dead_trees_cavern",
    "deadcraft:ghostwood_trees", "deadcraft:ghostwood_trees_sparse",
    "deadcraft:void_crystal_spikes", "deadcraft:void_crystal_spikes_sparse",
    "deadcraft:ruin_spires", "deadcraft:ruin_spires_cavern", "deadcraft:grave_patch",
    "deadcraft:patch_cinder_sprout", "deadcraft:patch_gloom_fern", "deadcraft:patch_pale_bloom", "deadcraft:patch_static_thorn",
]


def features_list(**by_step):
    for k, v in by_step.items():
        missing = [f for f in v if f not in FEATURE_ORDER]
        assert not missing, f"add {missing} to FEATURE_ORDER"
        by_step[k] = sorted(v, key=FEATURE_ORDER.index)
    order = ["raw_generation", "lakes", "local_modifications", "underground_structures", "surface_structures", "strongholds",
             "underground_ores", "underground_decoration", "fluid_springs", "vegetal_decoration", "top_layer_modification"]
    return [by_step.get(s, []) for s in order]


def spawners(monster=(), creature=(), ambient=()):
    def entries(lst):
        return [{"type": dc(t), "weight": w, "minCount": a, "maxCount": b} for t, w, a, b in lst]
    return {"monster": entries(monster), "creature": entries(creature), "ambient": entries(ambient),
            "axolotls": [], "underground_water_creature": [], "water_creature": [], "water_ambient": [], "misc": []}


def biome(name, temp, precip, colors, particle, monster, creature, features, carvers=(), music=True, mood="deadcraft:world.ambient_drone"):
    fog, sky, water, water_fog, grass, foliage = colors
    effects = {"fog_color": fog, "sky_color": sky, "water_color": water, "water_fog_color": water_fog,
               "grass_color": grass, "foliage_color": foliage,
               "ambient_sound": "deadcraft:world.ambient_wind",
               "mood_sound": {"sound": mood, "tick_delay": 3000, "block_search_extent": 8, "offset": 2.0},
               "additions_sound": {"sound": "deadcraft:mob.whisper", "tick_chance": 0.0008}}
    if particle:
        effects["particle"] = {"options": {"type": particle[0]}, "probability": particle[1]}
    if music:
        effects["music"] = {"sound": "deadcraft:music.dimension", "min_delay": 3000, "max_delay": 9000, "replace_current_music": False}
    out(f"deadcraft/worldgen/biome/{name}.json", {
        "has_precipitation": precip, "temperature": temp, "downfall": 0.4 if precip else 0.0, "effects": effects,
        "spawners": spawners(monster, creature), "spawn_costs": {}, "carvers": {"air": list(carvers)}, "features": features})


def bm(b):
    return {"Name": b}


ORE_TARGETS = {
    "ore_necrite": [("deadcraft:necrite_ore", "tag", "minecraft:stone_ore_replaceables"),
                    ("deadcraft:deepslate_necrite_ore", "tag", "minecraft:deepslate_ore_replaceables"),
                    ("deadcraft:necrite_ore", "block", "deadcraft:umbral_stone"),
                    ("deadcraft:necrite_ore", "block", "deadcraft:dustrock")],
    "ore_bloodstone": [("deadcraft:bloodstone_ore", "tag", "minecraft:stone_ore_replaceables"),
                       ("deadcraft:deepslate_bloodstone_ore", "tag", "minecraft:deepslate_ore_replaceables"),
                       ("deadcraft:bloodstone_ore", "block", "deadcraft:umbral_stone")],
    "ore_bloodstone_hell": [("deadcraft:bloodstone_ore", "block", "deadcraft:charnel_stone")],
    "ore_voidsteel": [("deadcraft:voidsteel_ore", "block", "deadcraft:voidrock")],
    "ore_stardust": [("deadcraft:stardust_ore", "block", "deadcraft:skystone"),
                     ("deadcraft:stardust_ore", "block", "deadcraft:voidrock")],
}


def worldgen_features():
    sizes = {"ore_necrite": 9, "ore_bloodstone": 4, "ore_bloodstone_hell": 6, "ore_voidsteel": 5, "ore_stardust": 5}
    for name, targets in ORE_TARGETS.items():
        out(f"deadcraft/worldgen/configured_feature/{name}.json", {"type": "minecraft:ore", "config": {
            "discard_chance_on_air_exposure": 0.0, "size": sizes[name],
            "targets": [{"state": {"Name": state}, "target": ({"predicate_type": "minecraft:tag_match", "tag": what}
                                                              if kind == "tag" else {"predicate_type": "minecraft:block_match", "block": what})}
                        for state, kind, what in targets]}})

    def height(lo, hi, trap=True):
        return {"type": "minecraft:height_range", "height": {"type": "minecraft:trapezoid" if trap else "minecraft:uniform",
                                                             "min_inclusive": lo, "max_inclusive": hi}}

    ore_place = {
        "ore_necrite": [{"type": "minecraft:count", "count": 9}, {"type": "minecraft:in_square"},
                        height({"absolute": -48}, {"absolute": 96}), {"type": "minecraft:biome"}],
        "ore_bloodstone": [{"type": "minecraft:count", "count": 4}, {"type": "minecraft:in_square"},
                           height({"above_bottom": -40}, {"absolute": 16}), {"type": "minecraft:biome"}],
        "ore_bloodstone_hell": [{"type": "minecraft:count", "count": 12}, {"type": "minecraft:in_square"},
                                height({"above_bottom": 4}, {"below_top": 10}, False), {"type": "minecraft:biome"}],
        "ore_voidsteel": [{"type": "minecraft:count", "count": 10}, {"type": "minecraft:in_square"},
                          height({"above_bottom": 0}, {"below_top": 0}, False), {"type": "minecraft:biome"}],
        "ore_stardust": [{"type": "minecraft:count", "count": 8}, {"type": "minecraft:in_square"},
                         height({"above_bottom": 0}, {"below_top": 0}, False), {"type": "minecraft:biome"}],
    }
    for name, placement in ore_place.items():
        out(f"deadcraft/worldgen/placed_feature/{name}.json", {"feature": f"deadcraft:{name}", "placement": placement})

    for f in ("dead_tree", "grave_patch", "ruin_spire", "bone_ribs", "ghostwood_tree", "void_crystal_spike",
              "skystone_stalactite", "charnel_stalactite"):
        out(f"deadcraft/worldgen/configured_feature/{f}.json", {"type": f"deadcraft:{f}", "config": {}})
    surface = [{"type": "minecraft:in_square"}, {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"}, {"type": "minecraft:biome"}]
    layers = lambda n: [{"type": "minecraft:count_on_every_layer", "count": n}, {"type": "minecraft:biome"}]

    def placed(name, feature, placement):
        out(f"deadcraft/worldgen/placed_feature/{name}.json", {"feature": f"deadcraft:{feature}", "placement": placement})

    placed("dead_trees", "dead_tree", [{"type": "minecraft:count", "count": 3}] + surface)
    placed("dead_trees_sparse", "dead_tree", [{"type": "minecraft:rarity_filter", "chance": 3}] + surface)
    placed("dead_trees_cavern", "dead_tree", layers(3))
    placed("grave_patch", "grave_patch", [{"type": "minecraft:rarity_filter", "chance": 6}] + surface)
    placed("grave_patch_rare", "grave_patch", [{"type": "minecraft:rarity_filter", "chance": 160}] + surface)
    placed("ruin_spires", "ruin_spire", [{"type": "minecraft:rarity_filter", "chance": 4}] + surface)
    placed("ruin_spires_cavern", "ruin_spire", [{"type": "minecraft:rarity_filter", "chance": 2}] + layers(1))
    placed("bone_ribs", "bone_ribs", [{"type": "minecraft:rarity_filter", "chance": 9}] + surface)
    placed("bone_ribs_cavern", "bone_ribs", [{"type": "minecraft:rarity_filter", "chance": 14}] + layers(1))
    placed("ghostwood_trees", "ghostwood_tree", [{"type": "minecraft:count", "count": 2}] + surface)
    placed("ghostwood_trees_sparse", "ghostwood_tree", [{"type": "minecraft:rarity_filter", "chance": 2}] + surface)
    placed("void_crystal_spikes", "void_crystal_spike", [{"type": "minecraft:count", "count": 3}] + surface)
    placed("void_crystal_spikes_sparse", "void_crystal_spike", [{"type": "minecraft:rarity_filter", "chance": 3}] + surface)
    placed("skystone_stalactites", "skystone_stalactite", [{"type": "minecraft:count", "count": 5}, {"type": "minecraft:in_square"}, {"type": "minecraft:biome"}])
    placed("charnel_stalactites", "charnel_stalactite", [{"type": "minecraft:count", "count": 8}, {"type": "minecraft:in_square"}, {"type": "minecraft:biome"}])

    # plant patches
    for plant, cavern in (("cinder_sprout", True), ("gloom_fern", False), ("pale_bloom", False), ("static_thorn", False)):
        out(f"deadcraft/worldgen/configured_feature/patch_{plant}.json", {"type": "minecraft:random_patch", "config": {
            "tries": 48, "xz_spread": 6, "y_spread": 3, "feature": {"feature": {"type": "minecraft:simple_block", "config": {
                "to_place": {"type": "minecraft:simple_state_provider", "state": {"Name": f"deadcraft:{plant}"}}}},
                "placement": [{"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:all_of", "predicates": [
                    {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"},
                    {"type": "minecraft:matching_block_tag", "offset": [0, -1, 0], "tag": "deadcraft:plant_soil"}]}}]}}})
        placement = layers(2) if cavern else [{"type": "minecraft:count", "count": 2}] + surface
        out(f"deadcraft/worldgen/placed_feature/patch_{plant}.json", {"feature": f"deadcraft:patch_{plant}", "placement": placement})

    # blood falls pouring from the cavern walls
    out("deadcraft/worldgen/configured_feature/blood_spring.json", {"type": "minecraft:spring_feature", "config": {
        "state": {"Name": "deadcraft:blood", "Properties": {"falling": "true"}}, "requires_block_below": False,
        "rock_count": 4, "hole_count": 1, "valid_blocks": ["deadcraft:charnel_stone", "deadcraft:sinew_block"]}})
    out("deadcraft/worldgen/placed_feature/blood_springs.json", {"feature": "deadcraft:blood_spring", "placement": [
        {"type": "minecraft:count", "count": 14}, {"type": "minecraft:in_square"},
        height({"above_bottom": 50}, {"below_top": 30}, False), {"type": "minecraft:biome"}]})


def biomes():
    # ---- Hell's Paradise ----------------------------------------------------------------------
    hell_common = dict(underground_ores=["deadcraft:ore_bloodstone_hell"], fluid_springs=["deadcraft:blood_springs"],
                       underground_decoration=["deadcraft:charnel_stalactites"])
    biome("scorched_forest", 2.0, False, (0x2a0d06, 0x3a0a05, 0x8a2a10, 0x2a0505, 0x3a2a20, 0x2a1a10),
          ("deadcraft:ember", 0.005),
          [("scorched", 50, 1, 3), ("wretched", 20, 1, 2), ("severed", 25, 1, 3), ("parasite", 30, 2, 4), ("lamplighter", 12, 1, 1)],
          [("ember_fox", 10, 1, 2), ("cinderhen", 10, 2, 4), ("candlewick_toad", 8, 1, 3)],
          features_list(**hell_common, vegetal_decoration=["deadcraft:dead_trees_cavern", "deadcraft:ruin_spires_cavern",
                                                           "deadcraft:patch_cinder_sprout"]))
    biome("bleeding_hollows", 1.6, False, (0x3a0408, 0x2a0004, 0x8a0010, 0x3a0006, 0x6a1a1a, 0x5a1010),
          ("deadcraft:blood", 0.0025),
          [("blood_demon", 20, 1, 1), ("severed", 30, 1, 3), ("parasite", 25, 2, 4), ("wretched", 15, 1, 2), ("mire_hag", 12, 1, 1),
           ("butcher", 8, 1, 1)],
          [("gravecat", 6, 1, 1), ("shellmourn", 8, 1, 2), ("sinew_slug", 12, 2, 4)],
          features_list(**hell_common, local_modifications=["deadcraft:bone_ribs_cavern"]))
    # ---- Skyfall Torn -------------------------------------------------------------------------
    sky_common = dict(underground_ores=["deadcraft:ore_stardust"], underground_decoration=["deadcraft:skystone_stalactites"])
    biome("floating_islands", 0.5, True, (0x8a94a8, 0x9aa6bc, 0x4a6a8a, 0x101828, 0x9ab8aa, 0x8aa89a),
          ("deadcraft:soul", 0.003),
          [("star_fall", 20, 1, 1), ("ghost", 25, 1, 2), ("illusion", 15, 1, 1)],
          [("sorrowdove", 10, 2, 4), ("wisplet", 8, 1, 3), ("lantern_moth", 8, 2, 4), ("veilfawn", 8, 1, 2), ("skyray", 10, 1, 3),
           ("veil_jelly", 6, 1, 3)],
          features_list(**sky_common, vegetal_decoration=["deadcraft:ghostwood_trees", "deadcraft:patch_pale_bloom",
                                                          "deadcraft:grave_patch"]))
    biome("shroud_reach", 0.3, True, (0x5a5468, 0x6a6480, 0x3a3a5a, 0x101018, 0x6a6280, 0x5a5470),
          ("minecraft:white_ash", 0.008),
          [("depart", 20, 1, 1), ("ghost", 25, 1, 3), ("the_forgotten", 15, 1, 1), ("hanged_man", 18, 1, 2)],
          [("hush_owl", 8, 1, 2), ("wisplet", 6, 1, 3), ("hollowfleece", 8, 2, 3), ("veil_jelly", 10, 1, 3)],
          features_list(**sky_common, vegetal_decoration=["deadcraft:ghostwood_trees_sparse", "deadcraft:ruin_spires"]))
    # ---- Light's Descent ----------------------------------------------------------------------
    caves = ["minecraft:cave", "minecraft:cave_extra_underground", "minecraft:canyon"]
    biome("dark_plains", 0.6, True, (0x1a0a10, 0x2a0a10, 0x1a2a3a, 0x05080c, 0x1f2a1f, 0x1a201a),
          ("minecraft:white_ash", 0.003),
          [("the_hollow", 20, 1, 1), ("the_forgotten", 25, 1, 2), ("ghost", 30, 1, 3), ("severed", 20, 1, 3),
           ("rotwalker", 30, 2, 4), ("hanged_man", 12, 1, 1), ("lamplighter", 12, 1, 1), ("butcher", 8, 1, 1)],
          [("cryptling", 10, 2, 4), ("ashgrazer", 10, 2, 4), ("gloomhog", 10, 2, 4), ("hollowfleece", 10, 2, 4), ("duskhare", 8, 1, 3), ("hush_owl", 6, 1, 2)],
          features_list(underground_ores=["deadcraft:ore_necrite", "deadcraft:ore_bloodstone"],
                        vegetal_decoration=["deadcraft:dead_trees_sparse", "deadcraft:grave_patch", "deadcraft:patch_gloom_fern"]),
          caves)
    biome("deserted_cliffs", 1.2, False, (0x3a1a14, 0x4a1a14, 0x3a5a6a, 0x101820, 0x6a5a40, 0x5a4a30),
          ("minecraft:white_ash", 0.004),
          [("cracked", 30, 1, 2), ("collapse", 15, 1, 1), ("bandit", 25, 2, 4), ("bandit_captain", 6, 1, 1), ("gravewyrm", 18, 1, 2)],
          [("ashen_goat", 12, 2, 4), ("cragstrider", 10, 1, 3), ("marrow_ox", 8, 2, 3), ("grimfinch", 6, 2, 4), ("mossback_tortoise", 5, 1, 2)],
          features_list(underground_ores=["deadcraft:ore_necrite"],
                        local_modifications=["deadcraft:bone_ribs"],
                        vegetal_decoration=["deadcraft:dead_trees_sparse", "deadcraft:ruin_spires"]),
          caves)
    # ---- Disposed Skies -----------------------------------------------------------------------
    void_common = dict(underground_ores=["deadcraft:ore_voidsteel", "deadcraft:ore_stardust"])
    biome("storm_field", 0.8, True, (0x0a0c18, 0x050510, 0x2a4a6a, 0x0a1018, 0x3a4a5a, 0x2a3a4a),
          ("deadcraft:spark", 0.0025),
          [("star_fall", 25, 1, 2), ("illusion", 20, 1, 1), ("depart", 20, 1, 1)],
          [("stormstag", 10, 1, 3), ("lumen_beetle", 8, 2, 4)],
          features_list(**void_common, vegetal_decoration=["deadcraft:void_crystal_spikes_sparse", "deadcraft:patch_static_thorn"]))
    biome("glass_wastes", 0.4, False, (0x140a24, 0x08040e, 0x5a3a9a, 0x100820, 0x5a4a8a, 0x4a3a7a),
          ("deadcraft:star", 0.002),
          [("illusion", 25, 1, 2), ("ghost", 20, 1, 2), ("depart", 15, 1, 1)],
          [("lumen_beetle", 8, 2, 4), ("wisplet", 8, 1, 3), ("veil_jelly", 8, 1, 3)],
          features_list(**void_common, vegetal_decoration=["deadcraft:void_crystal_spikes"]))


# =============================================================================================
# Terrain: each dimension has its own noises, density functions and surface rules
# =============================================================================================
NOISES = {
    "hell_caverns": (-7, [1.0, 1.0, 0.5, 0.25]),
    "hell_pillars": (-7, [1.0, 0.5]),
    "sky_islands": (-6, [1.0, 0.6, 0.3]),
    "sky_clusters": (-8, [1.0, 0.5]),
    "sky_detail": (-5, [1.0, 0.5]),
    "descent_cliffs": (-7, [1.0, 0.6, 0.3]),
    "descent_detail": (-5, [1.0, 0.5, 0.25]),
    "void_sheets": (-7, [1.0, 0.5]),
    "void_mask": (-8, [1.0, 1.0, 0.5]),
    "void_spires": (-6, [1.0, 0.5]),
    "climate_a": (-9, [1.0, 1.0]),
    "climate_b": (-9, [1.0, 1.0]),
}


def noise(n, xz, y):
    return {"type": "minecraft:noise", "noise": f"deadcraft:{n}", "xz_scale": xz, "y_scale": y}


def add(a, b):
    return {"type": "minecraft:add", "argument1": a, "argument2": b}


def mul(a, b):
    return {"type": "minecraft:mul", "argument1": a, "argument2": b}


def dmax(a, b):
    return {"type": "minecraft:max", "argument1": a, "argument2": b}


def dmin(a, b):
    return {"type": "minecraft:min", "argument1": a, "argument2": b}


def ygrad(y0, y1, v0, v1):
    return {"type": "minecraft:y_clamped_gradient", "from_y": y0, "to_y": y1, "from_value": v0, "to_value": v1}


def interp(f):
    return {"type": "minecraft:interpolated", "argument": f}


def router(final, temperature=0.0, vegetation=0.0, continents=0.0, erosion=0.0, initial=None):
    return {"barrier": 0, "fluid_level_floodedness": 0, "fluid_level_spread": 0, "lava": 0,
            "temperature": temperature, "vegetation": vegetation, "continents": continents, "erosion": erosion,
            "depth": 0, "ridges": 0, "initial_density_without_jaggedness": initial if initial is not None else final,
            "final_density": final, "vein_toggle": 0, "vein_ridged": 0, "vein_gap": 0}


def block(b):
    return {"type": "minecraft:block", "result_state": {"Name": b}}


def cond(c, then):
    return {"type": "minecraft:condition", "if_true": c, "then_run": then}


def seq(*rules):
    return {"type": "minecraft:sequence", "sequence": list(rules)}


def in_biome(*names):
    return {"type": "minecraft:biome", "biome_is": [f"deadcraft:{n}" for n in names]}


def floor(offset=0, add_depth=False):
    return {"type": "minecraft:stone_depth", "offset": offset, "surface_type": "floor", "add_surface_depth": add_depth,
            "secondary_depth_range": 0}


def ceiling(offset=0):
    return {"type": "minecraft:stone_depth", "offset": offset, "surface_type": "ceiling", "add_surface_depth": False,
            "secondary_depth_range": 0}


def bedrock_floor():
    return cond({"type": "minecraft:vertical_gradient", "random_name": "deadcraft:bedrock_floor",
                 "true_at_and_below": {"above_bottom": 0}, "false_at_and_above": {"above_bottom": 5}}, block("minecraft:bedrock"))


def bedrock_roof():
    return cond({"type": "minecraft:not", "invert": {"type": "minecraft:vertical_gradient", "random_name": "deadcraft:bedrock_roof",
                                                      "true_at_and_below": {"below_top": 5}, "false_at_and_above": {"below_top": 0}}},
                block("minecraft:bedrock"))


def noise_settings(name, min_y, height, default_block, default_fluid, sea, rtr, rule, size_v=2):
    out(f"deadcraft/worldgen/noise_settings/{name}.json", {
        "sea_level": sea, "disable_mob_generation": False, "aquifers_enabled": False, "ore_veins_enabled": False,
        "legacy_random_source": False, "default_block": {"Name": default_block}, "default_fluid": default_fluid,
        "noise": {"min_y": min_y, "height": height, "size_horizontal": 1, "size_vertical": size_v},
        "noise_router": rtr, "spawn_target": [], "surface_rule": rule})


def terrain():
    for n, (first, amps) in NOISES.items():
        out(f"deadcraft/worldgen/noise/{n}.json", {"firstOctave": first, "amplitudes": amps})
    temp = noise("climate_a", 0.5, 0.0)
    veg = noise("climate_b", 0.5, 0.0)

    # Hell's Paradise: a gutted cavern world. A floor and a ceiling of charnel stone, torn open by
    # vast caverns, with column-like pillars holding the roof up and a sea of blood below.
    hell = add(add(ygrad(0, 80, 2.0, -0.2), ygrad(160, 250, -0.2, 2.0)), mul(0.75, noise("hell_caverns", 1.0, 1.6)))
    pillars = add(-0.8, mul(1.5, noise("hell_pillars", 3.0, 0.04)))
    hell_final = interp(dmax(hell, pillars))
    hell_rule = seq(bedrock_floor(), bedrock_roof(),
                    cond(in_biome("scorched_forest"), seq(cond(floor(0), block("deadcraft:ash_soil")),
                                                          cond(floor(3), block("deadcraft:ash_soil")))),
                    cond(in_biome("bleeding_hollows"), seq(cond(floor(0), block("deadcraft:sinew_block")),
                                                           cond(floor(2), block("deadcraft:sinew_block")),
                                                           cond(ceiling(1), block("deadcraft:sinew_block")))))
    noise_settings("hells_paradise", 0, 256, "deadcraft:charnel_stone", {"Name": "deadcraft:blood", "Properties": {"level": "0"}},
                   48, router(hell_final, temp, veg), hell_rule)

    # Skyfall Torn: pale islands hanging in an empty sky, densest in a band between y 70 and 170.
    band = add(ygrad(30, 100, -1.8, 0.0), ygrad(130, 220, 0.0, -1.8))
    sky = add(add(add(mul(1.2, noise("sky_islands", 0.9, 1.8)), mul(0.35, noise("sky_clusters", 1.0, 0.0))), band),
              add(-0.38, mul(0.18, noise("sky_detail", 2.0, 2.0))))
    sky_rule = seq(cond(in_biome("floating_islands"), seq(cond(floor(0), block("deadcraft:pale_moss")))),
                   cond(in_biome("shroud_reach"), seq(cond(floor(0), block("deadcraft:shroud_mold")),
                                                      cond(floor(1), block("deadcraft:shroud_mold")))))
    noise_settings("skyfall_torn", 0, 256, "deadcraft:skystone", {"Name": "minecraft:air"}, 0,
                   router(interp(sky), temp, veg), sky_rule)

    # Light's Descent: a world of terraced mesas and sheer cliffs. A spline turns smooth noise
    # into flat steps separated by walls dozens of blocks high.
    out("deadcraft/worldgen/density_function/descent/cliffs.json", noise("descent_cliffs", 0.9, 0.0))
    steps = [(-1.0, -0.2), (-0.35, -0.16), (-0.22, 0.12), (0.05, 0.16), (0.16, 0.48), (0.42, 0.53), (0.52, 0.86), (1.0, 0.95)]
    terraces = {"type": "minecraft:spline", "spline": {"coordinate": "deadcraft:descent/cliffs", "points": [
        {"location": loc, "value": val, "derivative": 0.0} for loc, val in steps]}}
    descent = add(add(ygrad(-64, 192, 1.0, -1.0), terraces), mul(0.07, noise("descent_detail", 1.4, 1.4)))
    descent_rule = seq(
        bedrock_floor(),
        cond(in_biome("dark_plains"), seq(
            cond({"type": "minecraft:steep"}, block("deadcraft:umbral_stone")),
            cond(floor(0), block("deadcraft:gloom_grass")),
            cond(floor(3, True), block("deadcraft:grave_soil")))),
        cond(in_biome("deserted_cliffs"), seq(
            cond({"type": "minecraft:steep"}, block("deadcraft:dustrock")),
            cond(floor(0), block("deadcraft:bone_sand")),
            cond(floor(2, True), block("deadcraft:bone_sand")),
            cond({"type": "minecraft:y_above", "anchor": {"absolute": 20}, "surface_depth_multiplier": 0, "add_stone_depth": False},
                 block("deadcraft:dustrock")))))
    noise_settings("lights_descent", -64, 384, "deadcraft:umbral_stone", {"Name": "minecraft:water", "Properties": {"level": "0"}},
                   -64, router(interp(descent), temp, veg, continents="deadcraft:descent/cliffs",
                               initial=add(ygrad(-64, 192, 1.0, -1.0), terraces)), descent_rule)

    # Disposed Skies: the wreckage of a world. Thin shattered plates and needle spires adrift in void.
    plates = dmin(add(noise("void_sheets", 0.8, 1.6), -0.08), add(noise("void_mask", 0.45, 0.0), 0.18))
    spires = add(mul(1.6, noise("void_spires", 1.8, 0.05)), -0.95)
    vband = add(ygrad(20, 70, -1.5, 0.0), ygrad(170, 230, 0.0, -1.5))
    void = add(dmax(mul(1.4, plates), spires), vband)
    void_rule = seq(cond(in_biome("storm_field"), seq(cond(floor(0), block("deadcraft:static_crust")))),
                    cond(in_biome("glass_wastes"), seq(cond(floor(0), block("deadcraft:void_crystal")))))
    noise_settings("disposed_skies", 0, 256, "deadcraft:voidrock", {"Name": "minecraft:air"}, 0,
                   router(interp(void), temp, veg), void_rule)


def dimensions():
    base = {"ultrawarm": False, "natural": False, "coordinate_scale": 1.0, "piglin_safe": False, "bed_works": False,
            "respawn_anchor_works": False, "has_raids": False, "monster_spawn_block_light_limit": 0}
    types = {
        "hells_paradise": {**base, "ultrawarm": True, "coordinate_scale": 8.0, "has_skylight": False, "has_ceiling": True,
                           "ambient_light": 0.2, "fixed_time": 18000, "monster_spawn_light_level": 11,
                           "monster_spawn_block_light_limit": 15, "respawn_anchor_works": True, "logical_height": 256,
                           "min_y": 0, "height": 256, "infiniburn": "#minecraft:infiniburn_nether",
                           "effects": "deadcraft:hells_paradise"},
        "skyfall_torn": {**base, "has_skylight": True, "has_ceiling": False, "ambient_light": 0.08, "fixed_time": 6000,
                         "monster_spawn_light_level": 7, "logical_height": 256, "min_y": 0, "height": 256,
                         "infiniburn": "#minecraft:infiniburn_overworld", "effects": "deadcraft:skyfall_torn"},
        "lights_descent": {**base, "natural": True, "has_skylight": True, "has_ceiling": False, "ambient_light": 0.14,
                           "fixed_time": 13200, "monster_spawn_light_level": 7, "logical_height": 384, "min_y": -64,
                           "height": 384, "infiniburn": "#minecraft:infiniburn_overworld", "effects": "deadcraft:lights_descent"},
        "disposed_skies": {**base, "has_skylight": False, "has_ceiling": False, "ambient_light": 0.3, "fixed_time": 18000,
                           "monster_spawn_light_level": 7, "logical_height": 256, "min_y": 0, "height": 256,
                           "infiniburn": "#minecraft:infiniburn_end", "effects": "deadcraft:disposed_skies"},
    }
    for name, t in types.items():
        out(f"deadcraft/dimension_type/{name}.json", t)

    def p(temperature=0.0, humidity=0.0, continentalness=0.0, erosion=0.0, weirdness=0.0, depth=0.0, offset=0.0):
        return {"temperature": temperature, "humidity": humidity, "continentalness": continentalness, "erosion": erosion,
                "weirdness": weirdness, "depth": depth, "offset": offset}

    def two(a, b, by="temperature"):
        return {"type": "minecraft:multi_noise", "biomes": [
            {"biome": f"deadcraft:{a}", "parameters": p(**{by: [-1.0, 0.05]})},
            {"biome": f"deadcraft:{b}", "parameters": p(**{by: [0.05, 1.0]})}]}

    gens = {
        "hells_paradise": two("scorched_forest", "bleeding_hollows"),
        "skyfall_torn": two("floating_islands", "shroud_reach", "humidity"),
        "lights_descent": {"type": "minecraft:multi_noise", "biomes": [
            {"biome": "deadcraft:dark_plains", "parameters": p(continentalness=[-1.0, 0.1])},
            {"biome": "deadcraft:deserted_cliffs", "parameters": p(continentalness=[0.1, 1.0])}]},
        "disposed_skies": two("storm_field", "glass_wastes"),
    }
    for name, src in gens.items():
        out(f"deadcraft/dimension/{name}.json", {"type": f"deadcraft:{name}", "generator": {
            "type": "minecraft:noise", "settings": f"deadcraft:{name}", "biome_source": src}})


OVERWORLD_LAND = ["#minecraft:is_forest", "#minecraft:is_taiga", "#minecraft:is_savanna", "minecraft:plains",
                  "minecraft:sunflower_plains", "minecraft:snowy_plains", "minecraft:meadow", "minecraft:desert",
                  "minecraft:dark_forest"]


def structures():
    def st(name, kind, biomes_tag, adaptation, extra=None, overrides=None):
        s = {"type": "deadcraft:dead_structure", "biomes": biomes_tag, "step": "surface_structures",
             "terrain_adaptation": adaptation, "spawn_overrides": overrides or {}, "kind": kind}
        s.update(extra or {})
        out(f"deadcraft/worldgen/structure/{name}.json", s)

    def tag(name, values):
        out(f"deadcraft/tags/worldgen/biome/has_structure/{name}.json", {"values": values})
        return f"#deadcraft:has_structure/{name}"

    st("tomb", "tomb", tag("tomb", OVERWORLD_LAND + ["deadcraft:dark_plains"]), "none",
       {"boss": "deadcraft:petrified", "boss_chance": 0.2})
    st("graveyard", "graveyard", tag("graveyard", OVERWORLD_LAND + ["deadcraft:dark_plains", "deadcraft:floating_islands",
                                                                     "deadcraft:shroud_reach"]), "none")
    st("castle", "castle", tag("castle", ["minecraft:plains", "minecraft:meadow", "#minecraft:is_taiga", "minecraft:dark_forest",
                                          "minecraft:snowy_plains", "deadcraft:dark_plains"]), "beard_thin",
       {"boss": "deadcraft:skeleton", "boss_chance": 1.0})
    gw = tag("gateway", OVERWORLD_LAND)
    for i, dim in enumerate(("hells_paradise", "skyfall_torn", "lights_descent", "disposed_skies"), start=1):
        st(f"gateway_{dim}", "gateway", gw, "none", {"destination": i, "boss": "deadcraft:chronos", "boss_chance": 0.15})
    st("portal_hells_paradise", "portal", tag("portal_hells_paradise", ["deadcraft:scorched_forest", "deadcraft:bleeding_hollows"]), "none",
       {"destination": 0, "cavern": True, "boss": "deadcraft:dim_light_demon", "boss_chance": 0.3})
    st("portal_skyfall", "portal", tag("portal_skyfall", ["deadcraft:floating_islands", "deadcraft:shroud_reach"]), "none",
       {"destination": 0, "boss": "deadcraft:fracture", "boss_chance": 0.25})
    st("portal_lights_descent", "portal", tag("portal_lights_descent", ["deadcraft:dark_plains", "deadcraft:deserted_cliffs"]), "none",
       {"destination": 0, "boss": "deadcraft:chronos", "boss_chance": 0.2})
    st("portal_storm", "portal", tag("portal_storm", ["deadcraft:storm_field", "deadcraft:glass_wastes"]), "none",
       {"destination": 0, "boss": "deadcraft:fracture", "boss_chance": 0.3})
    city_spawns = {"monster": {"bounding_box": "full", "spawns": [
        {"type": "deadcraft:rotwalker", "weight": 30, "minCount": 2, "maxCount": 5},
        {"type": "deadcraft:bandit", "weight": 3, "minCount": 1, "maxCount": 2}]}}
    st("ruined_city", "ruined_city", tag("ruined_city", ["minecraft:plains", "minecraft:sunflower_plains", "minecraft:savanna",
                                                          "minecraft:desert", "minecraft:meadow", "deadcraft:dark_plains"]),
       "beard_thin", None, city_spawns)

    def sset(name, structs, spacing, separation, salt):
        out(f"deadcraft/worldgen/structure_set/{name}.json", {"structures": [{"structure": f"deadcraft:{s}", "weight": 1} for s in structs],
                                                              "placement": {"type": "minecraft:random_spread", "spacing": spacing,
                                                                            "separation": separation, "salt": salt}})

    sset("tombs", ["tomb"], 30, 10, 66613)
    sset("graveyards", ["graveyard"], 26, 9, 66614)
    sset("castles", ["castle"], 56, 22, 66615)
    sset("gateways", ["gateway_hells_paradise", "gateway_skyfall_torn", "gateway_lights_descent", "gateway_disposed_skies"], 40, 16, 66616)
    sset("portals", ["portal_hells_paradise", "portal_skyfall", "portal_lights_descent", "portal_storm"], 18, 6, 66617)
    sset("ruined_cities", ["ruined_city"], 60, 24, 66618)


def biome_modifiers():
    def mod(name, obj):
        # A HolderSet list may not mix tags and ids, so every list goes through a biome tag.
        if isinstance(obj.get("biomes"), list):
            out(f"deadcraft/tags/worldgen/biome/modifier/{name}.json", {"values": obj["biomes"]})
            obj["biomes"] = f"#deadcraft:modifier/{name}"
        out(f"deadcraft/neoforge/biome_modifier/{name}.json", obj)

    def spawns(lst):
        return [{"type": dc(t), "weight": w, "minCount": a, "maxCount": b} for t, w, a, b in lst]

    mod("overworld_monsters", {"type": "neoforge:add_spawns", "biomes": "#minecraft:is_overworld", "spawners": spawns([
        ("the_hollow", 3, 1, 1), ("severed", 6, 1, 2), ("cracked", 4, 1, 1), ("ghost", 5, 1, 2), ("parasite", 6, 2, 3),
        ("the_forgotten", 3, 1, 1), ("wretched", 4, 1, 1), ("illusion", 2, 1, 1), ("depart", 2, 1, 1), ("collapse", 2, 1, 1),
        ("blood_demon", 1, 1, 1), ("rotwalker", 6, 2, 4), ("butcher", 2, 1, 1), ("hanged_man", 3, 1, 1),
        ("lamplighter", 2, 1, 1)])})
    mod("swamp_monsters", {"type": "neoforge:add_spawns", "biomes": ["minecraft:swamp", "minecraft:mangrove_swamp"],
                           "spawners": spawns([("mire_hag", 6, 1, 1)])})
    mod("dry_monsters", {"type": "neoforge:add_spawns", "biomes": ["minecraft:desert", "#minecraft:is_badlands"],
                         "spawners": spawns([("gravewyrm", 5, 1, 1)])})
    mod("new_creatures", {"type": "neoforge:add_spawns", "biomes": ["#minecraft:is_forest", "#minecraft:is_taiga", "minecraft:plains"],
                          "spawners": spawns([("cryptling", 4, 1, 3)])})
    mod("goat_creatures", {"type": "neoforge:add_spawns", "biomes": "#minecraft:is_mountain",
                           "spawners": spawns([("ashen_goat", 6, 2, 3)])})
    mod("badlands_monsters", {"type": "neoforge:add_spawns", "biomes": ["#minecraft:is_badlands", "#minecraft:is_savanna", "minecraft:desert"],
                              "spawners": spawns([("bandit", 8, 2, 3), ("bandit_captain", 2, 1, 1), ("scorched", 5, 1, 2)])})
    mod("mountain_monsters", {"type": "neoforge:add_spawns", "biomes": "#minecraft:is_mountain", "spawners": spawns([("star_fall", 4, 1, 1)])})
    mod("forest_creatures", {"type": "neoforge:add_spawns", "biomes": ["#minecraft:is_forest", "#minecraft:is_taiga"], "spawners": spawns([
        ("ashgrazer", 6, 2, 3), ("veilfawn", 5, 1, 2), ("gloomhog", 6, 2, 4), ("duskhare", 5, 2, 3), ("hush_owl", 3, 1, 2),
        ("gravecat", 2, 1, 1), ("lantern_moth", 3, 1, 3), ("ember_fox", 2, 1, 2)])})
    mod("plains_creatures", {"type": "neoforge:add_spawns", "biomes": ["minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow"],
                             "spawners": spawns([("hollowfleece", 6, 2, 4), ("marrow_ox", 5, 2, 3), ("cinderhen", 6, 2, 4),
                                                 ("sorrowdove", 3, 2, 3), ("grimfinch", 3, 2, 3)])})
    mod("wet_creatures", {"type": "neoforge:add_spawns", "biomes": ["#minecraft:is_river", "minecraft:swamp", "minecraft:mangrove_swamp"],
                          "spawners": spawns([("candlewick_toad", 6, 1, 3), ("shellmourn", 5, 1, 2), ("mossback_tortoise", 4, 1, 2),
                                              ("wisplet", 3, 1, 2), ("lumen_beetle", 3, 2, 3)])})
    mod("mountain_creatures", {"type": "neoforge:add_spawns", "biomes": "#minecraft:is_mountain",
                               "spawners": spawns([("cragstrider", 6, 1, 3), ("stormstag", 3, 1, 2)])})
    mod("overworld_ores", {"type": "neoforge:add_features", "biomes": "#minecraft:is_overworld",
                           "features": ["deadcraft:ore_necrite", "deadcraft:ore_bloodstone"], "step": "underground_ores"})
    mod("overworld_graves", {"type": "neoforge:add_features", "biomes": ["#minecraft:is_forest", "#minecraft:is_taiga", "minecraft:plains"],
                             "features": ["deadcraft:grave_patch_rare"], "step": "vegetal_decoration"})


def main():
    if os.path.isdir(os.path.join(DATA, "deadcraft")):
        shutil.rmtree(os.path.join(DATA, "deadcraft"))
    if os.path.isdir(os.path.join(DATA, "minecraft")):
        shutil.rmtree(os.path.join(DATA, "minecraft"))
    loot()
    recipes()
    tags()
    enchantments()
    worldgen_features()
    biomes()
    terrain()
    dimensions()
    structures()
    landmark_loot()
    landmarks()
    biome_modifiers()
    print("data generated")


if __name__ == "__main__":
    main()
