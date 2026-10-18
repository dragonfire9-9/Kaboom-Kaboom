//! High-level game flow.
//!
//! ```text
//!  Loading ──► MainMenu ──► InGame ◄──► Paused
//!                 ▲            │
//!                 │            ▼
//!                 └─────── GameOver ──(retry)──► InGame
//! ```
//!
//! `InRun` is a *computed* state that is active for InGame, Paused and
//! GameOver. Gameplay entities are scoped to `InRun`, so pausing or dying does
//! not despawn the world; leaving to the main menu does.

use bevy::prelude::*;

#[derive(States, Debug, Clone, Copy, Default, PartialEq, Eq, Hash)]
pub enum AppState {
    /// Streams in every asset while showing a progress bar.
    #[default]
    Loading,
    MainMenu,
    InGame,
    Paused,
    GameOver,
}

/// Which page of the main menu is visible. Only exists while in `MainMenu`.
#[derive(SubStates, Debug, Clone, Copy, Default, PartialEq, Eq, Hash)]
#[source(AppState = AppState::MainMenu)]
pub enum MenuScreen {
    #[default]
    Main,
    Settings,
}

/// Active while a run is in progress (playing, paused, or on the death screen).
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub struct InRun;

impl ComputedStates for InRun {
    type SourceStates = AppState;

    fn compute(source: AppState) -> Option<Self> {
        match source {
            AppState::InGame | AppState::Paused | AppState::GameOver => Some(InRun),
            AppState::Loading | AppState::MainMenu => None,
        }
    }
}

pub struct AppStatePlugin;

impl Plugin for AppStatePlugin {
    fn build(&self, app: &mut App) {
        app.init_state::<AppState>()
            .add_sub_state::<MenuScreen>()
            .add_computed_state::<InRun>()
            .add_systems(Update, log_transitions);
    }
}

/// Logs every state change; handy when wiring up new screens.
fn log_transitions(mut transitions: MessageReader<StateTransitionEvent<AppState>>) {
    for t in transitions.read() {
        info!("AppState: {:?} -> {:?}", t.exited, t.entered);
    }
}
