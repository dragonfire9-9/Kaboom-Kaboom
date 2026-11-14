"""Renders SoulBound's interface sounds by modelling small physical objects, not tones.

Every sound is built the way the real thing makes noise:
  * an impact transient (a millisecond of filtered noise: the moment two parts touch),
  * a handful of damped, inharmonic resonances (the "body": plastic key, switch, glass),
  * filtered-noise motion for swishes (air and paper moving),
  * a tiny room (early reflections and a short diffuse tail) so nothing sounds dry and synthetic.
Three variants of each are rendered with small random differences (stiffness, size, strike
position); Minecraft picks one at random each time, so repeats never sound identical.

Usage: python3 tools/gen_ui_sounds.py     (needs numpy and ffmpeg with libvorbis)
"""
import json
import subprocess
import tempfile
import wave
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/assets/soulbound/sounds"
SR = 44100
VARIANTS = 3


# ------------------------------------------------------------------ building blocks
def t_axis(dur):
    return np.arange(int(SR * dur)) / SR


def onepole_lp(x, cutoff):
    a = np.exp(-2 * np.pi * cutoff / SR)
    y = np.empty_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc = (1 - a) * v + a * acc
        y[i] = acc
    return y


def onepole_hp(x, cutoff):
    return x - onepole_lp(x, cutoff)


def bandpass_fft(x, lo, hi, soft=0.25):
    """Smooth-edged band-pass in the frequency domain."""
    n = len(x)
    f = np.fft.rfftfreq(n, 1 / SR)
    spec = np.fft.rfft(x)
    lo_w = 1 / (1 + np.exp(-(np.log(f + 1) - np.log(lo)) / soft))
    hi_w = 1 / (1 + np.exp((np.log(f + 1) - np.log(hi)) / soft))
    return np.fft.irfft(spec * lo_w * hi_w, n)


def transient(rng, dur=0.004, tau=0.0005, hp=1500, lp=9000, amp=1.0):
    """A contact click: a burst of noise that dies in a fraction of a millisecond."""
    t = t_axis(dur)
    n = rng.standard_normal(len(t)) * np.exp(-t / tau)
    n = onepole_hp(n, hp)
    n = onepole_lp(n, lp)
    return amp * n / (np.max(np.abs(n)) + 1e-9)


def modes(rng, dur, freqs, taus, amps, detune=0.015):
    """Damped resonances with slightly random tuning and phase (a real object never rings the same twice)."""
    t = t_axis(dur)
    out = np.zeros(len(t))
    for f, tau, a in zip(freqs, taus, amps):
        f *= 1 + rng.uniform(-detune, detune)
        tau *= 1 + rng.uniform(-0.15, 0.15)
        out += a * np.sin(2 * np.pi * f * t + rng.uniform(0, 2 * np.pi)) * np.exp(-t / tau)
    return tail_fade(out)


def tail_fade(x, frac=0.3):
    """Ease the last part of a component to silence so nothing is ever cut off mid-ring."""
    n = max(1, int(len(x) * frac))
    x[-n:] *= np.cos(np.linspace(0, np.pi / 2, n)) ** 2
    return x


def swish(rng, dur, f0, f1, attack, release, width=0.9):
    """Air moving past something: noise through a band that glides from f0 to f1."""
    t = t_axis(dur)
    n = rng.standard_normal(len(t))
    out = np.zeros(len(t))
    # glide by cross-fading a few overlapping bands (smooth, no filter zipper)
    steps = 10
    for k in range(steps):
        c = f0 * (f1 / f0) ** (k / (steps - 1))
        band = bandpass_fft(n, c / (1 + width), c * (1 + width), 0.18)
        centre = k / (steps - 1) * dur
        w = np.exp(-0.5 * ((t - centre) / (dur / steps * 1.2)) ** 2)
        out += band * w
    env = np.minimum(1, t / attack) * np.exp(-np.maximum(0, t - attack) / release)
    return tail_fade(out * env / (np.max(np.abs(out)) + 1e-9), 0.2)


def place(buf, sig, at, gain=1.0):
    i = int(at * SR)
    end = min(len(buf), i + len(sig))
    buf[i:end] += gain * sig[: end - i]


def room(x, rng, size=1.0, wet=0.16):
    """A small, soft room: a few early reflections plus a short diffuse tail."""
    y = x.copy()
    for d, g in ((0.0071, 0.32), (0.0113, 0.24), (0.0167, 0.17), (0.0239, 0.11)):
        d *= size * (1 + rng.uniform(-0.05, 0.05))
        i = int(d * SR)
        r = np.zeros_like(x)
        r[i:] = x[: len(x) - i]
        y += wet * g * onepole_lp(r, 5200)
    tail_len = int(0.09 * size * SR)
    tail = rng.standard_normal(tail_len) * np.exp(-np.arange(tail_len) / (0.022 * size * SR))
    tail = onepole_lp(tail, 4000)
    y += wet * 0.05 * np.convolve(x, tail)[: len(x)] / (np.sqrt(tail_len) + 1e-9)
    return y


