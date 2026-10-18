//! Main menu: animated title, drifting soul-mote background, and the
//! Main / Settings pages (volume per channel, fullscreen toggle).

use bevy::app::AppExit;
use bevy::prelude::*;
use bevy::ui_widgets::Activate;
use rand::Rng;

use crate::assets::GameAssets;
use crate::palette;
use crate::settings::{GameSettings, VOLUME_STEPS, VolumeChannel};
use crate::states::{AppState, MenuScreen};
use crate::ui::widgets::{icon_button, label, menu_button, screen_root};
use crate::vfx::{Fade, Particle, Sway, VfxTextures};

/// How many ambient motes float behind the menu at once.
const MOTE_COUNT: usize = 140;
/// The title fades and rises in over this long.
const TITLE_INTRO_SECONDS: f32 = 1.6;

#[derive(Component, Clone, Copy)]
enum MenuAction {
    Play,
    Settings,
    Quit,
    Back,
    VolumeDown(VolumeChannel),
    VolumeUp(VolumeChannel),
    ToggleFullscreen,
}

#[derive(Component)]
struct MenuMote;

#[derive(Component)]
struct TitleText {
    age: f32,
}

/// One notch of a volume bar: lit when `index < volume`.
#[derive(Component)]
struct VolumeNotch {
    channel: VolumeChannel,
    index: u8,
}

#[derive(Component)]
struct FullscreenLabel;

pub struct MenuPlugin;

impl Plugin for MenuPlugin {
    fn build(&self, app: &mut App) {
        app.add_systems(OnEnter(AppState::MainMenu), spawn_menu_backdrop)
            .add_systems(OnEnter(MenuScreen::Main), spawn_main_page)
            .add_systems(OnEnter(MenuScreen::Settings), spawn_settings_page)
            .add_systems(
                Update,
                (
                    keep_motes_alive.run_if(in_state(AppState::MainMenu)),
                    animate_title,
                    refresh_settings_widgets.run_if(in_state(MenuScreen::Settings)),
                    settings_back_on_escape.run_if(in_state(MenuScreen::Settings)),
                ),
            )
            .add_observer(on_menu_action);
    }
}

// ------------------------------------------------------------- backdrop ----

fn spawn_menu_backdrop(mut commands: Commands, tex: Res<VfxTextures>, windows: Query<&Window>) {
    let size = windows.iter().next().map(|w| w.size()).unwrap_or(Vec2::new(1280.0, 720.0));
    let mut rng = rand::rng();
    // Pre-age the first batch so the screen starts full instead of empty.
    for _ in 0..MOTE_COUNT {
        let mut bundle = mote(&tex, size, &mut rng);
        bundle.0.age = rng.random_range(0.0..bundle.0.lifetime);
        commands.spawn(bundle);
    }
    // A big, dim violet haze behind the title for depth.
    commands.spawn((
        DespawnOnExit(AppState::MainMenu),
        Sprite {
            image: tex.soft_circle.clone(),
            color: palette::PURPLE.with_alpha(0.35),
            custom_size: Some(Vec2::new(1400.0, 900.0)),
            ..default()
        },
        Transform::from_xyz(0.0, 80.0, -5.0),
    ));
}

/// One drifting soul mote: mostly teal, some violet, rare embers.
fn mote(
    tex: &VfxTextures,
    area: Vec2,
    rng: &mut impl Rng,
) -> (Particle, Sway, Sprite, Transform, MenuMote, DespawnOnExit<AppState>) {
    let roll: f32 = rng.random();
    let (color, size) = if roll < 0.62 {
        (palette::SOUL_GLOW.with_alpha(rng.random_range(0.35..0.8)), rng.random_range(4.0..10.0))
    } else if roll < 0.92 {
        (palette::VIOLET_GLOW.with_alpha(rng.random_range(0.25..0.6)), rng.random_range(6.0..16.0))
    } else {
        (palette::EMBER_GLOW.with_alpha(rng.random_range(0.5..0.9)), rng.random_range(3.0..6.0))
    };
    let half = area / 2.0;
    let pos = Vec2::new(rng.random_range(-half.x..half.x), rng.random_range(-half.y - 40.0..half.y));
    let lifetime = rng.random_range(5.0..11.0);
    (
        Particle::new(Vec2::new(0.0, rng.random_range(12.0..38.0)), lifetime, size, color)
            .end_size(size * rng.random_range(0.4..1.2))
            .fade(Fade::InOut),
        Sway {
            amplitude: rng.random_range(6.0..26.0),
            frequency: rng.random_range(0.05..0.25),
            phase: rng.random_range(0.0..std::f32::consts::TAU),
        },
        Sprite { image: tex.soft_circle.clone(), color, custom_size: Some(Vec2::splat(size)), ..default() },
        Transform::from_translation(pos.extend(rng.random_range(-1.0..1.0))),
        MenuMote,
        DespawnOnExit(AppState::MainMenu),
    )
}

