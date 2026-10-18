//! Pause and Game Over overlays. Both sit on top of the (frozen) run world.

use bevy::app::AppExit;
use bevy::input::gamepad::{Gamepad, GamepadButton};
use bevy::prelude::*;
use bevy::ui_widgets::Activate;

use super::widgets::{label, menu_button, screen_root};
use crate::assets::GameAssets;
use crate::game::NewRun;
use crate::palette;
use crate::states::{AppState, InRun};

#[derive(Component, Clone, Copy)]
enum OverlayAction {
    Resume,
    Retry,
    MainMenu,
    Quit,
}

pub struct OverlaysPlugin;

impl Plugin for OverlaysPlugin {
    fn build(&self, app: &mut App) {
        app.add_systems(OnEnter(AppState::Paused), (spawn_pause, pause_time))
            .add_systems(OnExit(AppState::Paused), resume_time)
            .add_systems(OnEnter(AppState::GameOver), spawn_game_over)
            .add_systems(Update, toggle_pause.run_if(in_state(InRun)))
            .add_observer(on_overlay_action);
    }
}

fn pause_time(mut time: ResMut<Time<Virtual>>) {
    time.pause();
}

fn resume_time(mut time: ResMut<Time<Virtual>>) {
    time.unpause();
}

/// Esc / gamepad Start toggles pause.
fn toggle_pause(
    keys: Res<ButtonInput<KeyCode>>,
    gamepads: Query<&Gamepad>,
    state: Res<State<AppState>>,
    mut next: ResMut<NextState<AppState>>,
) {
    let pressed =
        keys.just_pressed(KeyCode::Escape) || gamepads.iter().any(|g| g.just_pressed(GamepadButton::Start));
    if !pressed {
        return;
    }
    match state.get() {
        AppState::InGame => next.set(AppState::Paused),
        AppState::Paused => next.set(AppState::InGame),
        _ => {}
    }
}

fn spawn_pause(mut commands: Commands, assets: Res<GameAssets>) {
    commands.spawn((
        DespawnOnExit(AppState::Paused),
        screen_root(),
        BackgroundColor(palette::OVERLAY_BG),
        GlobalZIndex(10),
        children![
            label(&assets, "Paused", 84.0, palette::SOUL_TEAL),
            (label(&assets, "The chains wait. They always wait.", 24.0, palette::ASH), Node {
                margin: UiRect::bottom(px(28)),
                ..default()
            }),
            menu_button(&assets, "Resume", 0, 300.0, OverlayAction::Resume),
            menu_button(&assets, "Main Menu", 1, 300.0, OverlayAction::MainMenu),
            menu_button(&assets, "Quit", 2, 300.0, OverlayAction::Quit),
        ],
    ));
}

fn spawn_game_over(mut commands: Commands, assets: Res<GameAssets>) {
    commands.spawn((
        DespawnOnExit(AppState::GameOver),
        screen_root(),
        BackgroundColor(Color::srgba(0.08, 0.0, 0.03, 0.8)),
        GlobalZIndex(10),
        children![
            label(&assets, "Your Chains Hold", 92.0, palette::BLOOD),
            (label(&assets, "The soul unravels... but it remembers.", 26.0, palette::ASH), Node {
                margin: UiRect::bottom(px(28)),
                ..default()
            }),
            menu_button(&assets, "Rise Again", 0, 300.0, OverlayAction::Retry),
            menu_button(&assets, "Main Menu", 1, 300.0, OverlayAction::MainMenu),
        ],
    ));
}

fn on_overlay_action(
    ev: On<Activate>,
    q: Query<&OverlayAction>,
    mut next: ResMut<NextState<AppState>>,
    mut new_run: MessageWriter<NewRun>,
    mut exit: MessageWriter<AppExit>,
) {
    let Ok(action) = q.get(ev.entity) else { return };
    match action {
        OverlayAction::Resume => next.set(AppState::InGame),
        OverlayAction::Retry => {
            new_run.write(NewRun);
            next.set(AppState::InGame);
        }
        OverlayAction::MainMenu => next.set(AppState::MainMenu),
        OverlayAction::Quit => {
            exit.write(AppExit::Success);
        }
    }
}
