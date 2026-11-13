"""Generates SoulBound's GUI icons and UI sound effects.

Usage: python3 tools/gen_assets.py [path-to-logo-image] [--sounds]
Icons are drawn white-on-transparent at 128px; the client resamples them to the
exact on-screen pixel size at runtime so they stay crisp at any GUI scale.
"""
import math, subprocess, sys, tempfile, wave
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
TEX = ROOT / "src/main/resources/assets/soulbound/textures/gui"
SND = ROOT / "src/main/resources/assets/soulbound/sounds"
TEX.mkdir(parents=True, exist_ok=True)
SND.mkdir(parents=True, exist_ok=True)

N = 128          # output size
SS = 4           # supersampling factor
W = (255, 255, 255, 255)


def canvas():
    img = Image.new("RGBA", (N * SS, N * SS), (0, 0, 0, 0))
    return img, ImageDraw.Draw(img)


def s(v):
    return v * SS


def save(img, name):
    img.resize((N, N), Image.LANCZOS).save(TEX / f"{name}.png")


def line(d, pts, w):
    d.line([(s(x), s(y)) for x, y in pts], fill=W, width=s(w), joint="curve")
    for x, y in (pts[0], pts[-1]):
        r = s(w) / 2
        d.ellipse([s(x) - r, s(y) - r, s(x) + r, s(y) + r], fill=W)


def ring(d, cx, cy, r, w):
    d.ellipse([s(cx - r), s(cy - r), s(cx + r), s(cy + r)], outline=W, width=s(w))


def dot(d, cx, cy, r):
    d.ellipse([s(cx - r), s(cy - r), s(cx + r), s(cy + r)], fill=W)


def icon_combat():
    img, d = canvas()
    ring(d, 64, 64, 40, 10)
    dot(d, 64, 64, 9)
    for a, b in (((64, 6), (64, 30)), ((64, 98), (64, 122)), ((6, 64), (30, 64)), ((98, 64), (122, 64))):
        line(d, [a, b], 10)
    save(img, "icon_combat")


def icon_visual():
    img, d = canvas()
    # almond eye outline built from two arcs
    pts_top = [(14 + t * 100, 64 - 38 * math.sin(math.pi * t)) for t in np.linspace(0, 1, 60)]
    pts_bot = [(14 + t * 100, 64 + 38 * math.sin(math.pi * t)) for t in np.linspace(0, 1, 60)]
    line(d, pts_top, 10)
    line(d, pts_bot, 10)
    ring(d, 64, 64, 18, 10)
    dot(d, 64, 64, 6)
    save(img, "icon_visual")


def icon_util():
    img, d = canvas()
    for x in (16, 70):
        for y in (16, 70):
            d.rounded_rectangle([s(x), s(y), s(x + 42), s(y + 42)], radius=s(11), fill=W)
    save(img, "icon_util")


def icon_movement():
    img, d = canvas()
    d.polygon([(s(x), s(y)) for x, y in [(76, 6), (24, 72), (60, 72), (50, 122), (104, 52), (68, 52), (80, 6)]], fill=W)
    save(img, "icon_movement")


def icon_settings():
    img, d = canvas()
    for y, kx in ((34, 84), (64, 44), (94, 74)):
        line(d, [(14, y), (114, y)], 9)
        dot(d, kx, y, 15)
    save(img, "icon_settings")


def icon_search():
    img, d = canvas()
    ring(d, 54, 54, 34, 11)
    line(d, [(80, 80), (112, 112)], 12)
    save(img, "icon_search")


def icon_close():
    img, d = canvas()
    line(d, [(28, 28), (100, 100)], 12)
    line(d, [(100, 28), (28, 100)], 12)
    save(img, "icon_close")


def icon_gear():
    img, d = canvas()
    cx = cy = 64
    pts = []
    teeth = 8
    for i in range(teeth * 4):
        a = i / (teeth * 4) * 2 * math.pi
        r = 54 if (i % 4) in (0, 1) else 40
        pts.append((cx + r * math.cos(a - math.pi / teeth / 2), cy + r * math.sin(a - math.pi / teeth / 2)))
    d.polygon([(s(x), s(y)) for x, y in pts], fill=W)
    d.ellipse([s(cx - 18), s(cy - 18), s(cx + 18), s(cy + 18)], fill=(0, 0, 0, 0))
    save(img, "icon_gear")


