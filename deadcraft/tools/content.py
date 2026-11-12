"""Reads the Java registries so the asset generator never drifts out of sync with the code."""
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = os.path.join(ROOT, "src/main/java/com/deadcraft")


def _read(rel):
    with open(os.path.join(JAVA, rel)) as f:
        return f.read()


def mobs():
    src = _read("entity/DeadMob.java")
    pat = re.compile(r'^\s+([A-Z_]+)\("([a-z_]+)", Kind\.(\w+), Archetype\.(\w+), Ability\.(\w+), ([\d.]+), ([\d.]+), ([\d.]+), ([\d.]+), '
                     r'([\d.]+)f, ([\d.]+)f, ([\d.]+)f, (0x[0-9a-f]+), (0x[0-9a-f]+), (0x[0-9a-f]+), ([^)]*)\)[,;]', re.M)
    out = []
    for m in pat.finditer(src):
        flags = m.group(16)
        out.append({
            "const": m.group(1), "id": m.group(2), "kind": m.group(3), "archetype": m.group(4).lower(),
            "ability": m.group(5), "health": float(m.group(6)), "base": int(m.group(13), 16),
            "accent": int(m.group(14), 16), "eye": int(m.group(15), 16),
            "features": set(re.findall(r"Features\.(\w+)", flags)),
        })
    assert len(out) == 41, f"expected 41 creatures, parsed {len(out)}"
    return out


def items():
    """(id, kind) for every item registered in ModItems, in registration order."""
    src = _read("registry/ModItems.java")
    out = []
    for m in re.finditer(r'(material|food|weapon|ammo|gun|spell|armor|riftKey|sigil)\("([a-z_]+)"', src):
        out.append((m.group(2), m.group(1)))
    for m in re.finditer(r'registerItem\("([a-z_]+)"', src):
        out.append((m.group(1), "special"))
    return out


def blocks():
    src = _read("registry/ModBlocks.java")
    return re.findall(r'(?:ore|storage|terrain|plant|registerBlock)\("([a-z_]+)"', src)


def sounds():
    src = _read("registry/ModSounds.java")
    return [s for s in re.findall(r'register\("([a-z_.]+)"\)', src) if s.strip(".")]


def particles():
    src = _read("registry/ModParticles.java")
    return re.findall(r'register\("([a-z_]+)"\)', src)
