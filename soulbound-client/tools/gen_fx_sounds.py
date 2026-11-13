"""Generates the star-burst sound effects used by the quick-settings sidebar.

Usage: python3 tools/gen_fx_sounds.py
"""
import subprocess, tempfile, wave
from pathlib import Path
import numpy as np

SND = Path(__file__).resolve().parent.parent / "src/main/resources/assets/soulbound/sounds"
SR = 48000


def env(n, attack, decay):
    t = np.arange(n) / SR
    return np.clip(t / max(attack, 1e-6), 0, 1) * np.exp(-t / decay)


def tone(n, f0, f1=None):
    t = np.arange(n) / SR
    f1 = f0 if f1 is None else f1
    freq = f0 * (f1 / f0) ** (t / (n / SR))
    return np.sin(2 * np.pi * np.cumsum(freq) / SR)


def noise(n, seed):
    return np.random.default_rng(seed).standard_normal(n)


def write(name, x, gain):
    x = x / (np.max(np.abs(x)) + 1e-9) * gain
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as f:
        tmp = f.name
    with wave.open(tmp, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((np.clip(x, -1, 1) * 32767).astype(np.int16).tobytes())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", tmp, "-c:a", "libvorbis", "-q:a", "6", str(SND / f"{name}.ogg")], check=True)
    Path(tmp).unlink()


def shine():
    # a rising sparkle: staggered high chimes over a soft airy swell
    n = int(SR * 0.55)
    out = np.zeros(n)
    for i, f in enumerate((1568, 2093, 2637, 3136, 4186)):
        off = int(SR * 0.06 * i)
        m = n - off
        out[off:] += tone(m, f) * env(m, 0.002, 0.09) * (0.9 - i * 0.1)
    t = np.arange(n) / SR
    air = np.diff(noise(n, 11), prepend=0) * 0.06 * np.sin(np.pi * np.clip(t / 0.5, 0, 1))
    write("shine", out + air, 0.5)


def burst():
    # thump + crackling burst + shimmering tail
    n = int(SR * 0.8)
    t = np.arange(n) / SR
    thump = tone(n, 140, 50) * env(n, 0.002, 0.08) * 0.9
    crack = noise(n, 12) * env(n, 0.0005, 0.05) * 0.7
    crack = np.convolve(crack, np.ones(6) / 6, mode="same")
    sparkle = np.zeros(n)
    rng = np.random.default_rng(13)
    for _ in range(26):
        off = int(rng.uniform(0.02, 0.45) * SR)
        m = int(SR * 0.12)
        if off + m > n:
            continue
        sparkle[off:off + m] += tone(m, rng.uniform(2500, 6000)) * env(m, 0.001, 0.025) * rng.uniform(0.15, 0.4)
    shimmer = sum(tone(n, f, f * 1.25) * 0.12 for f in (1760, 2217, 2637)) * env(n, 0.01, 0.25)
    write("burst", thump + crack + sparkle + shimmer, 0.75)


if __name__ == "__main__":
    shine()
    burst()
    print("ok")
