#!/usr/bin/env python3
"""Generate SoulBound's placeholder sound effects and music loops.

Pure standard library (no numpy), so it runs anywhere Python 3.8+ exists:

    python3 tools/gen_audio.py

Writes 16-bit mono WAV files into assets/audio/{sfx,music}. Every sound is
built from tiny sfxr-style building blocks (oscillators, noise, pitch sweeps,
envelopes). Swap any file for a real recording with the same name and the game
picks it up without code changes. See ASSETS.md for free replacement sources.
"""

import math
import os
import random
import struct
import wave

RATE = 22050
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "assets", "audio")

random.seed(1337)  # deterministic output => reproducible commits


# --------------------------------------------------------------------------
# Building blocks
# --------------------------------------------------------------------------

def osc(kind, phase):
    """One sample of a periodic waveform; `phase` is in cycles."""
    p = phase % 1.0
    if kind == "sine":
        return math.sin(2 * math.pi * p)
    if kind == "square":
        return 1.0 if p < 0.5 else -1.0
    if kind == "saw":
        return 2.0 * p - 1.0
    if kind == "tri":
        return 4.0 * abs(p - 0.5) - 1.0
    raise ValueError(kind)


def env(t, dur, attack=0.005, release=None):
    """Attack / exponential-ish decay envelope in [0, 1]."""
    if t < attack:
        return t / attack
    release = release if release is not None else dur - attack
    x = (t - attack) / max(release, 1e-6)
    return max(0.0, 1.0 - x) ** 2


def tone(dur, f0, f1=None, kind="sine", vol=0.6, attack=0.005, vibrato=0.0, vib_hz=6.0):
    """Pitch-swept oscillator with optional vibrato."""
    f1 = f0 if f1 is None else f1
    n = int(dur * RATE)
    out, phase = [], 0.0
    for i in range(n):
        t = i / RATE
        k = t / dur
        f = f0 * (f1 / f0) ** k  # exponential sweep sounds natural
        f *= 1.0 + vibrato * math.sin(2 * math.pi * vib_hz * t)
        phase += f / RATE
        out.append(osc(kind, phase) * env(t, dur, attack) * vol)
    return out


def noise(dur, vol=0.5, attack=0.002, lowpass=1.0):
    """White noise through a one-pole low-pass (`lowpass` 0..1, 1 = open)."""
    n = int(dur * RATE)
    out, y = [], 0.0
    for i in range(n):
        t = i / RATE
        y += lowpass * (random.uniform(-1, 1) - y)
        out.append(y * env(t, dur, attack) * vol)
    return out


def sweep_noise(dur, lp0, lp1, vol=0.5, attack=0.03):
    """Noise whose low-pass opens/closes over time — a 'whoosh'."""
    n = int(dur * RATE)
    out, y = [], 0.0
    for i in range(n):
        t = i / RATE
        lp = lp0 + (lp1 - lp0) * (t / dur)
        y += lp * (random.uniform(-1, 1) - y)
        # symmetric swell envelope
        e = math.sin(math.pi * min(1.0, t / dur)) ** 1.5
        out.append(y * e * vol)
    return out


def mix(*tracks, offsets=None):
    """Sum tracks (optionally delayed by `offsets` seconds)."""
    offsets = offsets or [0.0] * len(tracks)
    length = max(len(t) + int(o * RATE) for t, o in zip(tracks, offsets))
    out = [0.0] * length
    for t, o in zip(tracks, offsets):
        s = int(o * RATE)
        for i, v in enumerate(t):
            out[s + i] += v
    return out


def write(path, samples, peak=0.85):
    """Normalise to `peak` and write 16-bit mono WAV."""
    m = max(1e-9, max(abs(s) for s in samples))
    g = peak / m
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, s * g)) * 32767)) for s in samples))
    print(f"wrote {os.path.relpath(path)}  ({len(samples) / RATE:.2f}s)")


# --------------------------------------------------------------------------
# Sound effects
# --------------------------------------------------------------------------

