"""One procedural placeholder .ogg per ability (technique/<id>.ogg) and per domain (domain/<id>.ogg).
Each sound is shaped by keywords in the id so they are distinguishable in game. Replace with real SFX later."""
import re, subprocess, os, hashlib

J = "src/main/java/com/kaboom/jjk"
OUT = "src/main/resources/assets/jjk/sounds"
abilities = re.findall(r'(?:tech|universal|physical)\("([a-z_]+)"', open(f"{J}/ability/Abilities.java").read())
domains = re.findall(r'^\s+[A-Z_]+\("([a-z_]+)", 0x', open(f"{J}/ability/DomainType.java").read(), re.M)

def gen(path, dur, expr, af=None):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    cmd = ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-f", "lavfi", "-i", f"aevalsrc={expr}:s=44100:d={dur}"]
    if af: cmd += ["-af", af]
    cmd += ["-ac", "1", "-c:a", "libvorbis", "-q:a", "3", path]
    subprocess.run(cmd, check=True)

N = "(random(0)*2-1)"
def recipe(name):
    h = int(hashlib.md5(name.encode()).hexdigest()[:6], 16)
    f = 180 + h % 400          # base pitch
    if any(k in name for k in ("slash", "dismantle", "cleave", "blade", "hairpin")):
        return 0.35, f"{N}*exp(-14*t)*0.8+sin(2*PI*{f*6}*t)*exp(-30*t)*0.3", "highpass=f=2500"
    if any(k in name for k in ("blue", "pull", "toad", "absorb")):
        return 1.0, f"sin(2*PI*({f}-{f//2}*t)*t)*sin(PI*t)*0.6+{N}*0.15*sin(PI*t)", "lowpass=f=2500"
    if any(k in name for k in ("red", "blast", "burst", "cannon", "supernova", "slam", "elephant")):
        return 0.8, f"(sin(2*PI*(90-50*t)*t)+{N}*0.6*exp(-10*t))*exp(-4*t)*0.8", None
    if any(k in name for k in ("purple", "uzumaki", "meteor", "fuga", "mahoraga")):
        return 2.0, f"(0.6*sin(2*PI*55*t)+0.3*sin(2*PI*{f}*t*(1+0.2*t))+{N}*0.3*min(1\\,t))*min(1\\,t*3)*exp(-0.9*t)", None
    if any(k in name for k in ("ember", "flame", "fire")):
        return 0.9, f"{N}*(0.4+0.3*sin(2*PI*9*t))*exp(-2*t)", "lowpass=f=1800"
    if any(k in name for k in ("blood", "piercing", "convergence")):
        return 0.5, f"({N}*0.5+sin(2*PI*{f*2}*t)*0.4)*exp(-6*t)", "bandpass=f=1500:w=2000"
    if any(k in name for k in ("dont_move", "crush", "speech", "punchline")):
        return 0.9, f"sin(2*PI*{f//2}*t+3*sin(2*PI*7*t))*exp(-2.5*t)*0.7", None
    if any(k in name for k in ("dash", "escape", "projection", "swap", "heavenly")):
        return 0.35, f"{N}*sin(PI*t/0.35)*0.7", "bandpass=f=1200:w=1800"
    if any(k in name for k in ("infinity", "simple_domain", "amplification", "guard", "wall", "reinforce", "focus", "overtime", "repel")):
        return 1.2, f"(sin(2*PI*{f}*t)+sin(2*PI*{f*1.5:.1f}*t)*0.5)*sin(PI*t/1.2)*0.4", None
    if any(k in name for k in ("dog", "nue", "shadow", "release", "soul", "isomer")):
        return 0.9, f"sin(2*PI*({f}+80*sin(2*PI*5*t))*t)*exp(-2.5*t)*0.6+{N}*0.2*exp(-6*t)", None
    return 0.6, f"(sin(2*PI*{f}*t)*0.5+{N}*0.3)*exp(-5*t)", None

for a in abilities:
    gen(f"{OUT}/technique/{a}.ogg", *recipe(a))
for d in domains:
    gen(f"{OUT}/domain/{d}.ogg", 3.0, f"(0.5*sin(2*PI*{40 + len(d)*3}*t)+0.3*sin(2*PI*{300 + len(d)*20}*t)*exp(-t)+0.15*{N})*min(1\\,t*2)*exp(-0.5*t)")
gen(f"{OUT}/domain/shatter.ogg", 1.5, f"({N}*exp(-3*t)+sin(2*PI*2400*t)*exp(-8*t)*0.3)*0.8", "highpass=f=800")
gen(f"{OUT}/black_flash.ogg", 1.0, f"(sin(2*PI*(60-30*t)*t)*0.9+{N}*exp(-6*t)*0.8+sin(2*PI*3000*t)*exp(-25*t)*0.4)*exp(-2.5*t)")
curse_ids = re.findall(r'^    [A-Z_]+\("([a-z_]+)", Grade', open(f"{J}/entity/CurseType.java").read(), re.M)
for i, c in enumerate(curse_ids):
    base = 70 + i * 35
    gen(f"{OUT}/curse/{c}/ambient.ogg", 1.4, f"(sin(2*PI*({base}+15*sin(2*PI*3*t))*t)*0.5+{N}*0.25)*sin(PI*t/1.4)", "lowpass=f=1200")
    gen(f"{OUT}/curse/{c}/hurt.ogg", 0.4, f"(sin(2*PI*({base*2}-{base}*t)*t)*0.6+{N}*0.3)*exp(-6*t)")
    gen(f"{OUT}/curse/{c}/death.ogg", 1.5, f"(sin(2*PI*({base*1.5:.0f}-{base}*t/1.5)*t)*0.6+{N}*0.4*exp(-2*t))*exp(-1.5*t)", "lowpass=f=2000")
print(len(abilities), "ability sounds,", len(domains), "domain sounds")
