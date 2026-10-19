"""Procedural placeholder textures. Re-run after editing; replace any file with hand-drawn art later."""
import math, random, os, sys
from PIL import Image, ImageDraw, ImageFilter

ROOT = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/assets/jjk/textures"
random.seed(7)

def save(img, rel):
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)

def hexc(h, a=255):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255, a)

# ---------------------------------------------------------------- scrolls (16x16)
def scroll(paper, seal, ribbon):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # diagonal rolled scroll: rods at both ends, paper body between
    for i in range(10):
        x, y = 3 + i, 12 - i
        d.line([(x - 2, y - 2), (x + 2, y + 2)], fill=paper)
    shade = tuple(max(0, c - 40) for c in paper[:3]) + (255,)
    for i in range(10):
        d.point((3 + i + 2, 12 - i + 2), fill=shade)
    # wooden rods
    rod = (92, 58, 30, 255)
    d.line([(1, 12), (4, 15)], fill=rod)
    d.line([(11, 1), (14, 4)], fill=rod)
    d.point((1, 12), fill=(140, 96, 52, 255)); d.point((14, 4), fill=(140, 96, 52, 255))
    # ribbon + seal in the middle
    d.line([(6, 10), (10, 6)], fill=ribbon)
    d.rectangle([7, 7, 9, 9], fill=seal)
    d.point((8, 8), fill=tuple(min(255, c + 70) for c in seal[:3]) + (255,))
    return im

save(scroll((232, 214, 170, 255), hexc(0x7A3CFF), hexc(0x2F6BFF)), "item/cursed_energy_scroll.png")
save(scroll((236, 220, 180, 255), hexc(0xC0141E), hexc(0xE0B030)), "item/clan_scroll.png")
save(scroll((245, 245, 240, 255), hexc(0x1E3A1E), hexc(0x101010)), "item/heavenly_restriction_scroll.png")

# ---------------------------------------------------------------- emblem (16x16): navy disc, gold spiral
im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
d = ImageDraw.Draw(im)
d.ellipse([0, 0, 15, 15], fill=hexc(0x141C3A), outline=hexc(0xC9A63A))
for i in range(60):
    a = i * 0.32
    r = 0.9 + i * 0.1
    d.point((7.5 + math.cos(a) * r, 7.5 + math.sin(a) * r), fill=hexc(0xE8C860))
save(im, "item/jjk_emblem.png")

# ---------------------------------------------------------------- particles: soft glowing wisps
for i, size in enumerate([8, 7, 5, 3]):
    im = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    o = (8 - size) / 2
    d.ellipse([o, o, o + size - 1, o + size - 1], fill=(170, 140, 255, 200))
    if size > 3:
        d.ellipse([o + 1.5, o + 1.5, o + size - 2.5, o + size - 2.5], fill=(235, 225, 255, 255))
    save(im, f"particle/cursed_energy_{i}.png")

# ---------------------------------------------------------------- roll parchment (300x210)
W, H = 300, 210
im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
paper = Image.new("RGBA", (W - 16, H - 22), (226, 206, 160, 255))
px = paper.load()
for y in range(paper.height):
    for x in range(paper.width):
        n = random.randint(-14, 10)
        fiber = int(6 * math.sin(x * 0.21 + y * 0.03) * math.sin(y * 0.11))
        edge = min(x, y, paper.width - 1 - x, paper.height - 1 - y)
        burn = max(0, 14 - edge) * 5
        r, g, b, a = px[x, y]
        px[x, y] = (max(0, r + n + fiber - burn), max(0, g + n + fiber - int(burn * 1.15)), max(0, b + n - int(burn * 1.4)), 255)
paper = paper.filter(ImageFilter.SMOOTH)
im.paste(paper, (8, 11))
d = ImageDraw.Draw(im)
# talisman border: double red line with corner seals
red, dred = (150, 22, 22, 255), (110, 12, 12, 255)
d.rectangle([14, 17, W - 15, H - 18], outline=red, width=2)
d.rectangle([19, 22, W - 20, H - 23], outline=dred, width=1)
for cx, cy in [(16, 19), (W - 17, 19), (16, H - 20), (W - 17, H - 20)]:
    d.rectangle([cx - 5, cy - 5, cx + 5, cy + 5], fill=red)
    d.rectangle([cx - 3, cy - 3, cx + 3, cy + 3], outline=(230, 190, 120, 255))
    d.point((cx, cy), fill=(230, 190, 120, 255))
