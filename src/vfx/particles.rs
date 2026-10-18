//! A small, custom CPU particle system (bevy_hanabi does not yet support
//! Bevy 0.20). Each particle is a tinted sprite of a procedural texture.
//! A few hundred on screen at once cost well under a millisecond.

use bevy::prelude::*;

#[derive(Component, Clone)]
pub struct Particle {
    pub velocity: Vec2,
    pub age: f32,
    pub lifetime: f32,
    /// Fraction of velocity lost per second (0 = none).
    pub drag: f32,
    /// Constant acceleration (e.g. upward buoyancy for souls).
    pub gravity: Vec2,
    pub start_size: f32,
    pub end_size: f32,
    /// Tint; alpha is the peak opacity reached mid-life.
    pub color: Color,
    pub fade: Fade,
}

#[derive(Clone, Copy, Debug)]
pub enum Fade {
    /// Fully opaque at birth, fades linearly to 0.
    Out,
    /// Fades in then out (sine) — good for ambient motes.
    InOut,
}

impl Particle {
    pub fn new(velocity: Vec2, lifetime: f32, size: f32, color: Color) -> Self {
        Self {
            velocity,
            age: 0.0,
            lifetime: lifetime.max(0.01),
            drag: 0.0,
            gravity: Vec2::ZERO,
            start_size: size,
            end_size: size,
            color,
            fade: Fade::Out,
        }
    }

    #[allow(dead_code)] // used by hit sparks / death bursts (Stage 5)
    pub fn drag(mut self, d: f32) -> Self {
        self.drag = d;
        self
    }

    #[allow(dead_code)] // used by soul absorption (Stage 5)
    pub fn gravity(mut self, g: Vec2) -> Self {
        self.gravity = g;
        self
    }

    pub fn end_size(mut self, s: f32) -> Self {
        self.end_size = s;
        self
    }

    pub fn fade(mut self, f: Fade) -> Self {
        self.fade = f;
        self
    }
}

/// Horizontal sine sway layered on top of velocity (drifting embers/motes).
#[derive(Component, Clone, Copy)]
pub struct Sway {
    pub amplitude: f32,
    pub frequency: f32,
    pub phase: f32,
}

pub fn update_particles(
    mut commands: Commands,
    time: Res<Time>,
    mut q: Query<(Entity, &mut Particle, &mut Transform, &mut Sprite, Option<&Sway>)>,
) {
    let dt = time.delta_secs();
    for (e, mut p, mut tf, mut sprite, sway) in &mut q {
        p.age += dt;
        let t = p.age / p.lifetime;
        if t >= 1.0 {
            commands.entity(e).despawn();
            continue;
        }
        let g = p.gravity;
        p.velocity += g * dt;
        let damp = (1.0 - p.drag * dt).max(0.0);
        p.velocity *= damp;

        let mut step = p.velocity * dt;
        if let Some(s) = sway {
            // derivative of a*sin(w t + phase) gives smooth lateral drift
            let w = s.frequency * std::f32::consts::TAU;
            step.x += s.amplitude * w * (w * p.age + s.phase).cos() * dt;
        }
        tf.translation += step.extend(0.0);

        let size = p.start_size + (p.end_size - p.start_size) * t;
        sprite.custom_size = Some(Vec2::splat(size));
        let opacity = match p.fade {
            Fade::Out => 1.0 - t,
            Fade::InOut => (t * std::f32::consts::PI).sin(),
        };
        sprite.color = p.color.with_alpha(p.color.alpha() * opacity);
    }
}
