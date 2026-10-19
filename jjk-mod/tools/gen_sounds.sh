#!/usr/bin/env bash
# Generates placeholder .ogg sounds (mono, so they can be positional). Replace with real recordings later.
set -e
OUT=${1:-src/main/resources/assets/jjk/sounds}
mkdir -p "$OUT/roll" "$OUT/ability" "$OUT/ce" "$OUT/grade" "$OUT/domain"
gen() { # gen <file> <duration> <expr> [extra filters]
  ffmpeg -hide_banner -loglevel error -y -f lavfi -i "aevalsrc=$3:s=44100:d=$2" ${4:+-af "$4"} -ac 1 -c:a libvorbis -q:a 4 "$OUT/$1.ogg"
}
gen roll/open 0.45 "(random(0)*2-1)*exp(-6*t)*0.6" "highpass=f=1500,lowpass=f=6000"
gen roll/tick 0.05 "sin(2*PI*1900*t)*exp(-90*t)*0.7"
gen roll/reveal 0.9 "(sin(2*PI*659*t)+sin(2*PI*784*t)+sin(2*PI*988*t))/3*exp(-3.5*t)"
gen roll/reveal_rare 1.8 "(sin(2*PI*523*t)+sin(2*PI*659*t)+sin(2*PI*784*t)+0.6*sin(2*PI*1047*t)+0.3*sin(2*PI*2093*t*(1+0.01*sin(2*PI*6*t))))/3*exp(-1.6*t)"
gen ability/cast 0.45 "(random(0)*2-1)*sin(PI*t/0.45)*0.7" "bandpass=f=900:w=1200"
gen ability/impact 0.6 "(sin(2*PI*(70-40*t)*t)*0.9+(random(0)*2-1)*0.4*exp(-12*t))*exp(-5*t)"
gen ability/dash 0.3 "(random(0)*2-1)*sin(PI*t/0.3)*0.6" "highpass=f=600,lowpass=f=3000"
gen ability/clap 0.12 "(random(0)*2-1)*exp(-45*t)" "bandpass=f=1400:w=1600"
gen ability/heal 0.7 "sin(2*PI*(440+500*t)*t)*sin(PI*t/0.7)*0.5"
gen ce/depleted 0.4 "sin(2*PI*(260-180*t)*t)*exp(-4*t)*0.6"
gen grade/up 1.0 "(sin(2*PI*523*t)*lt(t\,0.25)+sin(2*PI*659*t)*between(t\,0.25\,0.5)+sin(2*PI*784*t)*between(t\,0.5\,0.75)+sin(2*PI*1047*t)*gte(t\,0.75))*0.5*exp(-1.5*t)"
gen domain/expansion 2.6 "(0.6*sin(2*PI*55*t)+0.4*sin(2*PI*82.5*t)+0.25*sin(2*PI*880*t)*exp(-2*t)+0.2*(random(0)*2-1)*exp(-3*t))*min(1\,t*4)*exp(-0.6*t)"

mkdir -p "$OUT/item"
gen item/finger_eat 0.7 "(random(0)*2-1)*exp(-5*t)*0.5+sin(2*PI*90*t)*exp(-3*t)*0.5" "lowpass=f=1800"
gen item/sukuna_laugh 2.2 "sin(2*PI*(110+20*sin(2*PI*5*t))*t)*(0.5+0.5*sin(2*PI*4*t))*exp(-0.8*t)*0.7"
gen item/prison_realm 1.4 "(sin(2*PI*65*t)*0.7+sin(2*PI*130*t)*0.2)*exp(-1.5*t)+(random(0)*2-1)*0.2*exp(-8*t)"
gen item/talisman_burn 0.6 "(random(0)*2-1)*exp(-4*t)*0.6" "bandpass=f=2500:w=3000"
