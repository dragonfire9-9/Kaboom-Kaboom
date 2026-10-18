# SoulBound

A dark-fantasy, top-down soul-hunting action game written in Rust with **Bevy 0.20**.
You are a bound spirit fighting through corrupted zones, devouring souls and breaking your
chains.

> **Status: Stage 1 of 7.** Done so far: project setup, game states, the loading screen,
> the main menu with settings, the audio manager, and the pause and game-over overlays.
> Coming next: player movement, aiming and the crosshair (Stage 2), then combat, enemies,
> VFX, bosses and polish.

## Requirements

- Rust **1.97.1 or newer** (Bevy 0.20 requires it): `rustup update stable`
- Linux only: `sudo apt install libasound2-dev libudev-dev libxkbcommon-x11-0 pkg-config`
  (Fedora: `alsa-lib-devel systemd-devel libxkbcommon-x11`).
- Windows and macOS need nothing extra.

## Run

```sh
cargo run              # dev build (dependencies are optimised, so it runs at full speed)
cargo run --release    # fully optimised
SOULBOUND_FPS=1 cargo run   # also log FPS / frame time once per second
cargo test             # unit tests (settings parsing)
```

The first build compiles Bevy and takes a few minutes. Later builds are incremental.

## Controls (Stage 1)

| Action | Keyboard / mouse | Gamepad |
|---|---|---|
| Move through menus | Mouse hover, ↑/↓ or W/S | D-pad or left stick |
| Confirm | Click, Enter or Space | A / Cross |
| Back (settings) | Esc | n/a |
| Pause / resume | Esc | Start |
| Test the game-over screen | G (in game) | n/a |

## Project layout

```
soulbound/
├── Cargo.toml              # exact dependency versions
├── ASSETS.md               # every asset, its license, and free replacement sources
├── assets/
│   ├── fonts/PirataOne-Regular.ttf (+ OFL.txt)
│   └── audio/
│       ├── sfx/*.wav       # 16 effects (generated)
│       └── music/*.wav     # 2 seamless loops (generated)
├── tools/
│   └── gen_audio.py        # regenerates every placeholder sound (Python stdlib only)
└── src/
    ├── main.rs             # window setup + plugin wiring
    ├── states.rs           # AppState, MenuScreen (sub-state), InRun (computed state)
    ├── palette.rs          # the one colour palette, including HDR glow colours
    ├── settings.rs         # volume and fullscreen settings, saved to soulbound_settings.cfg
    ├── assets.rs           # GameAssets: every handle + font fallback
    ├── audio/mod.rs        # music / sfx / ui channels, pitch variation, cross-fades
    ├── loading/mod.rs      # real-progress loading bar + rotating tips
    ├── menu/mod.rs         # animated title, soul-mote background, settings page
    ├── ui/
    │   ├── mod.rs
    │   ├── widgets.rs      # buttons, labels, mouse/keyboard/gamepad navigation
    │   └── overlays.rs     # pause and game-over screens
    ├── vfx/
    │   ├── mod.rs          # HDR camera with bloom
    │   ├── particles.rs    # custom CPU particle system
    │   └── textures.rs     # procedurally generated glow textures
    └── game/mod.rs         # run lifecycle (placeholder arena for now)
    # later stages add: player/, combat/, enemies/, bosses/
```

### Game states

```
Loading ──► MainMenu ──► InGame ◄──► Paused
               ▲            │
               │            ▼
               └─────── GameOver ──(Rise Again)──► InGame
```

`InRun` is a computed state that is active during InGame, Paused and GameOver. Everything
in the run world is scoped to it, so pausing or dying freezes the world without despawning
it. Pausing also stops virtual time.

## Notes

- **Particles:** `bevy_hanabi` does not support Bevy 0.20 yet (its latest release targets
  0.19), so SoulBound uses a small custom particle system instead.
- **Missing or broken assets never crash the game.** A missing sound is logged and stays
  silent, and a missing font falls back to Bevy's built-in font.