def finish(x, peak_db=-3.0, fade=0.015):
    # remove DC and sub-bass drift left by the filters (a gentle 2-pass high-pass at 35 Hz)
    x = onepole_hp(onepole_hp(x, 35)[::-1], 35)[::-1]
    n = int(fade * SR)
    if len(x) > 2 * n:
        x[-n:] *= np.linspace(1, 0, n) ** 2
        x[:16] *= np.linspace(0, 1, 16)
    peak = np.max(np.abs(x)) + 1e-9
    return x / peak * 10 ** (peak_db / 20)


# ------------------------------------------------------------------ the sounds
def hover(rng):
    """A fingertip brushing a soft-touch key: barely there."""
    buf = np.zeros(int(SR * 0.06))
    place(buf, transient(rng, 0.003, 0.0003, 2500, 7000, 0.7), 0)
    place(buf, modes(rng, 0.05, [2350, 3900, 6100], [0.0042, 0.0026, 0.0012], [0.35, 0.18, 0.06]), 0.0003)
    return finish(room(buf, rng, 0.7, 0.10), -9)


def click(rng):
    """A good mouse switch: a crisp press, then a quieter, slightly higher release."""
    buf = np.zeros(int(SR * 0.14))
    k = rng.uniform(0.97, 1.03)
    press = transient(rng, 0.004, 0.00045, 1800, 9500) * 0.9
    press_body = modes(rng, 0.06, [1880 * k, 3350 * k, 5300 * k, 7400 * k], [0.009, 0.006, 0.0032, 0.0015], [0.55, 0.32, 0.16, 0.05])
    place(buf, press, 0)
    place(buf, press_body, 0.0002)
    rel = rng.uniform(0.034, 0.042)
    place(buf, transient(rng, 0.003, 0.00035, 2200, 9000) * 0.42, rel)
    place(buf, modes(rng, 0.04, [2050 * k, 3600 * k, 5700 * k], [0.006, 0.004, 0.002], [0.25, 0.13, 0.05]), rel + 0.0002)
    return finish(room(buf, rng, 0.8, 0.14), -4)


def type_(rng):
    """A keyboard key: a soft top, then the dull bottom-out."""
    buf = np.zeros(int(SR * 0.09))
    k = rng.uniform(0.94, 1.06)
    place(buf, transient(rng, 0.003, 0.0004, 1200, 8000) * 0.6, 0)
    place(buf, modes(rng, 0.06, [920 * k, 1960 * k, 3700 * k], [0.012, 0.007, 0.0035], [0.5, 0.28, 0.1]), 0.0003)
    thud = onepole_lp(rng.standard_normal(int(SR * 0.012)) * np.exp(-t_axis(0.012) / 0.002), 900)
    place(buf, thud / (np.max(np.abs(thud)) + 1e-9) * 0.35, 0.004)
    return finish(room(buf, rng, 0.7, 0.12), -6)


def toggle_on(rng):
    """A small latching switch going on: a rounded thock, then the detent catching higher."""
    buf = np.zeros(int(SR * 0.16))
    k = rng.uniform(0.97, 1.03)
    place(buf, transient(rng, 0.004, 0.0006, 900, 6000) * 0.8, 0)
    place(buf, modes(rng, 0.09, [720 * k, 1540 * k, 2650 * k, 4100 * k], [0.016, 0.009, 0.005, 0.0025], [0.6, 0.34, 0.16, 0.06]), 0.0004)
    d = rng.uniform(0.045, 0.055)
    place(buf, transient(rng, 0.003, 0.0003, 2600, 10000) * 0.55, d)
    place(buf, modes(rng, 0.05, [2700 * k, 4450 * k, 6900 * k], [0.006, 0.0035, 0.0016], [0.32, 0.17, 0.06]), d + 0.0002)
    return finish(room(buf, rng, 0.85, 0.15), -3.5)


def toggle_off(rng):
    """The same switch going off: the detent lets go first, then a softer, more damped thock."""
    buf = np.zeros(int(SR * 0.15))
    k = rng.uniform(0.97, 1.03)
    place(buf, transient(rng, 0.003, 0.0003, 2400, 9000) * 0.5, 0)
    place(buf, modes(rng, 0.04, [2450 * k, 4000 * k], [0.005, 0.003], [0.28, 0.12]), 0.0002)
    d = rng.uniform(0.04, 0.05)
    place(buf, transient(rng, 0.004, 0.0007, 700, 5000) * 0.7, d)
    place(buf, modes(rng, 0.08, [610 * k, 1320 * k, 2300 * k], [0.011, 0.0065, 0.0035], [0.55, 0.28, 0.1]), d + 0.0004)
    return finish(room(buf, rng, 0.85, 0.15), -4.5)


def open_(rng):
    """A panel sliding open: a soft rising swish that settles with a light glassy tap."""
    dur = 0.42
    buf = np.zeros(int(SR * dur))
    place(buf, swish(rng, 0.30, 700, 2600, 0.07, 0.11) * 0.55, 0)
    k = rng.uniform(0.98, 1.02)
    tap_at = rng.uniform(0.2, 0.22)
    place(buf, transient(rng, 0.003, 0.0003, 3000, 11000) * 0.25, tap_at)
    place(buf, modes(rng, 0.2, [3150 * k, 3150 * 2.32 * k, 3150 * 4.25 * k], [0.09, 0.04, 0.018], [0.22, 0.09, 0.04], 0.004), tap_at)
    return finish(room(buf, rng, 1.0, 0.2), -5)


