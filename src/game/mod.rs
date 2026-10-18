//! Run lifecycle. Stage 1 only shows a placeholder arena so every state can be
//! visited; the player, enemies and bosses plug in here in later stages.

use bevy::prelude::*;

use crate::assets::GameAssets;
use crate::palette;
use crate::states::{AppState, InRun};
use crate::ui::widgets::label;
use crate::vfx::VfxTextures;

/// Tag for everything that belongs to the current run. Despawned when the run
/// ends (leaving `InRun`) or restarts (`NewRun`).
#[derive(Component)]
pub struct RunEntity;

/// Start (or restart) a run: clears old run entities and spawns a fresh arena.
#[derive(Message, Clone, Copy)]
pub struct NewRun;

pub struct GamePlugin;

impl Plugin for GamePlugin {
    fn build(&self, app: &mut App) {
        app.add_message::<NewRun>()
            .add_systems(OnEnter(InRun), |mut w: MessageWriter<NewRun>| {
                w.write(NewRun);
            })
            .add_systems(Update, start_run)
            .add_systems(Update, debug_game_over.run_if(in_state(AppState::InGame)));
    }
}

fn start_run(
    mut commands: Commands,
    mut runs: MessageReader<NewRun>,
    old: Query<Entity, With<RunEntity>>,
    assets: Res<GameAssets>,
    tex: Res<VfxTextures>,
) {
    if runs.read().count() == 0 {
        return;
    }
    for e in &old {
        commands.entity(e).despawn();
    }

    // Placeholder arena: a dark violet floor with a soft glow in the centre.
    commands.spawn((
        RunEntity,
        DespawnOnExit(InRun),
        Sprite::from_color(palette::DEEP_PURPLE, Vec2::new(1600.0, 1000.0)),
        Transform::from_xyz(0.0, 0.0, -10.0),
    ));
    commands.spawn((
        RunEntity,
        DespawnOnExit(InRun),
        Sprite {
            image: tex.soft_circle.clone(),
            color: palette::SOUL_GLOW.with_alpha(0.5),
            custom_size: Some(Vec2::splat(120.0)),
            ..default()
        },
        Transform::from_xyz(0.0, 0.0, 0.0),
    ));
    commands.spawn((
        RunEntity,
        DespawnOnExit(InRun),
        Node {
            position_type: PositionType::Absolute,
            width: percent(100),
            top: px(40),
            flex_direction: FlexDirection::Column,
            align_items: AlignItems::Center,
            ..default()
        },
        children![
            label(&assets, "The Corrupted Threshold", 48.0, palette::SOUL_TEAL),
            label(&assets, "Stage 1 placeholder - the bound spirit arrives in Stage 2", 24.0, palette::ASH),
            label(&assets, "Esc: pause      G: test Game Over screen", 22.0, palette::ASH.with_alpha(0.8)),
        ],
    ));
}

/// Debug shortcut until the player can actually die (Stage 3).
fn debug_game_over(keys: Res<ButtonInput<KeyCode>>, mut next: ResMut<NextState<AppState>>) {
    if keys.just_pressed(KeyCode::KeyG) {
        next.set(AppState::GameOver);
    }
}
