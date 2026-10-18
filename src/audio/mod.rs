//! Audio manager built on bevy_kira_audio.
//!
//! Three independent channels — music, sfx, ui — each with its own volume
//! from `GameSettings`. Gameplay code never touches kira directly: it sends a
//! `PlaySfx` message and declares which `MusicTrack` should be playing.

use std::time::Duration;

use bevy::platform::collections::HashMap;
use bevy::prelude::*;
use bevy_kira_audio::prelude::*;
use rand::Rng;

use crate::assets::{GameAssets, MusicTrack, Sfx};
use crate::settings::{GameSettings, VolumeChannel};
use crate::states::{AppState, InRun};

/// Channel marker resources (kira keys channels by type).
#[derive(Resource)]
pub struct MusicChannel;
#[derive(Resource)]
pub struct SfxChannel;
#[derive(Resource)]
pub struct UiChannel;

/// Ask the audio manager to play a sound effect.
#[derive(Message, Clone, Copy, Debug)]
pub struct PlaySfx {
    pub sfx: Sfx,
    /// Random +/- playback-rate range, e.g. 0.08 = up to 8% higher or lower.
    /// Keeps rapid repeats (bolts, hits) from sounding machine-gunned.
    pub pitch_jitter: f32,
    /// Extra volume offset in decibels (negative = quieter).
    pub volume_db: f32,
}

impl PlaySfx {
    /// Default: slight pitch variation, full volume.
    pub fn new(sfx: Sfx) -> Self {
        Self { sfx, pitch_jitter: 0.07, volume_db: 0.0 }
    }

    #[allow(dead_code)] // used by gameplay sounds from Stage 3
    pub fn exact(sfx: Sfx) -> Self {
        Self { sfx, pitch_jitter: 0.0, volume_db: 0.0 }
    }

    pub fn jitter(mut self, j: f32) -> Self {
        self.pitch_jitter = j;
        self
    }

    pub fn volume_db(mut self, db: f32) -> Self {
        self.volume_db = db;
        self
    }
}

/// Which track should be playing. Set it; the manager cross-fades.
#[derive(Resource, Default)]
pub struct MusicRequest(pub Option<MusicTrack>);

#[derive(Resource, Default)]
struct MusicPlaying(Option<MusicTrack>);

const MUSIC_FADE: Duration = Duration::from_millis(1200);
/// The same sound can start at most this many times per frame; stops a swarm
/// of simultaneous hits from clipping into one deafening spike.
const MAX_SAME_SFX_PER_FRAME: usize = 3;

pub struct GameAudioPlugin;

impl Plugin for GameAudioPlugin {
    fn build(&self, app: &mut App) {
        app.add_plugins(AudioPlugin)
            .add_audio_channel::<MusicChannel>()
            .add_audio_channel::<SfxChannel>()
            .add_audio_channel::<UiChannel>()
            .add_message::<PlaySfx>()
            .init_resource::<MusicRequest>()
            .init_resource::<MusicPlaying>()
            .add_systems(OnEnter(AppState::MainMenu), |mut r: ResMut<MusicRequest>| {
                r.0 = Some(MusicTrack::Menu)
            })
            .add_systems(OnEnter(InRun), |mut r: ResMut<MusicRequest>| r.0 = Some(MusicTrack::Battle))
            .add_systems(Update, (play_sfx, update_music, apply_volumes));
    }
}

/// Convert linear amplitude (0..1) to kira decibels; 0 maps to silence.
fn amplitude_to_db(amp: f32) -> f32 {
    if amp <= 0.001 { -60.0 } else { 20.0 * amp.log10() }
}

fn play_sfx(
    mut reader: MessageReader<PlaySfx>,
    assets: Res<GameAssets>,
    sfx: Res<AudioChannel<SfxChannel>>,
    ui: Res<AudioChannel<UiChannel>>,
) {
    let mut rng = rand::rng();
    let mut counts: HashMap<Sfx, usize> = HashMap::default();
    for msg in reader.read() {
        // Per-frame cap per sound.
        let count = counts.entry(msg.sfx).or_insert(0);
        *count += 1;
        if *count > MAX_SAME_SFX_PER_FRAME {
            continue;
        }

        let Some(handle) = assets.sfx(msg.sfx) else { continue };
        let rate = if msg.pitch_jitter > 0.0 {
            1.0 + rng.random_range(-msg.pitch_jitter..msg.pitch_jitter) as f64
        } else {
            1.0
        };
        let channel_is_ui = matches!(msg.sfx, Sfx::UiClick | Sfx::UiHover);
        let mut cmd = if channel_is_ui { ui.play(handle) } else { sfx.play(handle) };
        cmd.with_playback_rate(rate).with_volume(msg.volume_db);
    }
}

/// Cross-fade to the requested track when it changes.
fn update_music(
    request: Res<MusicRequest>,
    mut playing: ResMut<MusicPlaying>,
    assets: Res<GameAssets>,
    music: Res<AudioChannel<MusicChannel>>,
) {
    if request.0 == playing.0 {
        return;
    }
    music.stop().linear_fade_out(MUSIC_FADE);
    if let Some(track) = request.0 {
        if let Some(handle) = assets.music(track) {
            music.play(handle).looped().linear_fade_in(MUSIC_FADE);
        }
    }
    playing.0 = request.0;
}

/// Push settings volumes into the channels. Music ducks while paused.
fn apply_volumes(
    settings: Res<GameSettings>,
    state: Res<State<AppState>>,
    music: Res<AudioChannel<MusicChannel>>,
    sfx: Res<AudioChannel<SfxChannel>>,
    ui: Res<AudioChannel<UiChannel>>,
) {
    if !settings.is_changed() && !state.is_changed() {
        return;
    }
    let duck = if *state.get() == AppState::Paused { 0.35 } else { 1.0 };
    music.set_volume(amplitude_to_db(settings.amplitude(VolumeChannel::Music) * duck));
    sfx.set_volume(amplitude_to_db(settings.amplitude(VolumeChannel::Sfx)));
    ui.set_volume(amplitude_to_db(settings.amplitude(VolumeChannel::Ui)));
}
