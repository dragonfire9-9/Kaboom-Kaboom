"""Writes a tiny gzip'd structure NBT (3x3x3: stone floor, air above) used as the GameTest template."""
import gzip, struct, sys

def s(v): b = v.encode(); return struct.pack('>H', len(b)) + b
def named(tag_id, name, payload): return bytes([tag_id]) + s(name) + payload
def t_int(v): return struct.pack('>i', v)
def t_list(elem_id, items): return bytes([elem_id]) + struct.pack('>i', len(items)) + b''.join(items)
def t_compound(entries): return b''.join(entries) + b'\x00'

palette = [t_compound([named(8, 'Name', s('minecraft:stone'))]),
           t_compound([named(8, 'Name', s('minecraft:air'))])]
blocks = []
for x in range(3):
    for y in range(3):
        for z in range(3):
            blocks.append(t_compound([
                named(9, 'pos', t_list(3, [t_int(x), t_int(y), t_int(z)])),
                named(3, 'state', t_int(0 if y == 0 else 1))]))
root = named(10, '', t_compound([
    named(3, 'DataVersion', t_int(3955)),
    named(9, 'size', t_list(3, [t_int(3), t_int(3), t_int(3)])),
    named(9, 'palette', t_list(10, palette)),
    named(9, 'blocks', t_list(10, blocks)),
    named(9, 'entities', t_list(0, [])),
]))
with gzip.open(sys.argv[1], 'wb') as f:
    f.write(root)
