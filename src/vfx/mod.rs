//! Visual effects: procedural textures, particles, and the HDR/bloom camera.
//! (Screen shake, damage flash, vignette and gameplay emitters arrive in Stage 5.)

pub mod particles;
pub mod textures;

use bevy::core_pipeline::tonemapping::{DebandDither, Tonemapping};
use bevy::post_process::bloom::Bloom;
use bevy::prelude::*;

use crate::palette;

pub use particles::{Fade, Particle, Sway};
pub use textures::VfxTextures;

/// The single gameplay/UI camera.
#[derive(Component)]
pub struct MainCamera;

pub struct VfxPlugin;

impl Plugin for VfxPlugin {
    fn build(&self, app: &mut App) {
        app.init_resource::<VfxTextures>()
            .add_systems(Startup, spawn_camera)
            .add_systems(Update, particles::update_particles);
    }
}

fn spawn_camera(mut commands: Commands) {
    commands.spawn((
        MainCamera,
        Camera2d,
        Camera { clear_color: ClearColorConfig::Custom(palette::BG_VOID), ..default() },
        // A tonemapper that rolls bright HDR values off to white keeps glows
        // from clipping into flat blobs.
        Tonemapping::TonyMcMapface,
        Bloom { intensity: 0.22, ..Bloom::NATURAL },
        DebandDither::Enabled,
    ));
}