fn keep_motes_alive(
    mut commands: Commands,
    tex: Res<VfxTextures>,
    windows: Query<&Window>,
    motes: Query<(), With<MenuMote>>,
) {
    let size = windows.iter().next().map(|w| w.size()).unwrap_or(Vec2::new(1280.0, 720.0));
    let missing = MOTE_COUNT.saturating_sub(motes.iter().count());
    let mut rng = rand::rng();
    for _ in 0..missing {
        commands.spawn(mote(&tex, size, &mut rng));
    }
}

fn animate_title(time: Res<Time>, mut q: Query<(&mut TitleText, &mut TextColor, &mut UiTransform)>) {
    for (mut title, mut color, mut tf) in &mut q {
        title.age += time.delta_secs();
        let intro = (title.age / TITLE_INTRO_SECONDS).min(1.0);
        let ease = 1.0 - (1.0 - intro).powi(3);
        // Breathing glow between teal and a paler soul-white.
        let pulse = 0.5 + 0.5 * (title.age * 1.6).sin();
        let c = palette::SOUL_TEAL.mix(&palette::BONE, pulse * 0.35);
        color.0 = c.with_alpha(ease);
        tf.translation = Val2::px(0.0, 30.0 * (1.0 - ease) + 4.0 * (title.age * 0.9).sin());
        let s = 0.92 + 0.08 * ease + 0.012 * (title.age * 1.6).sin();
        tf.scale = Vec2::splat(s);
    }
}

// ---------------------------------------------------------------- pages ----

fn spawn_main_page(mut commands: Commands, assets: Res<GameAssets>) {
    commands.spawn((
        DespawnOnExit(MenuScreen::Main),
        screen_root(),
        children![
            (label(&assets, "SoulBound", 150.0, palette::SOUL_TEAL), TitleText { age: 0.0 }, UiTransform::default()),
            (label(&assets, "Break the chains. Devour the dark.", 28.0, palette::ASH), Node {
                margin: UiRect::bottom(px(46)),
                ..default()
            }),
            menu_button(&assets, "Play", 0, 320.0, MenuAction::Play),
            menu_button(&assets, "Settings", 1, 320.0, MenuAction::Settings),
            menu_button(&assets, "Quit", 2, 320.0, MenuAction::Quit),
            (
                Node { position_type: PositionType::Absolute, bottom: px(20), ..default() },
                children![label(
                    &assets,
                    "Mouse or arrows to choose  -  Enter / (A) to confirm",
                    20.0,
                    palette::ASH.with_alpha(0.7)
                )],
            ),
        ],
    ));
}

