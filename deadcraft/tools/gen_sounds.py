#!/usr/bin/env python3
"""Synthesise every DeadCraft sound (numpy + ffmpeg → Ogg Vorbis, mono, 44.1 kHz).

These are designed placeholders: layered, filtered and reverberated so they already sound
finished in game, and named so a sound designer can drop in recordings with the same file names.
"""
from __future__ import annotations

import os
import subprocess
import tempfile
import wave

import numpy as np

import content

SR = 44100
OUT = os.path.join(content.ROOT, "src/main/resources/assets/deadcraft/sounds")
rng = np.random.default_rng(1234)


# ---------------------------------------------------------------------------------------------
# DSP building blocks
# ---------------------------------------------------------------------------------------------
def t(d):
    return np.arange(int(d * SR)) / SR


def noise(d):
    return rng.standard_normal(int(d * SR))


def pink(d):
    n = int(d * SR)
    spec = np.fft.rfft(rng.standard_normal(n))
    f = np.fft.rfftfreq(n, 1 / SR)
    spec[1:] /= np.sqrt(f[1:])
    spec[0] = 0
    x = np.fft.irfft(spec, n)
    return x / (np.abs(x).max() + 1e-9)


def band(x, lo, hi):
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    lo_w = 1 / (1 + (lo / np.maximum(f, 1)) ** 4) if lo > 0 else 1
    hi_w = 1 / (1 + (f / hi) ** 4)
    return np.fft.irfft(spec * lo_w * hi_w, len(x))


def lp(x, hi):
    return band(x, 0, hi)


def hp(x, lo):
    return band(x, lo, SR / 2 - 1)


