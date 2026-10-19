"""Tiny NBT writer (enough for structure templates). Values are wrapped in typed helpers."""
import gzip, struct

class Byte(int): pass
class Short(int): pass
class Int(int): pass
class Long(int): pass
class Float(float): pass
class Double(float): pass
class IntList(list): pass       # TAG_List of TAG_Int
class DoubleList(list): pass    # TAG_List of TAG_Double

def _s(v):
    b = v.encode("utf-8")
    return struct.pack(">H", len(b)) + b

def _tag_id(v):
    if isinstance(v, Byte) or isinstance(v, bool): return 1
    if isinstance(v, Short): return 2
    if isinstance(v, Long): return 4
    if isinstance(v, Float): return 5
    if isinstance(v, Double): return 6
    if isinstance(v, (Int, int)): return 3
    if isinstance(v, float): return 6
    if isinstance(v, str): return 8
    if isinstance(v, (IntList, DoubleList, list)): return 9
    if isinstance(v, dict): return 10
    raise TypeError(type(v))

def _payload(v):
    t = _tag_id(v)
    if t == 1: return struct.pack(">b", int(v))
    if t == 2: return struct.pack(">h", v)
    if t == 3: return struct.pack(">i", v)
    if t == 4: return struct.pack(">q", v)
    if t == 5: return struct.pack(">f", v)
    if t == 6: return struct.pack(">d", v)
    if t == 8: return _s(v)
    if t == 9:
        if isinstance(v, IntList): et, items = 3, [Int(x) for x in v]
        elif isinstance(v, DoubleList): et, items = 6, [Double(x) for x in v]
        elif not v: et, items = 0, []
        else: et, items = _tag_id(v[0]), v
        return bytes([et]) + struct.pack(">i", len(items)) + b"".join(_payload(i) for i in items)
    if t == 10:
        return b"".join(bytes([_tag_id(val)]) + _s(k) + _payload(val) for k, val in v.items()) + b"\x00"

def write(path, root):
    with gzip.open(path, "wb") as f:
        f.write(bytes([10]) + _s("") + _payload(root))
