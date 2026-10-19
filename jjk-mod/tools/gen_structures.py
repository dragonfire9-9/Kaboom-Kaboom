"""Builds the mod's structure templates (data/jjk/structure/*.nbt) plus their worldgen JSON
(structure, template pool, structure set). Chest loot tables and biome tags come from Java datagen."""
import json, math, os, random, sys
sys.path.insert(0, os.path.dirname(__file__))
from nbtlib_min import Byte, Int, IntList, DoubleList, write

R = "src/main/resources"
DATA_VERSION = 3955  # 1.21.1

class S:
    def __init__(self, name, sx, sy, sz):
        self.name, self.size = name, (sx, sy, sz)
        self.blocks, self.entities = {}, []
        self.rnd = random.Random(name)

    def inb(self, x, y, z):
        return 0 <= x < self.size[0] and 0 <= y < self.size[1] and 0 <= z < self.size[2]

    def set(self, x, y, z, name, props=None, nbt=None):
        if self.inb(x, y, z):
            self.blocks[(x, y, z)] = (name if ":" in name else "minecraft:" + name, tuple(sorted((props or {}).items())), nbt)

    def fill(self, x0, y0, z0, x1, y1, z1, name, props=None):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, name, props)

    def box(self, x0, y0, z0, x1, y1, z1, wall, floor=None, roof=None, inside="air"):
        self.fill(x0, y0, z0, x1, y1, z1, wall)
        self.fill(x0 + 1, y0 + 1, z0 + 1, x1 - 1, y1 - 1, z1 - 1, inside)
        if floor: self.fill(x0, y0, z0, x1, y0, z1, floor)
        if roof: self.fill(x0, y1, z0, x1, y1, z1, roof)

    def weathered(self, chance, name="air", y_min=1):
        """Randomly break blocks (ruins)."""
        for k in list(self.blocks):
            if k[1] >= y_min and self.blocks[k][0] != "minecraft:air" and self.rnd.random() < chance:
                self.set(*k, name)

    def roof(self, x0, z0, x1, z1, y, block="deepslate_tile_slab", trim="dark_oak_slab", layers=4):
        for i in range(layers):
            self.fill(x0 - 1 + i, y + i, z0 - 1 + i, x1 + 1 - i, y + i, z1 + 1 - i, block, {"type": "bottom"} if "slab" in block else None)
            for x in range(x0 - 1 + i, x1 + 2 - i):
                for z in (z0 - 1 + i, z1 + 1 - i):
                    self.set(x, y + i, z, trim, {"type": "bottom"} if "slab" in trim else None)
        self.fill(x0 + layers - 1, y + layers, z0 + layers - 1, x1 - layers + 1, y + layers, z1 - layers + 1, block.replace("_slab", "s") if "tile_slab" in block else block)

    def torii(self, cx, y, z, height=7, width=7):
        for dx in (-width // 2, width // 2):
            self.fill(cx + dx, y, z, cx + dx, y + height - 1, z, "red_concrete")
        self.fill(cx - width // 2 - 1, y + height - 2, z, cx + width // 2 + 1, y + height - 2, z, "red_concrete")
        self.fill(cx - width // 2 - 2, y + height, z, cx + width // 2 + 2, y + height, z, "blackstone_slab", {"type": "bottom"})
        self.fill(cx - width // 2 - 1, y + height - 1, z, cx + width // 2 + 1, y + height - 1, z, "red_concrete")

    def chest(self, x, y, z, loot, facing="north"):
        self.set(x, y, z, "chest", {"facing": facing, "type": "single", "waterlogged": "false"},
                 {"id": "minecraft:chest", "LootTable": f"jjk:chests/{loot}"})

    def spawner(self, x, y, z, entity, delay=200):
        self.set(x, y, z, "spawner", None, {"id": "minecraft:mob_spawner", "SpawnData": {"entity": {"id": entity}},
                                             "Delay": Int(delay), "MinSpawnDelay": Int(300), "MaxSpawnDelay": Int(700),
                                             "SpawnCount": Int(2), "MaxNearbyEntities": Int(5), "RequiredPlayerRange": Int(20)})

    def entity(self, x, y, z, eid, extra=None):
        nbt = {"id": eid, "PersistenceRequired": Byte(1)}
        nbt.update(extra or {})
        self.entities.append({"pos": DoubleList([x + 0.5, y, z + 0.5]), "blockPos": IntList([x, y, z]), "nbt": nbt})

    def save(self):
        palette, index, blocks = [], {}, []
        for (x, y, z), (name, props, nbt) in sorted(self.blocks.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
            key = (name, props)
            if key not in index:
                index[key] = len(palette)
                entry = {"Name": name}
                if props: entry["Properties"] = dict(props)
                palette.append(entry)
            b = {"pos": IntList([x, y, z]), "state": Int(index[key])}
            if nbt: b["nbt"] = nbt
            blocks.append(b)
        os.makedirs(f"{R}/data/jjk/structure", exist_ok=True)
        write(f"{R}/data/jjk/structure/{self.name}.nbt", {
            "DataVersion": Int(DATA_VERSION), "size": IntList(list(self.size)),
            "palette": palette, "blocks": blocks, "entities": self.entities})
        return self

# ---------------------------------------------------------------- designs
def campus(name, wall, frame, roof, path, npcs, chest_loot):
    W, H, D = 41, 18, 41
    s = S(name, W, H, D)
    s.fill(0, 0, 0, W - 1, 0, D - 1, "grass_block", {"snowy": "false"})
    s.fill(0, 1, 0, W - 1, H - 1, D - 1, "air")
    # perimeter wall with cap
    for x in range(W):
        for z in (0, D - 1):
            s.fill(x, 1, z, x, 3, z, wall); s.set(x, 4, z, frame + "_slab", {"type": "bottom"})
    for z in range(D):
        for x in (0, W - 1):
            s.fill(x, 1, z, x, 3, z, wall); s.set(x, 4, z, frame + "_slab", {"type": "bottom"})
    s.fill(18, 1, 0, 22, 4, 0, "air")                     # south gate
    s.torii(20, 1, 3)
    s.fill(19, 0, 0, 21, 0, 30, path)                     # main path
    s.fill(8, 0, 20, 32, 0, 21, path)
    # main hall
    s.box(13, 0, 26, 27, 7, 37, wall, floor=frame + "_planks")
    for (x, z) in ((13, 26), (27, 26), (13, 37), (27, 37)): s.fill(x, 1, z, x, 7, z, frame + "_log", {"axis": "y"})
    s.fill(19, 1, 26, 21, 3, 26, "air")
    s.roof(13, 26, 27, 37, 8, roof)
    s.set(20, 1, 35, "lectern", {"facing": "south", "has_book": "false", "powered": "false"})
    s.chest(24, 1, 35, chest_loot)
    # dormitory (two floors)
    s.box(3, 0, 6, 12, 10, 16, wall, floor=frame + "_planks")
    s.fill(3, 5, 6, 12, 5, 16, frame + "_planks"); s.fill(4, 5, 7, 5, 5, 8, "air")
    s.fill(7, 1, 16, 8, 2, 16, "air")
    for y in (2, 7):
        for z in (9, 13): s.set(3, y, z, "glass_pane"); s.set(12, y, z, "glass_pane")
    s.roof(3, 6, 12, 16, 11, roof, layers=3)
    # training ground with targets
    s.fill(26, 0, 6, 37, 0, 17, "coarse_dirt")
    for (x, z) in ((28, 8), (32, 8), (36, 8)):
        s.set(x, 1, z, "hay_block", {"axis": "y"}); s.set(x, 2, z, "target", {"power": "0"})
    for (x, z) in ((28, 15), (35, 15)): s.set(x, 1, z, "stone_brick_wall", {"east": "none", "north": "none", "south": "none", "up": "true", "waterlogged": "false", "west": "none"})
    # lanterns & cherry trees
    for (x, z) in ((17, 6), (23, 6), (17, 18), (23, 18)):
        s.set(x, 1, z, "stone_brick_wall", {"east": "none", "north": "none", "south": "none", "up": "true", "waterlogged": "false", "west": "none"})
        s.set(x, 2, z, "lantern", {"hanging": "false", "waterlogged": "false"})
    for (x, z) in ((6, 30), (34, 30)):
        s.fill(x, 1, z, x, 4, z, "cherry_log", {"axis": "y"})
        s.fill(x - 2, 5, z - 2, x + 2, 6, z + 2, "cherry_leaves", {"distance": "1", "persistent": "true", "waterlogged": "false"})
    for i, (eid, x, z) in enumerate(npcs): s.entity(x, 1, z, eid)
    return s.save()

def shibuya(variant, boss):
    W, H, D = 33, 14, 33
    s = S(f"shibuya_station_{variant}", W, H, D)
    s.fill(0, 0, 0, W - 1, 0, D - 1, "polished_andesite")
    s.fill(0, 1, 0, W - 1, H - 1, D - 1, "air")
    s.box(0, 0, 0, W - 1, 9, D - 1, "gray_concrete", floor="smooth_stone", roof="gray_concrete")
    for x in range(4, W - 4, 6):
        for z in range(4, D - 4, 6):
            s.fill(x, 1, z, x, 8, z, "stone_bricks")
    # platforms and tracks
    s.fill(2, 1, 13, W - 3, 1, 19, "smooth_quartz")
    s.fill(2, 0, 9, W - 3, 0, 11, "gravel"); s.fill(2, 0, 21, W - 3, 0, 23, "gravel")
    for x in range(2, W - 2): s.set(x, 1, 10, "rail", {"shape": "east_west", "waterlogged": "false"}); s.set(x, 1, 22, "rail", {"shape": "east_west", "waterlogged": "false"})
    s.fill(15, 1, 0, 17, 4, 0, "air")  # entrance
    s.fill(15, 9, 0, 17, 9, 3, "air")
    s.weathered(0.12, "air", 2)
    s.weathered(0.08, "cracked_stone_bricks", 1)
    for (x, z) in ((6, 6), (26, 26), (6, 26), (26, 6)): s.spawner(x, 1, z, "jjk:fly_head")
    s.spawner(16, 1, 28, "jjk:roppongi_curse")
    s.set(16, 0, 16, "sculk"); s.entity(16, 2, 16, f"jjk:{boss}")
    s.chest(30, 2, 16, "shibuya_station", "west")
    return s.save()

def school():
    s = S("abandoned_school", 19, 12, 15)
    s.box(0, 0, 0, 18, 10, 14, "white_terracotta", floor="oak_planks", roof="light_gray_concrete")
    s.fill(0, 5, 0, 18, 5, 14, "oak_planks"); s.fill(2, 5, 2, 3, 5, 3, "air")
    for x in range(3, 16, 4):
        for y in (2, 7): s.set(x, y, 0, "glass_pane"); s.set(x, y, 14, "glass_pane")
    for x in range(4, 15, 3):
        for z in (4, 8, 11): s.set(x, 1, z, "oak_stairs", {"facing": "north", "half": "bottom", "shape": "straight", "waterlogged": "false"})
    s.fill(8, 1, 0, 10, 3, 0, "air")
    s.weathered(0.10)
    s.spawner(9, 1, 7, "jjk:fly_head"); s.spawner(9, 6, 7, "jjk:roppongi_curse")
    s.chest(16, 6, 12, "abandoned_ruins", "west")
    return s.save()

def hospital():
    s = S("abandoned_hospital", 21, 10, 17)
    s.box(0, 0, 0, 20, 8, 16, "white_concrete", floor="smooth_quartz", roof="white_concrete")
    for x in range(1, 20, 5): s.fill(x, 1, 8, x, 7, 8, "white_concrete")
    s.fill(1, 1, 8, 19, 3, 8, "air")
    for x in range(2, 19, 4):
        s.set(x, 1, 2, "white_bed", {"facing": "south", "occupied": "false", "part": "head"})
        s.set(x, 1, 3, "white_bed", {"facing": "south", "occupied": "false", "part": "foot"})
    s.fill(9, 1, 0, 11, 3, 0, "air")
    s.weathered(0.12)
    s.spawner(5, 1, 12, "jjk:fly_head"); s.spawner(15, 1, 12, "jjk:smallpox_deity", 600)
    s.chest(18, 1, 14, "abandoned_ruins", "west")
    return s.save()

def tunnel():
    s = S("cursed_tunnel", 9, 8, 33)
    s.fill(0, 0, 0, 8, 7, 32, "stone_bricks")
    s.fill(1, 1, 0, 7, 5, 32, "air")
    for z in range(0, 33, 4): s.fill(1, 6, z, 7, 6, z, "mossy_stone_bricks")
    for z in range(4, 30, 8): s.set(1, 3, z, "soul_lantern", {"hanging": "false", "waterlogged": "false"})
    s.weathered(0.06, "cracked_stone_bricks")
    s.spawner(4, 1, 10, "jjk:roppongi_curse"); s.spawner(4, 1, 22, "jjk:fly_head")
    s.chest(6, 1, 30, "abandoned_ruins", "west")
    return s.save()

def shrine():
    s = S("sukuna_shrine", 15, 14, 15)
    s.fill(0, 0, 0, 14, 0, 14, "polished_blackstone")
    s.fill(0, 1, 0, 14, 13, 14, "air")
    for (x, z) in ((2, 2), (12, 2), (2, 12), (12, 12)):
        s.fill(x, 1, z, x, 7, z, "red_nether_bricks")
        s.set(x, 8, z, "skeleton_skull", {"rotation": "0"})
    s.fill(4, 1, 4, 10, 1, 10, "polished_blackstone_bricks")
    s.fill(5, 2, 5, 9, 2, 9, "red_nether_brick_slab", {"type": "bottom", "waterlogged": "false"})
    s.roof(3, 3, 11, 11, 9, "blackstone_slab", "red_nether_brick_slab", 3)
    s.chest(7, 3, 7, "sukuna_shrine")
    for (x, z) in ((7, 3), (3, 7), (11, 7)): s.set(x, 1, z, "bone_block", {"axis": "y"})
    s.spawner(7, 1, 12, "jjk:fly_head")
    s.torii(7, 1, 0, 6, 7)
    return s.save()

def detention():
    s = S("curse_womb_detention_center", 23, 12, 19)
    s.box(0, 0, 0, 22, 10, 18, "gray_concrete", floor="polished_andesite", roof="gray_concrete")
    for x in range(2, 21, 5):
        s.fill(x, 1, 12, x, 5, 17, "iron_block")
        s.fill(x + 1, 1, 12, x + 3, 4, 12, "iron_bars", {"east": "true", "west": "true", "north": "false", "south": "false", "waterlogged": "false"})
    s.fill(10, 1, 0, 12, 3, 0, "air")
    s.spawner(5, 1, 15, "jjk:fly_head"); s.spawner(15, 1, 15, "jjk:finger_bearer", 800)
    s.entity(6, 1, 5, "jjk:curse_user"); s.entity(16, 1, 5, "jjk:curse_user")
    s.chest(20, 1, 2, "detention_center", "west")
    return s.save()

def culling_game():
    W = 37; c = W // 2
    s = S("culling_game_colony", W, 22, W)
    for x in range(W):
        for z in range(W):
            d = math.hypot(x - c, z - c)
            if c - 1 <= d <= c:
                s.fill(x, 0, z, x, 20, z, "black_stained_glass")
            elif d < c - 1:
                s.set(x, 0, z, "cracked_deepslate_bricks" if s.rnd.random() < 0.3 else "deepslate_bricks")
    s.fill(c, 1, c, c, 6, c, "obsidian"); s.set(c, 7, c, "crying_obsidian")
    s.chest(c + 1, 1, c, "culling_game", "east")
    for (dx, dz) in ((6, 0), (-6, 3), (0, -7)): s.entity(c + dx, 1, c + dz, "jjk:curse_user")
    s.spawner(c - 8, 1, c - 8, "jjk:roppongi_curse"); s.spawner(c + 8, 1, c + 8, "jjk:fly_head")
    return s.save()

# ---------------------------------------------------------------- worldgen JSON
def w(path, obj):
    p = os.path.join(R, path); os.makedirs(os.path.dirname(p), exist_ok=True)
    json.dump(obj, open(p, "w"), indent=2)

def register(sid, templates, spacing, separation, salt, step="surface_structures", underground=False, adapt="beard_thin"):
    w(f"data/jjk/worldgen/template_pool/{sid}/start.json", {"fallback": "minecraft:empty", "elements": [
        {"weight": 1, "element": {"element_type": "minecraft:single_pool_element", "location": f"jjk:{t}",
                                  "processors": "minecraft:empty", "projection": "rigid"}} for t in templates]})
    w(f"data/jjk/worldgen/structure/{sid}.json", {
        "type": "minecraft:jigsaw", "biomes": f"#jjk:has_structure/{sid}", "step": step,
        "spawn_overrides": {}, "terrain_adaptation": adapt, "start_pool": f"jjk:{sid}/start", "size": 1,
        "start_height": {"absolute": -14 if underground else 0},
        "project_start_to_heightmap": "WORLD_SURFACE_WG", "max_distance_from_center": 80, "use_expansion_hack": False})
    w(f"data/jjk/worldgen/structure_set/{sid}.json", {"structures": [{"structure": f"jjk:{sid}", "weight": 1}],
        "placement": {"type": "minecraft:random_spread", "spacing": spacing, "separation": separation, "salt": salt}})

if __name__ == "__main__":
    campus("tokyo_jujutsu_high", "white_terracotta", "dark_oak", "deepslate_tile_slab", "stone_bricks",
           [("jjk:gojo", 20, 22), ("jjk:yaga", 20, 32), ("jjk:yuji", 30, 12), ("jjk:megumi", 33, 12),
            ("jjk:nobara", 9, 20), ("jjk:maki", 34, 16), ("jjk:panda", 26, 20), ("jjk:inumaki", 15, 20)], "jujutsu_high")
    campus("kyoto_jujutsu_high", "stone_bricks", "spruce", "mud_brick_slab", "gravel",
           [("jjk:nanami", 20, 22), ("jjk:yaga", 20, 32), ("jjk:maki", 30, 12), ("jjk:panda", 26, 20)], "jujutsu_high")
    for v, boss in (("jogo", "jogo"), ("mahito", "mahito"), ("kenjaku", "kenjaku")): shibuya(v, boss)
    school(); hospital(); tunnel(); shrine(); detention(); culling_game()

    register("tokyo_jujutsu_high", ["tokyo_jujutsu_high"], 64, 32, 20391011)
    register("kyoto_jujutsu_high", ["kyoto_jujutsu_high"], 72, 36, 20391012)
    register("shibuya_station_ruins", ["shibuya_station_jogo", "shibuya_station_mahito", "shibuya_station_kenjaku"], 56, 28, 20391013)
    register("abandoned_school", ["abandoned_school"], 28, 12, 20391014)
    register("abandoned_hospital", ["abandoned_hospital"], 32, 14, 20391015)
    register("cursed_tunnel", ["cursed_tunnel"], 30, 12, 20391016, step="underground_structures", underground=True, adapt="bury")
    register("sukuna_shrine", ["sukuna_shrine"], 80, 40, 20391017)
    register("curse_womb_detention_center", ["curse_womb_detention_center"], 48, 20, 20391018)
    register("culling_game_colony", ["culling_game_colony"], 64, 30, 20391019)

    hotspots = ["shibuya_station_ruins", "abandoned_school", "abandoned_hospital", "cursed_tunnel",
                "curse_womb_detention_center", "culling_game_colony", "sukuna_shrine"]
    w("data/jjk/tags/worldgen/structure/negative_emotion_hotspots.json", {"replace": False, "values": [f"jjk:{h}" for h in hotspots]})
    print("structures written")
