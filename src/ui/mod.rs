//! UI plugin: shared widgets/navigation and the in-run overlays.
//! (HUD, boss bars and damage numbers arrive in later stages.)

pub mod overlays;
pub mod widgets;

use bevy::prelude::*;

pub struct UiPlugin;

impl Plugin for UiPlugin {
    fn build(&self, app: &mut App) {
        app.add_plugins((widgets::WidgetsPlugin, overlays::OverlaysPlugin));
    }
}
