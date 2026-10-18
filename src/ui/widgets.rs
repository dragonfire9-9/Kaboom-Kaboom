//! Reusable UI building blocks plus mouse / keyboard / gamepad menu navigation.
//!
//! Any entity with `Focusable` takes part in navigation:
//! * mouse hover moves focus to it, click activates it;
//! * Up/Down (arrows, W/S, D-pad, left stick) move focus by `order`;
//! * Enter / Space / gamepad South (A / Cross) activate the focused item.
//!
//! Activation fires Bevy's standard `Activate` event, so each screen just adds
//! an observer for `On<Activate>` and checks its own action component.

use bevy::input::gamepad::{Gamepad, GamepadButton};
use bevy::picking::hover::Hovered;
use bevy::prelude::*;
use bevy::ui_widgets::{Activate, Button};

use crate::assets::{GameAssets, Sfx};
use crate::audio::PlaySfx;
use crate::palette;

/// Participates in menu navigation; lower `order` comes first.
#[derive(Component, Clone, Copy)]
pub struct Focusable {
    pub order: u32,
}

/// Which `Focusable::order` is currently focused, if any.
#[derive(Resource, Default)]
pub struct UiFocus {
    pub current: Option<u32>,
    /// Cooldown so a held stick does not scroll through the whole menu.
    repeat_timer: f32,
}

/// Text helper using the game font.
pub fn label(assets: &GameAssets, text: impl Into<String>, size: f32, color: Color) -> impl Bundle {
    (
        Text::new(text),
        TextFont { font: assets.font.clone().into(), font_size: FontSize::Px(size), ..default() },
        TextColor(color),
    )
}

/// A themed, navigable button. `action` is whatever component the screen's
/// observer looks for.
pub fn menu_button(
    assets: &GameAssets,
    text: &str,
    order: u32,
    width: f32,
    action: impl Component,
) -> impl Bundle {
    (
        Button,
        Hovered::default(),
        Focusable { order },
        action,
        Node {
            width: px(width),
            height: px(54),
            margin: UiRect::all(px(6)),
            justify_content: JustifyContent::Center,
            align_items: AlignItems::Center,
            border: UiRect::all(px(2)),
            border_radius: BorderRadius::all(px(6)),
            ..default()
        },
        BackgroundColor(palette::BUTTON_BG),
        BorderColor::all(palette::BUTTON_BORDER),
        children![label(assets, text, 30.0, palette::BONE)],
    )
}

/// Small square button (used for the − / + volume controls).
pub fn icon_button(assets: &GameAssets, text: &str, order: u32, action: impl Component) -> impl Bundle {
    (
        Button,
        Hovered::default(),
        Focusable { order },
        action,
        Node {
            width: px(44),
            height: px(44),
            margin: UiRect::horizontal(px(8)),
            justify_content: JustifyContent::Center,
            align_items: AlignItems::Center,
            border: UiRect::all(px(2)),
            border_radius: BorderRadius::all(px(6)),
            ..default()
        },
        BackgroundColor(palette::BUTTON_BG),
        BorderColor::all(palette::BUTTON_BORDER),
        children![label(assets, text, 30.0, palette::BONE)],
    )
}

/// Full-screen centred column, the root of every menu page.
pub fn screen_root() -> Node {
    Node {
        width: percent(100),
        height: percent(100),
        flex_direction: FlexDirection::Column,
        justify_content: JustifyContent::Center,
        align_items: AlignItems::Center,
        position_type: PositionType::Absolute,
        ..default()
    }
}

pub struct WidgetsPlugin;

impl Plugin for WidgetsPlugin {
    fn build(&self, app: &mut App) {
        app.init_resource::<UiFocus>()
            .add_systems(Update, (hover_moves_focus, keyboard_gamepad_nav, style_focus).chain())
            .add_observer(click_sound);
    }
}

fn hover_moves_focus(mut focus: ResMut<UiFocus>, q: Query<(&Hovered, &Focusable), Changed<Hovered>>) {
    for (hovered, f) in &q {
        if hovered.get() {
            focus.current = Some(f.order);
        }
    }
}