# vertical ofuda strips with abstract sigils at the sides
for sx in (24, W - 34):
    d.rectangle([sx, 34, sx + 10, H - 40], fill=(240, 228, 196, 255), outline=dred)
    for k in range(7):
        yy = 40 + k * 20
        d.line([(sx + 2, yy), (sx + 8, yy)], fill=dred, width=1)
        d.line([(sx + 5, yy - 3), (sx + 5, yy + 5)], fill=dred, width=1)
        d.arc([sx + 2, yy + 3, sx + 8, yy + 9], 200, 340, fill=dred)
# wooden rollers at top and bottom
def roller(y):
    d.rounded_rectangle([0, y, W - 1, y + 10], radius=5, fill=(96, 58, 28, 255), outline=(52, 30, 14, 255))
    d.line([(4, y + 3), (W - 5, y + 3)], fill=(150, 98, 52, 255))
    for x in (0, W - 12):
        d.rounded_rectangle([x, y - 2, x + 11, y + 12], radius=4, fill=(176, 40, 30, 255), outline=(80, 14, 10, 255))
        d.ellipse([x + 3, y + 2, x + 8, y + 7], fill=(225, 180, 70, 255))
roller(0)
roller(H - 11)
save(im, "gui/roll_parchment.png")

# ---------------------------------------------------------------- technique icons (16x16)
TECH = {
    "none": 0x888888, "limitless": 0x6FD8FF, "shrine": 0xE0303A, "ten_shadows": 0x2A2440,
    "idle_transfiguration": 0x6FA88A, "disaster_flames": 0xFF7A1F, "cursed_spirit_manipulation": 0x4B3A5E,
    "cursed_speech": 0xC9C3B0, "blood_manipulation": 0xB0102A, "projection_sorcery": 0x7FFFB2,
    "boogie_woogie": 0xFFD15C, "ratio": 0xE8E0C8, "straw_doll": 0x9C7A4A, "black_flash_affinity": 0x201018,
    "puppet_manipulation": 0x8A97A8, "construction": 0xB5A27A, "comedian": 0xFF9FE0, "ce_reinforcement": 0x7A6CFF,
}
def icon(name, col):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    c = hexc(col)
    light = tuple(min(255, v + 80) for v in c[:3]) + (255,)
    dark = tuple(max(0, v - 60) for v in c[:3]) + (255,)
    d.ellipse([0, 0, 15, 15], fill=dark, outline=c)
    if name == "limitless":
        d.ellipse([3, 6, 6, 9], fill=hexc(0x2F7BFF)); d.ellipse([9, 6, 12, 9], fill=hexc(0xFF2A2A)); d.ellipse([6, 2, 9, 5], fill=hexc(0x9B30FF))
        d.ellipse([6, 10, 9, 13], fill=(255, 255, 255, 255))
    elif name == "shrine":
        for i in range(3): d.line([(4 + i * 3, 3), (2 + i * 3, 12)], fill=(255, 255, 255, 255))
        d.line([(3, 13), (12, 13)], fill=c)
    elif name == "ten_shadows":
        d.polygon([(3, 12), (5, 4), (7, 8), (9, 4), (11, 8), (13, 4), (12, 12)], fill=(20, 20, 30, 255), outline=light)
    elif name == "disaster_flames":
        d.polygon([(8, 2), (12, 9), (10, 13), (6, 13), (4, 9)], fill=hexc(0xFF5A1A)); d.polygon([(8, 6), (10, 10), (8, 12), (6, 10)], fill=hexc(0xFFE060))
    elif name == "blood_manipulation":
        d.polygon([(8, 2), (12, 9), (8, 13), (4, 9)], fill=hexc(0xD0203A)); d.point((7, 8), fill=(255, 160, 170, 255))
    elif name == "cursed_speech":
        d.arc([3, 4, 12, 11], 20, 160, fill=(40, 40, 40, 255)); d.line([(4, 7), (11, 7)], fill=(40, 40, 40, 255))
        d.point((5, 10), fill=(40, 40, 40, 255)); d.point((10, 10), fill=(40, 40, 40, 255))
    elif name == "boogie_woogie":
        d.line([(4, 5), (11, 5)], fill=(255, 255, 255, 255)); d.line([(4, 10), (11, 10)], fill=(255, 255, 255, 255))
        d.polygon([(11, 3), (13, 5), (11, 7)], fill=(255, 255, 255, 255)); d.polygon([(4, 8), (2, 10), (4, 12)], fill=(255, 255, 255, 255))
    elif name == "ratio":
        d.line([(3, 3), (12, 12)], fill=(30, 30, 30, 255)); d.line([(9, 3), (9, 12)], fill=hexc(0xC0141E))
    elif name == "black_flash_affinity":
        d.line([(3, 3), (7, 7), (5, 9), (12, 13)], fill=hexc(0xFF2040)); d.line([(10, 2), (8, 7)], fill=(0, 0, 0, 255))
    elif name == "projection_sorcery":
        for i in range(3): d.rectangle([3 + i * 4, 5, 5 + i * 4, 10], outline=(255, 255, 255, 255))
    else:
        # generic: concentric ring + glyph from the name's initial
        d.ellipse([4, 4, 11, 11], outline=light)
        d.ellipse([7, 7, 8, 8], fill=light)
    save(im, f"gui/technique/{name}.png")