def icon_chevron():
    img, d = canvas()
    line(d, [(28, 46), (64, 82), (100, 46)], 13)
    save(img, "icon_chevron")


def icon_keyboard():
    img, d = canvas()
    d.rounded_rectangle([s(8), s(28), s(120), s(100)], radius=s(16), outline=W, width=s(9))
    for row, y in enumerate((48, 64)):
        for i in range(5):
            dot(d, 30 + i * 17, y, 5)
    line(d, [(42, 82), (86, 82)], 9)
    save(img, "icon_keyboard")


def icon_bots():
    img, d = canvas()
    d.rounded_rectangle([s(14), s(38), s(114), s(112)], radius=s(22), outline=W, width=s(10))
    dot(d, 46, 74, 10)
    dot(d, 82, 74, 10)
    line(d, [(64, 38), (64, 20)], 9)
    dot(d, 64, 14, 10)
    line(d, [(4, 66), (4, 86)], 8)
    line(d, [(124, 66), (124, 86)], 8)
    save(img, "icon_bots")


def icon_sword():
    img, d = canvas()
    line(d, [(30, 98), (106, 22)], 13)
    d.polygon([(s(x), s(y)) for x, y in [(98, 14), (116, 12), (114, 30)]], fill=W)
    line(d, [(22, 78), (50, 106)], 11)
    line(d, [(36, 92), (16, 112)], 11)
    save(img, "icon_sword")


def icon_check():
    img, d = canvas()
    line(d, [(24, 66), (52, 94), (106, 36)], 16)
    save(img, "icon_check")


def icon_theme():
    img, d = canvas()
    d.ellipse([s(8), s(8), s(120), s(120)], outline=W, width=s(10))
    for cx, cy in ((44, 42), (80, 38), (96, 70)):
        dot(d, cx, cy, 11)
    d.ellipse([s(34), s(68), s(70), s(104)], fill=W)
    save(img, "icon_theme")


def icon_play():
    img, d = canvas()
    d.rounded_rectangle([s(26), s(14), s(108), s(114)], radius=s(4), fill=(0, 0, 0, 0))
    d.polygon([(s(34), s(16)), (s(110), s(64)), (s(34), s(112))], fill=W)
    save(img, "icon_play")


def icon_globe():
    img, d = canvas()
    ring(d, 64, 64, 52, 10)
    d.ellipse([s(40), s(12), s(88), s(116)], outline=W, width=s(9))
    line(d, [(14, 64), (114, 64)], 9)
    line(d, [(24, 38), (104, 38)], 7)
    line(d, [(24, 90), (104, 90)], 7)
    save(img, "icon_globe")


def icon_realms():
    img, d = canvas()
    d.polygon([(s(64), s(10)), (s(116), s(40)), (s(116), s(88)), (s(64), s(118)), (s(12), s(88)), (s(12), s(40))], outline=W, width=s(10))
    line(d, [(14, 40), (64, 68), (114, 40)], 9)
    line(d, [(64, 68), (64, 116)], 9)
    save(img, "icon_realms")


def icon_power():
    img, d = canvas()
    d.arc([s(14), s(18), s(114), s(118)], start=-55, end=235, fill=W, width=s(11))
    line(d, [(64, 8), (64, 60)], 12)
    save(img, "icon_power")


def icon_crystal():
    img, d = canvas()
    pts = [(64, 6), (106, 50), (64, 122), (22, 50)]
    d.polygon([(s(x), s(y)) for x, y in pts], outline=W, width=s(9))
    line(d, [(22, 50), (106, 50)], 8)
    line(d, [(64, 6), (48, 50), (64, 122)], 7)
    line(d, [(64, 6), (80, 50), (64, 122)], 7)
    save(img, "icon_crystal")


def icon_misc():
    img, d = canvas()
    for cx in (22, 64, 106):
        dot(d, cx, 64, 14)
    save(img, "icon_misc")


def logo(src):
    img = Image.open(src).convert("RGBA")
    w, h = img.size
    side = min(w, h)
    left, top = (w - side) // 2, (h - side) // 2
    sq = img.crop((left, top, left + side, top + side))
    # soften the square corners of the source art with a rounded alpha mask
    big = sq.resize((512, 512), Image.LANCZOS)
    mask = Image.new("L", (2048, 2048), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, 2047, 2047], radius=420, fill=255)
    big.putalpha(mask.resize((512, 512), Image.LANCZOS))
    big.resize((256, 256), Image.LANCZOS).save(ROOT / "src/main/resources/assets/soulbound/icon.png")
    big.save(TEX / "logo.png")