fn keyboard_gamepad_nav(
    mut commands: Commands,
    time: Res<Time<Real>>,
    keys: Res<ButtonInput<KeyCode>>,
    gamepads: Query<&Gamepad>,
    mut focus: ResMut<UiFocus>,
    items: Query<(Entity, &Focusable)>,
    added: Query<(), Added<Focusable>>,
) {
    // A new page appeared: forget the old page's focus so orders don't alias.
    if !added.is_empty() {
        focus.current = None;
    }
    if items.is_empty() {
        focus.current = None;
        return;
    }
    let mut orders: Vec<(u32, Entity)> = items.iter().map(|(e, f)| (f.order, e)).collect();
    orders.sort_by_key(|(o, _)| *o);

    let mut dir: i32 = 0;
    let mut confirm = false;
    if keys.any_just_pressed([KeyCode::ArrowUp, KeyCode::KeyW]) {
        dir = -1;
    }
    if keys.any_just_pressed([KeyCode::ArrowDown, KeyCode::KeyS, KeyCode::Tab]) {
        dir = 1;
    }
    if keys.any_just_pressed([KeyCode::Enter, KeyCode::Space, KeyCode::NumpadEnter]) {
        confirm = true;
    }

    focus.repeat_timer -= time.delta_secs();
    for pad in &gamepads {
        if pad.just_pressed(GamepadButton::DPadUp) {
            dir = -1;
        }
        if pad.just_pressed(GamepadButton::DPadDown) {
            dir = 1;
        }
        if pad.just_pressed(GamepadButton::South) {
            confirm = true;
        }
        let y = pad.left_stick().y;
        if y.abs() > 0.6 && focus.repeat_timer <= 0.0 {
            dir = if y > 0.0 { -1 } else { 1 };
            focus.repeat_timer = 0.22;
        } else if y.abs() < 0.3 {
            focus.repeat_timer = 0.0;
        }
    }

    if dir != 0 {
        let idx = focus.current.and_then(|c| orders.iter().position(|(o, _)| *o == c));
        let next = match idx {
            None => 0,
            Some(i) => (i as i32 + dir).rem_euclid(orders.len() as i32) as usize,
        };
        focus.current = Some(orders[next].0);
    }

    if confirm {
        match focus.current.and_then(|c| orders.iter().find(|(o, _)| *o == c)) {
            Some((_, entity)) => commands.trigger(Activate { entity: *entity }),
            // First press with nothing focused just focuses the first item.
            None => focus.current = Some(orders[0].0),
        }
    }
}

/// Highlight the focused button; play a soft tick when focus moves.
fn style_focus(
    focus: Res<UiFocus>,
    mut last: Local<Option<u32>>,
    mut sfx: MessageWriter<PlaySfx>,
    mut q: Query<(&Focusable, &mut BackgroundColor, &mut BorderColor)>,
) {
    if focus.current != *last {
        if focus.current.is_some() {
            sfx.write(PlaySfx::new(Sfx::UiHover).jitter(0.04).volume_db(-4.0));
        }
        *last = focus.current;
    }
    for (f, mut bg, mut border) in &mut q {
        let on = focus.current == Some(f.order);
        let (want_bg, want_border) = if on {
            (palette::BUTTON_BG_FOCUS, palette::BUTTON_BORDER_FOCUS)
        } else {
            (palette::BUTTON_BG, palette::BUTTON_BORDER)
        };
        if bg.0 != want_bg {
            bg.0 = want_bg;
        }
        if *border != BorderColor::all(want_border) {
            *border = BorderColor::all(want_border);
        }
    }
}

/// Every activated focusable widget clicks.
fn click_sound(ev: On<Activate>, q: Query<(), With<Focusable>>, mut sfx: MessageWriter<PlaySfx>) {
    if q.contains(ev.entity) {
        sfx.write(PlaySfx::new(Sfx::UiClick).jitter(0.03));
    }
}