for n, c in TECH.items():
    icon(n, c)

print("textures written")

# ================================================================ Phase 2 items
def canvas():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0)); return im, ImageDraw.Draw(im)

# Sukuna finger: grey-purple withered finger with dark nail and red bands
im, d = canvas()
for i in range(9):
    d.ellipse([3 + i, 11 - i, 5 + i, 13 - i], fill=(120, 96, 110, 255))
for i in range(9):
    d.point((3 + i, 12 - i), fill=(80, 60, 74, 255))
d.ellipse([11, 2, 13, 4], fill=(40, 24, 30, 255))
d.line([(5, 9), (7, 11)], fill=(160, 20, 30, 255)); d.line([(8, 6), (10, 8)], fill=(160, 20, 30, 255))
save(im, "item/sukuna_finger.png")

# cursed fragment: jagged dark crystal shard
im, d = canvas()
d.polygon([(4, 13), (6, 4), (9, 2), (12, 7), (10, 13)], fill=(58, 30, 78, 255), outline=(20, 8, 30, 255))
d.line([(7, 4), (8, 11)], fill=(140, 90, 200, 255))
save(im, "item/cursed_fragment.png")

# CE shard: glowing blue-purple crystal
im, d = canvas()
d.polygon([(8, 1), (12, 6), (10, 14), (6, 14), (4, 6)], fill=(110, 90, 255, 255), outline=(50, 30, 150, 255))
d.polygon([(8, 3), (10, 7), (8, 12), (7, 7)], fill=(210, 200, 255, 255))
save(im, "item/ce_shard.png")

# CE bottle
im, d = canvas()
d.rectangle([6, 1, 9, 3], fill=(140, 100, 60, 255))
d.ellipse([3, 4, 12, 14], fill=(200, 220, 255, 120), outline=(220, 230, 255, 255))
d.ellipse([4, 7, 11, 13], fill=(120, 70, 255, 230))
d.point((6, 8), fill=(240, 230, 255, 255))
save(im, "item/ce_bottle.png")

# talisman: paper strip with red sigil
im, d = canvas()
d.rectangle([5, 1, 10, 14], fill=(240, 226, 180, 255), outline=(170, 140, 90, 255))
d.line([(7, 3), (7, 12)], fill=(180, 20, 20, 255)); d.line([(6, 5), (9, 5)], fill=(180, 20, 20, 255))
d.arc([6, 8, 9, 11], 0, 270, fill=(180, 20, 20, 255))
save(im, "item/talisman.png")

# prison realm: dark cube with eye
im, d = canvas()
d.polygon([(8, 2), (14, 5), (8, 8), (2, 5)], fill=(70, 50, 90, 255))
d.polygon([(2, 5), (8, 8), (8, 15), (2, 12)], fill=(40, 28, 52, 255))
d.polygon([(14, 5), (8, 8), (8, 15), (14, 12)], fill=(54, 38, 70, 255))
d.ellipse([4, 9, 6, 11], fill=(230, 200, 120, 255)); d.point((5, 10), fill=(20, 10, 10, 255))
save(im, "item/prison_realm.png")

# death painting: fetus-like curse in a jar
im, d = canvas()
d.rectangle([4, 2, 11, 3], fill=(90, 60, 40, 255))
d.rectangle([3, 4, 12, 14], fill=(150, 170, 120, 140), outline=(200, 220, 190, 255))
d.ellipse([5, 6, 10, 12], fill=(140, 30, 40, 255)); d.point((7, 8), fill=(255, 220, 220, 255))
save(im, "item/death_painting.png")