fn spawn_settings_page(mut commands: Commands, assets: Res<GameAssets>, settings: Res<GameSettings>) {
    let root = commands
        .spawn((DespawnOnExit(MenuScreen::Settings), screen_root()))
        .id();

    commands.entity(root).with_children(|page| {
        page.spawn((label(&assets, "Settings", 84.0, palette::SOUL_TEAL), Node {
            margin: UiRect::bottom(px(20)),
            ..default()
        }));

        // Panel holding the rows
        page.spawn((
            Node {
                flex_direction: FlexDirection::Column,
                padding: UiRect::axes(px(36), px(24)),
                border: UiRect::all(px(2)),
                border_radius: BorderRadius::all(px(10)),
                align_items: AlignItems::Stretch,
                ..default()
            },
            BackgroundColor(palette::PANEL_BG),
            BorderColor::all(palette::BUTTON_BORDER),
        ))
        .with_children(|panel| {
            let mut order = 0;
            for ch in VolumeChannel::ALL {
                panel
                    .spawn(Node {
                        flex_direction: FlexDirection::Row,
                        align_items: AlignItems::Center,
                        margin: UiRect::vertical(px(6)),
                        ..default()
                    })
                    .with_children(|row| {
                        row.spawn((label(&assets, ch.label(), 30.0, palette::BONE), Node {
                            width: px(170),
                            ..default()
                        }));
                        row.spawn(icon_button(&assets, "-", order, MenuAction::VolumeDown(ch)));
                        order += 1;
                        // The notch bar
                        row.spawn(Node { flex_direction: FlexDirection::Row, ..default() })
                            .with_children(|bar| {
                                for i in 0..VOLUME_STEPS {
                                    bar.spawn((
                                        VolumeNotch { channel: ch, index: i },
                                        Node {
                                            width: px(18),
                                            height: px(26),
                                            margin: UiRect::horizontal(px(2)),
                                            border_radius: BorderRadius::all(px(3)),
                                            ..default()
                                        },
                                        BackgroundColor(notch_color(i < settings.volume(ch))),
                                    ));
                                }
                            });
                        row.spawn(icon_button(&assets, "+", order, MenuAction::VolumeUp(ch)));
                        order += 1;
                    });
            }

            panel
                .spawn(Node {
                    flex_direction: FlexDirection::Row,
                    justify_content: JustifyContent::Center,
                    margin: UiRect::top(px(14)),
                    ..default()
                })
                .with_children(|row| {
                    row.spawn(menu_button(&assets, "", order, 360.0, MenuAction::ToggleFullscreen))
                        .with_children(|b| {
                            b.spawn((label(&assets, fullscreen_text(&settings), 28.0, palette::BONE), FullscreenLabel));
                        });
                    order += 1;
                });

            panel
                .spawn(Node { justify_content: JustifyContent::Center, margin: UiRect::top(px(6)), ..default() })
                .with_children(|row| {
                    row.spawn(menu_button(&assets, "Back", order, 360.0, MenuAction::Back));
                });
        });
    });
}

fn notch_color(lit: bool) -> Color {
    if lit { palette::SOUL_TEAL } else { palette::BAR_EMPTY }
}

fn fullscreen_text(settings: &GameSettings) -> String {
    format!("Fullscreen: {}", if settings.fullscreen { "On" } else { "Off" })
}

fn refresh_settings_widgets(
    settings: Res<GameSettings>,
    mut notches: Query<(&VolumeNotch, &mut BackgroundColor)>,
    mut fs_label: Query<&mut Text, With<FullscreenLabel>>,
) {
    if !settings.is_changed() {
        return;
    }
    for (n, mut bg) in &mut notches {
        bg.0 = notch_color(n.index < settings.volume(n.channel));
    }
    for mut t in &mut fs_label {
        t.0 = fullscreen_text(&settings);
    }
}

fn settings_back_on_escape(keys: Res<ButtonInput<KeyCode>>, mut next: ResMut<NextState<MenuScreen>>) {
    if keys.just_pressed(KeyCode::Escape) {
        next.set(MenuScreen::Main);
    }
}

// --------------------------------------------------------------- actions ---

fn on_menu_action(
    ev: On<Activate>,
    q: Query<&MenuAction>,
    mut app_state: ResMut<NextState<AppState>>,
    mut screen: ResMut<NextState<MenuScreen>>,
    mut settings: ResMut<GameSettings>,
    mut exit: MessageWriter<AppExit>,
) {
    let Ok(action) = q.get(ev.entity) else { return };
    match *action {
        MenuAction::Play => app_state.set(AppState::InGame),
        MenuAction::Settings => screen.set(MenuScreen::Settings),
        MenuAction::Back => screen.set(MenuScreen::Main),
        MenuAction::Quit => {
            exit.write(AppExit::Success);
        }
        MenuAction::VolumeDown(ch) => settings.step_volume(ch, -1),
        MenuAction::VolumeUp(ch) => settings.step_volume(ch, 1),
        MenuAction::ToggleFullscreen => settings.fullscreen = !settings.fullscreen,
    }
}