def sfx():
    d = os.path.join(ROOT, "sfx")
    write(os.path.join(d, "ui_click.wav"), mix(tone(0.06, 1400, 900, "square", 0.3), tone(0.05, 700, 500, "sine", 0.5)), 0.6)
    write(os.path.join(d, "ui_hover.wav"), tone(0.04, 900, 1100, "sine", 0.4), 0.4)
    write(os.path.join(d, "soul_bolt.wav"), mix(tone(0.18, 1100, 320, "tri", 0.6), noise(0.08, 0.25, lowpass=0.3)))
    write(os.path.join(d, "charge_loop.wav"), tone(0.6, 220, 660, "saw", 0.35, attack=0.05, vibrato=0.02, vib_hz=18))
    write(os.path.join(d, "charged_shot.wav"), mix(tone(0.45, 180, 60, "square", 0.5), noise(0.35, 0.6, lowpass=0.15), tone(0.25, 1600, 400, "sine", 0.3)))
    write(os.path.join(d, "hit.wav"), mix(noise(0.09, 0.8, lowpass=0.6), tone(0.12, 160, 70, "sine", 0.9)))
    write(os.path.join(d, "hit_heavy.wav"), mix(noise(0.2, 0.9, lowpass=0.35), tone(0.28, 120, 40, "square", 0.6)))
    write(os.path.join(d, "dash.wav"), sweep_noise(0.26, 0.05, 0.6, 0.8))
    write(os.path.join(d, "player_hurt.wav"), mix(tone(0.22, 260, 90, "square", 0.5), noise(0.1, 0.4, lowpass=0.4)))
    write(os.path.join(d, "enemy_death.wav"), mix(tone(0.45, 600, 80, "saw", 0.45), noise(0.4, 0.5, lowpass=0.2)))
    # Soul absorb: quick rising minor arpeggio with shimmer.
    notes = [440, 523.25, 659.25, 880]
    arp = mix(*[tone(0.18, f, f * 1.01, "sine", 0.4, vibrato=0.01, vib_hz=12) for f in notes],
              offsets=[i * 0.06 for i in range(len(notes))])
    write(os.path.join(d, "soul_absorb.wav"), arp, 0.6)
    write(os.path.join(d, "soul_burst.wav"), mix(noise(1.2, 0.9, attack=0.01, lowpass=0.08), tone(1.0, 80, 900, "saw", 0.35, attack=0.02), tone(1.2, 55, 40, "sine", 0.8)))
    roar = mix(tone(1.6, 110, 70, "saw", 0.5, attack=0.15, vibrato=0.05, vib_hz=7),
               tone(1.6, 55, 35, "square", 0.3, attack=0.2),
               sweep_noise(1.6, 0.05, 0.2, 0.6))
    write(os.path.join(d, "boss_roar.wav"), roar)
    write(os.path.join(d, "chain_swing.wav"), mix(sweep_noise(0.4, 0.1, 0.5, 0.6), tone(0.4, 900, 1300, "square", 0.08, vibrato=0.2, vib_hz=40)))
    write(os.path.join(d, "ground_slam.wav"), mix(tone(0.6, 70, 30, "sine", 1.0), noise(0.5, 0.7, lowpass=0.1)))
    write(os.path.join(d, "teleport.wav"), mix(tone(0.3, 300, 1800, "sine", 0.5, vibrato=0.05, vib_hz=30), sweep_noise(0.3, 0.6, 0.05, 0.3)))


# --------------------------------------------------------------------------
# Music loops (seamless: every frequency completes whole cycles per loop)
# --------------------------------------------------------------------------

def snap(f, loop):
    """Round a frequency so it completes an integer number of cycles per loop."""
    return round(f * loop) / loop


def pad_loop(path, loop, chord, lfo_period, pulse_bpm=None, pulse_freq=55.0):
    n = int(loop * RATE)
    freqs = [snap(f, loop) for f in chord]
    pulse_f = snap(pulse_freq, loop)
    beat = 60.0 / pulse_bpm if pulse_bpm else None
    out = []
    for i in range(n):
        t = i / RATE
        lfo = 0.5 + 0.5 * math.sin(2 * math.pi * t / lfo_period)
        s = 0.0
        for k, f in enumerate(freqs):
            # detuned sine pair per voice gives a slow chorus shimmer
            det = snap(f * 1.003, loop)
            amp = 0.25 * (0.6 + 0.4 * math.sin(2 * math.pi * t / lfo_period + k))
            s += amp * (math.sin(2 * math.pi * f * t) + 0.6 * math.sin(2 * math.pi * det * t))
        s *= 0.55 + 0.45 * lfo
        if beat:
            bt = t % beat
            s += 0.9 * math.sin(2 * math.pi * pulse_f * t) * math.exp(-bt * 6.0)
        out.append(s)
    write(path, out, 0.7)


def music():
    d = os.path.join(ROOT, "music")
    # D minor add9 pad — melancholic menu drone (24 s loop, LFO 12 s divides it).
    pad_loop(os.path.join(d, "menu_theme.wav"), 24.0, [73.42, 146.83, 174.61, 220.0, 329.63], 12.0)
    # Darker B-flat/D tension with a 90 BPM heartbeat pulse (32 s = 48 beats).
    pad_loop(os.path.join(d, "battle_theme.wav"), 32.0, [58.27, 116.54, 146.83, 155.56, 233.08], 8.0, pulse_bpm=90)


if __name__ == "__main__":
    sfx()
    music()
