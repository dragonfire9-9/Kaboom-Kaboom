"""Procedural 64x64 player-format skins for the sorcerer NPCs (rendered with the vanilla PlayerModel)."""
import os, random
from PIL import Image, ImageDraw

OUT = "src/main/resources/assets/jjk/textures/entity/npc"
os.makedirs(OUT, exist_ok=True)

REGIONS = {  # (x0, y0, x1, y1) of each box's full UV area
    "head": (0, 0, 32, 16), "hat": (32, 0, 64, 16),
    "body": (16, 16, 40, 32), "rarm": (40, 16, 56, 32), "rleg": (0, 16, 16, 32),
    "larm": (32, 48, 48, 64), "lleg": (16, 48, 32, 64),
}

def fill(d, region, col, rnd, noise=8):
    x0, y0, x1, y1 = REGIONS[region]
    for y in range(y0, y1):
        for x in range(x0, x1):
            n = rnd.randint(-noise, noise)
            d.point((x, y), tuple(max(0, min(255, c + n)) for c in col) + (255,))

def skin(name, skin_col, hair_col, top_col, pants_col, shoes_col, hair="short", extras=()):
    rnd = random.Random(name)
    im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    fill(d, "head", skin_col, rnd, 4)
    for r in ("body", "rarm", "larm"): fill(d, r, top_col, rnd)
    for r in ("rleg", "lleg"): fill(d, r, pants_col, rnd)
    # bare hands (bottom 3 rows of arm sides) and shoes (bottom 3 rows of legs)
    for (x0, y0) in ((40, 20), (32, 52)):
        d.rectangle([x0, y0 + 9, x0 + 15, y0 + 11], fill=skin_col + (255,))
    for (x0, y0) in ((0, 20), (16, 52)):
        d.rectangle([x0, y0 + 9, x0 + 15, y0 + 11], fill=shoes_col + (255,))
    # hair: top of head, upper rows on every side, and the back
    if hair_col:
        d.rectangle([8, 0, 15, 7], fill=hair_col + (255,))
        rows = 3 if hair != "long" else 5
        d.rectangle([0, 8, 31, 8 + rows - 1], fill=hair_col + (255,))
        d.rectangle([24, 8, 31, 15 if hair == "long" else 12], fill=hair_col + (255,))
        if hair == "spiky":
            for x in range(8, 16, 2): d.point((x, 8 + rows), fill=hair_col + (255,))
        if hair == "bob":
            d.rectangle([0, 8, 7, 13], fill=hair_col + (255,)); d.rectangle([16, 8, 23, 13], fill=hair_col + (255,))
    # face (front 8..15 x 8..15)
    d.point((10, 12), fill=(30, 30, 40, 255)); d.point((13, 12), fill=(30, 30, 40, 255))
    d.point((10, 11), fill=(240, 240, 240, 255)); d.point((13, 11), fill=(240, 240, 240, 255))
    d.line([(11, 14), (12, 14)], fill=tuple(max(0, c - 50) for c in skin_col) + (255,))
    for e in extras:
        if e == "blindfold": d.rectangle([0, 11, 31, 12], fill=(15, 15, 18, 255))
        if e == "glasses": d.rectangle([9, 11, 11, 12], outline=(20, 20, 20, 255)); d.rectangle([12, 11, 14, 12], outline=(20, 20, 20, 255))
        if e == "sunglasses": d.rectangle([9, 11, 14, 12], fill=(10, 10, 10, 255))
        if e == "goggles": d.rectangle([8, 11, 15, 12], fill=(40, 40, 40, 255)); d.point((10, 11), fill=(200, 230, 255, 255)); d.point((13, 11), fill=(200, 230, 255, 255))
        if e == "collar": d.rectangle([8, 13, 15, 15], fill=top_col + (255,)); d.rectangle([0, 13, 31, 15], fill=top_col + (255,))
        if e == "beard": d.rectangle([9, 14, 14, 15], fill=(40, 30, 25, 255))
        if e == "scar": d.line([(9, 9), (10, 15)], fill=(150, 90, 90, 255))
        if e == "buttons":
            for y in range(21, 31, 3): d.point((24, y), fill=(220, 190, 80, 255))
        if e == "hood": d.rectangle([32, 0, 63, 15], fill=(160, 40, 40, 255)); d.rectangle([40, 8, 47, 15], fill=(0, 0, 0, 0))
        if e == "tie": d.line([(24, 20), (24, 27)], fill=(40, 70, 160, 255))
        if e == "panda":
            d.rectangle([0, 0, 31, 15], fill=(240, 240, 240, 255))
            d.rectangle([9, 10, 10, 12], fill=(20, 20, 20, 255)); d.rectangle([13, 10, 14, 12], fill=(20, 20, 20, 255))
            d.point((10, 11), fill=(250, 250, 250, 255)); d.point((13, 11), fill=(250, 250, 250, 255))
            d.rectangle([8, 0, 9, 1], fill=(20, 20, 20, 255)); d.rectangle([14, 0, 15, 1], fill=(20, 20, 20, 255))
            d.rectangle([11, 13, 12, 13], fill=(20, 20, 20, 255))
        if e == "robe": d.rectangle([20, 20, 27, 31], fill=top_col + (255,)); d.line([(20, 20), (27, 31)], fill=(210, 150, 40, 255))
    im.save(f"{OUT}/{name}.png")

SKIN, PALE = (233, 196, 168), (240, 220, 205)
NAVY, BLACK = (30, 36, 70), (26, 26, 30)
skin("gojo", PALE, (245, 245, 250), BLACK, BLACK, BLACK, "spiky", ("blindfold", "buttons"))
skin("yuji", SKIN, (240, 150, 170), NAVY, NAVY, (200, 40, 40), "spiky", ("hood", "buttons"))
skin("megumi", PALE, (20, 22, 30), NAVY, NAVY, BLACK, "spiky", ("buttons",))
skin("nobara", SKIN, (210, 120, 60), NAVY, NAVY, (60, 40, 30), "bob", ("buttons",))
skin("nanami", SKIN, (230, 210, 140), (200, 185, 150), (190, 175, 140), (70, 50, 35), "short", ("goggles", "tie"))
skin("maki", SKIN, (40, 70, 50), NAVY, NAVY, BLACK, "long", ("glasses", "scar"))
skin("panda", (30, 30, 32), None, (30, 30, 32), (30, 30, 32), (20, 20, 20), "short", ("panda",))
skin("inumaki", PALE, (225, 225, 230), NAVY, NAVY, BLACK, "short", ("collar",))
skin("yaga", (200, 160, 130), (25, 25, 25), (40, 40, 45), (40, 40, 45), BLACK, "short", ("sunglasses", "beard"))
skin("curse_user", (210, 200, 200), (30, 20, 40), (50, 25, 70), (35, 20, 45), BLACK, "long", ("robe",))
print("npc skins written")
