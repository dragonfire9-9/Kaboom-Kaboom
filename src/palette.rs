//! The single source of truth for SoulBound's colours.
//!
//! Every system pulls colours from here so the whole game keeps one moody
//! palette: deep purples for the world, teal soul-light for the player and
//! everything "soul", ember orange for danger and accents.
//!
//! The `*_GLOW` constants deliberately go above 1.0. The camera renders in HDR
//! with bloom, so these values bleed light into their surroundings. Use them
//! for world-space sprites only; UI colours stay in the normal 0..1 range.

#![allow(dead_code)] // the full palette is defined up front; later stages use the rest

use bevy::prelude::Color;

// ---------------------------------------------------------------- world ----

/// Clear colour: an almost-black violet void.
pub const BG_VOID: Color = Color::srgb(0.035, 0.02, 0.06);
/// Darkest world tone (floor shadows, panel backs).
pub const DEEP_PURPLE: Color = Color::srgb(0.13, 0.06, 0.22);
/// Mid world tone (floor tiles, corrupted stone).
pub const PURPLE: Color = Color::srgb(0.33, 0.16, 0.52);
/// Light purple haze used for fog and secondary particles.
pub const VIOLET_MIST: Color = Color::srgb(0.55, 0.38, 0.78);

// ----------------------------------------------------------------- soul ----

/// Signature teal of souls and the player.
pub const SOUL_TEAL: Color = Color::srgb(0.30, 0.95, 0.86);
/// Desaturated teal for inactive / background soul elements.
pub const SOUL_TEAL_DIM: Color = Color::srgb(0.12, 0.42, 0.40);

// --------------------------------------------------------------- accent ----

/// Ember orange: danger, enemy projectiles, highlights.
pub const EMBER: Color = Color::srgb(1.0, 0.50, 0.16);
/// Darker ember for borders and pressed states.
pub const EMBER_DIM: Color = Color::srgb(0.55, 0.22, 0.06);
/// Blood red for player damage feedback.
pub const BLOOD: Color = Color::srgb(0.82, 0.12, 0.22);

// ----------------------------------------------------------------- text ----

/// Primary text: warm bone white.
pub const BONE: Color = Color::srgb(0.90, 0.86, 0.80);
/// Secondary text: ashen lavender grey.
pub const ASH: Color = Color::srgb(0.55, 0.50, 0.60);

// ------------------------------------------------------- HDR glow (bloom) ---

pub const SOUL_GLOW: Color = Color::srgb(0.7, 3.4, 3.0);
pub const EMBER_GLOW: Color = Color::srgb(3.6, 1.4, 0.35);
pub const VIOLET_GLOW: Color = Color::srgb(1.7, 0.8, 3.0);

// ------------------------------------------------------------------- UI ----

pub const PANEL_BG: Color = Color::srgba(0.06, 0.03, 0.10, 0.88);
pub const OVERLAY_BG: Color = Color::srgba(0.02, 0.01, 0.04, 0.72);
pub const BUTTON_BG: Color = Color::srgba(0.12, 0.06, 0.20, 0.85);
pub const BUTTON_BG_FOCUS: Color = Color::srgba(0.20, 0.10, 0.32, 0.95);
pub const BUTTON_BORDER: Color = Color::srgb(0.30, 0.18, 0.42);
pub const BUTTON_BORDER_FOCUS: Color = SOUL_TEAL;
pub const BAR_EMPTY: Color = Color::srgb(0.10, 0.06, 0.15);
