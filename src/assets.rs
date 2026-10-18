//! Every file the game loads from `assets/`, gathered in one resource.
//!
//! Handles are requested as soon as the app is built; the loading screen then
//! watches their real load state. Nothing here ever unwraps: a missing or
//! broken file is logged and the game carries on (missing sounds are silent,
//! a missing font falls back to Bevy's built-in one).

use bevy::platform::collections::HashMap;
use bevy::prelude::*;
use bevy_kira_audio::AudioSource;

pub const FONT_PATH: &str = "fonts/PirataOne-Regular.ttf";

/// Every sound effect. File names match `tools/gen_audio.py` and ASSETS.md.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum Sfx {
    UiClick,
    UiHover,
    SoulBolt,
    ChargeLoop,
    ChargedShot,
    Hit,
    HitHeavy,
    Dash,
    PlayerHurt,
    EnemyDeath,
    SoulAbsorb,
    SoulBurst,
    BossRoar,
    ChainSwing,
    GroundSlam,
    Teleport,
}

impl Sfx {
    pub const ALL: [Sfx; 16] = [
        Sfx::UiClick,
        Sfx::UiHover,
        Sfx::SoulBolt,
        Sfx::ChargeLoop,
        Sfx::ChargedShot,
        Sfx::Hit,
        Sfx::HitHeavy,
        Sfx::Dash,
        Sfx::PlayerHurt,
        Sfx::EnemyDeath,
        Sfx::SoulAbsorb,
        Sfx::SoulBurst,
        Sfx::BossRoar,
        Sfx::ChainSwing,
        Sfx::GroundSlam,
        Sfx::Teleport,
    ];

    pub fn path(self) -> &'static str {
        match self {
            Sfx::UiClick => "audio/sfx/ui_click.wav",
            Sfx::UiHover => "audio/sfx/ui_hover.wav",
            Sfx::SoulBolt => "audio/sfx/soul_bolt.wav",
            Sfx::ChargeLoop => "audio/sfx/charge_loop.wav",
            Sfx::ChargedShot => "audio/sfx/charged_shot.wav",
            Sfx::Hit => "audio/sfx/hit.wav",
            Sfx::HitHeavy => "audio/sfx/hit_heavy.wav",
            Sfx::Dash => "audio/sfx/dash.wav",
            Sfx::PlayerHurt => "audio/sfx/player_hurt.wav",
            Sfx::EnemyDeath => "audio/sfx/enemy_death.wav",
            Sfx::SoulAbsorb => "audio/sfx/soul_absorb.wav",
            Sfx::SoulBurst => "audio/sfx/soul_burst.wav",
            Sfx::BossRoar => "audio/sfx/boss_roar.wav",
            Sfx::ChainSwing => "audio/sfx/chain_swing.wav",
            Sfx::GroundSlam => "audio/sfx/ground_slam.wav",
            Sfx::Teleport => "audio/sfx/teleport.wav",
        }
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum MusicTrack {
    Menu,
    Battle,
}

impl MusicTrack {
    pub const ALL: [MusicTrack; 2] = [MusicTrack::Menu, MusicTrack::Battle];

    pub fn path(self) -> &'static str {
        match self {
            MusicTrack::Menu => "audio/music/menu_theme.wav",
            MusicTrack::Battle => "audio/music/battle_theme.wav",
        }
    }
}

#[derive(Resource)]
pub struct GameAssets {
    /// UI font. Swapped for Bevy's default font if the .ttf fails to load.
    pub font: Handle<Font>,
    sfx: HashMap<Sfx, Handle<AudioSource>>,
    music: HashMap<MusicTrack, Handle<AudioSource>>,
}

impl FromWorld for GameAssets {
    fn from_world(world: &mut World) -> Self {
        let server = world.resource::<AssetServer>();
        Self {
            font: server.load(FONT_PATH),
            sfx: Sfx::ALL.iter().map(|&s| (s, server.load(s.path()))).collect(),
            music: MusicTrack::ALL.iter().map(|&m| (m, server.load(m.path()))).collect(),
        }
    }
}

impl GameAssets {
    pub fn sfx(&self, sfx: Sfx) -> Option<Handle<AudioSource>> {
        self.sfx.get(&sfx).cloned()
    }

    pub fn music(&self, track: MusicTrack) -> Option<Handle<AudioSource>> {
        self.music.get(&track).cloned()
    }

    /// Every handle, type-erased, with a readable name for error messages.
    pub fn all_untyped(&self) -> Vec<(&'static str, UntypedHandle)> {
        let mut out = vec![(FONT_PATH, self.font.clone().untyped())];
        out.extend(Sfx::ALL.iter().filter_map(|&s| Some((s.path(), self.sfx.get(&s)?.clone().untyped()))));
        out.extend(
            MusicTrack::ALL.iter().filter_map(|&m| Some((m.path(), self.music.get(&m)?.clone().untyped()))),
        );
        out
    }
}

pub struct GameAssetsPlugin;

impl Plugin for GameAssetsPlugin {
    fn build(&self, app: &mut App) {
        // Constructed immediately so loading starts before the first frame.
        app.init_resource::<GameAssets>().add_systems(Update, font_fallback);
    }
}

/// If the custom font failed, point the resource *and* every existing text at
/// Bevy's built-in font so text never silently disappears.
fn font_fallback(
    server: Res<AssetServer>,
    mut assets: ResMut<GameAssets>,
    mut texts: Query<&mut TextFont>,
    mut warned: Local<bool>,
) {
    if assets.font == Handle::default() || !server.load_state(&assets.font).is_failed() {
        return;
    }
    if !*warned {
        warn!("Font {FONT_PATH} failed to load; using Bevy's default font instead.");
        *warned = true;
    }
    let failed = assets.font.clone();
    assets.font = Handle::default();
    for mut tf in &mut texts {
        if matches!(&tf.font, bevy::text::FontSource::Handle(h) if *h == failed) {
            tf.font = Handle::<Font>::default().into();
        }
    }
}