# special grade cursed object: bound box with talismans
im, d = canvas()
d.rectangle([3, 4, 12, 13], fill=(60, 40, 30, 255), outline=(30, 18, 10, 255))
d.line([(3, 8), (12, 8)], fill=(220, 200, 150, 255)); d.line([(7, 4), (7, 13)], fill=(220, 200, 150, 255))
d.rectangle([6, 7, 8, 9], fill=(180, 20, 30, 255))
save(im, "item/special_grade_object.png")

# student ID: navy card with photo + gold swirl
im, d = canvas()
d.rectangle([1, 4, 14, 12], fill=(24, 32, 70, 255), outline=(200, 170, 70, 255))
d.rectangle([3, 6, 6, 10], fill=(220, 200, 180, 255))
for y in (6, 8, 10): d.line([(8, y), (12, y)], fill=(200, 210, 240, 255))
save(im, "item/student_id.png")

# curse glasses: black frames with rounded lenses
im, d = canvas()
d.ellipse([1, 6, 6, 10], outline=(20, 20, 20, 255), fill=(60, 80, 70, 160))
d.ellipse([9, 6, 14, 10], outline=(20, 20, 20, 255), fill=(60, 80, 70, 160))
d.line([(6, 7), (9, 7)], fill=(20, 20, 20, 255))
save(im, "item/curse_glasses.png")

# cursed tools (diagonal handheld)
def blade(name, handle, edge, length=11, width=1, extra=None):
    im, d = canvas()
    for i in range(4):
        d.point((2 + i, 13 - i), fill=handle)
        d.point((3 + i, 13 - i), fill=tuple(max(0, c - 40) for c in handle[:3]) + (255,))
    d.line([(5, 9), (7, 11)], fill=(60, 60, 60, 255))
    for i in range(length - 4):
        for w in range(width):
            d.point((6 + i + w, 9 - i), fill=edge)
        d.point((6 + i, 10 - i), fill=tuple(max(0, c - 60) for c in edge[:3]) + (255,))
    if extra: extra(d)
    save(im, f"item/{name}.png")

blade("inverted_spear_of_heaven", (80, 40, 30, 255), (190, 190, 200, 255), 12,
      extra=lambda d: (d.polygon([(13, 0), (15, 0), (15, 2)], fill=(220, 220, 230, 255)), d.line([(12, 0), (11, 3)], fill=(220, 220, 230, 255))))
blade("split_soul_katana", (40, 40, 50, 255), (220, 225, 235, 255), 12)
blade("slaughter_demon", (60, 30, 30, 255), (180, 180, 190, 255), 8, extra=lambda d: d.point((9, 5), fill=(200, 20, 20, 255)))
blade("dragon_bone", (90, 70, 50, 255), (230, 220, 190, 255), 11, 2)
# playful cloud: three red sections joined by chain
im, d = canvas()
for (x0, y0) in [(1, 13), (6, 8), (11, 3)]:
    d.line([(x0, y0), (x0 + 3, y0 - 3)], fill=(190, 30, 30, 255), width=2)
d.point((5, 9), fill=(150, 150, 150, 255)); d.point((10, 4), fill=(150, 150, 150, 255))
save(im, "item/playful_cloud.png")
# chain of a thousand miles
im, d = canvas()
for i in range(7):
    x, y = 1 + i * 2, 13 - i * 2
    d.ellipse([x, y - 1, x + 2, y + 1], outline=(150, 150, 165, 255))
d.polygon([(13, 0), (15, 2), (13, 3)], fill=(200, 200, 210, 255))
save(im, "item/chain_of_a_thousand_miles.png")
# black rope
im, d = canvas()
pts = [(2, 14), (4, 10), (7, 11), (9, 7), (12, 7), (13, 3)]
d.line(pts, fill=(15, 15, 18, 255), width=2)
d.line(pts, fill=(70, 70, 80, 255), width=1)
save(im, "item/black_rope.png")

