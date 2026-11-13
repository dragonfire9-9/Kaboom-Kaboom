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


def reverb(x, delays=(0.031, 0.047, 0.061, 0.083), decay=0.42, tail=0.6):
    """Tiny feedback-comb reverb for a sense of space."""
    out = np.concatenate([x, np.zeros(int(SR * tail))])
    wet = np.zeros_like(out)
    for d in delays:
        n = int(SR * d)
        buf = out.copy()
        for k in range(1, 8):
            off = n * k
            if off >= len(out):
                break
            wet[off:] += buf[:len(out) - off] * (decay ** k) / len(delays)
    return out * 0.8 + wet


def lowpass(x, a=0.08):
    y = np.zeros_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc += a * (v - acc)
        y[i] = acc
    return y


def bell(n, f, decay):
    # bell-like partials with slight detune
    return (tone(n, f) + 0.45 * tone(n, f * 2.01) + 0.18 * tone(n, f * 3.02)) * env(n, 0.004, decay)


def shine():
    # a soft, glassy two-note chime (E6 then B6) with a breath of air
    n = int(SR * 0.6)
    out = bell(n, 1318.5, 0.16) * 0.7
    off = int(SR * 0.075)
    out[off:] += bell(n - off, 1975.5, 0.2)[: n - off] * 0.55
    t = np.arange(n) / SR
    air = lowpass(noise(n, 21), 0.25) * 0.05 * np.sin(np.pi * np.clip(t / 0.35, 0, 1))
    write("shine", reverb(out + air), 0.42)


def burst():
    # an airy bloom: soft low-passed swell, a gentle sub pulse and a bright open chord
    n = int(SR * 0.7)
    t = np.arange(n) / SR
    swell = lowpass(noise(n, 22), 0.05) * env(n, 0.012, 0.16) * 2.2
    sub = tone(n, 90, 55) * env(n, 0.003, 0.09) * 0.35
    chord = sum(bell(n, f, 0.32) * g for f, g in ((1046.5, 0.45), (1568.0, 0.4), (2093.0, 0.32), (2637.0, 0.22)))
    shimmer = tone(n, 4186, 4700) * env(n, 0.02, 0.12) * 0.06
    write("burst", reverb(swell + sub + chord + shimmer), 0.6)


if __name__ == "__main__":
    shine()
    burst()
    print("ok")
