//! Loading screen: a progress bar driven by the real load state of every
//! handle in `GameAssets`, a pulsing caption and rotating gameplay tips.

use bevy::asset::UntypedAssetId;
use bevy::platform::collections::HashSet;
use bevy::prelude::*;

use crate::assets::GameAssets;
use crate::palette;
use crate::states::AppState;
use crate::ui::widgets::{label, screen_root};

/// Keep the screen up at least this long so the tips are readable even when
/// local assets load in a few milliseconds.
const MIN_SECONDS: f32 = 2.0;
const TIP_SECONDS: f32 = 3.2;
const BAR_WIDTH: f32 = 520.0;

const TIPS: &[&str] = &[
    "Dashing grants a breath of invulnerability. Dash through attacks, not away.",
    "Hold the secondary attack to charge a soul shot. Release at full glow for triple damage.",
    "Husk Brutes paw the ground before they charge. Step aside, then punish.",
    "Hexcaster orbs are slow but stubborn. Lure them into walls or shoot them down.",
    "Shardlings hunt in swarms. Keep moving and let them clump together.",
    "A full soul meter unleashes Soul Burst, clearing everything on screen.",
    "Press F1 to reveal every hitbox and hurtbox.",
    "The Chained Warden grows desperate as its chains break. Watch for the enrage.",
    "The Hollow Queen never stays where she was. Track the shimmer of her teleport.",
];

#[derive(Resource, Default)]
struct LoadingProgress {
    elapsed: f32,
    /// Smoothed fraction actually shown on the bar.
    shown: f32,
    reported_failures: HashSet<UntypedAssetId>,
    tip_index: usize,
    tip_timer: f32,
}

#[derive(Component)]
struct BarFill;
#[derive(Component)]
struct PercentText;
#[derive(Component)]
struct TipText;
#[derive(Component)]
struct PulseText;

pub struct LoadingPlugin;

impl Plugin for LoadingPlugin {
    fn build(&self, app: &mut App) {
        app.add_systems(OnEnter(AppState::Loading), spawn_loading_screen)
            .add_systems(OnExit(AppState::Loading), |mut c: Commands| c.remove_resource::<LoadingProgress>())
            .add_systems(Update, (track_loading, rotate_tips, pulse).run_if(in_state(AppState::Loading)));
    }
}

fn spawn_loading_screen(mut commands: Commands, assets: Res<GameAssets>) {
    commands.insert_resource(LoadingProgress::default());
    commands.spawn((
        DespawnOnExit(AppState::Loading),
        screen_root(),
        BackgroundColor(palette::BG_VOID),
        children![
            (label(&assets, "SoulBound", 64.0, palette::SOUL_TEAL), Node {
                margin: UiRect::bottom(px(12)),
                ..default()
            }),
            (label(&assets, "Binding souls...", 26.0, palette::VIOLET_MIST), PulseText, Node {
                margin: UiRect::bottom(px(26)),
                ..default()
            }),
            // Bar frame
            (
                Node {
                    width: px(BAR_WIDTH),
                    height: px(16),
                    border: UiRect::all(px(2)),
                    border_radius: BorderRadius::all(px(8)),
                    padding: UiRect::all(px(2)),
                    ..default()
                },
                BackgroundColor(palette::BAR_EMPTY),
                BorderColor::all(palette::PURPLE),
                children![(
                    BarFill,
                    Node { width: percent(0), height: percent(100), border_radius: BorderRadius::all(px(6)), ..default() },
                    BackgroundColor(palette::SOUL_TEAL),
                )],
            ),
            (label(&assets, "0%", 22.0, palette::BONE), PercentText, Node {
                margin: UiRect::top(px(10)),
                ..default()
            }),
            // Tip pinned to the bottom of the screen
            (
                Node {
                    position_type: PositionType::Absolute,
                    bottom: px(48),
                    max_width: px(900),
                    padding: UiRect::horizontal(px(24)),
                    ..default()
                },
                children![(
                    label(&assets, format!("Tip: {}", TIPS[0]), 24.0, palette::ASH),
                    TextLayout::justify(Justify::Center),
                    TipText,
                )],
            ),
        ],
    ));
}

fn track_loading(
    time: Res<Time>,
    server: Res<AssetServer>,
    assets: Res<GameAssets>,
    mut progress: ResMut<LoadingProgress>,
    mut fill: Query<&mut Node, With<BarFill>>,
    mut pct: Query<&mut Text, With<PercentText>>,
    mut next: ResMut<NextState<AppState>>,
) {
    progress.elapsed += time.delta_secs();

    let handles = assets.all_untyped();
    let total = handles.len().max(1);
    let mut done = 0;
    for (path, handle) in &handles {
        let state = server.load_state(handle.id());
        if state.is_loaded() {
            done += 1;
        } else if state.is_failed() {
            // A failed asset still counts as "done" so loading never hangs.
            done += 1;
            if progress.reported_failures.insert(handle.id()) {
                warn!("Asset failed to load: {path}. The game will continue without it.");
            }
        }
    }
    let real = done as f32 / total as f32;

    // Ease the bar toward the real value, but also cap it by elapsed time so
    // fast loads still read as a smooth fill rather than an instant jump.
    let time_cap = (progress.elapsed / MIN_SECONDS).min(1.0);
    let target = real.min(time_cap);
    progress.shown += (target - progress.shown) * (1.0 - (-8.0 * time.delta_secs()).exp());
    if target >= 1.0 && progress.shown > 0.995 {
        progress.shown = 1.0;
    }

    for mut node in &mut fill {
        node.width = percent(progress.shown * 100.0);
    }
    for mut text in &mut pct {
        text.0 = format!("{:.0}%   ({done} / {total} assets)", progress.shown * 100.0);
    }

    if done == total && progress.shown >= 1.0 && progress.elapsed >= MIN_SECONDS {
        next.set(AppState::MainMenu);
    }
}

fn rotate_tips(
    time: Res<Time>,
    mut progress: ResMut<LoadingProgress>,
    mut q: Query<(&mut Text, &mut TextColor), With<TipText>>,
) {
    progress.tip_timer += time.delta_secs();
    if progress.tip_timer >= TIP_SECONDS {
        progress.tip_timer = 0.0;
        progress.tip_index = (progress.tip_index + 1) % TIPS.len();
    }
    // Fade each tip in and out across its slot.
    let phase = progress.tip_timer / TIP_SECONDS;
    let alpha = (phase * std::f32::consts::PI).sin().powf(0.4);
    for (mut text, mut color) in &mut q {
        let wanted = format!("Tip: {}", TIPS[progress.tip_index]);
        if text.0 != wanted {
            text.0 = wanted;
        }
        color.0 = palette::ASH.with_alpha(alpha);
    }
}

fn pulse(time: Res<Time>, mut q: Query<&mut TextColor, With<PulseText>>) {
    let a = 0.45 + 0.55 * (time.elapsed_secs() * 3.0).sin().abs();
    for mut c in &mut q {
        c.0 = palette::VIOLET_MIST.with_alpha(a);
    }
}