def sweep_band(x, f0, f1, width=1.0, chunks=24):
    """Time-varying band-pass, done in overlapping chunks."""
    n = len(x)
    out = np.zeros(n)
    win = max(256, n // chunks)
    hop = win // 2
    w = np.hanning(win)
    for i, start in enumerate(range(0, n - win + 1 or 1, hop)):
        seg = x[start:start + win]
        if len(seg) < win:
            break
        k = start / max(1, n - win)
        fc = f0 * (f1 / f0) ** k
        out[start:start + win] += band(seg * w, fc / (1 + width), fc * (1 + width))
    return out


def env(n, a=0.005, d=None, curve=2.0):
    n = int(n)
    e = np.ones(n)
    na = max(1, int(a * SR))
    e[:na] = np.linspace(0, 1, na)
    rest = n - na
    if rest > 0:
        e[na:] = (1 - np.linspace(0, 1, rest)) ** curve
    return e


def bell(n, peak=0.5):
    x = np.linspace(0, 1, int(n))
    return np.where(x < peak, (x / peak), (1 - x) / (1 - peak)) ** 1.5


def sine(f, d, phase=0.0):
    tt = t(d)
    if callable(f):
        ph = 2 * np.pi * np.cumsum(f(tt)) / SR
    elif isinstance(f, tuple):
        fr = f[0] * (f[1] / f[0]) ** (tt / d)
        ph = 2 * np.pi * np.cumsum(fr) / SR
    else:
        ph = 2 * np.pi * f * tt
    return np.sin(ph + phase)


def saw(f, d):
    tt = t(d)
    fr = f[0] * (f[1] / f[0]) ** (tt / d) if isinstance(f, tuple) else np.full_like(tt, f)
    ph = np.cumsum(fr) / SR
    return 2 * (ph % 1) - 1


def pad(x, d):
    n = int(d * SR)
    return np.pad(x, (0, max(0, n - len(x))))[:n] if len(x) < n else x[:n]


def at(base, x, sec):
    i = int(sec * SR)
    end = min(len(base), i + len(x))
    base[i:end] += x[:end - i]
    return base


def reverb(x, size=1.2, mix=0.25, damp=4000):
    n = int(size * SR)
    ir = rng.standard_normal(n) * np.exp(-np.linspace(0, 6, n))
    ir = lp(ir, damp)
    ir /= np.abs(ir).sum() ** 0.5 * 4
    wet = np.fft.irfft(np.fft.rfft(x, len(x) + n) * np.fft.rfft(ir, len(x) + n), len(x) + n)
    dry = np.pad(x, (0, n))
    return dry * (1 - mix) + wet * mix * 3


def finish(x, gain=0.9, verb=None, tail=True):
    if verb:
        x = reverb(x, *verb)
    x = x - np.mean(x)
    peak = np.abs(x).max() + 1e-9
    x = np.tanh(1.2 * x / peak) / np.tanh(1.2) * gain
    fade = min(len(x), int(0.01 * SR))
    x[:fade] *= np.linspace(0, 1, fade) ** 0.5 if x[0] != 0 else 1
    x[-fade:] *= np.linspace(1, 0, fade)
    if tail:
        nz = np.where(np.abs(x) > 1e-4)[0]
        if len(nz):
            x = x[:nz[-1] + 1]
    return x


def loopable(x, xfade=0.5):
    """Crossfade the end into the start so the file loops without a click."""
    n = int(xfade * SR)
    head, body, tail = x[:n], x[n:-n], x[-n:]
    ramp = np.linspace(0, 1, n)
    return np.concatenate([body, tail * (1 - ramp) + head * ramp])


# ---------------------------------------------------------------------------------------------
# Sound recipes
# ---------------------------------------------------------------------------------------------
def step_gear(i):
    d = 0.22
    x = band(noise(d), 1800, 6500) * env(d * SR, 0.004, curve=3) * 0.6
    clink = sine(2900 + i * 230, d) * env(d * SR, 0.001, curve=6) * 0.15
    thud = lp(noise(d), 300) * env(d * SR, 0.002, curve=8) * 1.2
    return finish(x + clink + thud, 0.6, (0.3, 0.12))


def step_run(i):
    d = 0.3
    thump = sine((110 + i * 8, 45), d) * env(d * SR, 0.002, curve=6)
    grit = band(noise(d), 700, 4200) * env(d * SR, 0.003, curve=4) * 0.5
    return finish(thump + grit, 0.75, (0.3, 0.12))


def breath(i):
    d = 1.1
    x = band(pink(d), 350, 1900 + i * 200)
    e = np.concatenate([bell(0.45 * SR, 0.6), np.zeros(int(0.1 * SR)), bell(d * SR - int(0.55 * SR), 0.3) * 0.8])
    return finish(x * pad(e, d), 0.5, (0.4, 0.15))


def heartbeat():
    d = 0.7
    base = np.zeros(int(d * SR))
    for s, g in ((0.0, 1.0), (0.22, 0.75)):
        th = sine((70, 38), 0.18) * env(0.18 * SR, 0.003, curve=5) * g
        at(base, th, s)
    return finish(lp(base, 400), 0.9, (0.3, 0.1))


def land():
    d = 0.6
    x = sine((90, 30), d) * env(d * SR, 0.002, curve=5)
    x += band(noise(d), 200, 2500) * env(d * SR, 0.002, curve=6) * 0.6
    return finish(x, 0.85, (0.6, 0.2))


def whoosh(d=0.4, f0=300, f1=2500, gain=0.7):
    x = sweep_band(pink(d) + noise(d) * 0.3, f0, f1, 0.6)
    return x * bell(len(x), 0.45) * gain


def dodge():
    return finish(whoosh(0.38, 250, 2200), 0.7, (0.5, 0.2))


def ui_hover():
    d = 0.35
    x = band(pink(d), 2500, 9000) * bell(d * SR, 0.3) * 0.5
    x += sine(880, d) * env(d * SR, 0.01, curve=3) * 0.05
    return finish(x, 0.35, (0.8, 0.35))


def ui_click():
    d = 0.25
    snap = hp(noise(d), 1500) * env(d * SR, 0.0005, curve=30)
    body = sine((900, 500), d) * env(d * SR, 0.001, curve=10) * 0.5
    return finish(snap + body, 0.7, (0.5, 0.25))


def ui_static():
    d = 6.0
    x = band(noise(d), 600, 7000) * 0.25
    crack = np.zeros(int(d * SR))
    for _ in range(90):
        s = rng.uniform(0, d - 0.05)
        at(crack, hp(noise(0.02), 2000) * env(0.02 * SR, 0.0005, curve=8) * rng.uniform(0.3, 1.0), s)
    hum = sine(50, d) * 0.04 + sine(100, d) * 0.02
    return loopable(finish(x + crack + hum, 0.35, None, tail=False))


NOTES = {"A1": 55.0, "E2": 82.41, "A2": 110.0, "C3": 130.81, "D3": 146.83, "E3": 164.81, "G3": 196.0, "A3": 220.0,
         "C4": 261.63, "D4": 293.66, "E4": 329.63, "G4": 392.0, "A4": 440.0, "C5": 523.25, "Eb4": 311.13, "Bb3": 233.08}


def drone(d, roots, lfo=0.05):
    tt = t(d)
    x = np.zeros(len(tt))
    for k, f in enumerate(roots):
        for det in (-0.6, 0.0, 0.7):
            x += saw(f + det, d) * 0.12 / len(roots)
    x = lp(x, 900)
    swell = 0.6 + 0.4 * np.sin(2 * np.pi * lfo * tt - np.pi / 2)
    return x * swell


def bellnote(f, d=4.0):
    tt = t(d)
    x = (np.sin(2 * np.pi * f * tt) + 0.4 * np.sin(2 * np.pi * f * 2.76 * tt) + 0.2 * np.sin(2 * np.pi * f * 5.4 * tt))
    return x * np.exp(-tt * 1.2) * 0.25


def music(d, roots, scale, seed, pulse=0.0):
    r = np.random.default_rng(seed)
    x = drone(d, roots)
    x += lp(pink(d), 500) * 0.05
    for _ in range(int(d / 3.5)):
        s = r.uniform(1, d - 5)
        at(x, bellnote(NOTES[r.choice(scale)] * r.choice((1, 2))), s)
    if pulse:
        tt = t(d)
        beat = (np.sin(2 * np.pi * pulse * tt) > 0.97).astype(float)
        x += lp(beat * noise(d), 150) * 0.6
    fade = int(3 * SR)
    x[:fade] *= np.linspace(0, 1, fade)
    x[-fade:] *= np.linspace(1, 0, fade)
    return finish(reverb(x, 3.0, 0.4, 3000), 0.8, None, tail=False)


def swing(i):
    return finish(whoosh(0.28, 500 + i * 80, 3200 - i * 200, 0.8), 0.7, (0.4, 0.15))


def hit(i):
    d = 0.35
    th = sine((140 - i * 10, 50), d) * env(d * SR, 0.001, curve=7)
    wet = lp(noise(d), 1800 + i * 300) * env(d * SR, 0.001, curve=9) * 0.9
    return finish(th + wet, 0.85, (0.4, 0.15))


def crit():
    d = 0.6
    x = hit(0)
    ring = (sine(1250, d) + 0.5 * sine(1830, d)) * env(d * SR, 0.001, curve=4) * 0.4
    return finish(pad(x, d) + ring + hp(noise(d), 5000) * env(d * SR, 0.001, curve=10) * 0.3, 0.85, (0.6, 0.25))


def finisher():
    d = 1.4
    sub = sine((110, 28), d) * env(d * SR, 0.001, curve=3)
    burst = lp(noise(d), 3000) * env(d * SR, 0.001, curve=8)
    ring = (sine(620, d) + sine(931, d) * 0.5) * env(d * SR, 0.001, curve=3) * 0.25
    pre = whoosh(0.25, 2000, 400, 0.6)
    x = np.zeros(int((d + 0.25) * SR))
    at(x, pre, 0)
    at(x, sub + burst + ring, 0.22)
    return finish(x, 0.95, (1.2, 0.3))


def evade():
    d = 0.5
    x = whoosh(0.35, 1200, 5000, 0.6)
    x = pad(x, d) + sine(1760, d) * env(d * SR, 0.01, curve=4) * 0.15
    return finish(x, 0.6, (0.8, 0.3))


def gunshot(boom_f, crack_hi, tail, length, gain=1.0):
    d = length + tail
    crack = hp(noise(d), crack_hi) * env(d * SR, 0.0003, curve=60)
    body = lp(noise(d), 2500) * env(d * SR, 0.0005, curve=18)
    boom = sine((boom_f * 1.8, boom_f * 0.6), d) * env(d * SR, 0.001, curve=10) * 1.4
    tail_n = lp(noise(d), 900) * np.exp(-t(d) * (3.0 / tail)) * 0.25
    mech = band(noise(d), 2000, 6000) * env(d * SR, 0.001, curve=40) * 0.3
    return finish((crack + body + boom + tail_n + mech) * gain, 0.98, (tail, 0.35, 2500))


def reload():
    d = 1.1
    x = np.zeros(int(d * SR))
    for s, f in ((0.0, 2400), (0.18, 1700), (0.55, 3100), (0.8, 1400)):
        c = band(noise(0.06), f * 0.7, f * 1.4) * env(0.06 * SR, 0.0005, curve=20)
        c += sine(f * 0.5, 0.06) * env(0.06 * SR, 0.0005, curve=25) * 0.4
        at(x, c, s)
    at(x, band(noise(0.25), 1500, 5000) * bell(0.25 * SR) * 0.3, 0.25)
    return finish(x, 0.7, (0.3, 0.15))


def empty():
    d = 0.12
    x = band(noise(d), 1500, 6000) * env(d * SR, 0.0003, curve=30) + sine(3200, d) * env(d * SR, 0.0005, curve=40) * 0.3
    return finish(x, 0.6, (0.2, 0.1))


def impact(i):
    d = 0.5
    hitn = hp(noise(d), 1500) * env(d * SR, 0.0005, curve=30)
    whine = sine((3400 - i * 300, 1500), d) * env(d * SR, 0.003, curve=2) * 0.25
    return finish(hitn + whine, 0.55, (0.4, 0.2))


def shimmer(d, freqs, gain=0.6):
    tt = t(d)
    x = np.zeros(len(tt))
    for f in freqs:
        x += np.sin(2 * np.pi * f * tt) * (0.6 + 0.4 * np.sin(2 * np.pi * 7 * tt + f))
    x *= bell(len(tt), 0.2) * gain / len(freqs)
    x += hp(noise(d), 6000) * bell(len(tt), 0.1) * 0.08
    return x


def cast():
    return finish(shimmer(0.8, [523.25, 659.25, 783.99, 1046.5]), 0.6, (1.2, 0.35))


def soul_bolt():
    d = 0.9
    wail = sine(lambda tt: 700 + 300 * np.sin(2 * np.pi * 6 * tt) * (tt / d) + 400 * (1 - tt / d), d)
    x = wail * bell(d * SR, 0.2) * 0.4 + pad(whoosh(0.6, 400, 3000), d)
    return finish(x, 0.7, (1.0, 0.35))


def ember():
    d = 1.0
    roar = lp(noise(d), 1200) * bell(d * SR, 0.15)
    crackle = np.zeros(int(d * SR))
    for _ in range(60):
        at(crackle, hp(noise(0.01), 2500) * env(0.01 * SR, 0.0003, curve=8) * rng.uniform(0.2, 0.8), rng.uniform(0, d - 0.02))
    boom = sine((90, 40), d) * env(d * SR, 0.002, curve=5)
    return finish(roar + crackle + boom, 0.85, (0.8, 0.25))


def frost_nova():
    d = 1.2
    x = np.zeros(int(d * SR))
    for _ in range(40):
        f = rng.uniform(2500, 7000)
        at(x, sine(f, 0.25) * env(0.25 * SR, 0.0005, curve=8) * rng.uniform(0.1, 0.4), rng.uniform(0, 0.4))
    x += hp(noise(d), 3000) * env(d * SR, 0.001, curve=6) * 0.5
    x += sine((200, 60), d) * env(d * SR, 0.002, curve=5) * 0.6
    return finish(x, 0.8, (1.2, 0.35))


def blink():
    d = 0.6
    rev = whoosh(0.4, 4000, 300)[::-1]
    zap = sine((2000, 200), 0.2) * env(0.2 * SR, 0.001, curve=3) * 0.4
    x = np.zeros(int(d * SR))
    at(x, rev, 0)
    at(x, zap, 0.38)
    return finish(x, 0.7, (0.8, 0.3))


def thunder(d=3.0, near=True):
    x = lp(noise(d), 600 if near else 250) * np.exp(-t(d) * 1.4)
    rumble = lp(pink(d), 120) * bell(d * SR, 0.2) * 1.5
    if near:
        x += hp(noise(d), 1500) * env(d * SR, 0.0005, curve=25) * 1.5
    return x + rumble


def storm():
    return finish(thunder(2.5, True), 0.95, (1.5, 0.3))


def heal():
    return finish(shimmer(1.4, [261.63, 329.63, 392.0, 523.25], 0.8) + lp(pink(1.4), 400) * bell(1.4 * SR) * 0.1, 0.6, (1.5, 0.4))


def awaken():
    d = 3.0
    x = drone(d, [55, 82.41, 110], 0.33) * bell(d * SR, 0.6) * 2
    x += shimmer(d, [440, 554.37, 659.25, 880], 0.6)
    x += sine((40, 25), d) * env(d * SR, 0.5, curve=2) * 0.6
    return finish(x, 0.85, (2.5, 0.4))


def formant(src, formants, d):
    return sum(band(src, f * 0.85, f * 1.15) * g for f, g in formants)


def whisper(i):
    d = 1.2 + i * 0.15
    src = pink(d) * 0.6 + noise(d) * 0.4
    syll = np.zeros(int(d * SR))
    s = 0.0
    while s < d - 0.15:
        L = rng.uniform(0.08, 0.22)
        f1, f2 = rng.choice([(700, 1200), (400, 2200), (300, 900), (600, 1800)])
        seg = formant(noise(L), [(f1, 1.0), (f2, 0.7), (2600, 0.4)], L) * bell(L * SR, 0.3)
        at(syll, seg, s)
        s += L + rng.uniform(0.02, 0.08)
    x = syll + band(src, 2000, 8000) * bell(d * SR) * 0.1
    if i % 2:
        x = x[::-1]
    return finish(x, 0.45, (1.5, 0.45))


def groan(f0, f1, d, rough=0.3):
    src = saw((f0, f1), d) + noise(d) * rough
    x = formant(src, [(500, 1.0), (900, 0.6), (2400, 0.25)], d)
    am = 0.7 + 0.3 * np.sin(2 * np.pi * 23 * t(d))
    return x * am


def flesh_hurt(i):
    d = 0.5
    x = groan(180 - i * 20, 110, d) * env(d * SR, 0.01, curve=2) + lp(noise(d), 1500) * env(d * SR, 0.001, curve=12) * 0.6
    return finish(x, 0.8, (0.5, 0.2))


def flesh_death():
    d = 1.6
    x = groan(160, 50, d, 0.5) * env(d * SR, 0.02, curve=1.5)
    gurgle = lp(noise(d), 500) * (0.5 + 0.5 * np.sign(np.sin(2 * np.pi * 9 * t(d)))) * env(d * SR, 0.1, curve=2) * 0.4
    return finish(x + gurgle, 0.85, (1.0, 0.3))


def chitter(i):
    d = 0.7
    x = np.zeros(int(d * SR))
    s, rate = 0.0, rng.uniform(0.025, 0.04)
    while s < d - 0.02:
        c = band(noise(0.012), 2000 + i * 500, 6000) * env(0.012 * SR, 0.0005, curve=6)
        at(x, c * rng.uniform(0.4, 1.0), s)
        s += rate * rng.uniform(0.6, 1.4)
    return finish(x * bell(len(x), 0.4), 0.6, (0.6, 0.25))


def wail(i):
    d = 2.0
    f = lambda tt: (520 + i * 80) * (1 + 0.35 * np.sin(np.pi * tt / d)) + 12 * np.sin(2 * np.pi * 5.5 * tt)
    x = sine(f, d) * 0.5 + sine(lambda tt: f(tt) * 2.01, d) * 0.15
    x += band(pink(d), 800, 3000) * 0.2
    return finish(x * bell(d * SR, 0.4), 0.6, (2.0, 0.5))


def growl(i, d=1.0, f=70):
    src = saw((f + i * 6, f * 0.8), d) + noise(d) * 0.5
    x = lp(src, 700) * (0.6 + 0.4 * np.abs(np.sin(2 * np.pi * 17 * t(d))))
    return finish(x * bell(d * SR, 0.3), 0.8, (0.7, 0.25))


def brute_roar():
    return growl(0, 2.0, 55)


def stone_step(i):
    d = 0.5
    x = sine((70 - i * 8, 30), d) * env(d * SR, 0.002, curve=6) * 1.3 + band(noise(d), 300, 3000) * env(d * SR, 0.002, curve=8) * 0.5
    return finish(x, 0.8, (0.5, 0.2))


def stinger():
    d = 2.2
    x = np.zeros(int(d * SR))
    for f in (233.08, 246.94, 349.23, 369.99, 466.16):
        x += saw(f * rng.uniform(0.995, 1.005), d) * 0.12
    x = lp(x, 2500) * np.concatenate([np.linspace(0, 1, int(0.05 * SR)) ** 0.3, np.exp(-np.linspace(0, 3, int(d * SR) - int(0.05 * SR)))])
    x += sine((60, 30), d) * env(d * SR, 0.002, curve=3) * 0.8
    x += hp(noise(d), 4000) * env(d * SR, 0.001, curve=10) * 0.3
    return finish(x, 0.9, (2.0, 0.4))


def shout():
    d = 0.5
    x = formant(saw((220, 160), d) + noise(d) * 0.1, [(700, 1.0), (1220, 0.7), (2600, 0.3)], d)
    return finish(x * bell(d * SR, 0.25), 0.7, (0.8, 0.3))


def chirp(f0, f1, d):
    return sine((f0, f1), d) * bell(d * SR, 0.3)


def critter_ambient(i):
    d = 0.4
    x = np.zeros(int(d * SR))
    for k in range(2 + i):
        at(x, chirp(1500 + k * 300, 2600, 0.08) * 0.5, k * 0.11)
    return finish(x, 0.45, (0.4, 0.2))


def critter_hurt():
    return finish(chirp(2400, 1300, 0.25) + band(noise(0.25), 2000, 6000) * bell(0.25 * SR) * 0.2, 0.6, (0.3, 0.15))


def critter_death():
    return finish(chirp(2200, 500, 0.6) * env(0.6 * SR, 0.01, curve=1.2), 0.6, (0.5, 0.2))


def bird(i):
    d = 0.6
    x = np.zeros(int(d * SR))
    for k in range(3 + i):
        f = rng.uniform(2500, 4200)
        at(x, sine(lambda tt, f=f: f + 600 * np.sin(2 * np.pi * 35 * tt), 0.07) * bell(0.07 * SR, 0.2) * 0.5, k * 0.09)
    return finish(x, 0.45, (0.6, 0.25))


def beast_call(i):
    d = 1.4
    x = groan(110 + i * 25, 80 + i * 20, d, 0.15) * bell(d * SR, 0.35)
    return finish(x, 0.7, (1.2, 0.35))


def boss_roar():
    d = 2.8
    x = growl(0, d, 45) * 1.0
    x = pad(x, d) + sine((70, 32), d) * bell(d * SR, 0.2) * 0.8 + groan(140, 70, d, 0.6) * bell(d * SR, 0.3) * 0.6
    return finish(np.tanh(x * 2.5), 0.95, (2.0, 0.35))


def boss_phase():
    d = 2.2
    rise = sine((80, 400), 1.6) * np.linspace(0, 1, int(1.6 * SR)) ** 2 * 0.6 + band(noise(1.6), 500, 4000) * np.linspace(0, 1, int(1.6 * SR)) ** 3 * 0.3
    x = np.zeros(int(d * SR))
    at(x, rise, 0)
    at(x, sine((90, 25), 0.6) * env(0.6 * SR, 0.001, curve=3) * 1.4 + lp(noise(0.6), 2000) * env(0.6 * SR, 0.001, curve=8), 1.6)
    return finish(x, 0.95, (1.8, 0.35))


def boss_death():
    d = 4.5
    x = thunder(d, False) * 1.2 + groan(120, 30, d, 0.6) * env(d * SR, 0.05, curve=1.3) * 0.7
    x += pad(wail(1), d) * 0.4
    return finish(x, 0.95, (2.5, 0.4))


def boss_slam():
    d = 1.6
    x = sine((70, 22), d) * env(d * SR, 0.001, curve=4) * 1.6 + lp(noise(d), 1500) * env(d * SR, 0.001, curve=7)
    x += band(noise(d), 800, 5000) * env(d * SR, 0.05, curve=3) * 0.3
    return finish(x, 0.98, (1.5, 0.3))


def portal_hum():
    d = 3.0
    tt = t(d)
    x = sum(np.sin(2 * np.pi * f * tt) * g for f, g in ((110, 0.5), (220.5, 0.25), (331, 0.12), (660, 0.05)))
    x *= 0.7 + 0.3 * np.sin(2 * np.pi * 0.7 * tt)
    x += band(pink(d), 1500, 6000) * 0.05
    return loopable(finish(x, 0.5, (1.0, 0.3), tail=False), 0.3)


def portal_travel():
    d = 2.0
    x = pad(whoosh(1.2, 200, 4000, 0.7), d) + sine((60, 200), d) * bell(d * SR, 0.6) * 0.5 + shimmer(d, [880, 1320, 1760], 0.4)
    return finish(x, 0.8, (2.0, 0.4))


def ambient_wind():
    d = 10.0
    tt = t(d)
    x = sweep_band(pink(d), 250, 900, 0.5, 60) * (0.6 + 0.4 * np.sin(2 * np.pi * 0.15 * tt))
    x += band(noise(d), 1500, 4000) * (0.3 + 0.3 * np.sin(2 * np.pi * 0.23 * tt + 1)) * 0.1
    return loopable(finish(x, 0.5, None, tail=False), 1.5)


def ambient_drone():
    d = 10.0
    return loopable(finish(drone(d, [41.2, 61.7, 87.3], 0.1) + lp(pink(d), 300) * 0.1, 0.55, (2.0, 0.3), tail=False), 1.5)


def thunder_far():
    return finish(thunder(4.0, False), 0.7, (2.0, 0.3))


def engine():
    d = 2.0
    tt = t(d)
    f = 42
    pulses = np.sign(np.sin(2 * np.pi * f * tt)) * 0.5 + saw(f * 2, d) * 0.3
    x = lp(pulses, 500) + band(noise(d), 200, 1200) * 0.15
    x *= 0.85 + 0.15 * np.sin(2 * np.pi * 7 * tt)
    return loopable(finish(x, 0.7, None, tail=False), 0.25)


def hover():
    d = 2.0
    tt = t(d)
    x = (np.sin(2 * np.pi * 95 * tt) + 0.5 * np.sin(2 * np.pi * 190.7 * tt) + 0.2 * np.sin(2 * np.pi * 381 * tt))
    x *= 0.8 + 0.2 * np.sin(2 * np.pi * 3 * tt)
    x += band(noise(d), 2000, 7000) * 0.08
    return loopable(finish(x, 0.6, None, tail=False), 0.25)


RECIPES = {
    "player.step_gear": step_gear, "player.step_run": step_run, "player.breath": breath, "player.heartbeat": heartbeat,
    "player.land": land, "player.dodge": dodge, "ui.hover": ui_hover, "ui.click": ui_click, "ui.static": ui_static,
    "music.menu": lambda: music(72, [55, 82.41], ["A3", "C4", "E4", "G3", "D4", "A4"], 3),
    "music.dimension": lambda: music(64, [46.25, 69.3], ["Eb4", "Bb3", "C4", "G3", "D4"], 4, pulse=0.5),
    "combat.swing": swing, "combat.hit": hit, "combat.crit": crit, "combat.finisher": finisher, "combat.evade": evade,
    "gun.revolver": lambda: gunshot(110, 2500, 0.8, 0.3), "gun.shotgun": lambda: gunshot(70, 1500, 1.1, 0.45, 1.2),
    "gun.rifle": lambda: gunshot(90, 3500, 1.4, 0.35), "gun.smg": lambda: gunshot(140, 3000, 0.35, 0.12, 0.8),
    "gun.reload": reload, "gun.empty": empty, "gun.impact": impact,
    "magic.cast": cast, "magic.soul_bolt": soul_bolt, "magic.ember": ember, "magic.frost_nova": frost_nova,
    "magic.blink": blink, "magic.storm": storm, "magic.heal": heal, "magic.awaken": awaken,
    "mob.whisper": whisper, "mob.flesh_hurt": flesh_hurt, "mob.flesh_death": flesh_death, "mob.chitter": chitter,
    "mob.wail": wail, "mob.growl": growl, "mob.brute_roar": brute_roar, "mob.stone_step": stone_step,
    "mob.stinger": stinger, "mob.shout": shout, "critter.ambient": critter_ambient, "critter.hurt": critter_hurt,
    "critter.death": critter_death, "critter.chirp": bird, "critter.beast_call": beast_call,
    "boss.roar": boss_roar, "boss.phase": boss_phase, "boss.death": boss_death, "boss.slam": boss_slam,
    "world.portal_hum": portal_hum, "world.portal_travel": portal_travel, "world.ambient_wind": ambient_wind,
    "world.ambient_drone": ambient_drone, "world.thunder_far": thunder_far, "vehicle.engine": engine, "vehicle.hover": hover,
}


def write_ogg(path, x):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    pcm = (np.clip(x, -1, 1) * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        wav_path = tmp.name
    with wave.open(wav_path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav_path, "-ac", "1", "-c:a", "libvorbis", "-q:a", "5", path], check=True)
    os.remove(wav_path)


def main():
    import gen_assets  # for the variant counts used in sounds.json
    sounds = content.sounds()
    missing = [s for s in sounds if s not in RECIPES]
    assert not missing, f"no recipe for {missing}"
    total = 0
    for s in sounds:
        n = gen_assets.VARIANTS.get(s, 1)
        fn = RECIPES[s]
        for i in range(n):
            x = fn(i) if n > 1 else fn()
            name = s.replace(".", "/") + (f"_{i}" if n > 1 else "")
            write_ogg(os.path.join(OUT, name + ".ogg"), x)
            total += 1
    print(f"wrote {total} sound files")


if __name__ == "__main__":
    main()
