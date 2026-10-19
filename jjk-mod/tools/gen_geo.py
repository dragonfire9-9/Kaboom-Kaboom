"""Generates GeckoLib (Bedrock 1.12) geometry, animations and box-UV textures for the mod's creatures.

Each creature is described as bones made of cubes. Textures are painted procedurally per cube face
(base colour + noise, optional eyes/mouth on the front face), so models and textures always line up.
Replace any output with Blockbench work later; keep bone names if you keep the animations.

Output (per creature id):
  assets/jjk/geo/entity/<id>.geo.json
  assets/jjk/animations/entity/<id>.animation.json
  assets/jjk/textures/entity/<id>.png
"""
import json, math, os, random
from PIL import Image, ImageDraw

ROOT = "src/main/resources/assets/jjk"
TEX = 128

class Model:
    def __init__(self, name):
        self.name = name
        self.bones = []
        self.cubes = []  # (bone, cube dict, colour, features)
        self.cursor = [0, 0, 0]  # shelf packer: x, y, shelf height

    def bone(self, name, pivot, parent=None, rotation=None):
        b = {"name": name, "pivot": pivot}
        if parent: b["parent"] = parent
        if rotation: b["rotation"] = rotation
        b["cubes"] = []
        self.bones.append(b)
        return b

    def _alloc(self, w, h, d):
        uw, uh = 2 * d + 2 * w, d + h
        x, y, shelf = self.cursor
        if x + uw > TEX:
            x, y, shelf = 0, y + shelf, 0
        if y + uh > TEX:
            raise ValueError(f"{self.name}: texture atlas full")
        self.cursor = [x + uw, y, max(shelf, uh)]
        return x, y

    def cube(self, bone, origin, size, colour, features=(), inflate=0):
        w, h, d = size
        u, v = self._alloc(math.ceil(w), math.ceil(h), math.ceil(d))
        c = {"origin": origin, "size": size, "uv": [u, v]}
        if inflate: c["inflate"] = inflate
        bone["cubes"].append(c)
        self.cubes.append((c, colour, features))

    def write(self, anims):
        bones = [{k: v for k, v in b.items() if k != "cubes" or v} for b in self.bones]
        geo = {"format_version": "1.12.0", "minecraft:geometry": [{
            "description": {"identifier": f"geometry.{self.name}", "texture_width": TEX, "texture_height": TEX,
                            "visible_bounds_width": 6, "visible_bounds_height": 6, "visible_bounds_offset": [0, 2, 0]},
            "bones": bones}]}
        os.makedirs(f"{ROOT}/geo/entity", exist_ok=True)
        json.dump(geo, open(f"{ROOT}/geo/entity/{self.name}.geo.json", "w"), indent=1)
        os.makedirs(f"{ROOT}/animations/entity", exist_ok=True)
        json.dump({"format_version": "1.8.0", "animations": dict(anims),
                   "geckolib_format_version": 2}, open(f"{ROOT}/animations/entity/{self.name}.animation.json", "w"), indent=1)
        self.paint()

    def paint(self):
        rnd = random.Random(self.name)
        im = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
        d = ImageDraw.Draw(im)
        for c, colour, feats in self.cubes:
            w, h, dd = (math.ceil(x) for x in c["size"])
            u, v = c["uv"]
            faces = {  # name: (x, y, w, h, shade)
                "top": (u + dd, v, w, dd, 1.15), "bottom": (u + dd + w, v, w, dd, 0.6),
                "east": (u, v + dd, dd, h, 0.8), "front": (u + dd, v + dd, w, h, 1.0),
                "west": (u + dd + w, v + dd, dd, h, 0.8), "back": (u + 2 * dd + w, v + dd, w, h, 0.7)}
            for name, (x, y, fw, fh, shade) in faces.items():
                for yy in range(fh):
                    for xx in range(fw):
                        n = rnd.randint(-14, 14)
                        grad = 1 - 0.25 * (yy / max(1, fh))
                        col = tuple(max(0, min(255, int(ch * shade * grad) + n)) for ch in colour[:3]) + (255,)
                        im.putpixel((x + xx, y + yy), col)
            fx, fy, fw, fh, _ = faces["front"]
            for feat in feats:
                if feat == "eyes" and fw >= 4 and fh >= 3:
                    ey = fy + fh // 3
                    for ex in (fx + fw // 4, fx + fw - 1 - fw // 4):
                        d.rectangle([ex - 1 if fw > 8 else ex, ey, ex, ey + (1 if fh > 6 else 0)], fill=(250, 240, 120, 255))
                        im.putpixel((ex, ey), (20, 0, 0, 255))
                elif feat == "many_eyes":
                    for _ in range(max(3, fw * fh // 10)):
                        ex, ey = fx + rnd.randrange(fw), fy + rnd.randrange(max(1, fh * 2 // 3))
                        im.putpixel((ex, ey), (255, 250, 200, 255))
                        if ex + 1 < fx + fw: im.putpixel((ex + 1, ey), (30, 0, 0, 255))
                elif feat == "mouth" and fw >= 4:
                    my = fy + fh * 2 // 3
                    d.rectangle([fx + 1, my, fx + fw - 2, min(fy + fh - 1, my + max(1, fh // 6))], fill=(60, 0, 10, 255))
                    for tx in range(fx + 1, fx + fw - 1, 2): im.putpixel((tx, my), (240, 235, 210, 255))
                elif feat == "stripes":
                    for yy in range(0, fh, 3): d.line([(fx, fy + yy), (fx + fw - 1, fy + yy)], fill=(20, 20, 20, 255))
                elif feat == "buttons":
                    for yy in range(fy + 1, fy + fh, 3): im.putpixel((fx + fw // 2, yy), (220, 190, 80, 255))
                elif feat == "tie":
                    d.rectangle([fx + fw // 2 - 1, fy, fx + fw // 2, fy + fh * 2 // 3], fill=(40, 70, 160, 255))
                    d.line([(fx + fw // 2 - 2, fy), (fx + fw // 2 - 3, fy + 2)], fill=(240, 240, 235, 255))
                    d.line([(fx + fw // 2 + 1, fy), (fx + fw // 2 + 2, fy + 2)], fill=(240, 240, 235, 255))
                elif feat == "sash":
                    for i in range(max(fw, fh)): 
                        xx, yy = fx + i * fw // max(fw, fh), fy + i * fh // max(fw, fh)
                        d.rectangle([xx, yy, xx + 1, yy + 1], fill=(210, 150, 40, 255))
                elif feat == "blindfold":
                    d.rectangle([fx, fy + fh * 3 // 8, fx + fw - 1, fy + fh * 5 // 8], fill=(15, 15, 18, 255))
                elif feat == "band":
                    d.rectangle([fx, fy + fh // 2, fx + fw - 1, fy + fh // 2 + 1], fill=(30, 30, 35, 255))
                elif feat == "patchwork":
                    for _ in range(4):
                        px0, py0 = fx + rnd.randrange(max(1, fw - 2)), fy + rnd.randrange(max(1, fh - 2))
                        col = rnd.choice([(150, 90, 80), (90, 120, 150), (170, 160, 120), (110, 90, 130)])
                        d.rectangle([px0, py0, px0 + 2, py0 + 2], fill=col + (255,))
                        d.point((px0 + 1, py0 + 3), fill=(30, 20, 20, 255))
                elif feat == "glow":
                    d.rectangle([fx + fw // 3, fy + fh // 3, fx + fw * 2 // 3, fy + fh * 2 // 3], fill=(255, 255, 220, 255))
        os.makedirs(f"{ROOT}/textures/entity", exist_ok=True)
        im.save(f"{ROOT}/textures/entity/{self.name}.png")

# ---------------------------------------------------------------- animation helpers
def kf(values, length):
    """values: list of [x,y,z] evenly spaced over length (first == last for loops)."""
    n = len(values) - 1
    return {f"{length * i / n:.2f}": v for i, v in enumerate(values)}

def loop(length, bones):
    return {"loop": True, "animation_length": length, "bones": bones}

def once(length, bones):
    return {"animation_length": length, "bones": bones}

def swing(amount, length, axis=0, phase=1):
    a = [0, 0, 0]; b = [0, 0, 0]; a[axis] = amount * phase; b[axis] = -amount * phase
    return {"rotation": kf([[0, 0, 0], a, [0, 0, 0], b, [0, 0, 0]], length)}

def bob(amount, length):
    return {"position": kf([[0, 0, 0], [0, amount, 0], [0, 0, 0]], length)}

def humanoid_walk(length=1.0, arm="arm", leg="leg", amt=30):
    return {f"{arm}_l": swing(amt, length, 0, 1), f"{arm}_r": swing(amt, length, 0, -1),
            f"{leg}_l": swing(amt, length, 0, -1), f"{leg}_r": swing(amt, length, 0, 1)}

def attack_arms(arm="arm"):
    return once(0.5, {f"{arm}_r": {"rotation": kf([[0, 0, 0], [-110, 10, 0], [20, 0, 0], [0, 0, 0]], 0.5)},
                      f"{arm}_l": {"rotation": kf([[0, 0, 0], [-80, -10, 0], [10, 0, 0], [0, 0, 0]], 0.5)}})

def humanoid(m, body, head, arm, leg, cols, head_feats=("eyes", "mouth"), arm_len=None, extra=None):
    """Standard biped: legs at y=0..leg_h, body above, head on top."""
    bw, bh, bd = body; hw, hh, hd = head; aw, ah, ad = arm; lw, lh, ld = leg
    root = m.bone("root", [0, 0, 0])
    m.bone("body", [0, lh, 0], "root"); b = m.bones[-1]
    m.cube(b, [-bw / 2, lh, -bd / 2], [bw, bh, bd], cols["body"])
    hb = m.bone("head", [0, lh + bh, 0], "body")
    m.cube(hb, [-hw / 2, lh + bh, -hd / 2], [hw, hh, hd], cols["head"], head_feats)
    for side, sx in (("l", 1), ("r", -1)):
        ab = m.bone(f"arm_{side}", [sx * (bw / 2 + aw / 2), lh + bh - 2, 0], "body")
        m.cube(ab, [sx * (bw / 2 + aw / 2) - aw / 2, lh + bh - ah, -ad / 2], [aw, ah, ad], cols["arm"])
        lb = m.bone(f"leg_{side}", [sx * lw / 2 * 1.1, lh, 0], "root")
        m.cube(lb, [sx * lw / 2 * 1.1 - lw / 2, 0, -ld / 2], [lw, lh, ld], cols["leg"])
    if extra: extra(m)

# ================================================================ curses
def fly_head():
    m = Model("fly_head")
    root = m.bone("root", [0, 8, 0])
    head = m.bone("head", [0, 8, 0], "root")
    m.cube(head, [-5, 3, -5], [10, 10, 10], (120, 140, 90), ("many_eyes", "mouth"))
    for side, sx in (("l", 1), ("r", -1)):
        wb = m.bone(f"wing_{side}", [sx * 5, 11, 0], "head")
        m.cube(wb, [sx * 5 if sx > 0 else -15, 11, -4], [10, 1, 8], (190, 210, 220))
    for i, x in enumerate((-3, 0, 3)):
        lb = m.bone(f"leg_{i}", [x, 3, 0], "head")
        m.cube(lb, [x - 0.5, -2, -0.5], [1, 5, 1], (60, 50, 40))
    anims = {
        "idle": loop(0.4, {"wing_l": {"rotation": kf([[0, 0, -30], [0, 0, 30], [0, 0, -30]], 0.2)},
                           "wing_r": {"rotation": kf([[0, 0, 30], [0, 0, -30], [0, 0, 30]], 0.2)},
                           "head": bob(1.5, 0.4)}),
        "walk": loop(0.2, {"wing_l": {"rotation": kf([[0, 0, -40], [0, 0, 40], [0, 0, -40]], 0.1)},
                           "wing_r": {"rotation": kf([[0, 0, 40], [0, 0, -40], [0, 0, 40]], 0.1)}}),
        "attack": once(0.4, {"head": {"rotation": kf([[0, 0, 0], [-35, 0, 0], [0, 0, 0]], 0.4),
                                      "position": kf([[0, 0, 0], [0, 0, -4], [0, 0, 0]], 0.4)}}),
    }
    m.write(anims)

def roppongi_curse():
    m = Model("roppongi_curse")
    humanoid(m, (10, 12, 6), (12, 10, 10), (4, 20, 4), (4, 10, 4),
             {"body": (90, 100, 80), "head": (110, 120, 95), "arm": (80, 90, 70), "leg": (70, 75, 60)},
             ("many_eyes", "mouth"))
    m.write({"idle": loop(2, {"head": swing(5, 2, 2), "body": bob(0.5, 2)}),
             "walk": loop(0.8, humanoid_walk(0.8, amt=35)),
             "attack": attack_arms()})

def finger_bearer():
    m = Model("finger_bearer")
    humanoid(m, (20, 18, 14), (14, 12, 12), (6, 18, 6), (7, 12, 7),
             {"body": (200, 150, 140), "head": (210, 160, 150), "arm": (190, 140, 130), "leg": (170, 120, 110)},
             ("eyes", "mouth"),
             extra=lambda m: [m.cube(m.bones[2], [x, 12 + 18 + 12, -1], [2, 7, 2], (230, 200, 180)) for x in (-5, -2, 1, 4)])
    m.write({"idle": loop(2, {"body": bob(1, 2)}),
             "walk": loop(1.2, humanoid_walk(1.2, amt=25)),
             "attack": attack_arms()})

def rainbow_dragon():
    m = Model("rainbow_dragon")
    root = m.bone("root", [0, 10, 0])
    head = m.bone("head", [0, 10, -8], "root")
    m.cube(head, [-5, 6, -22], [10, 8, 14], (230, 230, 240), ("eyes", "mouth"))
    m.cube(head, [-4, 14, -16], [2, 5, 2], (250, 220, 120)); m.cube(head, [2, 14, -16], [2, 5, 2], (250, 220, 120))
    rainbow = [(230, 60, 60), (240, 150, 50), (240, 220, 60), (80, 200, 90), (70, 140, 230), (110, 80, 210), (200, 90, 200), (230, 60, 60)]
    parent, z = "root", -8
    for i, col in enumerate(rainbow):
        b = m.bone(f"seg_{i}", [0, 10, z], parent)
        m.cube(b, [-4, 6, z], [8, 8, 8], col, ("stripes",))
        parent, z = f"seg_{i}", z + 8
    wave = lambda amp, ph: {"rotation": kf([[0, 0, 0], [0, amp * ph, 0], [0, 0, 0], [0, -amp * ph, 0], [0, 0, 0]], 1.6)}
    m.write({"idle": loop(1.6, {f"seg_{i}": wave(10, 1 if i % 2 else -1) for i in range(8)}),
             "walk": loop(1.6, {**{f"seg_{i}": wave(18, 1 if i % 2 else -1) for i in range(8)}, "head": wave(8, 1)}),
             "attack": once(0.6, {"head": {"rotation": kf([[0, 0, 0], [25, 0, 0], [-15, 0, 0], [0, 0, 0]], 0.6)}})})

def smallpox_deity():
    m = Model("smallpox_deity")
    humanoid(m, (14, 20, 10), (12, 12, 12), (5, 18, 5), (6, 12, 6),
             {"body": (210, 205, 190), "head": (225, 220, 210), "arm": (200, 195, 180), "leg": (180, 175, 160)},
             ("many_eyes",),
             extra=lambda m: (m.bone("coffin", [0, 28, 10], "root"),
                              m.cube(m.bones[-1], [-7, 14, 9], [14, 28, 6], (90, 60, 40), ("stripes",))))
    m.write({"idle": loop(3, {"coffin": bob(2, 3), "body": bob(0.5, 3)}),
             "walk": loop(1.2, {**humanoid_walk(1.2, amt=20), "coffin": bob(2, 1.2)}),
             "attack": once(0.8, {"coffin": {"position": kf([[0, 0, 0], [0, 6, -20], [0, 0, 0]], 0.8),
                                             "rotation": kf([[0, 0, 0], [-60, 0, 0], [0, 0, 0]], 0.8)}})})

# ================================================================ shikigami (Ten Shadows)
def quadruped(name, body, head, leg, cols, tail=True, extra=None):
    bw, bh, bd = body; hw, hh, hd = head; lw, lh, ld = leg
    m = Model(name)
    m.bone("root", [0, 0, 0])
    b = m.bone("body", [0, lh + bh / 2, 0], "root")
    m.cube(b, [-bw / 2, lh, -bd / 2], [bw, bh, bd], cols["body"])
    h = m.bone("head", [0, lh + bh, -bd / 2], "body")
    m.cube(h, [-hw / 2, lh + bh / 2, -bd / 2 - hd], [hw, hh, hd], cols["head"], ("eyes", "mouth"))
    for side, sx in (("l", 1), ("r", -1)):
        for end, sz in (("f", -1), ("b", 1)):
            lb = m.bone(f"leg_{end}{side}", [sx * (bw / 2 - lw / 2), lh, sz * (bd / 2 - ld)], "root")
            m.cube(lb, [sx * (bw / 2 - lw / 2) - lw / 2, 0, sz * (bd / 2 - ld) - ld / 2], [lw, lh, ld], cols["leg"])
    if tail:
        t = m.bone("tail", [0, lh + bh - 1, bd / 2], "body")
        m.cube(t, [-1, lh + bh - 2, bd / 2], [2, 2, max(4, bd // 2)], cols["body"])
    if extra: extra(m)
    walk = {"leg_fl": swing(30, 0.6), "leg_br": swing(30, 0.6), "leg_fr": swing(30, 0.6, 0, -1), "leg_bl": swing(30, 0.6, 0, -1)}
    anims = {"idle": loop(2, {"head": swing(4, 2, 0), **({"tail": swing(15, 2, 1)} if tail else {})}),
             "walk": loop(0.6, walk),
             "attack": once(0.4, {"head": {"rotation": kf([[0, 0, 0], [-25, 0, 0], [15, 0, 0], [0, 0, 0]], 0.4)}})}
    return m, anims

def divine_dog():
    m, a = quadruped("divine_dog", (8, 8, 16), (7, 7, 8), (3, 10, 3), {"body": (235, 235, 240), "head": (240, 240, 245), "leg": (220, 220, 230)})
    m.write(a)

def toad():
    m, a = quadruped("toad", (14, 8, 14), (12, 6, 6), (4, 4, 4), {"body": (80, 120, 70), "head": (90, 130, 80), "leg": (70, 100, 60)}, tail=False)
    a["attack"] = once(0.5, {"head": {"position": kf([[0, 0, 0], [0, 0, -6], [0, 0, 0]], 0.5)}})
    m.write(a)

def max_elephant():
    def trunk(m):
        t = m.bone("trunk", [0, 26, -22], "head")
        m.cube(t, [-2, 10, -27], [4, 16, 4], (110, 120, 160))
    m, a = quadruped("max_elephant", (22, 18, 30), (16, 14, 10), (7, 14, 7), {"body": (100, 110, 150), "head": (110, 120, 160), "leg": (90, 100, 140)}, extra=trunk)
    a["attack"] = once(0.8, {"trunk": {"rotation": kf([[0, 0, 0], [-60, 0, 0], [20, 0, 0], [0, 0, 0]], 0.8)}})
    m.write(a)

def rabbit():
    m, a = quadruped("shadow_rabbit", (4, 4, 6), (4, 4, 3), (1, 2, 1), {"body": (240, 240, 240), "head": (250, 250, 250), "leg": (230, 230, 230)}, tail=False)
    m.write(a)

def nue():
    m = Model("nue")
    m.bone("root", [0, 8, 0])
    b = m.bone("body", [0, 8, 0], "root")
    m.cube(b, [-4, 4, -7], [8, 7, 14], (40, 40, 60), ("stripes",))
    h = m.bone("head", [0, 10, -7], "body")
    m.cube(h, [-3, 7, -13], [6, 6, 6], (230, 220, 120), ("eyes",))
    for side, sx in (("l", 1), ("r", -1)):
        w = m.bone(f"wing_{side}", [sx * 4, 10, 0], "body")
        m.cube(w, [sx * 4 if sx > 0 else -20, 10, -6], [16, 1, 12], (30, 30, 50))
    flap = lambda s: {"rotation": kf([[0, 0, -35 * s], [0, 0, 35 * s], [0, 0, -35 * s]], 0.5)}
    m.write({"idle": loop(0.5, {"wing_l": flap(1), "wing_r": flap(-1), "body": bob(1, 0.5)}),
             "walk": loop(0.3, {"wing_l": {"rotation": kf([[0, 0, -45], [0, 0, 45], [0, 0, -45]], 0.3)},
                                "wing_r": {"rotation": kf([[0, 0, 45], [0, 0, -45], [0, 0, 45]], 0.3)}}),
             "attack": once(0.5, {"body": {"rotation": kf([[0, 0, 0], [30, 0, 0], [0, 0, 0]], 0.5)}})})

def mahoraga():
    m = Model("mahoraga")
    def wheel(m):
        w = m.bone("wheel", [0, 62, 0], "head")
        m.cube(w, [-1, 61, -1], [2, 2, 2], (230, 200, 90))
        for i in range(8):
            a = i * math.pi / 4
            x, y = math.cos(a) * 8, 62 + math.sin(a) * 8
            m.cube(w, [x - 1.5, y - 1.5, -1], [3, 3, 2], (230, 200, 90))
    humanoid(m, (16, 24, 10), (10, 12, 10), (6, 24, 6), (7, 18, 7),
             {"body": (235, 230, 215), "head": (240, 235, 220), "arm": (225, 220, 205), "leg": (210, 205, 190)},
             ("eyes",), extra=wheel)
    spin = {"rotation": kf([[0, 0, 0], [0, 0, 180], [0, 0, 360]], 2)}
    m.write({"idle": loop(2, {"body": bob(0.6, 2)}),
             "walk": loop(1.0, humanoid_walk(1.0, amt=25)),
             "attack": attack_arms(),
             "adapt": once(1.0, {"wheel": {"rotation": kf([[0, 0, 0], [0, 0, 45]], 1.0)}}),
             "spin": loop(2, {"wheel": spin})})

# ================================================================ armor (GeoArmorRenderer bone names)
def armor(name, pieces, cols, feats=None, inflate=0.6):
    """pieces: subset of {head, body, arms, legs, boots}. cols/feats keyed by piece."""
    feats = feats or {}
    m = Model(name)
    def part(bone, pivot, cubes):
        b = m.bone(bone, pivot)
        for origin, size, piece in cubes:
            if piece in pieces:
                m.cube(b, origin, size, cols[piece], feats.get(piece, ()), inflate)
    part("armorHead", [0, 24, 0], [([-4, 24, -4], [8, 8, 8], "head")])
    part("armorBody", [0, 24, 0], [([-4, 12, -2], [8, 12, 4], "body")])
    part("armorRightArm", [-5, 22, 0], [([-8, 12, -2], [4, 12, 4], "arms")])
    part("armorLeftArm", [5, 22, 0], [([4, 12, -2], [4, 12, 4], "arms")])
    part("armorRightLeg", [-1.9, 12, 0], [([-3.9, 3, -2], [4, 9, 4], "legs")])
    part("armorLeftLeg", [1.9, 12, 0], [([-0.1, 3, -2], [4, 9, 4], "legs")])
    part("armorRightBoot", [-1.9, 12, 0], [([-3.9, 0, -2], [4, 3, 4], "boots")])
    part("armorLeftBoot", [1.9, 12, 0], [([-0.1, 0, -2], [4, 3, 4], "boots")])
    # GeckoLib item assets live under geo/item, textures/item, animations/item
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": f"geometry.{name}", "texture_width": TEX, "texture_height": TEX,
                        "visible_bounds_width": 3, "visible_bounds_height": 3, "visible_bounds_offset": [0, 1, 0]},
        "bones": [{k: v for k, v in b.items() if k != "cubes" or v} for b in m.bones]}]}
    os.makedirs(f"{ROOT}/geo/item/armor", exist_ok=True)
    json.dump(geo, open(f"{ROOT}/geo/item/armor/{name}.geo.json", "w"), indent=1)
    os.makedirs(f"{ROOT}/animations/item/armor", exist_ok=True)
    json.dump({"format_version": "1.8.0", "animations": {}, "geckolib_format_version": 2},
              open(f"{ROOT}/animations/item/armor/{name}.animation.json", "w"), indent=1)
    m.paint()
    os.makedirs(f"{ROOT}/textures/item/armor", exist_ok=True)
    os.replace(f"{ROOT}/textures/entity/{name}.png", f"{ROOT}/textures/item/armor/{name}.png")

NAVY, BLACK, WHITE = (30, 36, 70), (24, 24, 28), (235, 232, 225)
ARMOR_SETS = {
    "jujutsu_uniform": ({"body", "arms", "legs", "boots"}, {"body": NAVY, "arms": NAVY, "legs": NAVY, "boots": BLACK}, {"body": ("buttons",)}),
    "gojo_outfit": ({"head", "body", "arms", "legs", "boots"}, {"head": BLACK, "body": BLACK, "arms": BLACK, "legs": BLACK, "boots": BLACK},
                    {"head": ("blindfold",), "body": ("buttons",)}),
    "sukuna_kimono": ({"body", "arms", "legs"}, {"body": WHITE, "arms": WHITE, "legs": (60, 55, 60)}, {"body": ("band",)}),
    "toji_outfit": ({"body", "arms", "legs", "boots"}, {"body": BLACK, "arms": BLACK, "legs": (220, 218, 210), "boots": BLACK}, {}),
    "nanami_suit": ({"body", "arms", "legs", "boots"}, {"body": (200, 185, 150), "arms": (200, 185, 150), "legs": (190, 175, 140), "boots": (70, 50, 35)},
                    {"body": ("tie",)}),
    "mahito_patchwork": ({"body", "arms", "legs"}, {"body": (120, 140, 170), "arms": (200, 180, 170), "legs": (60, 60, 70)},
                         {"body": ("patchwork",), "arms": ("patchwork",)}),
    "geto_kesa": ({"body", "arms", "legs"}, {"body": BLACK, "arms": BLACK, "legs": BLACK}, {"body": ("sash",)}),
}

if __name__ == "__main__":
    for f in (fly_head, roppongi_curse, finger_bearer, rainbow_dragon, smallpox_deity,
              divine_dog, toad, max_elephant, rabbit, nue, mahoraga):
        f()
    for n, (pieces, cols, feats) in ARMOR_SETS.items():
        armor(n, pieces, cols, feats)
    print("geo models written")
