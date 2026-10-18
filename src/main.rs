//! SoulBound — a dark-fantasy, top-down soul-hunting action game.
//!
//! Each feature area is its own plugin; `main` only wires them together.

mod assets;
mod audio;
mod game;
mod loading;
mod menu;
mod palette;
mod settings;
mod states;
mod ui;
mod vfx;

use bevy::diagnostic::{FrameTimeDiagnosticsPlugin, LogDiagnosticsPlugin};
use bevy::prelude::*;
use bevy::window::PresentMode;

fn main() {
    let mut app = App::new();
    app
        .add_plugins(
            DefaultPlugins
                .set(WindowPlugin {
                    primary_window: Some(Window {
                        title: "SoulBound".into(),
                        resolution: (1280u32, 720u32).into(),
                        // VSync caps us at the display rate (60 FPS target)
                        // and avoids tearing.
                        present_mode: PresentMode::AutoVsync,
                        ..default()
                    }),
                    ..default()
                })
                // Smooth filtering for the procedural glow textures.
                .set(ImagePlugin::default_linear()),
        )
        .insert_resource(ClearColor(palette::BG_VOID))
        .add_plugins((
            states::AppStatePlugin,
            settings::SettingsPlugin,
            // Audio first: it registers kira's AudioSource asset type, which
            // GameAssets needs before it can request the sound files.
            audio::GameAudioPlugin,
            assets::GameAssetsPlugin,
            vfx::VfxPlugin,
            ui::UiPlugin,
            loading::LoadingPlugin,
            menu::MenuPlugin,
            game::GamePlugin,
        ));

    // `SOULBOUND_FPS=1 cargo run` logs frame time / FPS once per second.
    if std::env::var_os("SOULBOUND_FPS").is_some() {
        app.add_plugins((FrameTimeDiagnosticsPlugin::default(), LogDiagnosticsPlugin::default()));
    }

    app.run();
}