def close_(rng):
    """The panel sliding shut: a falling swish ending in a soft, low tuck."""
    dur = 0.34
    buf = np.zeros(int(SR * dur))
    place(buf, swish(rng, 0.24, 2300, 650, 0.03, 0.09) * 0.5, 0)
    at = rng.uniform(0.17, 0.19)
    place(buf, transient(rng, 0.004, 0.0007, 500, 4000) * 0.45, at)
    place(buf, modes(rng, 0.09, [520, 1180, 2050], [0.02, 0.011, 0.006], [0.45, 0.2, 0.07]), at + 0.0004)
    return finish(room(buf, rng, 1.0, 0.18), -5.5)


def slide(rng):
    """One notch of a slider detent: tiny and dry."""
    buf = np.zeros(int(SR * 0.035))
    place(buf, transient(rng, 0.002, 0.00025, 2500, 9000) * 0.6, 0)
    place(buf, modes(rng, 0.03, [3050, 5200], [0.0022, 0.0012], [0.3, 0.1]), 0.0002)
    return finish(room(buf, rng, 0.6, 0.08), -10)


def shine(rng):
    """A small glass struck with a soft mallet: inharmonic partials, a slow shimmer from two
    nearly-equal modes beating against each other, and a gentle sympathetic ring."""
    dur = 1.6
    t = t_axis(dur)
    f0 = 1640 * rng.uniform(0.99, 1.01)
    ratios = [1.0, 2.32, 4.25, 6.63, 9.38]
    taus = [0.75, 0.38, 0.19, 0.1, 0.05]
    amps = [1.0, 0.42, 0.2, 0.09, 0.04]
    ring = np.zeros(len(t))
    for r, tau, a in zip(ratios, taus, amps):
        for split, w in ((1.0, 0.6), (1.0 + 0.0009 * r, 0.4)):  # degenerate mode pair -> slow shimmer
            ring += a * w * np.sin(2 * np.pi * f0 * r * split * t + rng.uniform(0, 6.28)) * np.exp(-t / tau)
    strike = onepole_lp(rng.standard_normal(int(SR * 0.004)) * np.exp(-t_axis(0.004) / 0.0007), 6000)
    ring = tail_fade(ring, 0.4)
    buf = np.zeros(len(t))
    place(buf, strike / (np.max(np.abs(strike)) + 1e-9) * 0.3, 0)
    place(buf, ring / np.max(np.abs(ring)), 0.0008)
    # a second, much quieter glass nearby picking up the note
    sym = modes(rng, dur - 0.05, [f0 * 1.335, f0 * 1.335 * 2.32], [0.6, 0.25], [0.12, 0.04], 0.003)
    place(buf, sym, 0.05)
    env = np.minimum(1, t / 0.002)
    return finish(room(buf * env, rng, 1.4, 0.22), -6)


def burst(rng):
    """A soft bloom: a quick breath of air opening up, a low wooden knock underneath, and
    the glass from the shine still ringing faintly through it."""
    dur = 1.0
    buf = np.zeros(int(SR * dur))
    air = swish(rng, 0.6, 400, 5200, 0.025, 0.2, 1.4)
    place(buf, air * 0.6, 0)
    place(buf, transient(rng, 0.006, 0.0012, 200, 3000) * 0.6, 0.004)
    place(buf, modes(rng, 0.3, [180, 420, 1130], [0.06, 0.05, 0.025], [0.35, 0.45, 0.15]), 0.005)
    place(buf, modes(rng, 0.9, [1640 * 1.5, 1640 * 1.5 * 2.32, 1640 * 1.5 * 4.25], [0.42, 0.2, 0.09], [0.16, 0.06, 0.025], 0.004), 0.02)
    return finish(room(buf, rng, 1.5, 0.24), -4)


SOUNDS = {
    "hover": hover, "click": click, "type": type_, "toggle_on": toggle_on, "toggle_off": toggle_off,
    "open": open_, "close": close_, "slide": slide, "shine": shine, "burst": burst,
}


def write_ogg(path, x):
    pcm = (np.clip(x, -1, 1) * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        with wave.open(tmp.name, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", tmp.name, "-c:a", "libvorbis", "-q:a", "6", str(path)], check=True)
        Path(tmp.name).unlink()


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for old in OUT.glob("*.ogg"):
        old.unlink()
    manifest = {}
    for name, fn in SOUNDS.items():
        files = []
        for v in range(VARIANTS):
            rng = np.random.default_rng(1000 * (list(SOUNDS).index(name) + 1) + v)
            write_ogg(OUT / f"{name}_{v + 1}.ogg", fn(rng))
            files.append(f"soulbound:{name}_{v + 1}")
        manifest[f"ui.{name}"] = {"sounds": files}
        print("wrote", name)
    (OUT.parent / "sounds.json").write_text(json.dumps(manifest, indent=2) + "\n")