# ---------------------------------------------------------------- audio
SR = 48000


def env(n, attack, decay):
    t = np.arange(n) / SR
    a = np.clip(t / max(attack, 1e-6), 0, 1)
    return a * np.exp(-t / decay)


def hp_noise(n, seed):
    rng = np.random.default_rng(seed)
    x = rng.standard_normal(n)
    return np.diff(x, prepend=0.0)  # crude high-pass -> crisp, airy texture


def write_ogg(name, x, gain=0.8):
    x = x / (np.max(np.abs(x)) + 1e-9) * gain
    pcm = (np.clip(x, -1, 1) * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as f:
        tmp = f.name
    with wave.open(tmp, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", tmp, "-c:a", "libvorbis", "-q:a", "6",
                    str(SND / f"{name}.ogg")], check=True)
    Path(tmp).unlink()


def tone(n, f0, f1=None):
    t = np.arange(n) / SR
    f1 = f0 if f1 is None else f1
    freq = f0 * (f1 / f0) ** (t / (n / SR))
    return np.sin(2 * np.pi * np.cumsum(freq) / SR)


def sounds():
    # hover: tiny glassy tick
    n = int(SR * 0.045)
    hover = 0.55 * tone(n, 4200) * env(n, 0.0008, 0.006) + 0.25 * hp_noise(n, 1) * env(n, 0.0003, 0.0025)
    write_ogg("hover", hover, 0.45)

    # click: crisp transient + short tonal body
    n = int(SR * 0.09)
    click = (0.9 * hp_noise(n, 2) * env(n, 0.0002, 0.0018)
             + 0.6 * tone(n, 1900, 1500) * env(n, 0.0005, 0.012)
             + 0.25 * tone(n, 3800) * env(n, 0.0005, 0.006))
    write_ogg("click", click, 0.8)

    # toggle on: two-step upward sparkle
    n = int(SR * 0.16)
    a = tone(n, 1320) * env(n, 0.001, 0.03)
    b = np.zeros(n)
    off = int(SR * 0.045)
    b[off:] = (tone(n - off, 1980) * env(n - off, 0.001, 0.045))
    on = 0.6 * a + 0.55 * b + 0.4 * hp_noise(n, 3) * env(n, 0.0002, 0.0015)
    write_ogg("toggle_on", on, 0.7)

    # toggle off: downward pair, softer
    n = int(SR * 0.14)
    a = tone(n, 1760) * env(n, 0.001, 0.025)
    b = np.zeros(n)
    b[off:] = tone(n - off, 1175) * env(n - off, 0.001, 0.035)
    write_ogg("toggle_off", 0.55 * a + 0.55 * b + 0.35 * hp_noise(n, 4) * env(n, 0.0002, 0.0015), 0.6)

    # open: airy shimmer swell with a soft rising tone
    n = int(SR * 0.42)
    t = np.arange(n) / SR
    swell = np.sin(np.pi * np.clip(t / 0.42, 0, 1)) ** 2
    shimmer = sum(tone(n, f, f * 1.5) * 0.25 for f in (880, 1320, 1760, 2640))
    air = hp_noise(n, 5) * 0.18
    write_ogg("open", (shimmer + air) * swell * np.exp(-t / 0.35), 0.55)

    # close: quick falling whoosh
    n = int(SR * 0.24)
    t = np.arange(n) / SR
    fall = sum(tone(n, f, f * 0.6) * 0.3 for f in (1760, 2640))
    write_ogg("close", (fall + hp_noise(n, 6) * 0.15) * env(n, 0.004, 0.07), 0.45)

    # slide: very short soft tick used while dragging sliders
    n = int(SR * 0.03)
    write_ogg("slide", tone(n, 2600) * env(n, 0.0005, 0.004) + 0.2 * hp_noise(n, 7) * env(n, 0.0002, 0.0015), 0.3)

    # type: keyboard-ish tick for the search box
    n = int(SR * 0.03)
    write_ogg("type", hp_noise(n, 8) * env(n, 0.0002, 0.0022) + 0.3 * tone(n, 3100) * env(n, 0.0003, 0.003), 0.35)


if __name__ == "__main__":
    # icons now live in tools/gen_icons.py
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if args:
        logo(args[0])
    if "--sounds" in sys.argv:
        sounds()
    print("ok")