# armor layer for glasses (64x32): lenses on the head front face (8..16, 8..16)
im = Image.new("RGBA", (64, 32), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
d.rectangle([8, 11, 15, 11], fill=(20, 20, 20, 255))
d.rectangle([9, 11, 11, 13], outline=(20, 20, 20, 255), fill=(60, 90, 80, 150))
d.rectangle([12, 11, 14, 13], outline=(20, 20, 20, 255), fill=(60, 90, 80, 150))
d.line([(0, 11), (7, 11)], fill=(20, 20, 20, 255)); d.line([(16, 11), (23, 11)], fill=(20, 20, 20, 255))
save(im, "models/armor/curse_glasses_layer_1.png")

# Sukuna markings overlay (64x64 player skin layout)
im = Image.new("RGBA", (64, 64), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
R = (150, 10, 20, 230)
# face (8..16 x 8..16): second pair of eye slits + cheek lines
d.line([(9, 11), (10, 11)], fill=R); d.line([(13, 11), (14, 11)], fill=R)
d.line([(8, 13), (10, 14)], fill=R); d.line([(15, 13), (13, 14)], fill=R)
d.line([(11, 8), (12, 9)], fill=R)
# torso front (20..28 x 20..32): bands
for y in (22, 25, 28): d.line([(20, y), (27, y)], fill=R)
# arms (44..48 x 20..32 right arm front, 36..40 x 52..64 left arm front)
for y in (22, 25, 28): d.line([(44, y), (47, y)], fill=R); d.line([(36, y + 32), (39, y + 32)], fill=R)
save(im, "entity/sukuna_markings.png")
print("phase 2 textures written")

# ================================================================ Phase 3 particles (white; tinted in code)
def soft(size, radius, falloff=2.0):
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0)); px = im.load(); c = (size - 1) / 2
    for y in range(size):
        for x in range(size):
            dd = math.hypot(x - c, y - c) / radius
            if dd < 1: px[x, y] = (255, 255, 255, int(255 * (1 - dd) ** falloff))
    return im
for i, r in enumerate([7.5, 6.5, 5, 3.5]):
    save(soft(16, r, 1.6), f"particle/glow_{i}.png")
