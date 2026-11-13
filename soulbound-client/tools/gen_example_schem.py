"""Writes the bundled example schematic (Sponge .schem v2): a small oak-and-cobble starter hut.

Usage: python3 tools/gen_example_schem.py
"""
import gzip, struct
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/soulbound/schematics/starter_hut.schem"
OUT.parent.mkdir(parents=True, exist_ok=True)

W, H, L = 7, 5, 7
palette = {"minecraft:air": 0}


def pid(name):
    if name not in palette:
        palette[name] = len(palette)
    return palette[name]


blocks = [[[0] * W for _ in range(L)] for _ in range(H)]
for y in range(H):
    for z in range(L):
        for x in range(W):
            edge = x in (0, W - 1) or z in (0, L - 1)
            corner = x in (0, W - 1) and z in (0, L - 1)
            name = "minecraft:air"
            if y == 0:
                name = "minecraft:cobblestone" if edge else "minecraft:oak_planks"
            elif y in (1, 2, 3) and edge:
                name = "minecraft:oak_log" if corner else "minecraft:oak_planks"
                if y == 2 and not corner and (x == W // 2 or z == L // 2):
                    name = "minecraft:glass"
                if y in (1, 2) and z == 0 and x == W // 2:
                    name = "minecraft:air"  # doorway
            elif y == 4:
                name = "minecraft:cobblestone_slab[type=bottom]" if edge else "minecraft:oak_planks"
            blocks[y][z][x] = pid(name)


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


data = b"".join(varint(blocks[y][z][x]) for y in range(H) for z in range(L) for x in range(W))


def tag(t, name, payload):
    n = name.encode()
    return bytes([t]) + struct.pack(">H", len(n)) + n + payload


def short(name, v): return tag(2, name, struct.pack(">h", v))
def int_(name, v): return tag(3, name, struct.pack(">i", v))


pal = b"".join(int_(k, v) for k, v in palette.items()) + b"\x00"
body = (int_("Version", 2) + int_("DataVersion", 4440) + short("Width", W) + short("Height", H) + short("Length", L)
        + int_("PaletteMax", len(palette)) + tag(10, "Palette", pal)
        + tag(7, "BlockData", struct.pack(">i", len(data)) + data))
root = tag(10, "Schematic", body + b"\x00")
OUT.write_bytes(gzip.compress(root))
print("wrote", OUT, len(palette), "palette entries")
