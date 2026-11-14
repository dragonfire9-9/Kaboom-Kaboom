"""Writes the bundled example schematics (Sponge .schem v2): a small oak-and-cobble starter hut,
and a larger stone cottage with a stair roof and a lookout tower.

Usage: python3 tools/gen_example_schem.py
"""
import gzip, struct
from pathlib import Path

DIR = Path(__file__).resolve().parent.parent / "src/main/resources/assets/soulbound/schematics"
DIR.mkdir(parents=True, exist_ok=True)


def varint(v):
    out = bytearray()
    while True:
        b = v & 0x7F
        v >>= 7
        if v:
            out.append(b | 0x80)
        else:
            out.append(b)
            return bytes(out)




def tag(t, name, payload):
    n = name.encode()
    return bytes([t]) + struct.pack(">H", len(n)) + n + payload


def short(name, v): return tag(2, name, struct.pack(">h", v))
def int_(name, v): return tag(3, name, struct.pack(">i", v))


def write(name, W, H, L, fill):
    palette = {"minecraft:air": 0}
    blocks = [[[0] * W for _ in range(L)] for _ in range(H)]
    for y in range(H):
        for z in range(L):
            for x in range(W):
                n = fill(x, y, z)
                if n not in palette:
                    palette[n] = len(palette)
                blocks[y][z][x] = palette[n]
    data = b"".join(varint(blocks[y][z][x]) for y in range(H) for z in range(L) for x in range(W))
    pal = b"".join(int_(k, v) for k, v in palette.items()) + b"\x00"
    body = (int_("Version", 2) + int_("DataVersion", 4440) + short("Width", W) + short("Height", H) + short("Length", L)
            + int_("PaletteMax", len(palette)) + tag(10, "Palette", pal)
            + tag(7, "BlockData", struct.pack(">i", len(data)) + data))
    root = tag(10, "Schematic", body + b"\x00")
    (DIR / name).write_bytes(gzip.compress(root))
    print("wrote", name, len(palette), "palette entries")


def hut(x, y, z, W=7, H=5, L=7):
    edge = x in (0, W - 1) or z in (0, L - 1)
    corner = x in (0, W - 1) and z in (0, L - 1)
    if y == 0:
        return "minecraft:cobblestone" if edge else "minecraft:oak_planks"
    if y in (1, 2, 3) and edge:
        if y in (1, 2) and z == 0 and x == W // 2:
            return "minecraft:air"  # doorway
        if y == 2 and not corner and (x == W // 2 or z == L // 2):
            return "minecraft:glass"
        return "minecraft:oak_log" if corner else "minecraft:oak_planks"
    if y == 4:
        return "minecraft:cobblestone_slab[type=bottom]" if edge else "minecraft:oak_planks"
    return "minecraft:air"


def cottage(x, y, z):
    # 11 x 9 house (x 0..10, z 0..8) with 4-high walls and a gable stair roof, plus a tower at x 12..14
    if 12 <= x <= 14 and 3 <= z <= 5:
        tx, tz = x - 12, z - 3
        ring = tx != 1 or tz != 1
        if y == 0:
            return "minecraft:stone_bricks"
        if 1 <= y <= 9 and ring:
            if y in (3, 6, 9) and (tx == 1 or tz == 1):
                return "minecraft:glass"
            return "minecraft:stone_bricks"
        if y == 10:
            return "minecraft:stone_brick_slab[type=bottom]"
        return "minecraft:air"
    if x > 10:
        return "minecraft:air"
    edge = x in (0, 10) or z in (0, 8)
    corner = x in (0, 10) and z in (0, 8)
    if y == 0:
        return "minecraft:cobblestone" if edge else "minecraft:spruce_planks"
    if 1 <= y <= 4 and edge:
        if z == 0 and x == 5 and y in (1, 2):
            return "minecraft:air"
        if y in (2, 3) and not corner and x in (2, 8) and z in (0, 8):
            return "minecraft:glass_pane"
        return "minecraft:spruce_log[axis=y]" if corner else "minecraft:cobblestone"
    # gable roof rising along z, ridge at z = 4
    if 5 <= y <= 9:
        level = y - 5
        if z == level:
            return "minecraft:spruce_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]"
        if z == 8 - level:
            return "minecraft:spruce_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]"
        if level < z < 8 - level and x in (0, 10):
            return "minecraft:spruce_planks"
        if level == 4 and z == 4:
            return "minecraft:spruce_slab[type=bottom,waterlogged=false]"
    return "minecraft:air"


write("starter_hut.schem", 7, 5, 7, hut)
write("stone_cottage.schem", 15, 11, 9, cottage)