for i, r in enumerate([8, 6, 4, 2]):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    d.line([(8, 8 - r), (8, 8 + r)], fill=(255, 255, 255, 255)); d.line([(8 - r, 8), (8 + r, 8)], fill=(255, 255, 255, 255))
    d.line([(8 - r // 2, 8 - r // 2), (8 + r // 2, 8 + r // 2)], fill=(255, 255, 255, 180))
    d.line([(8 - r // 2, 8 + r // 2), (8 + r // 2, 8 - r // 2)], fill=(255, 255, 255, 180))
    d.ellipse([7, 7, 9, 9], fill=(255, 255, 255, 255))
    save(im, f"particle/spark_{i}.png")
for i, sweep in enumerate([60, 120, 160, 180]):
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    d.arc([2, 2, 29, 29], 200 - sweep // 2, 200 + sweep // 2, fill=(255, 255, 255, 255), width=3)
    d.arc([4, 4, 27, 27], 200 - sweep // 3, 200 + sweep // 3, fill=(255, 255, 255, 140), width=1)
    save(im, f"particle/slash_{i}.png")
im = Image.new("RGBA", (32, 32), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
d.ellipse([1, 1, 30, 30], outline=(255, 255, 255, 255), width=2); d.ellipse([4, 4, 27, 27], outline=(255, 255, 255, 90), width=1)
save(im, "particle/ring_0.png")
random.seed(3)
for i in range(3):
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    for branch in range(3):
        x, y = 16, 16
        a = random.uniform(0, math.tau)
        for seg in range(6):
            a += random.uniform(-0.9, 0.9)
            nx, ny = x + math.cos(a) * 3, y + math.sin(a) * 3
            d.line([(x, y), (nx, ny)], fill=(255, 255, 255, 255), width=2 if seg < 3 else 1)
            x, y = nx, ny
    save(im, f"particle/lightning_{i}.png")
print("phase 3 particles written")

# ================================================================ Phase 3 blocks
random.seed(11)
im = Image.new("RGBA", (16, 16), (8, 4, 14, 255)); px = im.load()
for y in range(16):
    for x in range(16):
        v = random.randint(0, 10); px[x, y] = (8 + v, 4 + v // 2, 16 + v, 255)
d = ImageDraw.Draw(im)
for _ in range(3):
    x, y = random.randint(0, 15), random.randint(0, 15)
    for _ in range(6):
        nx, ny = x + random.choice([-1, 0, 1]), y + random.choice([-1, 1])
        d.line([(x, y), (nx, ny)], fill=(110, 60, 190, 255)); x, y = nx % 16, ny % 16
save(im, "block/domain_barrier.png")
im = Image.new("RGBA", (16, 16), (20, 14, 26, 255)); d = ImageDraw.Draw(im)
d.rectangle([0, 0, 15, 15], outline=(60, 40, 80, 255))
d.line([(0, 8), (15, 8)], fill=(40, 28, 56, 255)); d.line([(8, 0), (8, 15)], fill=(40, 28, 56, 255))
save(im, "block/domain_floor.png")
print("phase 3 blocks written")

# ================================================================ Phase 5 outfit icons
def shirt(col, accent=None, feat=None):
    im, d = canvas()
    d.polygon([(4, 2), (6, 2), (8, 4), (10, 2), (12, 2), (15, 6), (13, 8), (12, 7), (12, 14), (4, 14), (4, 7), (3, 8), (1, 6)], fill=col)
    dark = tuple(max(0, c - 40) for c in col[:3]) + (255,)
    d.line([(4, 14), (12, 14)], fill=dark)
    if feat == "buttons":
        for y in (5, 8, 11): d.point((8, y), fill=(220, 190, 80, 255))
    if feat == "tie":
        d.line([(8, 4), (8, 11)], fill=(40, 70, 160, 255)); d.point((7, 4), fill=(240, 240, 240, 255)); d.point((9, 4), fill=(240, 240, 240, 255))
    if feat == "sash":
        d.line([(4, 3), (12, 13)], fill=(210, 150, 40, 255), width=2)
    if feat == "band":
        d.line([(4, 10), (12, 10)], fill=(30, 30, 35, 255), width=2)
    if feat == "patch":
        for (x, y, c) in [(5, 6, (150, 90, 80)), (9, 9, (90, 120, 150)), (6, 11, (170, 160, 120))]:
            d.rectangle([x, y, x + 2, y + 2], fill=c + (255,))
    return im

def pants(col):
    im, d = canvas()
    d.polygon([(4, 2), (12, 2), (13, 14), (9, 14), (8, 7), (7, 14), (3, 14)], fill=col)
    d.line([(4, 3), (12, 3)], fill=tuple(max(0, c - 50) for c in col[:3]) + (255,))
    return im

def shoes(col):
    im, d = canvas()
    for x0 in (1, 9):
        d.polygon([(x0, 8), (x0 + 3, 8), (x0 + 3, 11), (x0 + 6, 12), (x0 + 6, 14), (x0, 14)], fill=col)
    return im

NAVY, BLACK, WHITE = (30, 36, 70, 255), (28, 28, 32, 255), (235, 232, 225, 255)
save(shirt(NAVY, feat="buttons"), "item/jujutsu_uniform_jacket.png"); save(pants(NAVY), "item/jujutsu_uniform_pants.png"); save(shoes(BLACK), "item/jujutsu_uniform_shoes.png")
im, d = canvas(); d.rectangle([1, 6, 14, 9], fill=(15, 15, 18, 255)); d.line([(14, 8), (15, 12)], fill=(15, 15, 18, 255)); save(im, "item/gojo_blindfold.png")
save(shirt(BLACK, feat="buttons"), "item/gojo_jacket.png"); save(pants(BLACK), "item/gojo_pants.png"); save(shoes(BLACK), "item/gojo_shoes.png")
save(shirt(WHITE, feat="band"), "item/sukuna_kimono.png"); save(pants((60, 55, 60, 255)), "item/sukuna_hakama.png")
save(shirt(BLACK), "item/toji_shirt.png"); save(pants((220, 218, 210, 255)), "item/toji_pants.png"); save(shoes(BLACK), "item/toji_shoes.png")
save(shirt((200, 185, 150, 255), feat="tie"), "item/nanami_jacket.png"); save(pants((190, 175, 140, 255)), "item/nanami_slacks.png"); save(shoes((70, 50, 35, 255)), "item/nanami_shoes.png")
save(shirt((120, 140, 170, 255), feat="patch"), "item/mahito_patchwork_top.png"); save(pants((60, 60, 70, 255)), "item/mahito_patchwork_pants.png")
save(shirt(BLACK, feat="sash"), "item/geto_kesa.png"); save(pants(BLACK), "item/geto_hakama.png")
print("phase 5 icons written")
