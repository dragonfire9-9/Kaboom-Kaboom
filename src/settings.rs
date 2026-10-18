//! Player-facing settings (volume, fullscreen), persisted to a small text file.

use bevy::prelude::*;
use bevy::window::{MonitorSelection, PrimaryWindow, WindowMode};

/// Volume sliders go from 0 to this many notches.
pub const VOLUME_STEPS: u8 = 10;
/// Saved next to the executable's working directory. Human-editable.
const SETTINGS_FILE: &str = "soulbound_settings.cfg";

#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum VolumeChannel {
    Master,
    Music,
    Sfx,
    Ui,
}

impl VolumeChannel {
    pub const ALL: [VolumeChannel; 4] = [Self::Master, Self::Music, Self::Sfx, Self::Ui];

    pub fn label(self) -> &'static str {
        match self {
            Self::Master => "Master",
            Self::Music => "Music",
            Self::Sfx => "Effects",
            Self::Ui => "Interface",
        }
    }

    fn key(self) -> &'static str {
        match self {
            Self::Master => "master",
            Self::Music => "music",
            Self::Sfx => "sfx",
            Self::Ui => "ui",
        }
    }
}

#[derive(Resource, Clone, Debug, PartialEq)]
pub struct GameSettings {
    pub master: u8,
    pub music: u8,
    pub sfx: u8,
    pub ui: u8,
    pub fullscreen: bool,
}

impl Default for GameSettings {
    fn default() -> Self {
        Self { master: 8, music: 6, sfx: 8, ui: 7, fullscreen: false }
    }
}

impl GameSettings {
    pub fn volume(&self, ch: VolumeChannel) -> u8 {
        match ch {
            VolumeChannel::Master => self.master,
            VolumeChannel::Music => self.music,
            VolumeChannel::Sfx => self.sfx,
            VolumeChannel::Ui => self.ui,
        }
    }

    pub fn volume_mut(&mut self, ch: VolumeChannel) -> &mut u8 {
        match ch {
            VolumeChannel::Master => &mut self.master,
            VolumeChannel::Music => &mut self.music,
            VolumeChannel::Sfx => &mut self.sfx,
            VolumeChannel::Ui => &mut self.ui,
        }
    }

    /// Nudge a channel up/down by one notch, clamped to the valid range.
    pub fn step_volume(&mut self, ch: VolumeChannel, delta: i8) {
        let v = self.volume_mut(ch);
        *v = (*v as i16 + delta as i16).clamp(0, VOLUME_STEPS as i16) as u8;
    }

    /// Linear amplitude (0..1) for a channel, already multiplied by master.
    pub fn amplitude(&self, ch: VolumeChannel) -> f32 {
        let notch = |v: u8| v as f32 / VOLUME_STEPS as f32;
        let master = notch(self.master);
        match ch {
            VolumeChannel::Master => master,
            other => master * notch(self.volume(other)),
        }
    }

    /// Parse `key=value` lines. Unknown keys and bad values are ignored, so a
    /// hand-edited or outdated file never prevents the game from starting.
    fn parse(text: &str) -> Self {
        let mut s = Self::default();
        for line in text.lines() {
            let Some((key, value)) = line.split_once('=') else { continue };
            let (key, value) = (key.trim(), value.trim());
            if key == "fullscreen" {
                s.fullscreen = value == "true";
                continue;
            }
            for ch in VolumeChannel::ALL {
                if key == ch.key() {
                    if let Ok(v) = value.parse::<u8>() {
                        *s.volume_mut(ch) = v.min(VOLUME_STEPS);
                    }
                }
            }
        }
        s
    }

    fn serialize(&self) -> String {
        let mut out = String::new();
        for ch in VolumeChannel::ALL {
            out.push_str(&format!("{}={}\n", ch.key(), self.volume(ch)));
        }
        out.push_str(&format!("fullscreen={}\n", self.fullscreen));
        out
    }

    fn load_or_default() -> Self {
        match std::fs::read_to_string(SETTINGS_FILE) {
            Ok(text) => Self::parse(&text),
            Err(_) => Self::default(), // first launch: no file yet
        }
    }
}

pub struct SettingsPlugin;

impl Plugin for SettingsPlugin {
    fn build(&self, app: &mut App) {
        app.insert_resource(GameSettings::load_or_default())
            .add_systems(Update, (apply_window_mode, save_settings));
    }
}

/// Switch between windowed and borderless fullscreen whenever the setting changes.
fn apply_window_mode(settings: Res<GameSettings>, mut window: Single<&mut Window, With<PrimaryWindow>>) {
    if !settings.is_changed() {
        return;
    }
    let wanted = if settings.fullscreen {
        WindowMode::BorderlessFullscreen(MonitorSelection::Current)
    } else {
        WindowMode::Windowed
    };
    if window.mode != wanted {
        window.mode = wanted;
    }
}

/// Persist settings after the player changes them (not on the initial insert).
fn save_settings(settings: Res<GameSettings>) {
    if settings.is_changed() && !settings.is_added() {
        if let Err(e) = std::fs::write(SETTINGS_FILE, settings.serialize()) {
            warn!("Could not save settings to {SETTINGS_FILE}: {e}");
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn roundtrip() {
        let s = GameSettings { master: 3, music: 10, sfx: 0, ui: 5, fullscreen: true };
        assert_eq!(GameSettings::parse(&s.serialize()), s);
    }

    #[test]
    fn garbage_is_ignored() {
        let s = GameSettings::parse("master=99\nmusic=abc\nnonsense\n");
        assert_eq!(s.master, VOLUME_STEPS);
        assert_eq!(s.music, GameSettings::default().music);
    }
}
