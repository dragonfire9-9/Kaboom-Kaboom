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
